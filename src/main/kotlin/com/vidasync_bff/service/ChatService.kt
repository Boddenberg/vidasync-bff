package com.vidasync_bff.service

import com.vidasync_bff.dto.request.ChatRequest
import com.vidasync_bff.dto.response.ChatMemoryResponse
import com.vidasync_bff.dto.response.ChatResponse
import com.vidasync_bff.integration.aigateway.AIGatewayIntegration
import com.vidasync_bff.integration.aigateway.AIGatewayIntegrationException
import com.vidasync_bff.integration.aigateway.request.AIGatewayChatIntegrationRequest
import com.vidasync_bff.observability.TraceContext
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ChatService(
    private val aiGatewayIntegration: AIGatewayIntegration
) {

    companion object {
        const val DEFAULT_DISCLAIMER =
            "Informacao geral. Para orientacao personalizada, consulte um nutricionista."
    }

    private val log = LoggerFactory.getLogger(ChatService::class.java)

    fun chat(userId: String?, request: ChatRequest): ChatResponse {
        val prompt = request.prompt?.trim().orEmpty()
        require(prompt.isNotBlank()) { "prompt e obrigatorio" }

        val traceId = TraceContext.current()
        val conversationId = request.conversationId?.trim()?.takeIf { it.isNotBlank() }

        log.info(
            "chat.started trace_id={} userIdPresent={} promptChars={} hasConversationId={}",
            traceId,
            !userId.isNullOrBlank(),
            prompt.length,
            conversationId != null
        )

        val gatewayResponse = try {
            aiGatewayIntegration.chat(
                AIGatewayChatIntegrationRequest(
                    prompt = prompt,
                    conversationId = conversationId,
                    traceId = traceId
                )
            )
        } catch (ex: AIGatewayIntegrationException) {
            throw mapGatewayFailure(ex)
        }

        val responseText = gatewayResponse.response?.trim().orEmpty()
        if (responseText.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "A camada de IA retornou uma resposta vazia")
        }

        val memory = gatewayResponse.memoria.orEmpty()
        val warnings = toStringList(gatewayResponse.roteamento?.get("warnings")).ifEmpty { null }
        val needsReview = toBooleanValue(gatewayResponse.roteamento?.get("precisa_revisao")) ?: false

        val response = ChatResponse(
            response = responseText,
            model = gatewayResponse.model,
            conversationId = gatewayResponse.conversationId ?: toStringValue(memory["conversation_id"]),
            intent = toStringValue(gatewayResponse.intencaoDetectada?.get("intencao")),
            confidence = toDoubleValue(gatewayResponse.intencaoDetectada?.get("confianca")),
            needsReview = needsReview,
            warnings = warnings,
            memory = memory.toPublicMemoryResponse(),
            disclaimer = DEFAULT_DISCLAIMER,
            traceId = gatewayResponse.traceId ?: traceId
        )

        log.info(
            "chat.completed trace_id={} userIdPresent={} conversationId={} intent={} needsReview={} warnings={} totalTurns={}",
            response.traceId,
            !userId.isNullOrBlank(),
            response.conversationId,
            response.intent,
            response.needsReview,
            response.warnings?.size ?: 0,
            response.memory?.totalTurns
        )

        return response
    }

    private fun mapGatewayFailure(ex: AIGatewayIntegrationException): ResponseStatusException {
        val statusCode = ex.statusCode
        return when (statusCode) {
            400, 422 -> ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Nao foi possivel processar a mensagem enviada."
            )
            408, 504 -> ResponseStatusException(
                HttpStatus.GATEWAY_TIMEOUT,
                "A IA demorou mais que o esperado. Tente novamente."
            )
            429 -> ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Muitas mensagens em sequencia. Tente novamente em instantes."
            )
            else -> {
                log.error(
                    "chat.gateway_failure statusCode={} responseBody={} message={}",
                    statusCode,
                    ex.responseBody,
                    ex.message,
                    ex
                )
                ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Nao foi possivel consultar a IA no momento."
                )
            }
        }
    }

    private fun Map<String, Any?>.toPublicMemoryResponse(): ChatMemoryResponse? {
        if (isEmpty()) return null
        return ChatMemoryResponse(
            totalTurns = toIntValue(this["total_turnos"]),
            shortTermTurns = toIntValue(this["turnos_curto_prazo"]),
            summarizedTurns = toIntValue(this["turnos_resumidos"]),
            hasSummary = toBooleanValue(this["resumo_presente"]),
            updatedAt = toStringValue(this["atualizada_em"])
        )
    }

    private fun toStringList(value: Any?): List<String> {
        return when (value) {
            is List<*> -> value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotBlank() }
            is String -> listOf(value.trim()).filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun toStringValue(value: Any?): String? {
        val text = value?.toString()?.trim() ?: return null
        return text.takeIf { it.isNotBlank() }
    }

    private fun toBooleanValue(value: Any?): Boolean? {
        return when (value) {
            is Boolean -> value
            is String -> when (value.trim().lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
            else -> null
        }
    }

    private fun toIntValue(value: Any?): Int? {
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is Float -> value.toInt()
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
    }

    private fun toDoubleValue(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(",", ".").toDoubleOrNull()
            else -> null
        }
    }
}
