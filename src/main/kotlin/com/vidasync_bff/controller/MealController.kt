package com.vidasync_bff.controller

import com.vidasync_bff.dto.request.CreateMealRequest
import com.vidasync_bff.dto.request.UpdateMealRequest
import com.vidasync_bff.service.MealService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/meals")
class MealController(private val mealService: MealService) {

    private val log = LoggerFactory.getLogger(MealController::class.java)

    @PostMapping
    fun create(@RequestHeader("X-User-Id") userId: String, @RequestBody body: CreateMealRequest): ResponseEntity<Any> {
        log.info("POST /meals | userId={}, foods={}, mealType={}, date={}, time={}, hasNutrition={}, hasImage={}",
            userId, body.foods, body.mealType, body.date, body.time, body.nutrition != null, body.image != null)
        return try {
            val result = mealService.create(userId, body)
            log.info("POST /meals → 200 | id={}", result.id)
            ResponseEntity.ok(mapOf("meal" to result))
        } catch (e: Exception) {
            log.error("POST /meals → 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @GetMapping
    fun getByDate(@RequestHeader("X-User-Id") userId: String, @RequestParam date: String): ResponseEntity<Any> {
        log.info("GET /meals | userId={}, date={}", userId, date)
        return try {
            val result = mealService.getByDate(userId, date)
            log.info("GET /meals → 200 | date={}, count={}", date, result.size)
            ResponseEntity.ok(mapOf("meals" to result))
        } catch (e: Exception) {
            log.error("GET /meals → 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/summary")
    fun getDaySummary(@RequestHeader("X-User-Id") userId: String, @RequestParam date: String): ResponseEntity<Any> {
        log.info("GET /meals/summary | userId={}, date={}", userId, date)
        return try {
            val result = mealService.getDaySummary(userId, date)
            log.info("GET /meals/summary → 200 | date={}, totalMeals={}, totals={}",
                date, result.totalMeals, result.totals)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            log.error("GET /meals/summary → 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/range")
    fun getByDateRange(
        @RequestHeader("X-User-Id") userId: String,
        @RequestParam startDate: String,
        @RequestParam endDate: String
    ): ResponseEntity<Any> {
        log.info("GET /meals/range | userId={}, startDate={}, endDate={}", userId, startDate, endDate)
        return try {
            val result = mealService.getByDateRange(userId, startDate, endDate)
            log.info("GET /meals/range → 200 | period={} to {}, count={}", startDate, endDate, result.size)
            ResponseEntity.ok(mapOf("meals" to result))
        } catch (e: Exception) {
            log.error("GET /meals/range → 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @PutMapping("/{id}")
    fun update(@RequestHeader("X-User-Id") userId: String, @PathVariable id: String, @RequestBody body: UpdateMealRequest): ResponseEntity<Any> {
        log.info("PUT /meals/{} | userId={}, foods={}, mealType={}, date={}, time={}, hasNutrition={}",
            id, userId, body.foods, body.mealType, body.date, body.time, body.nutrition != null)
        return try {
            val result = mealService.update(userId, id, body)
            log.info("PUT /meals/{} → 200 | updated", id)
            ResponseEntity.ok(mapOf("meal" to result))
        } catch (e: Exception) {
            log.error("PUT /meals/{} → 500 | error={}", id, e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @DeleteMapping("/{id}")
    fun delete(@RequestHeader("X-User-Id") userId: String, @PathVariable id: String): ResponseEntity<Any> {
        log.info("DELETE /meals/{} | userId={}", id, userId)
        return try {
            mealService.delete(userId, id)
            log.info("DELETE /meals/{} → 200 | deleted", id)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            log.error("DELETE /meals/{} → 500 | error={}", id, e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/{id}/duplicate")
    fun duplicate(@RequestHeader("X-User-Id") userId: String, @PathVariable id: String): ResponseEntity<Any> {
        log.info("POST /meals/{}/duplicate | userId={}", id, userId)
        return try {
            val result = mealService.duplicate(userId, id)
            log.info("POST /meals/{}/duplicate → 200 | newId={}", id, result.id)
            ResponseEntity.ok(mapOf("meal" to result))
        } catch (e: Exception) {
            log.error("POST /meals/{}/duplicate → 500 | error={}", id, e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }
}
