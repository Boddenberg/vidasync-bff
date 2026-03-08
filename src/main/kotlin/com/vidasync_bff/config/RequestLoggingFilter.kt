package com.vidasync_bff.config

import com.vidasync_bff.observability.HttpMetricsRegistry
import com.vidasync_bff.observability.TraceContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeoutException

@Component
@Order(2)
class RequestLoggingFilter(
    private val metricsRegistry: HttpMetricsRegistry
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger("vidasync.http")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val traceId = TraceContext.resolveOrCreate(request.getHeader(TraceContext.TRACE_HEADER))
        TraceContext.put(traceId)

        val wrappedRequest = ContentCachingRequestWrapper(request)
        val wrappedResponse = ContentCachingResponseWrapper(response)

        val startedNs = System.nanoTime()
        var statusCode = 500
        var failure: Throwable? = null

        try {
            log.info(
                "http.request.received trace_id={} method={} path={} query={} client_ip={} content_type={} content_length={} request_body_preview={}",
                traceId,
                wrappedRequest.method,
                wrappedRequest.requestURI,
                sanitize(wrappedRequest.queryString ?: ""),
                wrappedRequest.remoteAddr,
                wrappedRequest.contentType,
                wrappedRequest.contentLengthLong,
                requestBodyPreview(wrappedRequest),
            )

            filterChain.doFilter(wrappedRequest, wrappedResponse)
            statusCode = wrappedResponse.status
        } catch (ex: Exception) {
            failure = ex
            statusCode = if (wrappedResponse.status > 0) wrappedResponse.status else 500
            throw ex
        } finally {
            val durationMs = (System.nanoTime() - startedNs) / 1_000_000.0
            val timeout = statusCode in setOf(408, 504) || isTimeoutFailure(failure)

            metricsRegistry.recordHttp(
                method = wrappedRequest.method,
                path = wrappedRequest.requestURI,
                statusCode = statusCode,
                durationMs = durationMs,
                timeout = timeout,
            )

            if (failure == null) {
                log.info(
                    "http.response.sent trace_id={} method={} path={} status={} duration_ms={} timeout={} request_body_preview={} response_body_preview={}",
                    traceId,
                    wrappedRequest.method,
                    wrappedRequest.requestURI,
                    statusCode,
                    String.format(Locale.US, "%.4f", durationMs),
                    timeout,
                    requestBodyPreview(wrappedRequest),
                    responseBodyPreview(wrappedResponse),
                )
            } else {
                log.error(
                    "http.request.failed trace_id={} method={} path={} status={} duration_ms={} timeout={} request_body_preview={} error_type={} error_message={}",
                    traceId,
                    wrappedRequest.method,
                    wrappedRequest.requestURI,
                    statusCode,
                    String.format(Locale.US, "%.4f", durationMs),
                    timeout,
                    requestBodyPreview(wrappedRequest),
                    failure::class.java.simpleName,
                    sanitize(failure.message ?: ""),
                    failure,
                )
            }

            wrappedResponse.setHeader(TraceContext.TRACE_HEADER, traceId)
            wrappedResponse.copyBodyToResponse()
            TraceContext.clear()
        }
    }

    private fun requestBodyPreview(request: ContentCachingRequestWrapper): String {
        val contentType = (request.contentType ?: "").lowercase()
        if (isBinaryContentType(contentType)) return "<body_binario_omitido>"
        return previewFromBytes(request.contentAsByteArray)
    }

    private fun responseBodyPreview(response: ContentCachingResponseWrapper): String {
        val contentType = (response.contentType ?: "").lowercase()
        if (isBinaryContentType(contentType)) return "<body_binario_omitido>"
        return previewFromBytes(response.contentAsByteArray)
    }

    private fun previewFromBytes(content: ByteArray): String {
        if (content.isEmpty()) return "(empty)"
        val text = sanitize(String(content, Charsets.UTF_8))
        return if (text.length > 4000) {
            text.substring(0, 4000) + "... (truncated, ${text.length} chars total)"
        } else {
            text
        }
    }

    private fun isBinaryContentType(contentType: String): Boolean {
        if (contentType.isBlank()) return false
        return contentType.startsWith("multipart/") ||
            contentType.startsWith("image/") ||
            contentType.startsWith("audio/") ||
            contentType.startsWith("video/") ||
            contentType.contains("application/pdf") ||
            contentType.contains("application/octet-stream")
    }

    private fun sanitize(raw: String): String {
        if (raw.isBlank()) return raw
        var text = raw
        val sensitiveKeys = listOf("authorization", "token", "api_key", "apikey", "password", "secret")
        for (key in sensitiveKeys) {
            text = text.replace(Regex("(${key}\\s*[=:]\\s*)([^&\\s,]+)", RegexOption.IGNORE_CASE), "$1***")
            text = text.replace(Regex("(\"${key}\"\\s*:\\s*\")(.*?)(\")", RegexOption.IGNORE_CASE), "$1***$3")
        }
        return text
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
