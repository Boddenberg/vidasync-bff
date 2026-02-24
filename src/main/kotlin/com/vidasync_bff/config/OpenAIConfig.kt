package com.vidasync_bff.config

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenAIConfig(
    @Value("\${openai.api-key:}") private val apiKey: String
) {

    private val log = LoggerFactory.getLogger(OpenAIConfig::class.java)

    @Bean
    fun openAIClient(): OpenAIClient {
        if (apiKey.isBlank()) {
            log.warn("⚠️ OPENAI_API_KEY não configurada. Chamadas à OpenAI irão falhar.")
        }
        return OpenAIOkHttpClient.builder()
            .apiKey(apiKey)
            .build()
    }
}
