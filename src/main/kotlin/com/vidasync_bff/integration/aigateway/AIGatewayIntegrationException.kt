package com.vidasync_bff.integration.aigateway

class AIGatewayIntegrationException(
    message: String,
    val statusCode: Int? = null,
    val responseBody: String? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)
