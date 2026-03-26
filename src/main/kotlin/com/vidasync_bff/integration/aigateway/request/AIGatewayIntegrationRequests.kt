package com.vidasync_bff.integration.aigateway.request

data class AIGatewayRouteIntegrationRequest(
    val contexto: String,
    val payload: Map<String, Any?> = emptyMap(),
    val idioma: String = "pt-BR",
    val traceId: String? = null,
    val metadados: Map<String, Any?> = mapOf("origem" to "vidasync-bff")
)

data class AIGatewayChatIntegrationRequest(
    val prompt: String,
    val conversationId: String? = null,
    val traceId: String? = null
)

data class AIGatewayPipelinePlanoImagemIntegrationRequest(
    val imagemUrl: String,
    val contexto: String,
    val idioma: String = "pt-BR",
    val executarOcrLiteral: Boolean = false,
    val traceId: String? = null
)

data class AIGatewayPipelinePlanoE2eTemporarioIntegrationRequest(
    val payload: Map<String, Any?> = emptyMap(),
    val traceId: String? = null
)

data class AIGatewayPipelineFotoCaloriasIntegrationRequest(
    val imageUrl: String? = null,
    val foods: String? = null,
    val idioma: String = "pt-BR",
    val traceId: String? = null,
    val payload: Map<String, Any?> = emptyMap()
)
