package com.vidasync_bff.controller

import com.vidasync_bff.dto.request.ChatRequest
import com.vidasync_bff.service.ChatService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/chat")
class ChatController(
    private val chatService: ChatService
) {

    private val log = LoggerFactory.getLogger(ChatController::class.java)

    @PostMapping
    fun chat(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestBody body: ChatRequest
    ): ResponseEntity<Any> {
        log.info(
            "POST /chat | userIdPresent={} promptChars={} hasConversationId={}",
            !userId.isNullOrBlank(),
            body.prompt?.length ?: 0,
            !body.conversationId.isNullOrBlank()
        )

        return try {
            val result = chatService.chat(userId, body)
            log.info(
                "POST /chat -> 200 | conversationId={} intent={} needsReview={} warnings={} traceId={}",
                result.conversationId,
                result.intent,
                result.needsReview,
                result.warnings?.size ?: 0,
                result.traceId
            )
            ResponseEntity.ok(result)
        } catch (ex: IllegalArgumentException) {
            log.warn("POST /chat -> 400 | error={}", ex.message)
            ResponseEntity.badRequest().body(mapOf("error" to ex.message))
        } catch (ex: ResponseStatusException) {
            log.warn("POST /chat -> {} | error={}", ex.statusCode.value(), ex.reason)
            ResponseEntity.status(ex.statusCode).body(mapOf("error" to (ex.reason ?: "erro interno")))
        } catch (ex: Exception) {
            log.error("POST /chat -> 500 | error={}", ex.message, ex)
            ResponseEntity.internalServerError().body(mapOf("error" to ex.message))
        }
    }
}
