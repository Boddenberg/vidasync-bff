package com.vidasync_bff.integration.aigateway.feign

import com.vidasync_bff.integration.aigateway.AIGatewayIntegrationException
import feign.Response
import feign.codec.ErrorDecoder
import java.nio.charset.StandardCharsets

class AIGatewayFeignErrorDecoder : ErrorDecoder {

    override fun decode(methodKey: String, response: Response): Exception {
        val responseBody = response.body()
            ?.asInputStream()
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText() }

        return AIGatewayIntegrationException(
            message = "Falha ao chamar AI Gateway em $methodKey: HTTP ${response.status()}",
            statusCode = response.status(),
            responseBody = responseBody
        )
    }
}
