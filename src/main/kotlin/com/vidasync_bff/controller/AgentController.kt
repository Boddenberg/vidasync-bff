package com.vidasync_bff.controller

import com.vidasync_bff.dto.request.AgentChatRequest
import com.vidasync_bff.dto.response.AgentChatResponse
import com.vidasync_bff.service.AgentService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller responsável pelos endpoints de agente conversacional.
 *
 * Recebe mensagens do front, injeta o userId autenticado e delega ao AgentService,
 * que por sua vez roteia ao BFA (Back for Agents) quando habilitado.
 *
 * Endpoints:
 *   POST /agent/chat  → Chat conversacional sobre nutrição
 */
@RestController
@RequestMapping("/agent")
class AgentController(private val agentService: AgentService) {

    private val log = LoggerFactory.getLogger(AgentController::class.java)

    /**
     * Recebe uma mensagem do usuário e retorna resposta do agente nutricional.
     *
     * Headers obrigatórios:
     *   X-User-Id        → ID do usuário autenticado
     *   X-Correlation-Id → ID de correlação para rastreabilidade (opcional)
     *
     * Request body:
     *   message   → Mensagem do usuário
     *   sessionId → ID da sessão para memória conversacional (opcional)
     *   context   → Contexto adicional (opcional)
     */
    @PostMapping("/chat")
    fun chat(
        @RequestHeader("X-User-Id") userId: String,
        @RequestHeader(value = "X-Correlation-Id", required = false) correlationId: String?,
        @RequestBody body: AgentChatRequest
    ): ResponseEntity<AgentChatResponse> {
        log.info("POST /agent/chat | userId={}, sessionId={}", userId, body.sessionId)
        return try {
            val request = body.copy(userId = userId)
            val result = agentService.chat(request, correlationId)

            if (result.reply == null && result.error != null) {
                log.warn("POST /agent/chat → 503 | error={}", result.error)
                ResponseEntity.status(503).body(result)
            } else {
                log.info("POST /agent/chat → 200 | agentUsed={}, sessionId={}", result.agentUsed, result.sessionId)
                ResponseEntity.ok(result)
            }
        } catch (e: Exception) {
            log.error("POST /agent/chat → 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(AgentChatResponse(error = e.message))
        }
    }
}
