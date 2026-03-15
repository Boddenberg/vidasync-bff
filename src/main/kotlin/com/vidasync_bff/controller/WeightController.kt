package com.vidasync_bff.controller

import com.vidasync_bff.dto.request.CreateWeightRequest
import com.vidasync_bff.service.WeightService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/weight")
class WeightController(
    private val weightService: WeightService
) {

    private val log = LoggerFactory.getLogger(WeightController::class.java)

    @PostMapping
    fun create(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody body: CreateWeightRequest
    ): ResponseEntity<Any> {
        log.info("POST /weight | userId={}, weightKg={}", userId, body.weightKg)
        return try {
            val result = weightService.create(userId, body)
            log.info("POST /weight -> 200 | id={}, measuredAt={}", result.id, result.measuredAt)
            ResponseEntity.ok(mapOf("weight" to result))
        } catch (e: IllegalArgumentException) {
            log.warn("POST /weight -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("POST /weight -> 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @GetMapping
    fun getAll(
        @RequestHeader("X-User-Id") userId: String
    ): ResponseEntity<Any> {
        log.info("GET /weight | userId={}", userId)
        return try {
            val result = weightService.getAll(userId)
            log.info("GET /weight -> 200 | totalEntries={}", result.size)
            ResponseEntity.ok(mapOf("weights" to result))
        } catch (e: IllegalArgumentException) {
            log.warn("GET /weight -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("GET /weight -> 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }
}
