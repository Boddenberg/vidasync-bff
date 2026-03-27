package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.response.SupabaseTelemetryAgentRunRow
import com.vidasync_bff.dto.response.SupabaseTelemetryAgentRunsDailyRow
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
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Service
class TelemetryService(
    private val supabaseClient: SupabaseClient,
    @Value("\${internal.admin.api-key:}") private val internalAdminApiKey: String,
    @Value("\${telemetry.dashboard.max-raw-runs:10000}") private val maxRawRunsForMetrics: Int
) {

    private val log = LoggerFactory.getLogger(TelemetryService::class.java)

    private val writeTypeRef = object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
    private val runsTypeRef = object : ParameterizedTypeReference<List<SupabaseTelemetryAgentRunRow>>() {}
    private val dailyRunsTypeRef = object : ParameterizedTypeReference<List<SupabaseTelemetryAgentRunsDailyRow>>() {}
    private val dailyModelsTypeRef = object : ParameterizedTypeReference<List<SupabaseTelemetryLlmModelsDailyRow>>() {}

    private val runsSelect = listOf(
        "run_id",
        "request_id",
        "trace_id",
        "agent",
        "endpoint",
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

    private val runsDailySelect = listOf(
        "day_utc",
        "agent",
        "endpoint",
        "run_count",
        "success_count",
        "error_count",
        "timeout_count",
        "total_cost_usd",
        "total_tokens",
        "avg_duration_ms",
        "p95_duration_ms"
    ).joinToString(",")

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
            insertRows("telemetry_agent_runs", listOf(toRunMap(snapshot.run)))
            if (snapshot.llmCalls.isNotEmpty()) {
                insertRows("telemetry_llm_calls", snapshot.llmCalls.map(::toLlmCallMap))
            }
            if (snapshot.toolCalls.isNotEmpty()) {
                insertRows("telemetry_tool_calls", snapshot.toolCalls.map(::toToolCallMap))
            }
            if (snapshot.stageEvents.isNotEmpty()) {
                insertRows("telemetry_stage_events", snapshot.stageEvents.map(::toStageEventMap))
            }
        } catch (ex: Exception) {
            log.warn("Falha ao fazer flush da telemetry do run {}: {}", snapshot.run.runId, ex.message, ex)
        }
    }

    fun getMetrics(
        actorUserId: String,
        providedInternalApiKey: String?,
        days: Int?,
        startDate: String?,
        endDate: String?,
        agent: String?
    ): TelemetryMetricsResponse {
        validateInternalAccess(actorUserId, providedInternalApiKey)

        val filters = resolveFilters(
            days = days,
            startDate = startDate,
            endDate = endDate,
            agent = agent,
            status = null
        )

        val rawRuns = loadRuns(filters, maxRawRunsForMetrics)
        val dailyRuns = loadDailyRuns(filters)
        val dailyModels = loadDailyModels(filters)

        val dailyByDay = dailyRuns.groupBy { it.dayUtc }
        val modelsByDay = dailyModels.groupBy { it.dayUtc }

        val daily = ((0 until filters.windowDays).map { filters.startDate.plusDays(it.toLong()).toString() })
            .map { day ->
                val runRows = dailyByDay[day].orEmpty()
                val modelRows = modelsByDay[day].orEmpty()
                TelemetryMetricsDailyPointResponse(
                    dayUtc = day,
                    runCount = runRows.sumOf { it.runCount },
                    successCount = runRows.sumOf { it.successCount },
                    errorCount = runRows.sumOf { it.errorCount },
                    timeoutCount = runRows.sumOf { it.timeoutCount },
                    totalCostUsd = round(runRows.sumOf { it.totalCostUsd ?: 0.0 }),
                    inputTokens = modelRows.sumOf { it.inputTokens ?: 0 },
                    outputTokens = modelRows.sumOf { it.outputTokens ?: 0 },
                    totalTokens = modelRows.sumOf { it.totalTokens ?: 0 },
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
                    successCount = rows.count { it.status == STATUS_SUCCESS },
                    errorCount = rows.count { it.status == STATUS_ERROR },
                    timeoutCount = rows.count { it.timeout },
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

        val byModel = dailyModels
            .groupBy { normalizeBucket(it.model) to normalizeBucket(it.agent) }
            .map { (key, rows) ->
                TelemetryMetricsModelBreakdownResponse(
                    model = key.first,
                    agent = key.second,
                    llmCallCount = rows.sumOf { it.llmCallCount },
                    totalCostUsd = round(rows.sumOf { it.totalCostUsd ?: 0.0 }),
                    inputTokens = rows.sumOf { it.inputTokens ?: 0 },
                    outputTokens = rows.sumOf { it.outputTokens ?: 0 },
                    totalTokens = rows.sumOf { it.totalTokens ?: 0 },
                    averageDurationMs = weightedAverage(rows.map { it.avgDurationMs to it.llmCallCount }),
                    p95DurationMs = weightedAverage(rows.map { it.p95DurationMs to it.llmCallCount })
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
        providedInternalApiKey: String?,
        days: Int?,
        startDate: String?,
        endDate: String?,
        agent: String?,
        status: String?,
        limit: Int?
    ): TelemetryRunsResponse {
        validateInternalAccess(actorUserId, providedInternalApiKey)

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

    private fun loadRuns(filters: ResolvedFilters, limit: Int): List<SupabaseTelemetryAgentRunRow> {
        val queryParams = mutableMapOf(
            "and" to "(started_at.gte.${filters.startInclusiveUtc},started_at.lt.${filters.endExclusiveUtc})",
            "order" to "started_at.desc,run_id.desc",
            "limit" to limit.toString()
        )
        filters.agent?.let { queryParams["agent"] = "eq.$it" }
        filters.status?.let { queryParams["status"] = "eq.$it" }

        return supabaseClient.get(
            table = "telemetry_agent_runs",
            select = runsSelect,
            queryParams = queryParams,
            typeRef = runsTypeRef
        ) ?: emptyList()
    }

    private fun loadDailyRuns(filters: ResolvedFilters): List<SupabaseTelemetryAgentRunsDailyRow> {
        val queryParams = mutableMapOf(
            "and" to "(day_utc.gte.${filters.startDate},day_utc.lte.${filters.endDate})",
            "order" to "day_utc.asc,agent.asc,endpoint.asc"
        )
        filters.agent?.let { queryParams["agent"] = "eq.$it" }

        return supabaseClient.get(
            table = "telemetry_agent_runs_daily",
            select = runsDailySelect,
            queryParams = queryParams,
            typeRef = dailyRunsTypeRef
        ) ?: emptyList()
    }

    private fun loadDailyModels(filters: ResolvedFilters): List<SupabaseTelemetryLlmModelsDailyRow> {
        val queryParams = mutableMapOf(
            "and" to "(day_utc.gte.${filters.startDate},day_utc.lte.${filters.endDate})",
            "order" to "day_utc.asc,agent.asc,model.asc"
        )
        filters.agent?.let { queryParams["agent"] = "eq.$it" }

        return supabaseClient.get(
            table = "telemetry_llm_models_daily",
            select = llmModelsDailySelect,
            queryParams = queryParams,
            typeRef = dailyModelsTypeRef
        ) ?: emptyList()
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

    private fun validateInternalAccess(actorUserId: String, providedInternalApiKey: String?) {
        if (actorUserId.trim().isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "header X-User-Id obrigatorio para auditoria")
        }
        if (internalAdminApiKey.isBlank()) {
            return
        }
        if (providedInternalApiKey.isNullOrBlank() || providedInternalApiKey != internalAdminApiKey) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "internal api key invalida")
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

    private fun toRunMap(run: AgentTelemetryRunRecord): Map<String, Any?> {
        return mapOf(
            "run_id" to run.runId,
            "request_id" to run.requestId,
            "trace_id" to run.traceId,
            "agent" to run.agent,
            "endpoint" to run.endpoint,
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

    private data class ResolvedFilters(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val windowDays: Int,
        val startInclusiveUtc: String,
        val endExclusiveUtc: String,
        val agent: String?,
        val status: String?
    )

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
