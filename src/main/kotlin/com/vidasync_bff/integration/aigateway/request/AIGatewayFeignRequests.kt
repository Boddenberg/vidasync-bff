package com.vidasync_bff.integration.aigateway.request

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonProperty

data class AIGatewayRouteFeignRequest(
    @JsonProperty("trace_id")
    val traceId: String? = null,
    val contexto: String,
    val idioma: String = "pt-BR",
    val payload: Map<String, Any?> = emptyMap(),
    val metadados: Map<String, Any?> = emptyMap()
)

data class AIGatewayChatFeignRequest(
    val prompt: String,
    @JsonProperty("conversation_id")
    val conversationId: String? = null,
    @JsonProperty("trace_id")
    val traceId: String? = null
)

data class AIGatewayPipelinePlanoImagemFeignRequest(
    @JsonProperty("imagem_url")
    val imagemUrl: String,
    val contexto: String,
    val idioma: String = "pt-BR",
    @JsonProperty("executar_ocr_literal")
    val executarOcrLiteral: Boolean = false,
    @JsonProperty("trace_id")
    val traceId: String? = null
)

data class AIGatewayPipelinePlanoE2eTemporarioFeignRequest(
    private val body: Map<String, Any?> = emptyMap()
) {
    @JsonAnyGetter
    fun asJson(): Map<String, Any?> = body
}

data class AIGatewayPipelineFotoCaloriasFeignRequest(
    private val body: Map<String, Any?> = emptyMap()
) {
    @JsonAnyGetter
    fun asJson(): Map<String, Any?> = body
}
