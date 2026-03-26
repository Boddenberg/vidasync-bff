package com.vidasync_bff.integration.aigateway.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class AIGatewayChatIntegrationResponse(
    val model: String? = null,
    val response: String? = null,
    @JsonProperty("conversation_id")
    val conversationId: String? = null,
    @JsonProperty("intencao_detectada")
    val intencaoDetectada: Map<String, Any?>? = null,
    val roteamento: Map<String, Any?>? = null,
    val memoria: Map<String, Any?>? = null,
    @JsonProperty("trace_id")
    val traceId: String? = null
)
