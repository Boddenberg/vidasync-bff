package com.vidasync_bff.controller

import com.vidasync_bff.dto.request.UploadPresignRequest
import com.vidasync_bff.service.UploadService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/uploads")
class UploadController(private val uploadService: UploadService) {

    private val log = LoggerFactory.getLogger(UploadController::class.java)

    @PostMapping("/presign")
    fun presign(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody request: UploadPresignRequest
    ): ResponseEntity<Any> {
        log.info(
            "POST /uploads/presign | userId={}, fileName={}, mimeType={}, sizeBytes={}, kind={}",
            userId, request.fileName, request.mimeType, request.sizeBytes, request.kind
        )

        return try {
            val response = uploadService.createPresignedUpload(userId, request)
            ResponseEntity.ok(response)
        } catch (e: IllegalArgumentException) {
            log.warn("POST /uploads/presign -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid request")))
        } catch (e: Exception) {
            log.error("POST /uploads/presign -> 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to (e.message ?: "Internal error")))
        }
    }
}
