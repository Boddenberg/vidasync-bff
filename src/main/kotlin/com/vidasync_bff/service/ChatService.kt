package com.vidasync_bff.service

import com.vidasync_bff.dto.request.ChatRequest
import com.vidasync_bff.dto.response.ChatJudgeCriterionResponse
import com.vidasync_bff.dto.response.ChatJudgeEvaluationResponse
import com.vidasync_bff.dto.response.ChatJudgeReferenceResponse
import com.vidasync_bff.dto.response.ChatMemoryResponse
import com.vidasync_bff.dto.response.ChatResponse
import com.vidasync_bff.integration.aigateway.AIGatewayIntegration
import com.vidasync_bff.integration.aigateway.AIGatewayIntegrationException
import com.vidasync_bff.integration.aigateway.request.AIGatewayChatIntegrationRequest
import com.vidasync_bff.observability.AgentTelemetryContext
import com.vidasync_bff.observability.TraceContext
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

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
        AgentTelemetryContext.recordStageEvent(
            stage = "chat_validation",
            eventType = "stage",
            status = "completed",
            detail = "chat request validated",
            payload = mapOf("promptChars" to prompt.length)
        )

        val traceId = TraceContext.current()
        val conversationId = request.conversationId?.trim()?.takeIf { it.isNotBlank() }
        val normalizedUserId = userId?.trim()?.takeIf { it.isNotBlank() }
        val requestId = AgentTelemetryContext.currentRequestId()
        val messageId = UUID.randomUUID().toString().replace("-", "")

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
                    traceId = traceId,
                    userId = normalizedUserId,
                    requestId = requestId,
                    messageId = messageId
                )
            )
        } catch (ex: AIGatewayIntegrationException) {
            AgentTelemetryContext.recordStageEvent(
                stage = "chat_gateway",
                eventType = "error",
                status = "error",
                detail = ex.message,
                payload = mapOf("conversationIdPresent" to (conversationId != null))
            )
            throw mapGatewayFailure(ex)
        }

        val responseText = gatewayResponse.response?.trim().orEmpty()
        if (responseText.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "A camada de IA retornou uma resposta vazia")
        }

        val memory = gatewayResponse.memoria.orEmpty()
        val warnings = toStringList(gatewayResponse.roteamento?.get("warnings")).ifEmpty { null }
        val needsReview = toBooleanValue(gatewayResponse.roteamento?.get("precisa_revisao")) ?: false

        if (!warnings.isNullOrEmpty() || needsReview) {
            AgentTelemetryContext.recordStageEvent(
                stage = "chat_result",
                eventType = "warning",
                status = "completed",
                detail = "chat response returned warnings or review flag",
                payload = mapOf(
                    "warningsCount" to (warnings?.size ?: 0),
                    "needsReview" to needsReview
                )
            )
        }

        val response = ChatResponse(
            response = responseText,
            model = gatewayResponse.model,
            conversationId = gatewayResponse.conversationId ?: toStringValue(memory["conversation_id"]),
            intent = toStringValue(gatewayResponse.intencaoDetectada?.get("intencao")),
            confidence = toDoubleValue(gatewayResponse.intencaoDetectada?.get("confianca")),
            needsReview = needsReview,
            warnings = warnings,
            memory = memory.toPublicMemoryResponse(),
            judge = gatewayResponse.judge?.evaluationId?.let {
                ChatJudgeReferenceResponse(
                    evaluationId = it,
                    status = toStringValue(gatewayResponse.judge.status)
                )
            },
            disclaimer = DEFAULT_DISCLAIMER,
            traceId = gatewayResponse.traceId ?: traceId
        )

        log.info(
            "chat.completed trace_id={} userIdPresent={} conversationId={} intent={} needsReview={} warnings={} totalTurns={} judgeEvaluationId={} judgeStatus={}",
            response.traceId,
            !userId.isNullOrBlank(),
            response.conversationId,
            response.intent,
            response.needsReview,
            response.warnings?.size ?: 0,
            response.memory?.totalTurns,
            response.judge?.evaluationId,
            response.judge?.status
        )
        AgentTelemetryContext.recordStageEvent(
            stage = "chat_completed",
            eventType = "flow",
            status = "completed",
            detail = "chat response prepared",
            payload = mapOf(
                "conversationIdPresent" to !response.conversationId.isNullOrBlank(),
                "warningsCount" to (response.warnings?.size ?: 0),
                "needsReview" to response.needsReview,
                "judgeStatus" to response.judge?.status
            )
        )

        return response
    }

    fun judge(evaluationId: String): ChatJudgeEvaluationResponse {
        val normalizedEvaluationId = evaluationId.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("evaluationId e obrigatorio")

        val gatewayResponse = try {
            aiGatewayIntegration.chatJudge(
                evaluationId = normalizedEvaluationId,
                traceId = TraceContext.current()
            )
        } catch (ex: AIGatewayIntegrationException) {
            AgentTelemetryContext.recordStageEvent(
                stage = "chat_judge_gateway",
                eventType = "error",
                status = "error",
                detail = ex.message,
                payload = mapOf("evaluationId" to normalizedEvaluationId)
            )
            throw mapJudgeFailure(ex)
        }

        val criterionScores = gatewayResponse.criterionScores.entries.associate { (key, value) ->
            key to toDoubleValue(value)
        }
        val criterionReasons = gatewayResponse.criterionReasons.entries.mapNotNull { (key, value) ->
            toStringValue(value)?.let { key to it }
        }.toMap()

        val criteria = (
            gatewayResponse.criteria.keys +
                criterionScores.keys +
                criterionReasons.keys
            ).distinct().map { key ->
                val criterion = gatewayResponse.criteria[key]
                val score = criterion?.score ?: criterionScores[key]
                val reason = criterion?.reason ?: criterionReasons[key]
                ChatJudgeCriterionResponse(
                    key = key,
                    score = score,
                    reason = reason,
                    approved = criterion?.approved
                )
            }

        return ChatJudgeEvaluationResponse(
            evaluationId = gatewayResponse.evaluationId ?: normalizedEvaluationId,
            status = toStringValue(gatewayResponse.status),
            overallScore = gatewayResponse.overallScore,
            approved = gatewayResponse.approved,
            decision = toStringValue(gatewayResponse.decision),
            criterionScores = criterionScores,
            criterionReasons = criterionReasons,
            criteria = criteria,
            score = gatewayResponse.score,
            approval = gatewayResponse.approval
        )
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

    private fun mapJudgeFailure(ex: AIGatewayIntegrationException): ResponseStatusException {
        val statusCode = ex.statusCode
        return when (statusCode) {
            400, 422 -> ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Nao foi possivel consultar a avaliacao do judge."
            )
            404 -> ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Avaliacao do judge nao encontrada."
            )
            408, 504 -> ResponseStatusException(
                HttpStatus.GATEWAY_TIMEOUT,
                "A avaliacao do judge demorou mais que o esperado. Tente novamente."
            )
            else -> {
                log.error(
                    "chat.judge_gateway_failure statusCode={} responseBody={} message={}",
                    statusCode,
                    ex.responseBody,
                    ex.message,
                    ex
                )
                ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Nao foi possivel consultar a avaliacao do judge no momento."
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
