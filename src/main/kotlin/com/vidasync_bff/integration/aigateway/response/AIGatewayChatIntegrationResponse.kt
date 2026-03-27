package com.vidasync_bff.integration.aigateway.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.vidasync_bff.dto.ai.AIGatewayUsageResponse

@JsonIgnoreProperties(ignoreUnknown = true)
data class AIGatewayChatIntegrationResponse(
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
    val judge: AIGatewayChatJudgeReferenceIntegrationResponse? = null,
    @JsonProperty("trace_id")
    val traceId: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AIGatewayChatJudgeReferenceIntegrationResponse(
    @JsonProperty("evaluation_id")
    val evaluationId: String? = null,
    val status: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AIGatewayChatJudgeCriterionIntegrationResponse(
    val score: Double? = null,
    val reason: String? = null,
    val approved: Boolean? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AIGatewayChatJudgeIntegrationResponse(
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
    val criteria: Map<String, AIGatewayChatJudgeCriterionIntegrationResponse> = emptyMap(),
    val score: Map<String, Any?> = emptyMap(),
    val approval: Map<String, Any?> = emptyMap()
)
