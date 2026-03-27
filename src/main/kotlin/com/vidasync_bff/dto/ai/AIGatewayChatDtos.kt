package com.vidasync_bff.dto.ai

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class AIGatewayOpenAIChatRequest(
    val prompt: String,
    @JsonProperty("conversation_id")
    val conversationId: String? = null,
    @JsonProperty("trace_id")
    val traceId: String? = null
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
    @JsonProperty("trace_id")
    val traceId: String? = null
)
