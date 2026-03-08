package com.vidasync_bff.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.util.DefaultUriBuilderFactory

@Configuration
class AIGatewayConfig(
    @Value("\${ai.gateway.base-url:http://127.0.0.1:8000}") private val aiGatewayBaseUrl: String,
    @Value("\${ai.gateway.timeout-ms:60000}") private val aiGatewayTimeoutMs: Int,
    @Value("\${ai.gateway.api-key:}") private val aiGatewayApiKey: String
) {

    private val log = LoggerFactory.getLogger(AIGatewayConfig::class.java)

    @Bean
    fun aiGatewayRestClient(): RestClient {
        var normalized = aiGatewayBaseUrl.trim()
        while (normalized.endsWith("/")) normalized = normalized.dropLast(1)
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }

        val uriFactory = DefaultUriBuilderFactory(normalized)
        uriFactory.encodingMode = DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT

        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(aiGatewayTimeoutMs)
            setReadTimeout(aiGatewayTimeoutMs)
        }

        log.info("Configured AI Gateway base URL: {} | timeout={}ms", normalized, aiGatewayTimeoutMs)

        val builder = RestClient.builder()
            .uriBuilderFactory(uriFactory)
            .requestFactory(requestFactory)
            .defaultHeader("Content-Type", "application/json")

        if (aiGatewayApiKey.isNotBlank()) {
            builder.defaultHeader("X-Internal-Api-Key", aiGatewayApiKey)
        }

        return builder.build()
    }
}

