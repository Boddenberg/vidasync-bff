package com.vidasync_bff.controller

import com.vidasync_bff.dto.request.UpsertNutritionGoalsRequest
import com.vidasync_bff.service.NutritionGoalsService
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
@RequestMapping("/nutrition-goals")
class NutritionGoalsController(
    private val nutritionGoalsService: NutritionGoalsService
) {

    private val log = LoggerFactory.getLogger(NutritionGoalsController::class.java)

    @PostMapping
    fun upsert(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody body: UpsertNutritionGoalsRequest
    ): ResponseEntity<Any> {
        log.info(
            "POST /nutrition-goals | userId={}, date={}, caloriesGoal={}, proteinGoal={}, carbsGoal={}, fatGoal={}",
            userId, body.date, body.caloriesGoal, body.proteinGoal, body.carbsGoal, body.fatGoal
        )
        return try {
            val result = nutritionGoalsService.upsert(userId, body)
            log.info("POST /nutrition-goals -> 200 | id={}, date={}", result.id, result.date)
            ResponseEntity.ok(mapOf("nutritionGoals" to result))
        } catch (e: IllegalArgumentException) {
            log.warn("POST /nutrition-goals -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("POST /nutrition-goals -> 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @GetMapping
    fun getDay(
        @RequestHeader("X-User-Id") userId: String,
        @RequestParam(required = false) date: String?
    ): ResponseEntity<Any> {
        log.info("GET /nutrition-goals | userId={}, date={}", userId, date)
        return try {
            val result = nutritionGoalsService.getDay(userId, date)
            log.info("GET /nutrition-goals -> 200 | hasData={}", result != null)
            ResponseEntity.ok(mapOf("nutritionGoals" to result))
        } catch (e: IllegalArgumentException) {
            log.warn("GET /nutrition-goals -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            log.error("GET /nutrition-goals -> 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }
}
