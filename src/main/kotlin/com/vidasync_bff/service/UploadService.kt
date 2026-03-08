package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseStorageClient
import com.vidasync_bff.dto.request.UploadPresignRequest
import com.vidasync_bff.dto.response.UploadPresignResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

@Service
class UploadService(
    private val storageClient: SupabaseStorageClient,
    @Value("\${supabase.pipeline.bucket:pipeline-inputs}") private val pipelineBucket: String,
    @Value("\${supabase.storage.signed-upload-ttl-seconds:900}") private val signedUploadTtlSeconds: Int,
) {

    private val log = LoggerFactory.getLogger(UploadService::class.java)
    private val maxAllowedSizeBytes = 50L * 1024L * 1024L

    fun createPresignedUpload(userId: String, request: UploadPresignRequest): UploadPresignResponse {
        val fileName = request.fileName.trim()
        val mimeType = request.mimeType.trim().lowercase()
        val kind = normalizeKind(request.kind)
        val sizeBytes = request.sizeBytes

        require(fileName.isNotBlank()) { "fileName is required" }
        require(mimeType.isNotBlank()) { "mimeType is required" }
        require(sizeBytes in 1..maxAllowedSizeBytes) { "sizeBytes must be between 1 and $maxAllowedSizeBytes" }

        val extension = inferExtension(fileName, mimeType)
        val safeFileName = sanitizeName(fileName.substringBeforeLast("."))
        val date = LocalDate.now().toString()
        val fileKey = "$kind/$userId/$date/${UUID.randomUUID()}_${safeFileName}.$extension"

        val signedUpload = storageClient.createSignedUploadUrl(
            fileKey = fileKey,
            targetBucket = pipelineBucket,
            expiresInSeconds = signedUploadTtlSeconds
        )

        log.info(
            "Created presigned upload URL: userId={}, kind={}, mimeType={}, sizeBytes={}, bucket={}, fileKey={}, expiresIn={}",
            userId,
            kind,
            mimeType,
            sizeBytes,
            pipelineBucket,
            signedUpload.fileKey,
            signedUpload.expiresIn
        )

        return UploadPresignResponse(
            uploadUrl = signedUpload.uploadUrl,
            fileKey = signedUpload.fileKey,
            expiresIn = signedUpload.expiresIn
        )
    }

    private fun normalizeKind(rawKind: String): String {
        val normalized = rawKind.trim().lowercase()
        return when (normalized) {
            "image", "audio", "pdf", "document", "video" -> normalized
            else -> "file"
        }
    }

    private fun inferExtension(fileName: String, mimeType: String): String {
        val extensionFromName = fileName.substringAfterLast(".", "").trim().lowercase()
        if (extensionFromName.matches(Regex("^[a-z0-9]{1,10}$"))) {
            return extensionFromName
        }

        return when (mimeType) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "audio/mpeg" -> "mp3"
            "audio/mp4" -> "m4a"
            "audio/wav" -> "wav"
            "application/pdf" -> "pdf"
            else -> "bin"
        }
    }

    private fun sanitizeName(value: String): String {
        val sanitized = value.trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        return sanitized.ifBlank { "file" }
    }
}
