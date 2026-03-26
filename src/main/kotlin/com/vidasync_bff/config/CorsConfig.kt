package com.vidasync_bff.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig(
    @Value("\${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
    private val allowedOriginPatternsRaw: String,
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        val allowedOriginPatterns = allowedOriginPatternsRaw
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("http://localhost:*", "http://127.0.0.1:*") }

        registry.addMapping("/**")
            .allowedOriginPatterns(*allowedOriginPatterns.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("X-Request-ID", "X-Trace-Id")
            .maxAge(3600)
    }
}
