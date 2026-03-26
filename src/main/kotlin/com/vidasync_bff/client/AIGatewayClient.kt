package com.vidasync_bff.client

import com.vidasync_bff.dto.ai.AIGatewayOpenAIChatRequest
import com.vidasync_bff.dto.ai.AIGatewayOpenAIChatResponse
import com.vidasync_bff.dto.ai.AIGatewayRouteRequest
import com.vidasync_bff.dto.ai.AIGatewayRouteResponse
import com.vidasync_bff.observability.TraceContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClient
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeoutException

@Component
class AIGatewayClient(private val aiGatewayRestClient: RestClient) {

    class AIGatewayRequestException(
        message: String,
        val statusCode: Int? = null,
        val responseBody: String? = null,
        cause: Throwable? = null
    ) : RuntimeException(message, cause)

    private val log = LoggerFactory.getLogger(AIGatewayClient::class.java)
    private val agentesBasePath = "/agentes"
    private val planoImagemPath = "$agentesBasePath/pipeline-plano-imagem"
    private val planoE2eTemporarioPath = "$agentesBasePath/pipeline-plano-e2e-temporario"
    private val fotoCaloriasPath = "$agentesBasePath/pipeline-foto-calorias"
    private val openAiChatPath = "/v1/openai/chat"

    fun chat(
        prompt: String,
        conversationId: String? = null,
        traceId: String? = null
    ): AIGatewayOpenAIChatResponse {
        val resolvedTraceId = traceId?.takeIf { it.isNotBlank() } ?: TraceContext.current()
        val request = AIGatewayOpenAIChatRequest(
            prompt = prompt,
            conversationId = conversationId?.takeIf { it.isNotBlank() },
            traceId = resolvedTraceId
        )
        return executeChatPost(
            path = openAiChatPath,
            operation = "openai_chat",
            traceId = resolvedTraceId,
            body = request
        )
    }

    fun route(
        contexto: String,
        payload: Map<String, Any?>,
        idioma: String = "pt-BR",
        traceId: String? = null,
        metadados: Map<String, Any?> = mapOf("origem" to "vidasync-bff")
    ): AIGatewayRouteResponse {
        val resolvedTraceId = traceId?.takeIf { it.isNotBlank() } ?: TraceContext.current()
        val request = AIGatewayRouteRequest(
            traceId = resolvedTraceId,
            contexto = contexto,
            idioma = idioma,
            payload = payload,
            metadados = metadados
        )
        return executePost(
            path = "/ai/router",
            operation = "ai_router",
            traceId = resolvedTraceId,
            body = request
        )
    }

    fun pipelinePlanoImagem(
        imagemUrl: String,
        contexto: String,
        idioma: String = "pt-BR",
        executarOcrLiteral: Boolean = false,
        traceId: String? = null
    ): AIGatewayRouteResponse {
        val resolvedTraceId = traceId?.takeIf { it.isNotBlank() } ?: TraceContext.current()
        val body = mutableMapOf<String, Any?>(
            "imagem_url" to imagemUrl,
            "contexto" to contexto,
            "idioma" to idioma,
            "executar_ocr_literal" to executarOcrLiteral
        )
        resolvedTraceId?.let { body["trace_id"] = it }
        return executePost(
            path = planoImagemPath,
            operation = "pipeline_plano_imagem",
            traceId = resolvedTraceId,
            body = body
        )
    }

    fun pipelinePlanoE2eTemporario(
        payload: Map<String, Any?>,
        traceId: String? = null
    ): AIGatewayRouteResponse {
        val resolvedTraceId = traceId?.takeIf { it.isNotBlank() } ?: TraceContext.current()
        val body = payload.toMutableMap()
        resolvedTraceId?.let { body.putIfAbsent("trace_id", it) }
        return executePost(
            path = planoE2eTemporarioPath,
            operation = "pipeline_plano_e2e_temporario",
            traceId = resolvedTraceId,
            body = body
        )
    }

    fun pipelineFotoCalorias(
        payload: Map<String, Any?>,
        idioma: String = "pt-BR",
        traceId: String? = null
    ): AIGatewayRouteResponse {
        val resolvedTraceId = traceId?.takeIf { it.isNotBlank() } ?: TraceContext.current()
        val body = payload.toMutableMap()
        body.putIfAbsent("idioma", idioma)
        resolvedTraceId?.let { body.putIfAbsent("trace_id", it) }
        return executePost(
            path = fotoCaloriasPath,
            operation = "pipeline_foto_calorias",
            traceId = resolvedTraceId,
            body = body
        )
    }

