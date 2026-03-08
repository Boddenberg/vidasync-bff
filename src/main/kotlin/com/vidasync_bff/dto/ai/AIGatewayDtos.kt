package com.vidasync_bff.dto.ai

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class AIGatewayRouteRequest(
    @JsonProperty("trace_id")
    val traceId: String? = null,
    val contexto: String,
    val idioma: String = "pt-BR",
    val payload: Map<String, Any?> = emptyMap(),
    val metadados: Map<String, Any?> = emptyMap()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AIGatewayRouteResponse(
    @JsonProperty("trace_id")
    val traceId: String? = null,
    val contexto: String? = null,
    val status: String? = null,
    val warnings: List<String>? = emptyList(),
    @JsonProperty("precisa_revisao")
    val precisaRevisao: Boolean? = null,
    val resultado: Map<String, Any?>? = null,
    val erro: Any? = null
)

