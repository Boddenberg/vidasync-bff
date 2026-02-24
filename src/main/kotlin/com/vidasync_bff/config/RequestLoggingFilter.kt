package com.vidasync_bff.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper

@Component
class RequestLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger("HTTP")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val wrappedRequest = ContentCachingRequestWrapper(request)
        val wrappedResponse = ContentCachingResponseWrapper(response)

        val start = System.currentTimeMillis()

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse)
        } finally {
            val duration = System.currentTimeMillis() - start
            val method = wrappedRequest.method
            val uri = wrappedRequest.requestURI
            val query = wrappedRequest.queryString?.let { "?$it" } ?: ""
            val status = wrappedResponse.status

            // Log request
            val requestBody = getBody(wrappedRequest.contentAsByteArray)
            val responseBody = getBody(wrappedResponse.contentAsByteArray)

            log.info(
                "→ {} {}{} | body: {}",
                method, uri, query, requestBody
            )
            log.info(
                "← {} {}{} | status: {} | {}ms | body: {}",
                method, uri, query, status, duration, responseBody
            )

            // IMPORTANT: copy body back to response
            wrappedResponse.copyBodyToResponse()
        }
    }

    private fun getBody(content: ByteArray): String {
        if (content.isEmpty()) return "(empty)"
        val body = String(content, Charsets.UTF_8)
        // Truncate base64 images to keep logs readable
        return if (body.length > 1000) {
            body.substring(0, 1000) + "... (truncated, ${body.length} chars total)"
        } else {
            body
        }
    }
}
