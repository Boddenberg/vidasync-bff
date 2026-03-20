package com.vidasync_bff.integration.aigateway.impl

import com.vidasync_bff.integration.aigateway.AIGatewayIntegration
import com.vidasync_bff.integration.aigateway.AIGatewayIntegrationException
import com.vidasync_bff.integration.aigateway.feign.AIGatewayFeignClient
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelineFotoCaloriasIntegrationRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelinePlanoE2eTemporarioIntegrationRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelinePlanoImagemIntegrationRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayRouteIntegrationRequest
import com.vidasync_bff.integration.aigateway.response.AIGatewayIntegrationResponse
import com.vidasync_bff.integration.aigateway.translator.AIGatewayIntegrationTranslator
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
            throw AIGatewayIntegrationException(
                message = "Falha ao chamar AI Gateway em $operation: ${e.message}",
                cause = e
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
