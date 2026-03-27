package com.vidasync_bff.dto.ai

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class AIGatewayOpenAIChatRequest(
    val prompt: String,
    @JsonProperty("conversation_id")
    val conversationId: String? = null,
    @JsonProperty("trace_id")
    val traceId: String? = null,
    @JsonProperty("user_id")
    val userId: String? = null,
    @JsonProperty("request_id")
    val requestId: String? = null,
    @JsonProperty("message_id")
    val messageId: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AIGatewayOpenAIChatResponse(
    val model: String? = null,
    val status: String? = null,
    val response: String? = null,
    @JsonProperty("conversation_id")
    val conversationId: String? = null,
    @JsonProperty("intencao_detectada")
    val intencaoDetectada: Map<String, Any?>? = null,
    val roteamento: Map<String, Any?>? = null,
    val memoria: Map<String, Any?>? = null,
    val usage: AIGatewayUsageResponse? = null,
    val metadata: Map<String, Any?>? = null,
    @JsonProperty("provider_response_id")
    val providerResponseId: String? = null,
    @JsonProperty("duration_ms")
    val durationMs: Double? = null,
    val judge: AIGatewayChatJudgeReferenceResponse? = null,
    @JsonProperty("trace_id")
    val traceId: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AIGatewayChatJudgeReferenceResponse(
    @JsonProperty("evaluation_id")
    val evaluationId: String? = null,
    val status: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AIGatewayChatJudgeCriterionResponse(
    val score: Double? = null,
    val reason: String? = null,
    val approved: Boolean? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AIGatewayOpenAIChatJudgeResponse(
    @JsonProperty("evaluation_id")
    val evaluationId: String? = null,
    val status: String? = null,
    @JsonProperty("overall_score")
    val overallScore: Double? = null,
    val approved: Boolean? = null,
    val decision: String? = null,
    @JsonProperty("criterion_scores")
    val criterionScores: Map<String, Any?> = emptyMap(),
    @JsonProperty("criterion_reasons")
    val criterionReasons: Map<String, Any?> = emptyMap(),
    val criteria: Map<String, AIGatewayChatJudgeCriterionResponse> = emptyMap(),
    val score: Map<String, Any?> = emptyMap(),
    val approval: Map<String, Any?> = emptyMap()
)
