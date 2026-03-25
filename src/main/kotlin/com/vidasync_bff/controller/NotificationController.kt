package com.vidasync_bff.controller

import com.vidasync_bff.dto.request.UpdateNotificationsRequest
import com.vidasync_bff.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {

    private val log = LoggerFactory.getLogger(NotificationController::class.java)

    @GetMapping
    fun getInbox(
        @RequestHeader("X-User-Id") userId: String
    ): ResponseEntity<Any> {
        log.info("GET /notifications | userId={}", userId)
        return try {
            val result = notificationService.getInbox(userId)
            log.info("GET /notifications -> 200 | userId={}, count={}, unreadCount={}", userId, result.notifications.size, result.unreadCount)
            ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            log.warn("GET /notifications -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("GET /notifications -> 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/read")
    fun markRead(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody body: UpdateNotificationsRequest
    ): ResponseEntity<Any> {
        log.info("POST /notifications/read | userId={}, idsCount={}, markAll={}", userId, body.notificationIds?.size ?: 0, body.markAll == true)
        return try {
            val result = notificationService.markRead(userId, body)
            log.info("POST /notifications/read -> 200 | userId={}, affected={}, unreadCount={}", userId, result.notifications.size, result.unreadCount)
            ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            log.warn("POST /notifications/read -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("POST /notifications/read -> 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/delete")
    fun markDeleted(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody body: UpdateNotificationsRequest
    ): ResponseEntity<Any> {
        log.info("POST /notifications/delete | userId={}, idsCount={}, markAll={}", userId, body.notificationIds?.size ?: 0, body.markAll == true)
        return try {
            val result = notificationService.markDeleted(userId, body)
            log.info("POST /notifications/delete -> 200 | userId={}, affected={}, unreadCount={}", userId, result.notifications.size, result.unreadCount)
            ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            log.warn("POST /notifications/delete -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("POST /notifications/delete -> 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @DeleteMapping
    fun deleteAll(
        @RequestHeader("X-User-Id") userId: String
    ): ResponseEntity<Any> {
        log.info("DELETE /notifications | userId={}", userId)
        return try {
            val result = notificationService.deleteAll(userId)
            log.info("DELETE /notifications -> 200 | userId={}, deletedCount={}", userId, result.deletedCount)
            ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            log.warn("DELETE /notifications -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("DELETE /notifications -> 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }
}
