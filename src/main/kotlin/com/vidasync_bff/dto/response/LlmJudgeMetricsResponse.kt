package com.vidasync_bff.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupabaseLlmJudgeEvaluationRow(
    @JsonProperty("evaluation_id") val evaluationId: String,
    @JsonProperty("created_at") val createdAt: String,
    val feature: String,
    @JsonProperty("judge_status") val judgeStatus: String,
    val idioma: String = "pt-BR",
    val pipeline: String? = null,
    val handler: String? = null,
    @JsonProperty("source_model") val sourceModel: String,
    @JsonProperty("source_duration_ms") val sourceDurationMs: Double? = null,
    @JsonProperty("source_total_tokens") val sourceTotalTokens: Int? = null,
    @JsonProperty("judge_duration_ms") val judgeDurationMs: Double? = null,
    @JsonProperty("judge_total_tokens") val judgeTotalTokens: Int? = null,
    @JsonProperty("judge_overall_score") val judgeOverallScore: Double? = null,
    @JsonProperty("judge_decision") val judgeDecision: String? = null,
    @JsonProperty("judge_rejection_reasons") val judgeRejectionReasons: List<Any?> = emptyList()
)

data class LlmJudgeMetricsFiltersResponse(
    val startDate: String,
    val endDate: String,
    val days: Int,
    val feature: String?,
    val pipeline: String?,
    val handler: String?,
    val idioma: String?,
    val sourceModel: String?,
    val judgeStatus: String?,
    val judgeDecision: String?
)

data class LlmJudgeMetricsSummaryResponse(
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
    val averageJudgeTotalTokens: Double?,
    val latestEvaluationAt: String?,
    val oldestEvaluationAt: String?
)

data class LlmJudgeMetricsBucketResponse(
    val key: String,
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

data class LlmJudgeMetricsDailyPointResponse(
    val date: String,
    val totalEvaluations: Int,
    val completedCount: Int,
    val pendingCount: Int,
    val failedCount: Int,
    val approvedCount: Int,
    val rejectedCount: Int,
    val completionRatePercent: Double,
    val failureRatePercent: Double,
    val approvalRatePercent: Double?,
    val averageOverallScore: Double?
)

data class LlmJudgeMetricsCountResponse(
    val key: String,
    val count: Int
)

data class LlmJudgeRecentEvaluationResponse(
    val evaluationId: String,
    val createdAt: String,
    val feature: String,
    val judgeStatus: String,
    val judgeDecision: String?,
    val judgeOverallScore: Double?,
    val idioma: String,
    val pipeline: String?,
    val handler: String?,
    val sourceModel: String,
    val sourceDurationMs: Double?,
    val judgeDurationMs: Double?,
    val sourceTotalTokens: Int?,
    val judgeTotalTokens: Int?
)

data class LlmJudgeMetricsResponse(
    val filters: LlmJudgeMetricsFiltersResponse,
    val summary: LlmJudgeMetricsSummaryResponse,
    val byFeature: List<LlmJudgeMetricsBucketResponse>,
    val byPipeline: List<LlmJudgeMetricsBucketResponse>,
    val byHandler: List<LlmJudgeMetricsBucketResponse>,
    val byIdioma: List<LlmJudgeMetricsBucketResponse>,
    val bySourceModel: List<LlmJudgeMetricsBucketResponse>,
    val daily: List<LlmJudgeMetricsDailyPointResponse>,
    val topRejectionReasons: List<LlmJudgeMetricsCountResponse>,
    val recentEvaluations: List<LlmJudgeRecentEvaluationResponse>
)
