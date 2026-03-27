package com.vidasync_bff.integration.aigateway.impl

import com.vidasync_bff.client.AIGatewayClient
import com.vidasync_bff.integration.aigateway.AIGatewayIntegration
import com.vidasync_bff.integration.aigateway.AIGatewayIntegrationException
import com.vidasync_bff.integration.aigateway.request.AIGatewayChatIntegrationRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelineFotoCaloriasIntegrationRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelinePlanoE2eTemporarioIntegrationRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelinePlanoImagemIntegrationRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayRouteIntegrationRequest
import com.vidasync_bff.integration.aigateway.response.AIGatewayChatIntegrationResponse
import com.vidasync_bff.integration.aigateway.response.AIGatewayChatJudgeIntegrationResponse
import com.vidasync_bff.integration.aigateway.response.AIGatewayIntegrationResponse
import com.vidasync_bff.integration.aigateway.translator.AIGatewayIntegrationTranslator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "integrations.ai-gateway",
    name = ["provider"],
    havingValue = "rest-client"
)
class AIGatewayLegacyIntegration(
    private val legacyClient: AIGatewayClient,
    private val translator: AIGatewayIntegrationTranslator
) : AIGatewayIntegration {

    override fun chat(request: AIGatewayChatIntegrationRequest): AIGatewayChatIntegrationResponse {
        return try {
            translator.toChatIntegrationResponse(
                legacyClient.chat(
                    prompt = request.prompt,
                    conversationId = request.conversationId,
                    traceId = translator.resolveTraceId(request.traceId),
                    userId = request.userId,
                    requestId = request.requestId,
                    messageId = request.messageId
                )
            )
        } catch (e: AIGatewayClient.AIGatewayRequestException) {
            throw AIGatewayIntegrationException(
                message = e.message ?: "Falha ao chamar AI Gateway",
                statusCode = e.statusCode,
                responseBody = e.responseBody,
                cause = e
            )
        }
    }

    override fun chatJudge(
        evaluationId: String,
        traceId: String?
    ): AIGatewayChatJudgeIntegrationResponse {
        return try {
            translator.toChatJudgeIntegrationResponse(
                legacyClient.chatJudge(
                    evaluationId = evaluationId,
                    traceId = translator.resolveTraceId(traceId)
                )
            )
        } catch (e: AIGatewayClient.AIGatewayRequestException) {
            throw AIGatewayIntegrationException(
                message = e.message ?: "Falha ao chamar AI Gateway",
                statusCode = e.statusCode,
                responseBody = e.responseBody,
                cause = e
            )
        }
    }

    override fun route(request: AIGatewayRouteIntegrationRequest): AIGatewayIntegrationResponse {
        return try {
            translator.toIntegrationResponse(
                legacyClient.route(
                    contexto = request.contexto,
                    payload = request.payload,
                    idioma = request.idioma,
                    traceId = translator.resolveTraceId(request.traceId),
                    metadados = request.metadados
                )
            )
        } catch (e: AIGatewayClient.AIGatewayRequestException) {
            throw AIGatewayIntegrationException(
                message = e.message ?: "Falha ao chamar AI Gateway",
                statusCode = e.statusCode,
                responseBody = e.responseBody,
                cause = e
            )
        }
    }

    override fun pipelinePlanoImagem(
        request: AIGatewayPipelinePlanoImagemIntegrationRequest
    ): AIGatewayIntegrationResponse {
        return try {
            translator.toIntegrationResponse(
                legacyClient.pipelinePlanoImagem(
                    imagemUrl = request.imagemUrl,
                    contexto = request.contexto,
                    idioma = request.idioma,
                    executarOcrLiteral = request.executarOcrLiteral,
                    traceId = translator.resolveTraceId(request.traceId)
                )
            )
        } catch (e: AIGatewayClient.AIGatewayRequestException) {
            throw AIGatewayIntegrationException(
                message = e.message ?: "Falha ao chamar AI Gateway",
                statusCode = e.statusCode,
                responseBody = e.responseBody,
                cause = e
            )
        }
    }

    override fun pipelinePlanoE2eTemporario(
        request: AIGatewayPipelinePlanoE2eTemporarioIntegrationRequest
    ): AIGatewayIntegrationResponse {
        return try {
            translator.toIntegrationResponse(
                legacyClient.pipelinePlanoE2eTemporario(
                    payload = translator.toPipelinePlanoE2eTemporarioBody(request),
                    traceId = translator.resolveTraceId(request.traceId)
                )
            )
        } catch (e: AIGatewayClient.AIGatewayRequestException) {
            throw AIGatewayIntegrationException(
                message = e.message ?: "Falha ao chamar AI Gateway",
                statusCode = e.statusCode,
                responseBody = e.responseBody,
                cause = e
            )
        }
    }

    override fun pipelineFotoCalorias(
        request: AIGatewayPipelineFotoCaloriasIntegrationRequest
    ): AIGatewayIntegrationResponse {
        return try {
            translator.toIntegrationResponse(
                legacyClient.pipelineFotoCalorias(
                    payload = translator.toPipelineFotoCaloriasBody(request),
                    idioma = request.idioma,
                    traceId = translator.resolveTraceId(request.traceId)
                )
            )
        } catch (e: AIGatewayClient.AIGatewayRequestException) {
            throw AIGatewayIntegrationException(
                message = e.message ?: "Falha ao chamar AI Gateway",
                statusCode = e.statusCode,
                responseBody = e.responseBody,
                cause = e
            )
        }
    }
}
