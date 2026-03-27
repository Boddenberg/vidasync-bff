package com.vidasync_bff.integration.aigateway.impl

import com.vidasync_bff.integration.aigateway.AIGatewayIntegration
import com.vidasync_bff.integration.aigateway.AIGatewayIntegrationException
import com.vidasync_bff.integration.aigateway.request.AIGatewayChatIntegrationRequest
import com.vidasync_bff.integration.aigateway.feign.AIGatewayFeignClient
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelineFotoCaloriasIntegrationRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelinePlanoE2eTemporarioIntegrationRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelinePlanoImagemIntegrationRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayRouteIntegrationRequest
import com.vidasync_bff.integration.aigateway.response.AIGatewayChatIntegrationResponse
import com.vidasync_bff.integration.aigateway.response.AIGatewayChatJudgeIntegrationResponse
import com.vidasync_bff.integration.aigateway.response.AIGatewayIntegrationResponse
import com.vidasync_bff.integration.aigateway.translator.AIGatewayIntegrationTranslator
import com.vidasync_bff.observability.AgentTelemetryContext
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeoutException

@Component
@ConditionalOnProperty(
    prefix = "integrations.ai-gateway",
    name = ["provider"],
    havingValue = "feign",
    matchIfMissing = true
)
class AIGatewayFeignIntegration(
    private val feignClient: AIGatewayFeignClient,
    private val translator: AIGatewayIntegrationTranslator
) : AIGatewayIntegration {

    private val log = LoggerFactory.getLogger(AIGatewayFeignIntegration::class.java)
    private val agentesBasePath = "/agentes"
    private val planoImagemPath = "$agentesBasePath/pipeline-plano-imagem"
    private val planoE2eTemporarioPath = "$agentesBasePath/pipeline-plano-e2e-temporario"
    private val fotoCaloriasPath = "$agentesBasePath/pipeline-foto-calorias"

    override fun chat(request: AIGatewayChatIntegrationRequest): AIGatewayChatIntegrationResponse {
        val resolvedTraceId = translator.resolveTraceId(request.traceId)
        val payloadKeys = buildList {
            add("prompt")
            if (!request.conversationId.isNullOrBlank()) add("conversation_id")
            if (!resolvedTraceId.isNullOrBlank()) add("trace_id")
            if (!request.userId.isNullOrBlank()) add("user_id")
            if (!request.requestId.isNullOrBlank()) add("request_id")
            if (!request.messageId.isNullOrBlank()) add("message_id")
        }
        val startedNs = System.nanoTime()
        log.info(
            "ai_gateway.request provider=feign trace_id={} operation={} path={} payload_keys={}",
            resolvedTraceId,
            "openai_chat",
            "/v1/openai/chat",
            payloadKeys
        )

        return try {
            val response = feignClient.chat(
                request = translator.toChatFeignRequest(request.copy(traceId = resolvedTraceId)),
                traceId = resolvedTraceId
            )
            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            val warnings = (response.roteamento?.get("warnings") as? List<*>)?.size ?: 0
            log.info(
                "ai_gateway.response provider=feign trace_id={} operation={} path={} response_chars={} warnings={} duration_ms={}",
                response.traceId ?: resolvedTraceId,
                "openai_chat",
                "/v1/openai/chat",
                response.response?.length ?: 0,
                warnings,
                String.format(Locale.US, "%.4f", durationMs)
            )
            AgentTelemetryContext.recordLlmCall(
                provider = "openai",
                operation = "openai_chat",
                model = response.model,
                status = response.status ?: "success",
                inputTokens = response.usage?.inputTokens,
                outputTokens = response.usage?.outputTokens,
                totalTokens = response.usage?.totalTokens,
                durationMs = durationMs,
                providerResponseId = response.providerResponseId,
                endpoint = "/v1/openai/chat",
                metadata = mapOf("warningsCount" to warnings)
            )
            if (warnings > 0) {
                AgentTelemetryContext.recordStageEvent(
                    stage = "openai_chat",
                    eventType = "warning",
                    status = "completed",
                    detail = "AI Gateway returned warnings",
                    payload = mapOf("warningsCount" to warnings)
                )
            }
            response
        } catch (e: AIGatewayIntegrationException) {
            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            val timeout = isTimeoutFailure(e)
            log.error(
                "ai_gateway.error provider=feign trace_id={} operation={} path={} status_code={} timeout={} duration_ms={} body={}",
                resolvedTraceId,
                "openai_chat",
                "/v1/openai/chat",
                e.statusCode,
                timeout,
                String.format(Locale.US, "%.4f", durationMs),
                e.responseBody,
                e
            )
            AgentTelemetryContext.recordLlmCall(
                provider = "openai",
                operation = "openai_chat",
                status = "error",
                durationMs = durationMs,
                endpoint = "/v1/openai/chat",
                errorMessage = e.message,
                metadata = mapOf(
                    "statusCode" to e.statusCode,
                    "timeout" to timeout
                )
            )
            throw e
        } catch (e: Exception) {
            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            val timeout = isTimeoutFailure(e)
            log.error(
                "ai_gateway.error provider=feign trace_id={} operation={} path={} timeout={} duration_ms={} error_type={} error_message={}",
                resolvedTraceId,
                "openai_chat",
                "/v1/openai/chat",
                timeout,
                String.format(Locale.US, "%.4f", durationMs),
                e::class.java.simpleName,
                e.message,
                e
            )
            AgentTelemetryContext.recordLlmCall(
                provider = "openai",
                operation = "openai_chat",
                status = "error",
                durationMs = durationMs,
                endpoint = "/v1/openai/chat",
                errorMessage = e.message,
                metadata = mapOf("timeout" to timeout)
            )
            throw AIGatewayIntegrationException(
                message = "Falha ao chamar AI Gateway em openai_chat: ${e.message}",
                cause = e
            )
        }
    }

    override fun chatJudge(
        evaluationId: String,
        traceId: String?
    ): AIGatewayChatJudgeIntegrationResponse {
        val resolvedTraceId = translator.resolveTraceId(traceId)
        val startedNs = System.nanoTime()
        log.info(
            "ai_gateway.request provider=feign trace_id={} operation={} path={} evaluation_id={}",
            resolvedTraceId,
            "openai_chat_judge",
            "/v1/openai/chat/judge/{evaluationId}",
            evaluationId
        )

        return try {
            val response = feignClient.chatJudge(
                evaluationId = evaluationId,
                traceId = resolvedTraceId
            )
            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            log.info(
                "ai_gateway.response provider=feign trace_id={} operation={} path={} status={} duration_ms={}",
                resolvedTraceId,
                "openai_chat_judge",
                "/v1/openai/chat/judge/{evaluationId}",
                response.status,
                String.format(Locale.US, "%.4f", durationMs)
            )
            response
        } catch (e: AIGatewayIntegrationException) {
            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            log.error(
                "ai_gateway.error provider=feign trace_id={} operation={} path={} status_code={} duration_ms={} body={}",
                resolvedTraceId,
                "openai_chat_judge",
                "/v1/openai/chat/judge/{evaluationId}",
                e.statusCode,
                String.format(Locale.US, "%.4f", durationMs),
                e.responseBody,
                e
            )
            throw e
        } catch (e: Exception) {
            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            log.error(
                "ai_gateway.error provider=feign trace_id={} operation={} path={} duration_ms={} error_type={} error_message={}",
                resolvedTraceId,
                "openai_chat_judge",
                "/v1/openai/chat/judge/{evaluationId}",
                String.format(Locale.US, "%.4f", durationMs),
                e::class.java.simpleName,
                e.message,
                e
            )
            throw AIGatewayIntegrationException(
                message = "Falha ao chamar AI Gateway em openai_chat_judge: ${e.message}",
                cause = e
            )
        }
    }

    override fun route(request: AIGatewayRouteIntegrationRequest): AIGatewayIntegrationResponse {
        val resolvedTraceId = translator.resolveTraceId(request.traceId)
        return executePost(
            path = "/ai/router",
            operation = "ai_router",
            traceId = resolvedTraceId,
            payloadKeys = request.payload.keys
        ) {
            translator.toIntegrationResponse(
                feignClient.route(
                    request = translator.toRouteFeignRequest(request.copy(traceId = resolvedTraceId)),
                    traceId = resolvedTraceId
                )
            )
        }
    }

    override fun pipelinePlanoImagem(
        request: AIGatewayPipelinePlanoImagemIntegrationRequest
    ): AIGatewayIntegrationResponse {
        val resolvedTraceId = translator.resolveTraceId(request.traceId)
        return executePost(
            path = planoImagemPath,
            operation = "pipeline_plano_imagem",
            traceId = resolvedTraceId,
            payloadKeys = listOf("imagem_url", "contexto", "idioma", "executar_ocr_literal")
        ) {
            translator.toIntegrationResponse(
                feignClient.pipelinePlanoImagem(
                    request = translator.toPipelinePlanoImagemFeignRequest(request.copy(traceId = resolvedTraceId)),
                    traceId = resolvedTraceId
                )
            )
        }
    }

    override fun pipelinePlanoE2eTemporario(
        request: AIGatewayPipelinePlanoE2eTemporarioIntegrationRequest
    ): AIGatewayIntegrationResponse {
        val resolvedTraceId = translator.resolveTraceId(request.traceId)
        return executePost(
            path = planoE2eTemporarioPath,
            operation = "pipeline_plano_e2e_temporario",
            traceId = resolvedTraceId,
            payloadKeys = request.payload.keys
        ) {
            translator.toIntegrationResponse(
                feignClient.pipelinePlanoE2eTemporario(
                    request = translator.toPipelinePlanoE2eTemporarioFeignRequest(
                        request.copy(traceId = resolvedTraceId)
                    ),
                    traceId = resolvedTraceId
                )
            )
        }
    }

    override fun pipelineFotoCalorias(
        request: AIGatewayPipelineFotoCaloriasIntegrationRequest
    ): AIGatewayIntegrationResponse {
        val resolvedTraceId = translator.resolveTraceId(request.traceId)
        val payloadKeys = translator.toPipelineFotoCaloriasBody(request.copy(traceId = resolvedTraceId)).keys
        return executePost(
            path = fotoCaloriasPath,
            operation = "pipeline_foto_calorias",
            traceId = resolvedTraceId,
            payloadKeys = payloadKeys
        ) {
            translator.toIntegrationResponse(
                feignClient.pipelineFotoCalorias(
                    request = translator.toPipelineFotoCaloriasFeignRequest(request.copy(traceId = resolvedTraceId)),
                    traceId = resolvedTraceId
                )
            )
        }
    }

    private fun executePost(
        path: String,
        operation: String,
        traceId: String?,
        payloadKeys: Collection<*>,
        request: () -> AIGatewayIntegrationResponse
    ): AIGatewayIntegrationResponse {
        val startedNs = System.nanoTime()
        log.info(
            "ai_gateway.request provider=feign trace_id={} operation={} path={} payload_keys={}",
            traceId,
            operation,
            path,
            payloadKeys
        )

        return try {
            val response = request()
            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            log.info(
                "ai_gateway.response provider=feign trace_id={} operation={} path={} status={} warnings={} duration_ms={}",
                response.traceId ?: traceId,
                operation,
                path,
                response.status,
                response.warnings?.size ?: 0,
                String.format(Locale.US, "%.4f", durationMs)
            )
            recordOperationalTelemetry(operation, path, response, durationMs)
            response
        } catch (e: AIGatewayIntegrationException) {
            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            val timeout = isTimeoutFailure(e)
            log.error(
                "ai_gateway.error provider=feign trace_id={} operation={} path={} status_code={} timeout={} duration_ms={} body={}",
                traceId,
                operation,
                path,
                e.statusCode,
                timeout,
                String.format(Locale.US, "%.4f", durationMs),
                e.responseBody,
                e
            )
            AgentTelemetryContext.recordStageEvent(
                stage = operation,
                eventType = if (timeout) "timeout" else "error",
                status = "error",
                durationMs = durationMs,
                detail = e.message,
                payload = mapOf(
                    "path" to path,
                    "statusCode" to e.statusCode,
                    "timeout" to timeout
                )
            )
            throw e
        } catch (e: Exception) {
            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            val timeout = isTimeoutFailure(e)
            log.error(
                "ai_gateway.error provider=feign trace_id={} operation={} path={} timeout={} duration_ms={} error_type={} error_message={}",
                traceId,
                operation,
                path,
                timeout,
                String.format(Locale.US, "%.4f", durationMs),
                e::class.java.simpleName,
                e.message,
                e
            )
            AgentTelemetryContext.recordStageEvent(
                stage = operation,
                eventType = if (timeout) "timeout" else "error",
                status = "error",
                durationMs = durationMs,
                detail = e.message,
                payload = mapOf(
                    "path" to path,
                    "timeout" to timeout
                )
            )
            throw AIGatewayIntegrationException(
                message = "Falha ao chamar AI Gateway em $operation: ${e.message}",
                cause = e
            )
        }
    }

    private fun recordOperationalTelemetry(
        operation: String,
        path: String,
        response: AIGatewayIntegrationResponse,
        durationMs: Double
    ) {
        val warningsCount = response.warnings?.size ?: 0
        val hasLlmUsage = !response.model.isNullOrBlank() ||
            response.usage != null ||
            !response.providerResponseId.isNullOrBlank()

        if (hasLlmUsage) {
            AgentTelemetryContext.recordLlmCall(
                provider = "ai_gateway",
                operation = operation,
                model = response.model,
                status = response.status ?: "success",
                inputTokens = response.usage?.inputTokens,
                outputTokens = response.usage?.outputTokens,
                totalTokens = response.usage?.totalTokens,
                durationMs = durationMs,
                providerResponseId = response.providerResponseId,
                endpoint = path,
                metadata = mapOf("warningsCount" to warningsCount)
            )
        }

        AgentTelemetryContext.recordStageEvent(
            stage = operation,
            eventType = "flow",
            status = if (response.status.equals("erro", ignoreCase = true)) "error" else "completed",
            durationMs = durationMs,
            detail = "AI Gateway call completed",
            payload = mapOf(
                "path" to path,
                "warningsCount" to warningsCount,
                "needsReview" to (response.precisaRevisao == true)
            )
        )

        if (warningsCount > 0 || response.precisaRevisao == true) {
            AgentTelemetryContext.recordStageEvent(
                stage = operation,
                eventType = "warning",
                status = "completed",
                detail = "AI Gateway returned warnings or review flags",
                payload = mapOf(
                    "warningsCount" to warningsCount,
                    "needsReview" to (response.precisaRevisao == true)
                )
            )
        }
    }

    private fun isTimeoutFailure(failure: Throwable?): Boolean {
        var current = failure
        while (current != null) {
            val name = current::class.java.simpleName.lowercase()
            val message = (current.message ?: "").lowercase()
            if (
                current is TimeoutException ||
                current is SocketTimeoutException ||
                name.contains("timeout") ||
                message.contains("timeout") ||
                message.contains("timed out")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
