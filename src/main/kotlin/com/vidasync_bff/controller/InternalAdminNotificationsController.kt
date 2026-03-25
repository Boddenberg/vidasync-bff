package com.vidasync_bff.controller

import com.vidasync_bff.dto.request.PublishNotificationBroadcastRequest
import com.vidasync_bff.dto.request.PublishNotificationToUserRequest
import com.vidasync_bff.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/internal/admin/notifications")
class InternalAdminNotificationsController(
    private val notificationService: NotificationService
) {

    private val log = LoggerFactory.getLogger(InternalAdminNotificationsController::class.java)

    @PostMapping
    fun publishToUser(
        @RequestHeader("X-Internal-Api-Key", required = false) internalApiKey: String?,
        @RequestBody body: PublishNotificationToUserRequest
    ): ResponseEntity<Any> {
        log.info(
            "POST /internal/admin/notifications | targetUserId={}, type={}",
            body.userId,
            body.type
        )
        return try {
            val result = notificationService.publishToUser(internalApiKey, body)
            log.info(
                "POST /internal/admin/notifications -> 201 | targetUserId={}, notificationId={}",
                body.userId,
                result.id
            )
            ResponseEntity.status(HttpStatus.CREATED).body(mapOf("notification" to result))
        } catch (ex: ResponseStatusException) {
            log.warn(
                "POST /internal/admin/notifications -> {} | error={}",
                ex.statusCode.value(),
                ex.reason
            )
            ResponseEntity.status(ex.statusCode).body(mapOf("error" to (ex.reason ?: "erro interno")))
        } catch (ex: IllegalArgumentException) {
            log.warn("POST /internal/admin/notifications -> 400 | error={}", ex.message)
            ResponseEntity.badRequest().body(mapOf("error" to ex.message))
        } catch (ex: Exception) {
            log.error("POST /internal/admin/notifications -> 500 | error={}", ex.message, ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (ex.message ?: "erro interno")))
        }
    }

    @PostMapping("/broadcast")
    fun publishToAll(
        @RequestHeader("X-User-Id") createdBy: String,
        @RequestHeader("X-Internal-Api-Key", required = false) internalApiKey: String?,
        @RequestBody body: PublishNotificationBroadcastRequest
    ): ResponseEntity<Any> {
        log.info(
            "POST /internal/admin/notifications/broadcast | actorUserId={}, type={}",
            createdBy,
            body.type
        )
        return try {
            val result = notificationService.publishToAll(createdBy, internalApiKey, body)
            log.info(
                "POST /internal/admin/notifications/broadcast -> 200 | actorUserId={}, createdCount={}",
                createdBy,
                result.createdCount
            )
            ResponseEntity.ok(result)
        } catch (ex: ResponseStatusException) {
            log.warn(
                "POST /internal/admin/notifications/broadcast -> {} | error={}",
                ex.statusCode.value(),
                ex.reason
            )
            ResponseEntity.status(ex.statusCode).body(mapOf("error" to (ex.reason ?: "erro interno")))
        } catch (ex: IllegalArgumentException) {
            log.warn("POST /internal/admin/notifications/broadcast -> 400 | error={}", ex.message)
            ResponseEntity.badRequest().body(mapOf("error" to ex.message))
        } catch (ex: Exception) {
            log.error("POST /internal/admin/notifications/broadcast -> 500 | error={}", ex.message, ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (ex.message ?: "erro interno")))
        }
    }
}
