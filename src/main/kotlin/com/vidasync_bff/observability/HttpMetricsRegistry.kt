package com.vidasync_bff.observability

import org.springframework.stereotype.Component
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.DoubleAdder

@Component
class HttpMetricsRegistry {

    private val requestCount = ConcurrentHashMap<RequestKey, AtomicLong>()
    private val durationCount = ConcurrentHashMap<RequestKey, AtomicLong>()
    private val durationSumMs = ConcurrentHashMap<RequestKey, DoubleAdder>()
    private val timeoutCount = ConcurrentHashMap<EndpointKey, AtomicLong>()

    fun recordHttp(method: String, path: String, statusCode: Int, durationMs: Double, timeout: Boolean) {
        val requestKey = RequestKey(method.uppercase(), path, statusCode.toString())
        requestCount.computeIfAbsent(requestKey) { AtomicLong(0) }.incrementAndGet()
        durationCount.computeIfAbsent(requestKey) { AtomicLong(0) }.incrementAndGet()
        durationSumMs.computeIfAbsent(requestKey) { DoubleAdder() }.add(durationMs)

        if (timeout) {
            val endpointKey = EndpointKey(method.uppercase(), path)
            timeoutCount.computeIfAbsent(endpointKey) { AtomicLong(0) }.incrementAndGet()
        }
    }

    fun renderPrometheus(): String {
        val lines = mutableListOf<String>()

        lines += "# HELP bff_http_requests_total Total de requests HTTP recebidos no BFF."
        lines += "# TYPE bff_http_requests_total counter"
        requestCount.entries.sortedBy { it.key.toSortKey() }.forEach { (key, value) ->
            lines += """bff_http_requests_total{method="${escape(key.method)}",path="${escape(key.path)}",status="${escape(key.status)}"} ${value.get()}"""
        }

        lines += "# HELP bff_http_request_duration_ms_sum Soma de duracao das requests HTTP do BFF em ms."
        lines += "# TYPE bff_http_request_duration_ms_sum counter"
        lines += "# HELP bff_http_request_duration_ms_count Quantidade de requests HTTP do BFF com duracao."
        lines += "# TYPE bff_http_request_duration_ms_count counter"
        durationSumMs.entries.sortedBy { it.key.toSortKey() }.forEach { (key, value) ->
            val labels = """method="${escape(key.method)}",path="${escape(key.path)}",status="${escape(key.status)}""""
            lines += "bff_http_request_duration_ms_sum{$labels} ${String.format(Locale.US, "%.6f", value.sum())}"
        }
        durationCount.entries.sortedBy { it.key.toSortKey() }.forEach { (key, value) ->
            val labels = """method="${escape(key.method)}",path="${escape(key.path)}",status="${escape(key.status)}""""
            lines += "bff_http_request_duration_ms_count{$labels} ${value.get()}"
        }

        lines += "# HELP bff_http_timeouts_total Total de timeouts por endpoint HTTP no BFF."
        lines += "# TYPE bff_http_timeouts_total counter"
        timeoutCount.entries.sortedBy { it.key.toSortKey() }.forEach { (key, value) ->
            lines += """bff_http_timeouts_total{method="${escape(key.method)}",path="${escape(key.path)}"} ${value.get()}"""
        }

        return lines.joinToString(separator = "\n", postfix = "\n")
    }

    private fun escape(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private data class RequestKey(val method: String, val path: String, val status: String) {
        fun toSortKey(): String = "$method|$path|$status"
    }

    private data class EndpointKey(val method: String, val path: String) {
        fun toSortKey(): String = "$method|$path"
    }
}
