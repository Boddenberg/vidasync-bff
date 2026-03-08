package com.vidasync_bff.controller

import com.vidasync_bff.observability.HttpMetricsRegistry
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MetricsController(
    private val httpMetricsRegistry: HttpMetricsRegistry
) {

    @GetMapping("/metrics", produces = ["text/plain; version=0.0.4"])
    fun metrics(): ResponseEntity<String> {
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType("text/plain; version=0.0.4"))
            .body(httpMetricsRegistry.renderPrometheus())
    }
}
