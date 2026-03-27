package com.vidasync_bff.integration.aigateway.translator

import com.vidasync_bff.dto.ai.AIGatewayOpenAIChatResponse
import com.vidasync_bff.dto.ai.AIGatewayRouteResponse
import com.vidasync_bff.integration.aigateway.request.AIGatewayChatFeignRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayChatIntegrationRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelineFotoCaloriasFeignRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelineFotoCaloriasIntegrationRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelinePlanoE2eTemporarioFeignRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelinePlanoE2eTemporarioIntegrationRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelinePlanoImagemFeignRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelinePlanoImagemIntegrationRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayRouteFeignRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayRouteIntegrationRequest
import com.vidasync_bff.integration.aigateway.response.AIGatewayChatIntegrationResponse
import com.vidasync_bff.integration.aigateway.response.AIGatewayFeignResponse
import com.vidasync_bff.integration.aigateway.response.AIGatewayIntegrationResponse
import com.vidasync_bff.observability.TraceContext
import org.springframework.stereotype.Component

@Component
class AIGatewayIntegrationTranslator {

    fun resolveTraceId(traceId: String?): String? {
        val candidate = traceId?.trim().orEmpty()
        if (candidate.isNotBlank()) {
            return candidate
        }
        return TraceContext.current()?.trim()?.takeIf { it.isNotBlank() }
    }

    fun toRouteFeignRequest(request: AIGatewayRouteIntegrationRequest): AIGatewayRouteFeignRequest {
        val resolvedTraceId = resolveTraceId(request.traceId)
        return AIGatewayRouteFeignRequest(
            traceId = resolvedTraceId,
            contexto = request.contexto,
            idioma = request.idioma,
            payload = request.payload,
            metadados = request.metadados
        )
    }

    fun toChatFeignRequest(request: AIGatewayChatIntegrationRequest): AIGatewayChatFeignRequest {
        return AIGatewayChatFeignRequest(
            prompt = request.prompt,
            conversationId = request.conversationId,
            traceId = resolveTraceId(request.traceId)
        )
    }

    fun toPipelinePlanoImagemFeignRequest(
        request: AIGatewayPipelinePlanoImagemIntegrationRequest
    ): AIGatewayPipelinePlanoImagemFeignRequest {
        return AIGatewayPipelinePlanoImagemFeignRequest(
            imagemUrl = request.imagemUrl,
            contexto = request.contexto,
            idioma = request.idioma,
            executarOcrLiteral = request.executarOcrLiteral,
            traceId = resolveTraceId(request.traceId)
        )
    }

    fun toPipelinePlanoE2eTemporarioBody(
        request: AIGatewayPipelinePlanoE2eTemporarioIntegrationRequest
    ): Map<String, Any?> {
        val body = request.payload.toMutableMap()
        resolveTraceId(request.traceId)?.let { body.putIfAbsent("trace_id", it) }
        return body.toMap()
    }

    fun toPipelinePlanoE2eTemporarioFeignRequest(
        request: AIGatewayPipelinePlanoE2eTemporarioIntegrationRequest
    ): AIGatewayPipelinePlanoE2eTemporarioFeignRequest {
        return AIGatewayPipelinePlanoE2eTemporarioFeignRequest(
            body = toPipelinePlanoE2eTemporarioBody(request)
        )
    }

    fun toPipelineFotoCaloriasBody(
        request: AIGatewayPipelineFotoCaloriasIntegrationRequest
    ): Map<String, Any?> {
        val body = request.payload.toMutableMap()

        request.imageUrl?.trim()?.takeIf { it.isNotBlank() }?.let {
            body.putIfAbsent("image_url", it)
        }
        request.foods?.trim()?.takeIf { it.isNotBlank() }?.let {
            body.putIfAbsent("foods", it)
        }

        body.putIfAbsent("idioma", request.idioma)
        resolveTraceId(request.traceId)?.let { body.putIfAbsent("trace_id", it) }

        return body.toMap()
    }

    fun toPipelineFotoCaloriasFeignRequest(
        request: AIGatewayPipelineFotoCaloriasIntegrationRequest
    ): AIGatewayPipelineFotoCaloriasFeignRequest {
        return AIGatewayPipelineFotoCaloriasFeignRequest(
            body = toPipelineFotoCaloriasBody(request)
        )
    }

    fun toIntegrationResponse(response: AIGatewayFeignResponse): AIGatewayIntegrationResponse {
        return AIGatewayIntegrationResponse(
            traceId = response.traceId,
            contexto = response.contexto,
            status = response.status,
            nomePratoDetectado = response.nomePratoDetectado,
            composicao = response.composicao,
            warnings = response.warnings,
            precisaRevisao = response.precisaRevisao,
            resultado = response.resultado,
            caloriasTexto = response.caloriasTexto,
            model = response.model,
            usage = response.usage,
            metadata = response.metadata,
            providerResponseId = response.providerResponseId,
            durationMs = response.durationMs,
            erro = response.erro
        )
    }

    fun toIntegrationResponse(response: AIGatewayRouteResponse): AIGatewayIntegrationResponse {
        return AIGatewayIntegrationResponse(
            traceId = response.traceId,
            contexto = response.contexto,
            status = response.status,
            nomePratoDetectado = response.nomePratoDetectado,
            composicao = response.composicao,
            warnings = response.warnings,
            precisaRevisao = response.precisaRevisao,
            resultado = response.resultado,
            caloriasTexto = response.caloriasTexto,
            model = response.model,
            usage = response.usage,
            metadata = response.metadata,
            providerResponseId = response.providerResponseId,
            durationMs = response.durationMs,
            erro = response.erro
        )
    }

    fun toChatIntegrationResponse(response: AIGatewayOpenAIChatResponse): AIGatewayChatIntegrationResponse {
        return AIGatewayChatIntegrationResponse(
            model = response.model,
            status = response.status,
            response = response.response,
            conversationId = response.conversationId,
            intencaoDetectada = response.intencaoDetectada,
            roteamento = response.roteamento,
            memoria = response.memoria,
            usage = response.usage,
            metadata = response.metadata,
            providerResponseId = response.providerResponseId,
            durationMs = response.durationMs,
            traceId = response.traceId
        )
    }
}
