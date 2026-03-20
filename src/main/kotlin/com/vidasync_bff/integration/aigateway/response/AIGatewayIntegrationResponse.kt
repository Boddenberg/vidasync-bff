package com.vidasync_bff.integration.aigateway.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class AIGatewayIntegrationResponse(
    @JsonProperty("trace_id")
    val traceId: String? = null,
    val contexto: String? = null,
    val status: String? = null,
    @JsonProperty("nome_prato_detectado")
    val nomePratoDetectado: String? = null,
    val composicao: List<Map<String, Any?>>? = null,
    val warnings: List<String>? = emptyList(),
    @JsonProperty("precisa_revisao")
    val precisaRevisao: Boolean? = null,
    val resultado: Map<String, Any?>? = null,
    @JsonProperty("calorias_texto")
    val caloriasTexto: Map<String, Any?>? = null,
    val erro: Any? = null
)
