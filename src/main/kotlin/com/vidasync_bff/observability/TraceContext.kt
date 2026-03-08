package com.vidasync_bff.observability

import org.slf4j.MDC
import java.util.UUID

object TraceContext {
    const val TRACE_HEADER = "X-Request-ID"
    private const val TRACE_KEY = "trace_id"

    fun resolveOrCreate(headerValue: String?): String {
        val candidate = headerValue?.trim().orEmpty()
        if (candidate.isNotBlank()) return candidate
        return UUID.randomUUID().toString().replace("-", "")
    }

    fun put(traceId: String) {
        MDC.put(TRACE_KEY, traceId)
    }

    fun current(): String? = MDC.get(TRACE_KEY)

    fun clear() {
        MDC.remove(TRACE_KEY)
    }
}
