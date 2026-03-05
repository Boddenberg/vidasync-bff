package com.vidasync_bff.client

import com.vidasync_bff.dto.request.AgentChatRequest
import com.vidasync_bff.dto.response.AgentChatResponse
import com.vidasync_bff.dto.response.CalorieResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Cliente HTTP para comunicação com o BFA (Back for Agents).
 *
 * O BFA é o serviço separado de multiagentes do VidaSync responsável por:
 *   - Cálculo nutricional via agentes + RAG
 *   - Chat conversacional sobre nutrição
 *   - Orquestração de fluxos multi-step (LangGraph)
 *
 * Endpoints consumidos:
 *   POST /nutrition/calculate  → cálculo nutricional delegado
 *   POST /agent/chat           → conversa com agente nutricional
 */
@Component
class BfaClient(private val bfaRestClient: RestClient) {

    private val log = LoggerFactory.getLogger(BfaClient::class.java)

    /**
     * Delega o cálculo nutricional ao BFA.
     * Substitui a chamada direta à OpenAI no NutritionService quando bfa.enabled=true.
     *
     * @param foods        Descrição dos alimentos (ex: "200g de arroz, 100g de frango")
     * @param correlationId ID de correlação para rastreabilidade (X-Correlation-Id)
     */
    fun calculateNutrition(foods: String, correlationId: String? = null): CalorieResponse {
        log.info("BFA → POST /nutrition/calculate | foods={}", foods)
        return try {
            bfaRestClient.post()
                .uri("/nutrition/calculate")
                .applyCorrelationId(correlationId)
                .body(mapOf("foods" to foods))
                .retrieve()
                .body(CalorieResponse::class.java)
                ?: CalorieResponse(error = "Resposta vazia do BFA")
        } catch (e: Exception) {
            log.error("Erro ao chamar BFA /nutrition/calculate: {}", e.message, e)
            throw e
        }
    }

    /**
     * Envia mensagem ao agente conversacional do BFA.
     *
     * @param request      Requisição de chat com mensagem, userId e sessionId opcional
     * @param correlationId ID de correlação para rastreabilidade
     */
    fun chat(request: AgentChatRequest, correlationId: String? = null): AgentChatResponse {
        log.info("BFA → POST /agent/chat | userId={}, sessionId={}", request.userId, request.sessionId)
        return try {
            bfaRestClient.post()
                .uri("/agent/chat")
                .applyCorrelationId(correlationId)
                .body(request)
                .retrieve()
                .body(AgentChatResponse::class.java)
                ?: AgentChatResponse(error = "Resposta vazia do BFA")
        } catch (e: Exception) {
            log.error("Erro ao chamar BFA /agent/chat: {}", e.message, e)
            throw e
        }
    }

    private fun RestClient.RequestBodySpec.applyCorrelationId(correlationId: String?): RestClient.RequestBodySpec =
        apply { if (!correlationId.isNullOrBlank()) header("X-Correlation-Id", correlationId) }
}
