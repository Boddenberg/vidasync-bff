package com.vidasync_bff.observability

import kotlin.test.Test
import kotlin.test.assertTrue

class HttpMetricsRegistryTests {

    @Test
    fun `deve expor metricas de latencia e timeout por endpoint`() {
        val registry = HttpMetricsRegistry()

        registry.recordHttp(
            method = "POST",
            path = "/nutrition/calories",
            statusCode = 200,
            durationMs = 123.45,
            timeout = false,
        )
        registry.recordHttp(
            method = "POST",
            path = "/nutrition/calories",
            statusCode = 504,
            durationMs = 3000.0,
            timeout = true,
        )

        val metrics = registry.renderPrometheus()
        assertTrue(metrics.contains("bff_http_requests_total"))
        assertTrue(metrics.contains("""path="/nutrition/calories""""))
        assertTrue(metrics.contains("bff_http_request_duration_ms_sum"))
        assertTrue(metrics.contains("bff_http_timeouts_total"))
    }
}
