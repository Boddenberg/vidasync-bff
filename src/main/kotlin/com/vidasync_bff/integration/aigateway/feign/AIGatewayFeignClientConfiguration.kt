package com.vidasync_bff.integration.aigateway.feign

import feign.Logger
import feign.Request
import feign.RequestInterceptor
import feign.Retryer
import feign.codec.ErrorDecoder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.concurrent.TimeUnit

@Configuration
class AIGatewayFeignClientConfiguration(
    @Value("\${ai.gateway.timeout-ms:80000}") private val aiGatewayTimeoutMs: Long,
    @Value("\${ai.gateway.api-key:}") private val aiGatewayApiKey: String
) {

    @Bean
    fun aiGatewayFeignOptions(): Request.Options {
        return Request.Options(
            aiGatewayTimeoutMs,
            TimeUnit.MILLISECONDS,
            aiGatewayTimeoutMs,
            TimeUnit.MILLISECONDS,
            true
        )
    }

    @Bean
    fun aiGatewayFeignRequestInterceptor(): RequestInterceptor {
        return RequestInterceptor { template ->
            template.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            if (aiGatewayApiKey.isNotBlank()) {
                template.header("X-Internal-Api-Key", aiGatewayApiKey)
            }
        }
    }

    @Bean
    fun aiGatewayFeignRetryer(): Retryer = Retryer.NEVER_RETRY

    @Bean
    fun aiGatewayFeignLoggerLevel(): Logger.Level = Logger.Level.BASIC

    @Bean
    fun aiGatewayFeignErrorDecoder(): ErrorDecoder = AIGatewayFeignErrorDecoder()
}
