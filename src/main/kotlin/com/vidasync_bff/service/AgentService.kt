package com.vidasync_bff.service

import com.vidasync_bff.client.BfaClient
import com.vidasync_bff.dto.request.AgentChatRequest
import com.vidasync_bff.dto.response.AgentChatResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Serviço responsável por rotear requisições de agente conversacional.
 *
 * Quando bfa.enabled=true, delega ao BFA (Back for Agents).
 * Quando bfa.enabled=false, retorna resposta de fallback informando que o
 * agente conversacional ainda não está disponível.
 *
 * Feature flag: bfa.enabled (application.properties / env var BFA_ENABLED)
 */
@Service
class AgentService(
    private val bfaClient: BfaClient,
    @Value("\${bfa.enabled:false}") private val bfaEnabled: Boolean
) {

    private val log = LoggerFactory.getLogger(AgentService::class.java)

    /**
     * Processa uma mensagem de chat do usuário, delegando ao BFA quando habilitado.
     *
     * @param request      Requisição de chat com mensagem e contexto
     * @param correlationId ID de correlação para rastreabilidade entre BFF e BFA
     * @return Resposta do agente ou mensagem de fallback
     */
    fun chat(request: AgentChatRequest, correlationId: String? = null): AgentChatResponse {
        if (!bfaEnabled) {
            log.warn("BFA desabilitado (bfa.enabled=false). Retornando resposta de fallback para chat.")
            return AgentChatResponse(
                error = "Agente conversacional ainda não disponível. Configure bfa.enabled=true e bfa.url para ativar."
            )
        }

        log.info("AgentService.chat → delegando ao BFA | userId={}, sessionId={}", request.userId, request.sessionId)
        return try {
            bfaClient.chat(request, correlationId)
        } catch (e: Exception) {
            log.error("Falha ao chamar BFA para chat: {}", e.message, e)
            AgentChatResponse(error = "Agente temporariamente indisponível. Tente novamente em instantes.")
        }
    }
}
