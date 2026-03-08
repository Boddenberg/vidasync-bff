package com.vidasync_bff.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.DefaultUriBuilderFactory
import java.util.Base64
import java.util.UUID

@Component
class SupabaseStorageClient(
    @Value("\${supabase.url:}") private val supabaseUrl: String,
    @Value("\${supabase.anon-key:}") private val supabaseAnonKey: String,
    @Value("\${supabase.service-role-key:}") private val supabaseServiceRoleKey: String,
    @Value("\${supabase.storage.bucket:favorite-images}") private val bucket: String,
    @Value("\${supabase.storage.signed-download-ttl-seconds:120}") private val signedDownloadTtlSeconds: Int,
    @Value("\${supabase.storage.signed-upload-ttl-seconds:900}") private val signedUploadTtlSeconds: Int
) {

    data class UploadedObject(
        val fileKey: String,
        val contentType: String,
        val sizeBytes: Int
    )

    data class SignedUploadResult(
        val uploadUrl: String,
        val fileKey: String,
        val expiresIn: Int
    )

    private data class DecodedFile(
        val bytes: ByteArray,
        val contentType: String,
        val extension: String
    )

    private val log = LoggerFactory.getLogger(SupabaseStorageClient::class.java)

    private val normalizedSupabaseUrl: String by lazy {
        normalizeBaseUrl(supabaseUrl)
    }

    private val storageApiBaseUrl: String by lazy {
        val base = "$normalizedSupabaseUrl/storage/v1"
        log.info("Configured Supabase Storage base URL: {}", base)
        base
    }

    private val storageClient: RestClient by lazy {
        buildStorageClient(requireAuthKey("storage upload"))
    }

    private val storageAdminClient: RestClient by lazy {
        val key = requireAuthKey("signed URL generation")
        if (supabaseServiceRoleKey.isBlank()) {
            log.warn(
                "SUPABASE_SERVICE_ROLE_KEY is not configured; signed URLs may fail for private buckets (fallbacking to anon key)."
            )
        }
        buildStorageClient(key)
    }

    /**
     * Uploads a base64-encoded image to Supabase Storage and returns the public URL.
     * Kept for backward compatibility with existing endpoints.
     */
    fun uploadBase64Image(base64Data: String, fileNamePrefix: String = "fav"): String {
        return uploadBase64Image(base64Data, fileNamePrefix, bucket)
    }

    fun uploadBase64Image(base64Data: String, fileNamePrefix: String, targetBucket: String): String {
        val uploaded = uploadBase64Object(base64Data, fileNamePrefix, targetBucket)
        val publicUrl = buildPublicObjectUrl(uploaded.fileKey, targetBucket)
        log.info("Image uploaded: {}", publicUrl)
        return publicUrl
    }

    fun uploadBase64Object(
        base64Data: String,
        fileNamePrefix: String = "upload",
        targetBucket: String = bucket
    ): UploadedObject {
        val decoded = decodeBase64Image(base64Data)
        val safePrefix = sanitizeSegment(fileNamePrefix.ifBlank { "upload" })
        val fileKey = "${safePrefix}_${UUID.randomUUID()}.${decoded.extension}"

        log.info(
            "Uploading object to storage: bucket={}, fileKey={}, size={} bytes, contentType={}",
            targetBucket,
            fileKey,
            decoded.bytes.size,
            decoded.contentType
        )

        storageClient.post()
            .uri { it.path("/object/$targetBucket/$fileKey").build() }
            .contentType(MediaType.parseMediaType(decoded.contentType))
            .header("x-upsert", "true")
            .body(decoded.bytes)
            .retrieve()
            .toBodilessEntity()

        return UploadedObject(
            fileKey = fileKey,
            contentType = decoded.contentType,
            sizeBytes = decoded.bytes.size
        )
    }

    fun createSignedUploadUrl(
        fileKey: String,
        targetBucket: String = bucket,
        expiresInSeconds: Int = signedUploadTtlSeconds
    ): SignedUploadResult {
        val normalizedKey = normalizeFileKey(fileKey)
        val response = storageAdminClient.post()
            .uri { it.path("/object/upload/sign/$targetBucket/$normalizedKey").build() }
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("expiresIn" to expiresInSeconds))
            .retrieve()
            .body(Map::class.java) ?: throw IllegalStateException("Supabase returned empty body while creating signed upload URL")

        val token = mapString(response, "token")
        val signedPath = mapString(response, "url")
            ?: mapString(response, "signedUrl")
            ?: mapString(response, "signedURL")
            ?: token?.let { "/object/upload/sign/$targetBucket/$normalizedKey?token=$it" }
            ?: throw IllegalStateException("Supabase response missing signed upload path")

        val uploadUrl = toAbsoluteStorageUrl(signedPath)
        return SignedUploadResult(
            uploadUrl = uploadUrl,
            fileKey = normalizedKey,
            expiresIn = expiresInSeconds
        )
    }

    fun createSignedDownloadUrl(
        fileKey: String,
        targetBucket: String = bucket,
        expiresInSeconds: Int = signedDownloadTtlSeconds
    ): String {
        val normalizedKey = normalizeFileKey(fileKey)
        val response = storageAdminClient.post()
            .uri { it.path("/object/sign/$targetBucket/$normalizedKey").build() }
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("expiresIn" to expiresInSeconds))
            .retrieve()
            .body(Map::class.java) ?: throw IllegalStateException("Supabase returned empty body while creating signed download URL")

        val signedPath = mapString(response, "signedURL")
            ?: mapString(response, "signedUrl")
            ?: mapString(response, "url")
            ?: throw IllegalStateException("Supabase response missing signed download path")

        return toAbsoluteStorageUrl(signedPath)
    }

    fun buildPublicObjectUrl(fileKey: String, targetBucket: String = bucket): String {
        val normalizedKey = normalizeFileKey(fileKey)
        return "$normalizedSupabaseUrl/storage/v1/object/public/$targetBucket/$normalizedKey"
    }

    private fun buildStorageClient(authKey: String): RestClient {
        val uriFactory = DefaultUriBuilderFactory(storageApiBaseUrl)
        uriFactory.encodingMode = DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT

        return RestClient.builder()
            .uriBuilderFactory(uriFactory)
            .defaultHeader("apikey", authKey)
            .defaultHeader("Authorization", "Bearer $authKey")
            .build()
    }

    private fun normalizeBaseUrl(url: String): String {
        var normalized = url.trim()
        while (normalized.endsWith("/")) normalized = normalized.dropLast(1)
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        if (normalized.isBlank() || normalized == "https://") {
            throw IllegalStateException(
                "Missing Supabase configuration: 'supabase.url' is not set. Please set SUPABASE_URL."
            )
        }
        return normalized
    }

    private fun requireAuthKey(operation: String): String {
        val key = when {
            supabaseServiceRoleKey.isNotBlank() -> supabaseServiceRoleKey.trim()
            supabaseAnonKey.isNotBlank() -> supabaseAnonKey.trim()
            else -> ""
        }
        if (key.isBlank()) {
            throw IllegalStateException(
                "Missing Supabase Storage credentials for $operation. Configure SUPABASE_SERVICE_ROLE_KEY or SUPABASE_ANON_KEY."
            )
        }
        return key
    }

    private fun decodeBase64Image(base64Data: String): DecodedFile {
        val raw = if (base64Data.contains(",")) {
            base64Data.substringAfter(",")
        } else {
            base64Data
        }

        val contentType = when {
            base64Data.startsWith("data:image/png", ignoreCase = true) -> "image/png"
            base64Data.startsWith("data:image/webp", ignoreCase = true) -> "image/webp"
            else -> "image/jpeg"
        }
        val extension = when (contentType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }

        return DecodedFile(
            bytes = Base64.getDecoder().decode(raw),
            contentType = contentType,
            extension = extension
        )
    }

    private fun mapString(source: Map<*, *>, key: String): String? {
        return source[key]?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun toAbsoluteStorageUrl(pathOrUrl: String): String {
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl
        }
        val normalizedPath = if (pathOrUrl.startsWith("/")) pathOrUrl else "/$pathOrUrl"
        return if (normalizedPath.startsWith("/storage/v1")) {
            "$normalizedSupabaseUrl$normalizedPath"
        } else {
            "$normalizedSupabaseUrl/storage/v1$normalizedPath"
        }
    }

    private fun sanitizeSegment(value: String): String {
        val sanitized = value.trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        return sanitized.ifBlank { "file" }
    }

    private fun normalizeFileKey(fileKey: String): String {
        val normalized = fileKey.trim().replace("\\", "/")
        return normalized.removePrefix("/").takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("fileKey is required")
    }
}
