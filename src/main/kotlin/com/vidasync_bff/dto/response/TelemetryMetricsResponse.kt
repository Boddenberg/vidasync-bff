package com.vidasync_bff.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupabaseTelemetryAgentRunRow(
    @JsonProperty("run_id") val runId: String,
    @JsonProperty("request_id") val requestId: String,
    @JsonProperty("trace_id") val traceId: String? = null,
    val agent: String = "unknown",
    val endpoint: String = "unknown",
    @JsonProperty("http_method") val httpMethod: String = "GET",
    @JsonProperty("http_status") val httpStatus: Int? = null,
    val status: String = "success",
    val timeout: Boolean = false,
    @JsonProperty("duration_ms") val durationMs: Double? = null,
    @JsonProperty("input_tokens") val inputTokens: Int? = null,
    @JsonProperty("output_tokens") val outputTokens: Int? = null,
    @JsonProperty("total_tokens") val totalTokens: Int? = null,
    @JsonProperty("total_cost_usd") val totalCostUsd: Double? = null,
    @JsonProperty("llm_call_count") val llmCallCount: Int = 0,
    @JsonProperty("tool_call_count") val toolCallCount: Int = 0,
    @JsonProperty("stage_event_count") val stageEventCount: Int = 0,
    @JsonProperty("error_message") val errorMessage: String? = null,
    @JsonProperty("request_context") val requestContext: Map<String, Any?> = emptyMap(),
    @JsonProperty("started_at") val startedAt: String,
    @JsonProperty("finished_at") val finishedAt: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupabaseTelemetryAgentRunsDailyRow(
    @JsonProperty("day_utc") val dayUtc: String,
    val agent: String = "unknown",
    val endpoint: String = "unknown",
    @JsonProperty("run_count") val runCount: Int = 0,
    @JsonProperty("success_count") val successCount: Int = 0,
    @JsonProperty("error_count") val errorCount: Int = 0,
    @JsonProperty("timeout_count") val timeoutCount: Int = 0,
    @JsonProperty("total_cost_usd") val totalCostUsd: Double? = null,
    @JsonProperty("total_tokens") val totalTokens: Int? = null,
    @JsonProperty("avg_duration_ms") val avgDurationMs: Double? = null,
    @JsonProperty("p95_duration_ms") val p95DurationMs: Double? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupabaseTelemetryLlmModelsDailyRow(
    @JsonProperty("day_utc") val dayUtc: String,
    val agent: String = "unknown",
    val model: String = "unknown",
    @JsonProperty("llm_call_count") val llmCallCount: Int = 0,
    @JsonProperty("total_cost_usd") val totalCostUsd: Double? = null,
    @JsonProperty("input_tokens") val inputTokens: Int? = null,
    @JsonProperty("output_tokens") val outputTokens: Int? = null,
    @JsonProperty("total_tokens") val totalTokens: Int? = null,
    @JsonProperty("avg_duration_ms") val avgDurationMs: Double? = null,
    @JsonProperty("p95_duration_ms") val p95DurationMs: Double? = null
)

data class TelemetryMetricsFiltersResponse(
    val startDate: String,
    val endDate: String,
    val days: Int,
    val agent: String?,
    val model: String?,
    val status: String?
)

data class TelemetryMetricsSummaryResponse(
    val totalRuns: Int,
    val successCount: Int,
    val errorCount: Int,
    val timeoutCount: Int,
    val totalCostUsd: Double,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val averageDurationMs: Double?,
    val p95DurationMs: Double?,
    val latestRunAt: String?,
    val oldestRunAt: String?
)

data class TelemetryMetricsDailyPointResponse(
    val dayUtc: String,
    val runCount: Int,
    val successCount: Int,
    val errorCount: Int,
    val timeoutCount: Int,
    val totalCostUsd: Double,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val averageDurationMs: Double?,
    val p95DurationMs: Double?
)

data class TelemetryMetricsAgentBreakdownResponse(
    val agent: String,
    val runCount: Int,
    val successCount: Int,
    val errorCount: Int,
    val timeoutCount: Int,
    val totalCostUsd: Double,
    val totalTokens: Int,
    val averageDurationMs: Double?,
    val p95DurationMs: Double?
)

data class TelemetryMetricsModelBreakdownResponse(
    val model: String,
    val agent: String,
    val llmCallCount: Int,
    val totalCostUsd: Double,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val averageDurationMs: Double?,
    val p95DurationMs: Double?
)

data class TelemetryRecentRunResponse(
    val runId: String,
    val requestId: String,
    val traceId: String?,
    val agent: String,
    val endpoint: String,
    val httpMethod: String,
    val httpStatus: Int?,
    val status: String,
    val timeout: Boolean,
    val durationMs: Double?,
    val totalCostUsd: Double?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val totalTokens: Int?,
    val llmCallCount: Int,
    val toolCallCount: Int,
    val stageEventCount: Int,
    val errorMessage: String?,
    val startedAt: String,
    val finishedAt: String?,
    val requestContext: Map<String, Any?>
)

data class TelemetryMetricsResponse(
    val filters: TelemetryMetricsFiltersResponse,
    val summary: TelemetryMetricsSummaryResponse,
    val daily: List<TelemetryMetricsDailyPointResponse>,
    val byAgent: List<TelemetryMetricsAgentBreakdownResponse>,
    val byModel: List<TelemetryMetricsModelBreakdownResponse>
)

data class TelemetryRunsResponse(
    val filters: TelemetryMetricsFiltersResponse,
    val limit: Int,
    val recentRuns: List<TelemetryRecentRunResponse>
)
