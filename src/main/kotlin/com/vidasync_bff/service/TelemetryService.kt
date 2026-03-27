package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.response.SupabaseTelemetryAgentRunRow
import com.vidasync_bff.dto.response.SupabaseTelemetryAgentRunsDailyRow
import com.vidasync_bff.dto.response.SupabaseTelemetryLlmCallRow
import com.vidasync_bff.dto.response.SupabaseTelemetryLlmModelsDailyRow
import com.vidasync_bff.dto.response.TelemetryMetricsAgentBreakdownResponse
import com.vidasync_bff.dto.response.TelemetryMetricsDailyPointResponse
import com.vidasync_bff.dto.response.TelemetryMetricsFiltersResponse
import com.vidasync_bff.dto.response.TelemetryMetricsModelBreakdownResponse
import com.vidasync_bff.dto.response.TelemetryMetricsResponse
import com.vidasync_bff.dto.response.TelemetryMetricsSummaryResponse
import com.vidasync_bff.dto.response.TelemetryRecentRunResponse
import com.vidasync_bff.dto.response.TelemetryRunsResponse
import com.vidasync_bff.observability.AgentTelemetryLlmCallRecord
import com.vidasync_bff.observability.AgentTelemetryRunRecord
import com.vidasync_bff.observability.AgentTelemetrySnapshot
import com.vidasync_bff.observability.AgentTelemetryStageEventRecord
import com.vidasync_bff.observability.AgentTelemetryToolCallRecord
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Service
class TelemetryService(
    private val supabaseClient: SupabaseClient,
    @org.springframework.beans.factory.annotation.Value("\${telemetry.dashboard.max-raw-runs:10000}") private val maxRawRunsForMetrics: Int
) {

    private val log = LoggerFactory.getLogger(TelemetryService::class.java)
    @Volatile private var runsReadSchema = RunsReadSchema.CURRENT
    @Volatile private var runsWriteSchema = RunsWriteSchema.CURRENT
    @Volatile private var llmCallsWriteSchema = ChildWriteSchema.CURRENT
    @Volatile private var toolCallsWriteSchema = ChildWriteSchema.CURRENT
    @Volatile private var stageEventsWriteSchema = ChildWriteSchema.CURRENT
    @Volatile private var dailyRunsReadSchema = DailyRunsReadSchema.CURRENT
    @Volatile private var runsTableColumn = RunsPathColumn.ENTRYPOINT
    @Volatile private var dailyRunsColumn = RunsPathColumn.ENTRYPOINT

    private val writeTypeRef = object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
    private val runsTypeRef = object : ParameterizedTypeReference<List<SupabaseTelemetryAgentRunRow>>() {}
    private val dailyRunsTypeRef = object : ParameterizedTypeReference<List<SupabaseTelemetryAgentRunsDailyRow>>() {}
    private val llmCallsTypeRef = object : ParameterizedTypeReference<List<SupabaseTelemetryLlmCallRow>>() {}
    private val dailyModelsTypeRef = object : ParameterizedTypeReference<List<SupabaseTelemetryLlmModelsDailyRow>>() {}

    private val llmModelsDailySelect = listOf(
        "day_utc",
        "agent",
        "model",
        "llm_call_count",
        "total_cost_usd",
        "input_tokens",
        "output_tokens",
        "total_tokens",
        "avg_duration_ms",
        "p95_duration_ms"
    ).joinToString(",")

    fun flushQuietly(snapshot: AgentTelemetrySnapshot?) {
        if (snapshot == null) return

        try {
            insertRunRows(listOf(snapshot.run))
            if (snapshot.llmCalls.isNotEmpty()) {
                insertLlmCallRows(snapshot.llmCalls)
            }
            if (snapshot.toolCalls.isNotEmpty()) {
                insertToolCallRows(snapshot.toolCalls)
            }
            if (snapshot.stageEvents.isNotEmpty()) {
                insertStageEventRows(snapshot.stageEvents)
            }
        } catch (ex: Exception) {
            log.warn("Falha ao fazer flush da telemetry do run {}: {}", snapshot.run.runId, ex.message, ex)
        }
    }

    fun getMetrics(
        actorUserId: String,
        days: Int?,
        startDate: String?,
        endDate: String?,
        agent: String?
    ): TelemetryMetricsResponse {
        validateInternalAccess(actorUserId)

        val filters = resolveFilters(
            days = days,
            startDate = startDate,
            endDate = endDate,
            agent = agent,
            status = null
        )

        val rawRuns = loadRuns(filters, maxRawRunsForMetrics)
        val dailyRuns = loadDailyRuns(filters)
        val rawLlmCalls = loadLlmCalls(rawRuns.map { it.runId })

        val dailyByDay = dailyRuns.groupBy { normalizeDayUtc(it.dayUtc) }
        val llmCallsByDay = rawLlmCalls.groupBy { normalizeDayUtc(it.createdAt) }
        val runAgentById = rawRuns.associate { it.runId to normalizeBucket(it.agent) }

        val daily = ((0 until filters.windowDays).map { filters.startDate.plusDays(it.toLong()).toString() })
            .map { day ->
                val runRows = dailyByDay[day].orEmpty()
                val llmCallRows = llmCallsByDay[day].orEmpty()
                val hasDailyTokenBreakdown = runRows.any { it.inputTokens != null || it.outputTokens != null }
                TelemetryMetricsDailyPointResponse(
                    dayUtc = day,
                    runCount = runRows.sumOf { it.runCount },
                    successCount = runRows.sumOf(::resolveSuccessCount),
                    errorCount = runRows.sumOf(::resolveErrorCount),
                    timeoutCount = runRows.sumOf(::resolveTimeoutCount),
                    totalCostUsd = round(runRows.sumOf { it.totalCostUsd ?: 0.0 }),
                    inputTokens = if (hasDailyTokenBreakdown) {
                        runRows.sumOf { it.inputTokens ?: 0 }
                    } else {
                        llmCallRows.sumOf { it.inputTokens ?: 0 }
                    },
                    outputTokens = if (hasDailyTokenBreakdown) {
                        runRows.sumOf { it.outputTokens ?: 0 }
                    } else {
                        llmCallRows.sumOf { it.outputTokens ?: 0 }
                    },
                    totalTokens = if (runRows.any { it.totalTokens != null }) {
                        runRows.sumOf { it.totalTokens ?: 0 }
                    } else {
                        llmCallRows.sumOf { it.totalTokens ?: 0 }
                    },
                    averageDurationMs = weightedAverage(runRows.map { it.avgDurationMs to it.runCount }),
                    p95DurationMs = weightedAverage(runRows.map { it.p95DurationMs to it.runCount })
                )
            }

        val byAgent = rawRuns
            .groupBy { normalizeBucket(it.agent) }
            .map { (agentKey, rows) ->
                TelemetryMetricsAgentBreakdownResponse(
                    agent = agentKey,
                    runCount = rows.size,
                    successCount = rows.count { isSuccessStatus(it.status) },
                    errorCount = rows.count { isErrorStatus(it.status, it.timeout) },
                    timeoutCount = rows.count { isTimeoutStatus(it.status, it.timeout) },
                    totalCostUsd = round(rows.sumOf { it.totalCostUsd ?: 0.0 }),
                    totalTokens = rows.sumOf { it.totalTokens ?: 0 },
                    averageDurationMs = average(rows.mapNotNull { it.durationMs }),
                    p95DurationMs = percentile95(rows.mapNotNull { it.durationMs })
                )
            }
            .sortedWith(
                compareByDescending<TelemetryMetricsAgentBreakdownResponse> { it.runCount }
                    .thenBy { it.agent }
            )

        val byModel = rawLlmCalls
            .groupBy { normalizeBucket(it.model) to normalizeBucket(runAgentById[it.runId]) }
            .map { (key, rows) ->
                TelemetryMetricsModelBreakdownResponse(
                    model = key.first,
                    agent = key.second,
                    llmCallCount = rows.size,
                    totalCostUsd = round(rows.sumOf { it.costUsd ?: 0.0 }),
                    inputTokens = rows.sumOf { it.inputTokens ?: 0 },
                    outputTokens = rows.sumOf { it.outputTokens ?: 0 },
                    totalTokens = rows.sumOf { it.totalTokens ?: 0 },
                    averageDurationMs = average(rows.mapNotNull { it.durationMs }),
                    p95DurationMs = percentile95(rows.mapNotNull { it.durationMs })
                )
            }
            .sortedWith(
                compareByDescending<TelemetryMetricsModelBreakdownResponse> { it.llmCallCount }
                    .thenBy { it.model }
                    .thenBy { it.agent }
            )

        val summary = TelemetryMetricsSummaryResponse(
            totalRuns = daily.sumOf { it.runCount },
            successCount = daily.sumOf { it.successCount },
            errorCount = daily.sumOf { it.errorCount },
            timeoutCount = daily.sumOf { it.timeoutCount },
            totalCostUsd = round(daily.sumOf { it.totalCostUsd }),
            inputTokens = daily.sumOf { it.inputTokens },
            outputTokens = daily.sumOf { it.outputTokens },
            totalTokens = daily.sumOf { it.totalTokens },
            averageDurationMs = average(rawRuns.mapNotNull { it.durationMs }),
            p95DurationMs = percentile95(rawRuns.mapNotNull { it.durationMs }),
            latestRunAt = rawRuns.firstOrNull()?.startedAt,
            oldestRunAt = rawRuns.lastOrNull()?.startedAt
        )

        return TelemetryMetricsResponse(
            filters = TelemetryMetricsFiltersResponse(
                startDate = filters.startDate.toString(),
                endDate = filters.endDate.toString(),
                days = filters.windowDays,
                agent = filters.agent,
                model = null,
                status = null
            ),
            summary = summary,
            daily = daily,
            byAgent = byAgent,
            byModel = byModel
        )
    }

    fun getRecentRuns(
        actorUserId: String,
        days: Int?,
        startDate: String?,
        endDate: String?,
        agent: String?,
        status: String?,
        limit: Int?
    ): TelemetryRunsResponse {
        validateInternalAccess(actorUserId)

        val filters = resolveFilters(
            days = days,
            startDate = startDate,
            endDate = endDate,
            agent = agent,
            status = status
        )
        val resolvedLimit = normalizeLimit(limit)
        val rows = loadRuns(filters, resolvedLimit)

        return TelemetryRunsResponse(
            filters = TelemetryMetricsFiltersResponse(
                startDate = filters.startDate.toString(),
                endDate = filters.endDate.toString(),
                days = filters.windowDays,
                agent = filters.agent,
                model = null,
                status = filters.status
            ),
            limit = resolvedLimit,
            recentRuns = rows.map(::toRecentRunResponse)
        )
    }

    private fun insertRows(table: String, rows: List<Map<String, Any?>>) {
        if (rows.isEmpty()) return
        supabaseClient.post(
            table = table,
            body = rows,
            typeRef = writeTypeRef
        )
    }

    private fun insertLlmCallRows(calls: List<AgentTelemetryLlmCallRecord>) {
        if (calls.isEmpty()) return

        if (llmCallsWriteSchema == ChildWriteSchema.CURRENT) {
            try {
                insertRows("telemetry_llm_calls", calls.map(::toCurrentLlmCallMap))
                return
            } catch (ex: Exception) {
                if (!shouldFallbackToLegacyLlmCalls(ex)) {
                    throw ex
                }

                log.warn(
                    "Falha ao gravar telemetry_llm_calls no schema atual. Tentando fallback legado. erro={}",
                    ex.message
                )
                llmCallsWriteSchema = ChildWriteSchema.LEGACY
            }
        }

        insertRows("telemetry_llm_calls", calls.map(::toLlmCallMap))
        llmCallsWriteSchema = ChildWriteSchema.LEGACY
    }

    private fun insertToolCallRows(calls: List<AgentTelemetryToolCallRecord>) {
        if (calls.isEmpty()) return

        if (toolCallsWriteSchema == ChildWriteSchema.CURRENT) {
            try {
                insertRows("telemetry_tool_calls", calls.map(::toCurrentToolCallMap))
                return
            } catch (ex: Exception) {
                if (!shouldFallbackToLegacyToolCalls(ex)) {
                    throw ex
                }

                log.warn(
                    "Falha ao gravar telemetry_tool_calls no schema atual. Tentando fallback legado. erro={}",
                    ex.message
                )
                toolCallsWriteSchema = ChildWriteSchema.LEGACY
            }
        }

        insertRows("telemetry_tool_calls", calls.map(::toToolCallMap))
        toolCallsWriteSchema = ChildWriteSchema.LEGACY
    }

    private fun insertStageEventRows(events: List<AgentTelemetryStageEventRecord>) {
        if (events.isEmpty()) return

        if (stageEventsWriteSchema == ChildWriteSchema.CURRENT) {
            try {
                insertRows("telemetry_stage_events", events.map(::toCurrentStageEventMap))
                return
            } catch (ex: Exception) {
                if (!shouldFallbackToLegacyStageEvents(ex)) {
                    throw ex
                }

                log.warn(
                    "Falha ao gravar telemetry_stage_events no schema atual. Tentando fallback legado. erro={}",
                    ex.message
                )
                stageEventsWriteSchema = ChildWriteSchema.LEGACY
            }
        }

        insertRows("telemetry_stage_events", events.map(::toStageEventMap))
        stageEventsWriteSchema = ChildWriteSchema.LEGACY
    }

    private fun insertRunRows(runs: List<AgentTelemetryRunRecord>) {
        if (runs.isEmpty()) return

        if (runsWriteSchema == RunsWriteSchema.CURRENT) {
            try {
                postCurrentRunRows(runs)
                return
            } catch (ex: Exception) {
                if (!shouldFallbackToLegacyRunWrites(ex)) {
                    throw ex
                }

                log.warn(
                    "Falha ao gravar telemetry_agent_runs no schema atual. Tentando fallback legado. erro={}",
                    ex.message
                )
                runsWriteSchema = RunsWriteSchema.LEGACY
            }
        }

        postLegacyRunRows(runs)
    }

    private fun postCurrentRunRows(runs: List<AgentTelemetryRunRecord>) {
        supabaseClient.post(
            table = "telemetry_agent_runs",
            body = runs.map(::toCurrentRunMap),
            typeRef = writeTypeRef
        )
        runsWriteSchema = RunsWriteSchema.CURRENT
        runsTableColumn = RunsPathColumn.ENTRYPOINT
    }

    private fun postLegacyRunRows(runs: List<AgentTelemetryRunRecord>) {
        val preferredColumn = runsTableColumn
        try {
            postLegacyRunRows(runs, preferredColumn)
        } catch (ex: Exception) {
            if (!shouldRetryRunColumn(ex, preferredColumn)) {
                throw ex
            }

            val fallbackColumn = preferredColumn.fallback()
            log.warn(
                "Falha ao gravar telemetry_agent_runs com coluna {}. Tentando fallback para {}. erro={}",
                preferredColumn.dbColumn,
                fallbackColumn.dbColumn,
                ex.message
            )
            postLegacyRunRows(runs, fallbackColumn)
            runsTableColumn = fallbackColumn
        }
    }

    private fun postLegacyRunRows(runs: List<AgentTelemetryRunRecord>, column: RunsPathColumn) {
        supabaseClient.post(
            table = "telemetry_agent_runs",
            body = runs.map { toLegacyRunMap(it, column) },
            typeRef = writeTypeRef
        )
        runsWriteSchema = RunsWriteSchema.LEGACY
        runsTableColumn = column
    }

    private fun loadRuns(filters: ResolvedFilters, limit: Int): List<SupabaseTelemetryAgentRunRow> {
        if (runsReadSchema == RunsReadSchema.LEGACY) {
            return loadLegacyRuns(filters, limit)
        }

        return try {
            loadCurrentRuns(filters, limit)
        } catch (ex: Exception) {
            if (!shouldFallbackToLegacyRuns(ex)) {
                throw ex
            }

            log.warn(
                "Falha ao consultar telemetry_agent_runs no schema atual. Tentando fallback legado. erro={}",
                ex.message
            )
            runsReadSchema = RunsReadSchema.LEGACY
            loadLegacyRuns(filters, limit)
        }
    }

    private fun loadCurrentRuns(
        filters: ResolvedFilters,
        limit: Int
    ): List<SupabaseTelemetryAgentRunRow> {
        val queryParams = mutableMapOf(
            "and" to "(started_at.gte.${filters.startInclusiveUtc},started_at.lt.${filters.endExclusiveUtc})",
            "order" to "started_at.desc,run_id.desc",
            "limit" to limit.toString()
        )
        filters.agent?.let { queryParams["agent"] = "eq.$it" }
        filters.status?.let { queryParams["status"] = "eq.$it" }

        return supabaseClient.get(
            table = "telemetry_agent_runs",
            select = buildCurrentRunsSelect(),
            queryParams = queryParams,
            typeRef = runsTypeRef
        ) ?: emptyList()
    }

    private fun loadLegacyRuns(filters: ResolvedFilters, limit: Int): List<SupabaseTelemetryAgentRunRow> {
        val preferredColumn = runsTableColumn
        return try {
            getLegacyRuns(filters, limit, preferredColumn)
        } catch (ex: Exception) {
            if (!shouldRetryRunColumn(ex, preferredColumn)) {
                throw ex
            }

            val fallbackColumn = preferredColumn.fallback()
            log.warn(
                "Falha ao consultar telemetry_agent_runs com coluna {}. Tentando fallback para {}. erro={}",
                preferredColumn.dbColumn,
                fallbackColumn.dbColumn,
                ex.message
            )
            getLegacyRuns(filters, limit, fallbackColumn)
        }
    }

    private fun getLegacyRuns(
        filters: ResolvedFilters,
        limit: Int,
        column: RunsPathColumn
    ): List<SupabaseTelemetryAgentRunRow> {
        val queryParams = mutableMapOf(
            "and" to "(started_at.gte.${filters.startInclusiveUtc},started_at.lt.${filters.endExclusiveUtc})",
            "order" to "started_at.desc,run_id.desc",
            "limit" to limit.toString()
        )
        filters.agent?.let { queryParams["agent"] = "eq.$it" }
        filters.status?.let { queryParams["status"] = "eq.$it" }

        val rows = supabaseClient.get(
            table = "telemetry_agent_runs",
            select = buildLegacyRunsSelect(column),
            queryParams = queryParams,
            typeRef = runsTypeRef
        ) ?: emptyList()
        runsTableColumn = column
        return rows
    }

    private fun loadDailyRuns(filters: ResolvedFilters): List<SupabaseTelemetryAgentRunsDailyRow> {
        if (dailyRunsReadSchema == DailyRunsReadSchema.LEGACY) {
            return loadLegacyDailyRuns(filters)
        }

        return try {
            loadCurrentDailyRuns(filters)
        } catch (ex: Exception) {
            if (!shouldFallbackToLegacyDailyRuns(ex)) {
                throw ex
            }

            log.warn(
                "Falha ao consultar telemetry_agent_runs_daily no schema atual. Tentando fallback legado. erro={}",
                ex.message
            )
            dailyRunsReadSchema = DailyRunsReadSchema.LEGACY
            loadLegacyDailyRuns(filters)
        }
    }

    private fun loadCurrentDailyRuns(filters: ResolvedFilters): List<SupabaseTelemetryAgentRunsDailyRow> {
        val queryParams = mutableMapOf(
            "and" to "(day_utc.gte.${filters.startDate},day_utc.lte.${filters.endDate})",
            "order" to "day_utc.asc,agent.asc,status.asc"
        )
        filters.agent?.let { queryParams["agent"] = "eq.$it" }

        return supabaseClient.get(
            table = "telemetry_agent_runs_daily",
            select = buildCurrentDailyRunsSelect(),
            queryParams = queryParams,
            typeRef = dailyRunsTypeRef
        ) ?: emptyList()
    }

    private fun loadLegacyDailyRuns(filters: ResolvedFilters): List<SupabaseTelemetryAgentRunsDailyRow> {
        val preferredColumn = dailyRunsColumn
        return try {
            getLegacyDailyRuns(filters, preferredColumn)
        } catch (ex: Exception) {
            if (!shouldRetryRunColumn(ex, preferredColumn)) {
                throw ex
            }

            val fallbackColumn = preferredColumn.fallback()
            log.warn(
                "Falha ao consultar telemetry_agent_runs_daily com coluna {}. Tentando fallback para {}. erro={}",
                preferredColumn.dbColumn,
                fallbackColumn.dbColumn,
                ex.message
            )
            getLegacyDailyRuns(filters, fallbackColumn)
        }
    }

    private fun getLegacyDailyRuns(
        filters: ResolvedFilters,
        column: RunsPathColumn
    ): List<SupabaseTelemetryAgentRunsDailyRow> {
        val queryParams = mutableMapOf(
            "and" to "(day_utc.gte.${filters.startDate},day_utc.lte.${filters.endDate})",
            "order" to "day_utc.asc,agent.asc,${column.dbColumn}.asc"
        )
        filters.agent?.let { queryParams["agent"] = "eq.$it" }

        val rows = supabaseClient.get(
            table = "telemetry_agent_runs_daily",
            select = buildLegacyDailyRunsSelect(column),
            queryParams = queryParams,
            typeRef = dailyRunsTypeRef
        ) ?: emptyList()
        dailyRunsColumn = column
        return rows
    }

    private fun loadDailyModels(filters: ResolvedFilters): List<SupabaseTelemetryLlmModelsDailyRow> {
        val queryParams = mutableMapOf(
            "and" to "(day_utc.gte.${filters.startDate},day_utc.lte.${filters.endDate})",
            "order" to "day_utc.asc,agent.asc,model.asc"
        )
        filters.agent?.let { queryParams["agent"] = "eq.$it" }

        return try {
            supabaseClient.get(
                table = "telemetry_llm_models_daily",
                select = llmModelsDailySelect,
                queryParams = queryParams,
                typeRef = dailyModelsTypeRef
            ) ?: emptyList()
        } catch (ex: Exception) {
            log.warn("Falha ao consultar telemetry_llm_models_daily. Seguindo com lista vazia. erro={}", ex.message)
            emptyList()
        }
    }

    private fun loadLlmCalls(runIds: List<String>): List<SupabaseTelemetryLlmCallRow> {
        if (runIds.isEmpty()) {
            return emptyList()
        }

        val select = listOf(
            "run_id",
            "provider",
            "operation",
            "model",
            "status",
            "input_tokens",
            "output_tokens",
            "total_tokens",
            "duration_ms",
            "cost_usd",
            "created_at"
        ).joinToString(",")

        return runIds
            .distinct()
            .chunked(200)
            .flatMap { chunk ->
                supabaseClient.get(
                    table = "telemetry_llm_calls",
                    select = select,
                    queryParams = mapOf(
                        "run_id" to "in.(${chunk.joinToString(",")})",
                        "order" to "created_at.desc"
                    ),
                    typeRef = llmCallsTypeRef
                ) ?: emptyList()
            }
    }

    private fun resolveFilters(
        days: Int?,
        startDate: String?,
        endDate: String?,
        agent: String?,
        status: String?
    ): ResolvedFilters {
        val resolvedEndDate = parseDate(endDate, "endDate")
            ?: LocalDate.now(ZoneOffset.UTC)
        val resolvedStartDate = parseDate(startDate, "startDate")
            ?: resolvedEndDate.minusDays((normalizeDays(days) - 1).toLong())

        if (resolvedStartDate.isAfter(resolvedEndDate)) {
            throw IllegalArgumentException("startDate deve ser menor ou igual a endDate")
        }

        val windowDays = ChronoUnit.DAYS.between(resolvedStartDate, resolvedEndDate).toInt() + 1
        if (windowDays > MAX_WINDOW_DAYS) {
            throw IllegalArgumentException("intervalo maximo suportado e de $MAX_WINDOW_DAYS dias")
        }

        return ResolvedFilters(
            startDate = resolvedStartDate,
            endDate = resolvedEndDate,
            windowDays = windowDays,
            startInclusiveUtc = resolvedStartDate.atStartOfDay().atOffset(ZoneOffset.UTC).toString(),
            endExclusiveUtc = resolvedEndDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).toString(),
            agent = normalizeOptional(agent),
            status = normalizeStatus(status)
        )
    }

    private fun parseDate(value: String?, fieldName: String): LocalDate? {
        val normalized = normalizeOptional(value) ?: return null
        return try {
            LocalDate.parse(normalized)
        } catch (_: Exception) {
            throw IllegalArgumentException("$fieldName deve estar no formato YYYY-MM-DD")
        }
    }

    private fun normalizeDays(days: Int?): Int {
        val resolved = days ?: DEFAULT_WINDOW_DAYS
        if (resolved !in 1..MAX_WINDOW_DAYS) {
            throw IllegalArgumentException("days deve estar entre 1 e $MAX_WINDOW_DAYS")
        }
        return resolved
    }

    private fun normalizeLimit(limit: Int?): Int {
        val resolved = limit ?: DEFAULT_RUNS_LIMIT
        if (resolved !in 1..MAX_RUNS_LIMIT) {
            throw IllegalArgumentException("limit deve estar entre 1 e $MAX_RUNS_LIMIT")
        }
        return resolved
    }

    private fun normalizeOptional(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun normalizeStatus(value: String?): String? {
        val normalized = normalizeOptional(value)?.lowercase() ?: return null
        if (normalized !in setOf(STATUS_SUCCESS, STATUS_ERROR, STATUS_TIMEOUT)) {
            throw IllegalArgumentException("status invalido")
        }
        return normalized
    }

    private fun normalizeBucket(value: String?): String {
        return normalizeOptional(value) ?: "unknown"
    }

    private fun normalizeDayUtc(value: String): String {
        return try {
            OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDate().toString()
        } catch (_: Exception) {
            value.substringBefore('T')
        }
    }

    private fun validateInternalAccess(actorUserId: String) {
        if (actorUserId.trim().isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "header X-User-Id obrigatorio para auditoria")
        }
    }

    private fun toRecentRunResponse(row: SupabaseTelemetryAgentRunRow): TelemetryRecentRunResponse {
        return TelemetryRecentRunResponse(
            runId = row.runId,
            requestId = row.requestId,
            traceId = row.traceId,
            agent = normalizeBucket(row.agent),
            endpoint = normalizeBucket(row.endpoint),
            httpMethod = row.httpMethod,
            httpStatus = row.httpStatus,
            status = row.status,
            timeout = row.timeout,
            durationMs = row.durationMs?.let(::round),
            totalCostUsd = row.totalCostUsd?.let(::round),
            inputTokens = row.inputTokens,
            outputTokens = row.outputTokens,
            totalTokens = row.totalTokens,
            llmCallCount = row.llmCallCount,
            toolCallCount = row.toolCallCount,
            stageEventCount = row.stageEventCount,
            errorMessage = row.errorMessage,
            startedAt = row.startedAt,
            finishedAt = row.finishedAt,
            requestContext = row.requestContext
        )
    }

    private fun toCurrentRunMap(run: AgentTelemetryRunRecord): Map<String, Any?> {
        return mapOf(
            "run_id" to run.runId,
            "request_id" to run.requestId,
            "trace_id" to run.traceId,
            "agent" to run.agent,
            "entrypoint" to run.endpoint,
            "http_method" to run.httpMethod,
            "http_status_code" to run.httpStatus,
            "status" to run.status,
            "timeout" to run.timeout,
            "duration_ms" to run.durationMs,
            "total_cost_usd" to run.totalCostUsd,
            "total_input_tokens" to run.inputTokens,
            "total_output_tokens" to run.outputTokens,
            "total_tokens" to run.totalTokens,
            "llm_calls_count" to run.llmCallCount,
            "tool_calls_count" to run.toolCallCount,
            "stage_events_count" to run.stageEventCount,
            "error_message" to run.errorMessage,
            "metadata_json" to run.requestContext,
            "started_at" to run.startedAt,
            "finished_at" to run.finishedAt
        )
    }

    private fun toLegacyRunMap(run: AgentTelemetryRunRecord, column: RunsPathColumn): Map<String, Any?> {
        return mapOf(
            "run_id" to run.runId,
            "request_id" to run.requestId,
            "trace_id" to run.traceId,
            "agent" to run.agent,
            column.dbColumn to run.endpoint,
            "http_method" to run.httpMethod,
            "http_status" to run.httpStatus,
            "status" to run.status,
            "timeout" to run.timeout,
            "duration_ms" to run.durationMs,
            "total_cost_usd" to run.totalCostUsd,
            "input_tokens" to run.inputTokens,
            "output_tokens" to run.outputTokens,
            "total_tokens" to run.totalTokens,
            "llm_call_count" to run.llmCallCount,
            "tool_call_count" to run.toolCallCount,
            "stage_event_count" to run.stageEventCount,
            "error_message" to run.errorMessage,
            "request_context" to run.requestContext,
            "started_at" to run.startedAt,
            "finished_at" to run.finishedAt
        )
    }

    private fun toCurrentLlmCallMap(call: AgentTelemetryLlmCallRecord): Map<String, Any?> {
        return mapOf(
            "call_id" to call.callId,
            "run_id" to call.runId,
            "request_id" to call.requestId,
            "trace_id" to call.traceId,
            "created_at" to call.createdAt,
            "provider" to call.provider,
            "operation" to call.operation,
            "model" to call.model,
            "provider_response_id" to call.providerResponseId,
            "status" to call.status,
            "timeout" to resolveTimeoutFlag(call.status, call.metadata["timeout"]),
            "duration_ms" to call.durationMs,
            "input_tokens" to call.inputTokens,
            "output_tokens" to call.outputTokens,
            "total_tokens" to call.totalTokens,
            "cost_usd" to call.costUsd,
            "prompt_chars" to extractIntMetadata(call.metadata, "promptChars", "prompt_chars"),
            "output_chars" to extractIntMetadata(call.metadata, "outputChars", "output_chars"),
            "error_type" to call.metadata["errorType"]?.toString(),
            "error_message" to call.errorMessage,
            "prompt_preview_masked" to call.metadata["promptPreviewMasked"]?.toString(),
            "response_preview_masked" to call.metadata["responsePreviewMasked"]?.toString(),
            "metadata_json" to call.metadata
        )
    }

    private fun toLlmCallMap(call: AgentTelemetryLlmCallRecord): Map<String, Any?> {
        return mapOf(
            "call_id" to call.callId,
            "run_id" to call.runId,
            "request_id" to call.requestId,
            "trace_id" to call.traceId,
            "agent" to call.agent,
            "provider" to call.provider,
            "operation" to call.operation,
            "model" to call.model,
            "status" to call.status,
            "input_tokens" to call.inputTokens,
            "output_tokens" to call.outputTokens,
            "total_tokens" to call.totalTokens,
            "duration_ms" to call.durationMs,
            "cost_usd" to call.costUsd,
            "provider_response_id" to call.providerResponseId,
            "endpoint" to call.endpoint,
            "error_message" to call.errorMessage,
            "metadata" to call.metadata,
            "created_at" to call.createdAt
        )
    }

    private fun toCurrentToolCallMap(call: AgentTelemetryToolCallRecord): Map<String, Any?> {
        return mapOf(
            "tool_call_id" to call.toolCallId,
            "run_id" to call.runId,
            "request_id" to call.requestId,
            "trace_id" to call.traceId,
            "created_at" to call.createdAt,
            "tool_name" to call.toolName,
            "status" to call.status,
            "duration_ms" to call.durationMs,
            "timeout" to resolveTimeoutFlag(call.status, call.metadata["timeout"]),
            "error_type" to call.metadata["errorType"]?.toString(),
            "warnings_count" to (extractIntMetadata(call.metadata, "warningsCount", "warnings_count") ?: 0),
            "precisa_revisao" to (extractBooleanMetadata(call.metadata, "needsReview", "precisa_revisao") ?: false),
            "metadata_json" to call.metadata
        )
    }

    private fun toToolCallMap(call: AgentTelemetryToolCallRecord): Map<String, Any?> {
        return mapOf(
            "tool_call_id" to call.toolCallId,
            "run_id" to call.runId,
            "request_id" to call.requestId,
            "trace_id" to call.traceId,
            "agent" to call.agent,
            "tool_name" to call.toolName,
            "status" to call.status,
            "duration_ms" to call.durationMs,
            "error_message" to call.errorMessage,
            "metadata" to call.metadata,
            "created_at" to call.createdAt
        )
    }

    private fun toCurrentStageEventMap(event: AgentTelemetryStageEventRecord): Map<String, Any?> {
        return mapOf(
            "event_id" to event.eventId,
            "run_id" to event.runId,
            "request_id" to event.requestId,
            "trace_id" to event.traceId,
            "created_at" to event.createdAt,
            "event_type" to event.eventType,
            "name" to event.stage,
            "status" to event.status,
            "duration_ms" to event.durationMs,
            "timeout" to resolveTimeoutFlag(event.status, event.payload["timeout"]),
            "flow" to event.payload["flow"]?.toString(),
            "engine" to event.payload["engine"]?.toString(),
            "reason" to event.detail,
            "used" to extractBooleanMetadata(event.payload, "used"),
            "documents_count" to extractIntMetadata(event.payload, "documentsCount", "documents_count"),
            "metadata_json" to event.payload
        )
    }

    private fun toStageEventMap(event: AgentTelemetryStageEventRecord): Map<String, Any?> {
        return mapOf(
            "event_id" to event.eventId,
            "run_id" to event.runId,
            "request_id" to event.requestId,
            "trace_id" to event.traceId,
            "agent" to event.agent,
            "stage" to event.stage,
            "event_type" to event.eventType,
            "status" to event.status,
            "duration_ms" to event.durationMs,
            "detail" to event.detail,
            "payload" to event.payload,
            "created_at" to event.createdAt
        )
    }

    private fun resolveTimeoutFlag(status: String?, rawTimeout: Any?): Boolean {
        extractBooleanMetadata(mapOf("timeout" to rawTimeout), "timeout")?.let { return it }
        val normalizedStatus = normalizeOptional(status)?.lowercase() ?: return false
        return normalizedStatus in setOf("timeout", "timed_out")
    }

    private fun extractIntMetadata(metadata: Map<String, Any?>, vararg keys: String): Int? {
        return keys.asSequence()
            .mapNotNull { key ->
                when (val value = metadata[key]) {
                    is Int -> value
                    is Long -> value.toInt()
                    is Double -> value.toInt()
                    is Float -> value.toInt()
                    is Number -> value.toInt()
                    is String -> value.trim().toIntOrNull()
                    else -> null
                }
            }
            .firstOrNull()
    }

    private fun extractBooleanMetadata(metadata: Map<String, Any?>, vararg keys: String): Boolean? {
        return keys.asSequence()
            .mapNotNull { key ->
                when (val value = metadata[key]) {
                    is Boolean -> value
                    is String -> when (value.trim().lowercase()) {
                        "true" -> true
                        "false" -> false
                        else -> null
                    }
                    else -> null
                }
            }
            .firstOrNull()
    }

    private fun average(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        return round(values.average())
    }

    private fun weightedAverage(values: List<Pair<Double?, Int>>): Double? {
        val filtered = values.filter { (value, weight) -> value != null && weight > 0 }
        if (filtered.isEmpty()) return null
        val totalWeight = filtered.sumOf { it.second }
        if (totalWeight == 0) return null
        val weightedSum = filtered.sumOf { (value, weight) -> (value ?: 0.0) * weight.toDouble() }
        return round(weightedSum / totalWeight.toDouble())
    }

    private fun percentile95(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val index = ((sorted.size - 1) * 0.95).toInt()
        return round(sorted[index])
    }

    private fun round(value: Double): Double {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    private fun buildCurrentRunsSelect(): String {
        return listOf(
            "run_id",
            "request_id",
            "trace_id",
            "agent",
            "endpoint:entrypoint",
            "http_method",
            "http_status:http_status_code",
            "status",
            "timeout",
            "duration_ms",
            "input_tokens:total_input_tokens",
            "output_tokens:total_output_tokens",
            "total_tokens",
            "total_cost_usd",
            "llm_call_count:llm_calls_count",
            "tool_call_count:tool_calls_count",
            "stage_event_count:stage_events_count",
            "error_message",
            "request_context:metadata_json",
            "started_at",
            "finished_at"
        ).joinToString(",")
    }

    private fun buildLegacyRunsSelect(column: RunsPathColumn): String {
        return listOf(
            "run_id",
            "request_id",
            "trace_id",
            "agent",
            aliasAsEndpoint(column),
            "http_method",
            "http_status",
            "status",
            "timeout",
            "duration_ms",
            "input_tokens",
            "output_tokens",
            "total_tokens",
            "total_cost_usd",
            "llm_call_count",
            "tool_call_count",
            "stage_event_count",
            "error_message",
            "request_context",
            "started_at",
            "finished_at"
        ).joinToString(",")
    }

    private fun buildCurrentDailyRunsSelect(): String {
        return listOf(
            "day_utc",
            "agent",
            "status",
            "run_count:runs_count",
            "llm_call_count:llm_calls_count",
            "tool_call_count:tool_calls_count",
            "stage_event_count:stage_events_count",
            "total_cost_usd",
            "input_tokens:total_input_tokens",
            "output_tokens:total_output_tokens",
            "total_tokens",
            "avg_duration_ms",
            "review_rate"
        ).joinToString(",")
    }

    private fun buildLegacyDailyRunsSelect(column: RunsPathColumn): String {
        return listOf(
            "day_utc",
            "agent",
            aliasAsEndpoint(column),
            "run_count",
            "success_count",
            "error_count",
            "timeout_count",
            "total_cost_usd",
            "total_tokens",
            "avg_duration_ms",
            "p95_duration_ms"
        ).joinToString(",")
    }

    private fun aliasAsEndpoint(column: RunsPathColumn): String {
        return if (column == RunsPathColumn.ENDPOINT) "endpoint" else "endpoint:${column.dbColumn}"
    }

    private fun shouldFallbackToLegacyLlmCalls(ex: Exception): Boolean {
        val errorText = buildErrorText(ex)
        return errorText.contains("telemetry_llm_calls") && (
            errorText.contains("timeout") ||
                errorText.contains("prompt_chars") ||
                errorText.contains("output_chars") ||
                errorText.contains("error_type") ||
                errorText.contains("prompt_preview_masked") ||
                errorText.contains("response_preview_masked") ||
                errorText.contains("metadata_json")
            )
    }

    private fun shouldFallbackToLegacyToolCalls(ex: Exception): Boolean {
        val errorText = buildErrorText(ex)
        return errorText.contains("telemetry_tool_calls") && (
            errorText.contains("timeout") ||
                errorText.contains("error_type") ||
                errorText.contains("warnings_count") ||
                errorText.contains("precisa_revisao") ||
                errorText.contains("metadata_json")
            )
    }

    private fun shouldFallbackToLegacyStageEvents(ex: Exception): Boolean {
        val errorText = buildErrorText(ex)
        return errorText.contains("telemetry_stage_events") && (
            errorText.contains("name") ||
                errorText.contains("timeout") ||
                errorText.contains("flow") ||
                errorText.contains("engine") ||
                errorText.contains("reason") ||
                errorText.contains("used") ||
                errorText.contains("documents_count") ||
                errorText.contains("metadata_json")
            )
    }

    private fun shouldFallbackToLegacyRunWrites(ex: Exception): Boolean {
        val errorText = buildErrorText(ex)
        return errorText.contains("telemetry_agent_runs") && (
            errorText.contains("entrypoint") ||
                errorText.contains("http_status_code") ||
                errorText.contains("total_input_tokens") ||
                errorText.contains("total_output_tokens") ||
                errorText.contains("llm_calls_count") ||
                errorText.contains("tool_calls_count") ||
                errorText.contains("stage_events_count") ||
                errorText.contains("metadata_json")
            )
    }

    private fun shouldFallbackToLegacyRuns(ex: Exception): Boolean {
        val errorText = buildErrorText(ex)
        return errorText.contains("telemetry_agent_runs") && (
            errorText.contains("entrypoint") ||
                errorText.contains("http_status_code") ||
                errorText.contains("total_input_tokens") ||
                errorText.contains("total_output_tokens") ||
                errorText.contains("llm_calls_count") ||
                errorText.contains("tool_calls_count") ||
                errorText.contains("stage_events_count") ||
                errorText.contains("metadata_json")
            )
    }

    private fun shouldFallbackToLegacyDailyRuns(ex: Exception): Boolean {
        val errorText = buildErrorText(ex)
        return errorText.contains("telemetry_agent_runs_daily") && (
            errorText.contains("runs_count") ||
                errorText.contains("llm_calls_count") ||
                errorText.contains("tool_calls_count") ||
                errorText.contains("stage_events_count") ||
                errorText.contains("total_input_tokens") ||
                errorText.contains("total_output_tokens") ||
                errorText.contains("review_rate")
            )
    }

    private fun shouldRetryRunColumn(ex: Exception, attemptedColumn: RunsPathColumn): Boolean {
        val errorText = buildErrorText(ex)
        return errorText.contains(attemptedColumn.dbColumn) &&
            (errorText.contains("telemetry_agent_runs") || errorText.contains("telemetry_agent_runs_daily")) &&
            (
                errorText.contains("does not exist") ||
                    errorText.contains("schema cache") ||
                    errorText.contains("could not find")
                )
    }

    private fun buildErrorText(ex: Exception): String {
        val responseBody = (ex as? RestClientResponseException)?.responseBodyAsString.orEmpty()
        return "${ex.message.orEmpty()} $responseBody".lowercase()
    }

    private fun resolveSuccessCount(row: SupabaseTelemetryAgentRunsDailyRow): Int {
        if (row.successCount > 0 || row.errorCount > 0 || row.timeoutCount > 0) {
            return row.successCount
        }
        return if (isSuccessStatus(row.status)) row.runCount else 0
    }

    private fun resolveErrorCount(row: SupabaseTelemetryAgentRunsDailyRow): Int {
        if (row.successCount > 0 || row.errorCount > 0 || row.timeoutCount > 0) {
            return row.errorCount
        }
        return if (isErrorStatus(row.status, false)) row.runCount else 0
    }

    private fun resolveTimeoutCount(row: SupabaseTelemetryAgentRunsDailyRow): Int {
        if (row.successCount > 0 || row.errorCount > 0 || row.timeoutCount > 0) {
            return row.timeoutCount
        }
        return if (isTimeoutStatus(row.status, false)) row.runCount else 0
    }

    private fun isSuccessStatus(status: String?): Boolean {
        val normalized = normalizeOptional(status)?.lowercase() ?: return false
        return normalized in setOf("success", "completed", "ok", "partial", "parcial", "done")
    }

    private fun isTimeoutStatus(status: String?, timeoutFlag: Boolean): Boolean {
        if (timeoutFlag) return true
        val normalized = normalizeOptional(status)?.lowercase() ?: return false
        return normalized in setOf("timeout", "timed_out")
    }

    private fun isErrorStatus(status: String?, timeoutFlag: Boolean): Boolean {
        if (isTimeoutStatus(status, timeoutFlag)) return false
        if (isSuccessStatus(status)) return false
        return normalizeOptional(status) != null
    }

    private data class ResolvedFilters(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val windowDays: Int,
        val startInclusiveUtc: String,
        val endExclusiveUtc: String,
        val agent: String?,
        val status: String?
    )

    private enum class RunsReadSchema {
        CURRENT,
        LEGACY
    }

    private enum class RunsWriteSchema {
        CURRENT,
        LEGACY
    }

    private enum class ChildWriteSchema {
        CURRENT,
        LEGACY
    }

    private enum class DailyRunsReadSchema {
        CURRENT,
        LEGACY
    }

    private enum class RunsPathColumn(val dbColumn: String) {
        ENTRYPOINT("entrypoint"),
        ENDPOINT("endpoint");

        fun fallback(): RunsPathColumn {
            return if (this == ENTRYPOINT) ENDPOINT else ENTRYPOINT
        }
    }

    companion object {
        private const val DEFAULT_WINDOW_DAYS = 7
        private const val MAX_WINDOW_DAYS = 366
        private const val DEFAULT_RUNS_LIMIT = 20
        private const val MAX_RUNS_LIMIT = 100
        private const val STATUS_SUCCESS = "success"
        private const val STATUS_ERROR = "error"
        private const val STATUS_TIMEOUT = "timeout"
    }
}
