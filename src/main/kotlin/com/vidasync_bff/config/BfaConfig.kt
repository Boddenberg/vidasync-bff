package com.vidasync_bff.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

/**
 * Configura o RestClient para comunicação com o BFA (Back for Agents).
 *
 * O BFA é o serviço separado responsável por orquestração de agentes, RAG,
 * ferramentas e toda a inteligência do VidaSync. O BFF delega para ele
 * quando bfa.enabled=true.
 *
 * Variáveis de ambiente:
 *   BFA_URL     → URL base do BFA (ex: http://localhost:8000)
 *   BFA_API_KEY → Chave de autenticação interna BFF→BFA
 *   BFA_ENABLED → true/false para ativar delegação
 */
@Configuration
class BfaConfig(
    @Value("\${bfa.url:}") private val bfaUrl: String,
    @Value("\${bfa.api-key:}") private val bfaApiKey: String
) {

    private val log = LoggerFactory.getLogger(BfaConfig::class.java)

    @Bean
    fun bfaRestClient(): RestClient {
        val resolvedUrl = bfaUrl.ifBlank { "http://localhost:8000" }
        if (bfaUrl.isBlank()) {
            log.warn("⚠️ bfa.url não configurada. Usando fallback: {}. Chamadas ao BFA irão falhar se bfa.enabled=true.", resolvedUrl)
        } else {
            log.info("BFA configurado: url={}", resolvedUrl)
        }

        val builder = RestClient.builder()
            .baseUrl(resolvedUrl)
            .defaultHeader("Content-Type", "application/json")

        if (bfaApiKey.isNotBlank()) {
            builder.defaultHeader("X-Api-Key", bfaApiKey)
        }

        return builder.build()
    }
}
