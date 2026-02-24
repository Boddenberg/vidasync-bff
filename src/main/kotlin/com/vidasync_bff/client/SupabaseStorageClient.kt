package com.vidasync_bff.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.DefaultUriBuilderFactory
import java.util.*

@Component
class SupabaseStorageClient(
    @Value("\${supabase.url:}") private val supabaseUrl: String,
    @Value("\${supabase.anon-key:}") private val supabaseAnonKey: String,
    @Value("\${supabase.storage.bucket:favorite-images}") private val bucket: String
) {

    private val log = LoggerFactory.getLogger(SupabaseStorageClient::class.java)

    private val storageClient: RestClient by lazy {
        var normalized = supabaseUrl.trim()
        while (normalized.endsWith("/")) normalized = normalized.dropLast(1)
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }

        val base = "$normalized/storage/v1"
        log.info("Configured Supabase Storage base URL: {}", base)

        val uriFactory = DefaultUriBuilderFactory(base)
        uriFactory.encodingMode = DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT

        RestClient.builder()
            .uriBuilderFactory(uriFactory)
            .defaultHeader("apikey", supabaseAnonKey)
            .defaultHeader("Authorization", "Bearer $supabaseAnonKey")
            .build()
    }

    /**
     * Uploads a base64-encoded image to Supabase Storage and returns the public URL.
     *
     * @param base64Data the full data URI (e.g. "data:image/jpeg;base64,/9j/...") or raw base64
     * @param fileNamePrefix prefix for the generated filename
     * @return the public URL of the uploaded image
     */
    fun uploadBase64Image(base64Data: String, fileNamePrefix: String = "fav"): String {
        // Strip the data URI prefix if present
        val raw = if (base64Data.contains(",")) {
            base64Data.substringAfter(",")
        } else {
            base64Data
        }

        // Detect content type from prefix
        val contentType = if (base64Data.startsWith("data:image/png")) {
            "image/png"
        } else {
            "image/jpeg"
        }

        val extension = if (contentType == "image/png") "png" else "jpg"
        val bytes = Base64.getDecoder().decode(raw)
        val fileName = "${fileNamePrefix}_${UUID.randomUUID()}.$extension"

        log.info("Uploading image to storage: bucket={}, file={}, size={} bytes", bucket, fileName, bytes.size)

        storageClient.post()
            .uri { it.path("/object/$bucket/$fileName").build() }
            .contentType(MediaType.parseMediaType(contentType))
            .header("x-upsert", "true")
            .body(bytes)
            .retrieve()
            .toBodilessEntity()

        // Build public URL
        var normalized = supabaseUrl.trim()
        while (normalized.endsWith("/")) normalized = normalized.dropLast(1)
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }

        val publicUrl = "$normalized/storage/v1/object/public/$bucket/$fileName"
        log.info("Image uploaded: {}", publicUrl)
        return publicUrl
    }
}
