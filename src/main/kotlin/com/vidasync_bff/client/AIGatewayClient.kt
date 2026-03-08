package com.vidasync_bff.client

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

    private val log = LoggerFactory.getLogger(AIGatewayClient::class.java)

    fun route(
        contexto: String,
        payload: Map<String, Any?>,
        idioma: String = "pt-BR",
        traceId: String? = null,
        metadados: Map<String, Any?> = mapOf("origem" to "vidasync-bff")
    ): AIGatewayRouteResponse {
        val resolvedTraceId = traceId?.takeIf { it.isNotBlank() } ?: TraceContext.current()
        val startedNs = System.nanoTime()
        val request = AIGatewayRouteRequest(
            traceId = resolvedTraceId,
            contexto = contexto,
            idioma = idioma,
            payload = payload,
            metadados = metadados
        )

        log.info(
            "ai_gateway.request trace_id={} contexto={} idioma={} payload_keys={}",
            resolvedTraceId, contexto, idioma, payload.keys
        )

        return try {
            var requestSpec = aiGatewayRestClient.post().uri("/ai/router")
            if (!resolvedTraceId.isNullOrBlank()) {
                requestSpec = requestSpec.header(TraceContext.TRACE_HEADER, resolvedTraceId)
            }
            val response = requestSpec
                .body(request)
                .retrieve()
                .body(AIGatewayRouteResponse::class.java)

            if (response == null) {
                throw IllegalStateException("Resposta vazia do AI Gateway")
            }

            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            log.info(
                "ai_gateway.response trace_id={} contexto={} status={} warnings={} duration_ms={}",
                response.traceId ?: resolvedTraceId,
                response.contexto,
                response.status,
                response.warnings?.size ?: 0,
                String.format(Locale.US, "%.4f", durationMs),
            )
            response
        } catch (e: HttpStatusCodeException) {
            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            val timeout = e.statusCode.value() in listOf(408, 504)
            log.error(
                "ai_gateway.error trace_id={} contexto={} status_code={} timeout={} duration_ms={} body={}",
                resolvedTraceId,
                contexto,
                e.statusCode.value(),
                timeout,
                String.format(Locale.US, "%.4f", durationMs),
                e.responseBodyAsString,
                e,
            )
            throw IllegalStateException("Falha ao chamar AI Gateway: HTTP ${e.statusCode.value()}", e)
        } catch (e: Exception) {
            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            val timeout = isTimeoutFailure(e)
            log.error(
                "ai_gateway.error trace_id={} contexto={} timeout={} duration_ms={} error_type={} error_message={}",
                resolvedTraceId,
                contexto,
                timeout,
                String.format(Locale.US, "%.4f", durationMs),
                e::class.java.simpleName,
                e.message,
                e,
            )
            throw IllegalStateException("Falha ao chamar AI Gateway: ${e.message}", e)
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
