package com.vidasync_bff.controller

import com.vidasync_bff.dto.request.CreateFeedbackRequest
import com.vidasync_bff.service.FeedbackService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/feedback")
class FeedbackController(
    private val feedbackService: FeedbackService
) {

    private val log = LoggerFactory.getLogger(FeedbackController::class.java)

    @PostMapping
    fun create(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody body: CreateFeedbackRequest
    ): ResponseEntity<Any> {
        log.info("POST /feedback | userId={}, userName={}, hasImage={}", userId, body.userName, body.imageUrl != null)
        return try {
            val result = feedbackService.create(userId, body)
            log.info("POST /feedback -> 200 | id={}, createdAt={}", result.id, result.createdAt)
            ResponseEntity.ok(mapOf("feedback" to result))
        } catch (e: IllegalArgumentException) {
            log.warn("POST /feedback -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("POST /feedback -> 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @GetMapping
    fun getAll(
        @RequestHeader("X-User-Id") actorUserId: String,
        @RequestHeader("X-Internal-Api-Key", required = false) internalApiKey: String?
    ): ResponseEntity<Any> {
        log.info("GET /feedback | actorUserId={}", actorUserId)
        return try {
            val result = feedbackService.getAll(actorUserId, internalApiKey)
            log.info("GET /feedback -> 200 | totalFeedbacks={}", result.size)
            ResponseEntity.ok(mapOf("feedbacks" to result))
        } catch (e: ResponseStatusException) {
            log.warn("GET /feedback -> {} | error={}", e.statusCode.value(), e.reason)
            ResponseEntity.status(e.statusCode).body(mapOf("error" to (e.reason ?: "erro interno")))
        } catch (e: IllegalArgumentException) {
            log.warn("GET /feedback -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("GET /feedback -> 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }
}
