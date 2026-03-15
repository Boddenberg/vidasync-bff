package com.vidasync_bff.controller

import com.vidasync_bff.dto.request.UpsertWaterRequest
import com.vidasync_bff.service.WaterService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/water")
class WaterController(
    private val waterService: WaterService
) {

    private val log = LoggerFactory.getLogger(WaterController::class.java)

    @PostMapping
    fun upsert(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody body: UpsertWaterRequest
    ): ResponseEntity<Any> {
        log.info("POST /water | userId={}, date={}, goalMl={}, deltaMl={}", userId, body.date, body.goalMl, body.deltaMl)
        return try {
            val result = waterService.upsert(userId, body)
            log.info("POST /water -> 200 | id={}, date={}, consumedMl={}, goalMl={}",
                result.id, result.date, result.consumedMl, result.goalMl)
            ResponseEntity.ok(mapOf("water" to result))
        } catch (e: IllegalArgumentException) {
            log.warn("POST /water -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("POST /water -> 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @GetMapping
    fun getDay(
        @RequestHeader("X-User-Id") userId: String,
        @RequestParam(required = false) date: String?
    ): ResponseEntity<Any> {
        log.info("GET /water | userId={}, date={}", userId, date)
        return try {
            val result = waterService.getDay(userId, date)
            log.info("GET /water -> 200 | hasData={}", result != null)
            ResponseEntity.ok(mapOf("water" to result))
        } catch (e: IllegalArgumentException) {
            log.warn("GET /water -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("GET /water -> 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/history")
    fun getHistory(
        @RequestHeader("X-User-Id") userId: String,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?
    ): ResponseEntity<Any> {
        log.info("GET /water/history | userId={}, startDate={}, endDate={}", userId, startDate, endDate)
        return try {
            val result = waterService.getHistory(userId, startDate, endDate)
            log.info("GET /water/history -> 200 | totalDays={}", result.days.size)
            ResponseEntity.ok(mapOf("waterHistory" to result))
        } catch (e: IllegalArgumentException) {
            log.warn("GET /water/history -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("GET /water/history -> 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }
}
