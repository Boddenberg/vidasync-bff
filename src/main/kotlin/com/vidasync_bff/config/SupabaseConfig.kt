package com.vidasync_bff.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import org.springframework.web.util.DefaultUriBuilderFactory

@Configuration
class SupabaseConfig(
    @Value("\${supabase.url:}") private val supabaseUrl: String,
    @Value("\${supabase.anon-key:}") private val supabaseAnonKey: String
) {

    private val log = LoggerFactory.getLogger(SupabaseConfig::class.java)

    @Bean
    fun supabaseRestClient(): RestClient {
        // Fail fast if configuration is missing
        if (supabaseUrl.isBlank()) {
            throw IllegalStateException(
                "Missing Supabase configuration: 'supabase.url' is not set. Please set SUPABASE_URL in .env.properties or application properties."
            )
        }
        if (supabaseAnonKey.isBlank()) {
            throw IllegalStateException(
                "Missing Supabase configuration: 'supabase.anon-key' is not set. Please set SUPABASE_ANON_KEY in .env.properties or application properties."
            )
        }

        // Normalize URL: remove trailing slash and ensure scheme
        var normalized = supabaseUrl.trim()
        // remove trailing slashes
        while (normalized.endsWith("/")) normalized = normalized.dropLast(1)
        // ensure scheme (default to https)
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }

        val base = "$normalized/rest/v1"

        // Log configured base (safe — does not include anon key)
        log.info("Configured Supabase base URL: {}", base)

        // Use URI_COMPONENT encoding so {..} in query values are NOT treated as template variables
        val uriFactory = DefaultUriBuilderFactory(base)
        uriFactory.encodingMode = DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT

        return RestClient.builder()
            .uriBuilderFactory(uriFactory)
            .defaultHeader("apikey", supabaseAnonKey)
            .defaultHeader("Authorization", "Bearer $supabaseAnonKey")
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Prefer", "return=representation")
            .build()
    }
}
