package com.vidasync_bff.dto.ai

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class AIGatewayUsageResponse(
    @JsonProperty("input_tokens")
    val inputTokens: Int? = null,
    @JsonProperty("output_tokens")
    val outputTokens: Int? = null,
    @JsonProperty("total_tokens")
    val totalTokens: Int? = null
)