    private fun executePost(
        path: String,
        operation: String,
        traceId: String?,
        body: Any
    ): AIGatewayRouteResponse {
        val startedNs = System.nanoTime()
        val payloadKeys = if (body is Map<*, *>) body.keys else emptyList<Any>()
        log.info(
            "ai_gateway.request trace_id={} operation={} path={} payload_keys={}",
            traceId,
            operation,
            path,
            payloadKeys
        )

        return try {
            var requestSpec = aiGatewayRestClient.post().uri(path)
            if (!traceId.isNullOrBlank()) {
                requestSpec = requestSpec.header(TraceContext.TRACE_HEADER, traceId)
            }
            val response = requestSpec
                .body(body)
                .retrieve()
                .body(AIGatewayRouteResponse::class.java)

            if (response == null) {
                throw IllegalStateException("Resposta vazia do AI Gateway")
            }

            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            log.info(
                "ai_gateway.response trace_id={} operation={} path={} status={} warnings={} duration_ms={}",
                response.traceId ?: traceId,
                operation,
                path,
                response.status,
                response.warnings?.size ?: 0,
                String.format(Locale.US, "%.4f", durationMs),
            )
            response
        } catch (e: HttpStatusCodeException) {
            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            val timeout = e.statusCode.value() in listOf(408, 504)
            val statusCode = e.statusCode.value()
            val responseBody = e.responseBodyAsString
            log.error(
                "ai_gateway.error trace_id={} operation={} path={} status_code={} timeout={} duration_ms={} body={}",
                traceId,
                operation,
                path,
                statusCode,
                timeout,
                String.format(Locale.US, "%.4f", durationMs),
                responseBody,
                e,
            )
            throw AIGatewayRequestException(
                message = "Falha ao chamar AI Gateway em $operation: HTTP $statusCode",
                statusCode = statusCode,
                responseBody = responseBody,
                cause = e
            )
        } catch (e: Exception) {
            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            val timeout = isTimeoutFailure(e)
            log.error(
                "ai_gateway.error trace_id={} operation={} path={} timeout={} duration_ms={} error_type={} error_message={}",
                traceId,
                operation,
                path,
                timeout,
                String.format(Locale.US, "%.4f", durationMs),
                e::class.java.simpleName,
                e.message,
                e,
            )
            throw AIGatewayRequestException(
                message = "Falha ao chamar AI Gateway em $operation: ${e.message}",
                cause = e
            )
        }
    }

    private fun executeChatPost(
        path: String,
        operation: String,
        traceId: String?,
        body: Any
    ): AIGatewayOpenAIChatResponse {
        val startedNs = System.nanoTime()
        log.info(
            "ai_gateway.request trace_id={} operation={} path={} payload_keys={}",
            traceId,
            operation,
            path,
            listOf("prompt", "conversation_id", "trace_id")
        )

        return try {
            var requestSpec = aiGatewayRestClient.post().uri(path)
            if (!traceId.isNullOrBlank()) {
                requestSpec = requestSpec.header(TraceContext.TRACE_HEADER, traceId)
            }
            val response = requestSpec
                .body(body)
                .retrieve()
                .body(AIGatewayOpenAIChatResponse::class.java)

            if (response == null) {
                throw IllegalStateException("Resposta vazia do AI Gateway")
            }

            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            val warnings = (response.roteamento?.get("warnings") as? List<*>)?.size ?: 0
            log.info(
                "ai_gateway.response trace_id={} operation={} path={} response_chars={} warnings={} duration_ms={}",
                response.traceId ?: traceId,
                operation,
                path,
                response.response?.length ?: 0,
                warnings,
                String.format(Locale.US, "%.4f", durationMs),
            )
            response
        } catch (e: HttpStatusCodeException) {
            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            val timeout = e.statusCode.value() in listOf(408, 504)
            val statusCode = e.statusCode.value()
            val responseBody = e.responseBodyAsString
            log.error(
                "ai_gateway.error trace_id={} operation={} path={} status_code={} timeout={} duration_ms={} body={}",
                traceId,
                operation,
                path,
                statusCode,
                timeout,
                String.format(Locale.US, "%.4f", durationMs),
                responseBody,
                e,
            )
            throw AIGatewayRequestException(
                message = "Falha ao chamar AI Gateway em $operation: HTTP $statusCode",
                statusCode = statusCode,
                responseBody = responseBody,
                cause = e
            )
        } catch (e: Exception) {
            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            val timeout = isTimeoutFailure(e)
            log.error(
                "ai_gateway.error trace_id={} operation={} path={} timeout={} duration_ms={} error_type={} error_message={}",
                traceId,
                operation,
                path,
                timeout,
                String.format(Locale.US, "%.4f", durationMs),
                e::class.java.simpleName,
                e.message,
                e,
            )
            throw AIGatewayRequestException(
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
