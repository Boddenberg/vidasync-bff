package com.vidasync_bff.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(
    @Value("\${openai.api-key:}") private val openaiKey: String,
    @Value("\${supabase.url:}") private val supabaseUrl: String,
    @Value("\${supabase.anon-key:}") private val supabaseKey: String
) {

    @GetMapping("/health")
    fun health(): Map<String, Any> {
        return mapOf(
            "status" to "UP",
            "env" to mapOf(
                "OPENAI_API_KEY" to if (openaiKey.isNotBlank()) "✅ configurada (${openaiKey.take(10)}...)" else "❌ ausente",
                "SUPABASE_URL" to if (supabaseUrl.isNotBlank()) "✅ configurada" else "❌ ausente",
                "SUPABASE_ANON_KEY" to if (supabaseKey.isNotBlank()) "✅ configurada" else "❌ ausente"
            )
        )
    }
}
