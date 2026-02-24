package com.vidasync_bff.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {

    @GetMapping("/health")
    fun health(): Map<String, Any> {
        val openaiKey = System.getenv("OPENAI_API_KEY") ?: ""
        val supabaseUrl = System.getenv("SUPABASE_URL") ?: ""
        val supabaseKey = System.getenv("SUPABASE_ANON_KEY") ?: ""

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
