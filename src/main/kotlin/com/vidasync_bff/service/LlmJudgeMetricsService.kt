package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.response.LlmJudgeMetricsBucketResponse
import com.vidasync_bff.dto.response.LlmJudgeMetricsCountResponse
import com.vidasync_bff.dto.response.LlmJudgeMetricsDailyPointResponse
import com.vidasync_bff.dto.response.LlmJudgeMetricsFiltersResponse
import com.vidasync_bff.dto.response.LlmJudgeMetricsResponse
import com.vidasync_bff.dto.response.LlmJudgeMetricsSummaryResponse
import com.vidasync_bff.dto.response.LlmJudgeRecentEvaluationResponse
import com.vidasync_bff.dto.response.SupabaseLlmJudgeEvaluationRow
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Service
class LlmJudgeMetricsService(
    private val supabaseClient: SupabaseClient
) {

    private val log = LoggerFactory.getLogger(LlmJudgeMetricsService::class.java)
    private val tableName = "llm_judge_evaluations"
    private val evaluationTypeRef = object : ParameterizedTypeReference<List<SupabaseLlmJudgeEvaluationRow>>() {}
    private val selectColumns = listOf(
        "evaluation_id",
        "created_at",
        "feature",
        "judge_status",
        "idioma",
        "pipeline",
        "handler",
        "source_model",
        "source_duration_ms",
        "source_total_tokens",
        "judge_duration_ms",
        "judge_total_tokens",
        "judge_overall_score",
        "judge_decision",
        "judge_rejection_reasons"
    ).joinToString(",")

    fun getMetrics(
        actorUserId: String,
        days: Int?,
        startDate: String?,
        endDate: String?,
        feature: String?,
        pipeline: String?,
        handler: String?,
        idioma: String?,
        sourceModel: String?,
        judgeStatus: String?,
        judgeDecision: String?
    ): LlmJudgeMetricsResponse {
        validateInternalAccess(actorUserId)

        val filters = resolveFilters(
            days = days,
            startDate = startDate,
            endDate = endDate,
            feature = feature,
            pipeline = pipeline,
            handler = handler,
            idioma = idioma,
            sourceModel = sourceModel,
            judgeStatus = judgeStatus,
            judgeDecision = judgeDecision
        )

        log.info(
            "Buscando metricas do llm judge: actorUserId={}, startDate={}, endDate={}, feature={}, pipeline={}, handler={}, idioma={}, sourceModel={}, judgeStatus={}, judgeDecision={}",
            actorUserId.trim(),
            filters.startDate,
            filters.endDate,
            filters.feature,
            filters.pipeline,
            filters.handler,
            filters.idioma,
            filters.sourceModel,
            filters.judgeStatus,
            filters.judgeDecision
        )

        val rows = loadRows(filters)
        val summaryAggregates = buildAggregates(rows)

        return LlmJudgeMetricsResponse(
            filters = LlmJudgeMetricsFiltersResponse(
                startDate = filters.startDate.toString(),
                endDate = filters.endDate.toString(),
                days = filters.windowDays,
                feature = filters.feature,
                pipeline = filters.pipeline,
                handler = filters.handler,
                idioma = filters.idioma,
                sourceModel = filters.sourceModel,
                judgeStatus = filters.judgeStatus,
                judgeDecision = filters.judgeDecision
            ),
            summary = LlmJudgeMetricsSummaryResponse(
                totalEvaluations = summaryAggregates.totalEvaluations,
                completedCount = summaryAggregates.completedCount,
                pendingCount = summaryAggregates.pendingCount,
                failedCount = summaryAggregates.failedCount,
                approvedCount = summaryAggregates.approvedCount,
                rejectedCount = summaryAggregates.rejectedCount,
                completionRatePercent = summaryAggregates.completionRatePercent,
                failureRatePercent = summaryAggregates.failureRatePercent,
                approvalRatePercent = summaryAggregates.approvalRatePercent,
                averageOverallScore = summaryAggregates.averageOverallScore,
                averageSourceDurationMs = summaryAggregates.averageSourceDurationMs,
                averageJudgeDurationMs = summaryAggregates.averageJudgeDurationMs,
                averageSourceTotalTokens = summaryAggregates.averageSourceTotalTokens,
                averageJudgeTotalTokens = summaryAggregates.averageJudgeTotalTokens,
                latestEvaluationAt = rows.firstOrNull()?.createdAt,
                oldestEvaluationAt = rows.lastOrNull()?.createdAt
            ),
            byFeature = buildBuckets(rows) { it.feature },
            byPipeline = buildBuckets(rows) { it.pipeline },
            byHandler = buildBuckets(rows) { it.handler },
            byIdioma = buildBuckets(rows) { it.idioma },
            bySourceModel = buildBuckets(rows) { it.sourceModel },
            daily = buildDailySeries(filters.startDate, filters.endDate, rows),
            topRejectionReasons = buildTopRejectionReasons(rows),
            recentEvaluations = rows.take(15).map(::toRecentEvaluation)
        )
    }

    private fun loadRows(filters: ResolvedFilters): List<SupabaseLlmJudgeEvaluationRow> {
        val queryParams = mutableMapOf(
            "and" to "(created_at.gte.${filters.startInclusiveUtc},created_at.lt.${filters.endExclusiveUtc})",
            "order" to "created_at.desc,evaluation_id.desc"
        )

        filters.feature?.let { queryParams["feature"] = "eq.$it" }
        filters.pipeline?.let { queryParams["pipeline"] = "eq.$it" }
        filters.handler?.let { queryParams["handler"] = "eq.$it" }
        filters.idioma?.let { queryParams["idioma"] = "eq.$it" }
        filters.sourceModel?.let { queryParams["source_model"] = "eq.$it" }
        filters.judgeStatus?.let { queryParams["judge_status"] = "eq.$it" }
        filters.judgeDecision?.let { queryParams["judge_decision"] = "eq.$it" }

        return supabaseClient.get(
            table = tableName,
            select = selectColumns,
            queryParams = queryParams,
            typeRef = evaluationTypeRef
        ) ?: emptyList()
    }

    private fun buildBuckets(
        rows: List<SupabaseLlmJudgeEvaluationRow>,
        selector: (SupabaseLlmJudgeEvaluationRow) -> String?
    ): List<LlmJudgeMetricsBucketResponse> {
        return rows
            .groupBy { normalizeBucketKey(selector(it)) }
            .entries
            .map { (key, bucketRows) ->
                val aggregates = buildAggregates(bucketRows)
                LlmJudgeMetricsBucketResponse(
                    key = key,
                    totalEvaluations = aggregates.totalEvaluations,
                    completedCount = aggregates.completedCount,
                    pendingCount = aggregates.pendingCount,
                    failedCount = aggregates.failedCount,
                    approvedCount = aggregates.approvedCount,
                    rejectedCount = aggregates.rejectedCount,
                    completionRatePercent = aggregates.completionRatePercent,
                    failureRatePercent = aggregates.failureRatePercent,
                    approvalRatePercent = aggregates.approvalRatePercent,
                    averageOverallScore = aggregates.averageOverallScore,
                    averageSourceDurationMs = aggregates.averageSourceDurationMs,
                    averageJudgeDurationMs = aggregates.averageJudgeDurationMs,
                    averageSourceTotalTokens = aggregates.averageSourceTotalTokens,
                    averageJudgeTotalTokens = aggregates.averageJudgeTotalTokens
                )
            }
            .sortedWith(
                compareByDescending<LlmJudgeMetricsBucketResponse> { it.totalEvaluations }
                    .thenBy { it.key }
            )
    }

    private fun buildDailySeries(
        startDate: LocalDate,
        endDate: LocalDate,
        rows: List<SupabaseLlmJudgeEvaluationRow>
    ): List<LlmJudgeMetricsDailyPointResponse> {
        val rowsByDate = rows.groupBy { OffsetDateTime.parse(it.createdAt).withOffsetSameInstant(ZoneOffset.UTC).toLocalDate() }
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1

        return (0 until totalDays).map { index ->
            val currentDate = startDate.plusDays(index.toLong())
            val bucketRows = rowsByDate[currentDate].orEmpty()
            val aggregates = buildAggregates(bucketRows)

            LlmJudgeMetricsDailyPointResponse(
                date = currentDate.toString(),
                totalEvaluations = aggregates.totalEvaluations,
                completedCount = aggregates.completedCount,
                pendingCount = aggregates.pendingCount,
                failedCount = aggregates.failedCount,
                approvedCount = aggregates.approvedCount,
                rejectedCount = aggregates.rejectedCount,
                completionRatePercent = aggregates.completionRatePercent,
                failureRatePercent = aggregates.failureRatePercent,
                approvalRatePercent = aggregates.approvalRatePercent,
                averageOverallScore = aggregates.averageOverallScore
            )
        }
    }

    private fun buildTopRejectionReasons(rows: List<SupabaseLlmJudgeEvaluationRow>): List<LlmJudgeMetricsCountResponse> {
        return rows
            .asSequence()
            .flatMap { row ->
                row.judgeRejectionReasons.asSequence()
                    .mapNotNull { value -> value?.toString()?.trim()?.takeIf { it.isNotBlank() } }
            }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(10)
            .map { (key, count) -> LlmJudgeMetricsCountResponse(key = key, count = count) }
    }

    private fun toRecentEvaluation(row: SupabaseLlmJudgeEvaluationRow): LlmJudgeRecentEvaluationResponse {
        return LlmJudgeRecentEvaluationResponse(
            evaluationId = row.evaluationId,
            createdAt = row.createdAt,
            feature = row.feature,
            judgeStatus = row.judgeStatus,
            judgeDecision = row.judgeDecision,
            judgeOverallScore = roundOrNull(row.judgeOverallScore),
            idioma = row.idioma,
            pipeline = row.pipeline,
            handler = row.handler,
            sourceModel = row.sourceModel,
            sourceDurationMs = roundOrNull(row.sourceDurationMs),
            judgeDurationMs = roundOrNull(row.judgeDurationMs),
            sourceTotalTokens = row.sourceTotalTokens,
            judgeTotalTokens = row.judgeTotalTokens
        )
    }

    private fun buildAggregates(rows: List<SupabaseLlmJudgeEvaluationRow>): Aggregates {
        val total = rows.size
        val completed = rows.count { it.judgeStatus == STATUS_COMPLETED }
        val pending = rows.count { it.judgeStatus == STATUS_PENDING }
        val failed = rows.count { it.judgeStatus == STATUS_FAILED }
        val approved = rows.count { it.judgeDecision == DECISION_APPROVED }
        val rejected = rows.count { it.judgeDecision == DECISION_REJECTED }

        return Aggregates(
            totalEvaluations = total,
            completedCount = completed,
            pendingCount = pending,
            failedCount = failed,
            approvedCount = approved,
            rejectedCount = rejected,
            completionRatePercent = percentageOrZero(completed, total),
            failureRatePercent = percentageOrZero(failed, total),
            approvalRatePercent = percentageOrNull(approved, approved + rejected),
            averageOverallScore = averageOf(rows.mapNotNull { it.judgeOverallScore }),
            averageSourceDurationMs = averageOf(rows.mapNotNull { it.sourceDurationMs }),
            averageJudgeDurationMs = averageOf(rows.mapNotNull { it.judgeDurationMs }),
            averageSourceTotalTokens = averageOf(rows.mapNotNull { it.sourceTotalTokens?.toDouble() }),
            averageJudgeTotalTokens = averageOf(rows.mapNotNull { it.judgeTotalTokens?.toDouble() })
        )
    }

    private fun resolveFilters(
        days: Int?,
        startDate: String?,
        endDate: String?,
        feature: String?,
        pipeline: String?,
        handler: String?,
        idioma: String?,
        sourceModel: String?,
        judgeStatus: String?,
        judgeDecision: String?
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
            feature = normalizeOptional(feature),
            pipeline = normalizeOptional(pipeline),
            handler = normalizeOptional(handler),
            idioma = normalizeOptional(idioma),
            sourceModel = normalizeOptional(sourceModel),
            judgeStatus = normalizeEnum(judgeStatus, "judgeStatus", setOf(STATUS_PENDING, STATUS_COMPLETED, STATUS_FAILED)),
            judgeDecision = normalizeEnum(judgeDecision, "judgeDecision", setOf(DECISION_APPROVED, DECISION_REJECTED))
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

    private fun normalizeOptional(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun normalizeEnum(value: String?, fieldName: String, allowedValues: Set<String>): String? {
        val normalized = normalizeOptional(value)?.lowercase() ?: return null
        if (normalized !in allowedValues) {
            throw IllegalArgumentException("$fieldName invalido")
        }
        return normalized
    }

    private fun normalizeBucketKey(value: String?): String {
        return normalizeOptional(value) ?: "nao_informado"
    }

    private fun validateInternalAccess(actorUserId: String) {
        if (actorUserId.trim().isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "header X-User-Id obrigatorio para auditoria")
        }
    }

    private fun percentageOrZero(numerator: Int, denominator: Int): Double {
        if (denominator == 0) {
            return 0.0
        }
        return round((numerator.toDouble() / denominator.toDouble()) * 100.0)
    }

    private fun percentageOrNull(numerator: Int, denominator: Int): Double? {
        if (denominator == 0) {
            return null
        }
        return round((numerator.toDouble() / denominator.toDouble()) * 100.0)
    }

    private fun averageOf(values: List<Double>): Double? {
        if (values.isEmpty()) {
            return null
        }
        return round(values.average())
    }

    private fun roundOrNull(value: Double?): Double? {
        return value?.let(::round)
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
        val feature: String?,
        val pipeline: String?,
        val handler: String?,
        val idioma: String?,
        val sourceModel: String?,
        val judgeStatus: String?,
        val judgeDecision: String?
    )

    private data class Aggregates(
        val totalEvaluations: Int,
        val completedCount: Int,
        val pendingCount: Int,
        val failedCount: Int,
        val approvedCount: Int,
        val rejectedCount: Int,
        val completionRatePercent: Double,
        val failureRatePercent: Double,
        val approvalRatePercent: Double?,
        val averageOverallScore: Double?,
        val averageSourceDurationMs: Double?,
        val averageJudgeDurationMs: Double?,
        val averageSourceTotalTokens: Double?,
        val averageJudgeTotalTokens: Double?
    )

    companion object {
        private const val DEFAULT_WINDOW_DAYS = 7
        private const val MAX_WINDOW_DAYS = 366
        private const val STATUS_PENDING = "pending"
        private const val STATUS_COMPLETED = "completed"
        private const val STATUS_FAILED = "failed"
        private const val DECISION_APPROVED = "approved"
        private const val DECISION_REJECTED = "rejected"
    }
}
