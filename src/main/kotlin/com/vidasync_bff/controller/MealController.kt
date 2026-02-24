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
    fun create(@RequestBody request: CreateMealRequest): ResponseEntity<Any> {
        log.info("POST /meals | foods={}, mealType={}, date={}, time={}, hasNutrition={}",
            request.foods, request.mealType, request.date, request.time, request.nutrition != null)
        return try {
            val result = mealService.create(request)
            log.info("POST /meals → 200 | id={}", result.id)
            ResponseEntity.ok(mapOf("meal" to result))
        } catch (e: Exception) {
            log.error("POST /meals → 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @GetMapping
    fun getByDate(@RequestParam date: String): ResponseEntity<Any> {
        log.info("GET /meals | date={}", date)
        return try {
            val result = mealService.getByDate(date)
            log.info("GET /meals → 200 | date={}, count={}", date, result.size)
            ResponseEntity.ok(mapOf("meals" to result))
        } catch (e: Exception) {
            log.error("GET /meals → 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/summary")
    fun getDaySummary(@RequestParam date: String): ResponseEntity<Any> {
        log.info("GET /meals/summary | date={}", date)
        return try {
            val result = mealService.getDaySummary(date)
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
        @RequestParam startDate: String,
        @RequestParam endDate: String
    ): ResponseEntity<Any> {
        log.info("GET /meals/range | startDate={}, endDate={}", startDate, endDate)
        return try {
            val result = mealService.getByDateRange(startDate, endDate)
            log.info("GET /meals/range → 200 | period={} to {}, count={}", startDate, endDate, result.size)
            ResponseEntity.ok(mapOf("meals" to result))
        } catch (e: Exception) {
            log.error("GET /meals/range → 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: String, @RequestBody request: UpdateMealRequest): ResponseEntity<Any> {
        log.info("PUT /meals/{} | foods={}, mealType={}, date={}, time={}, hasNutrition={}",
            id, request.foods, request.mealType, request.date, request.time, request.nutrition != null)
        return try {
            val result = mealService.update(id, request)
            log.info("PUT /meals/{} → 200 | updated", id)
            ResponseEntity.ok(mapOf("meal" to result))
        } catch (e: Exception) {
            log.error("PUT /meals/{} → 500 | error={}", id, e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Any> {
        log.info("DELETE /meals/{}", id)
        return try {
            mealService.delete(id)
            log.info("DELETE /meals/{} → 200 | deleted", id)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            log.error("DELETE /meals/{} → 500 | error={}", id, e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/{id}/duplicate")
    fun duplicate(@PathVariable id: String): ResponseEntity<Any> {
        log.info("POST /meals/{}/duplicate", id)
        return try {
            val result = mealService.duplicate(id)
            log.info("POST /meals/{}/duplicate → 200 | newId={}", id, result.id)
            ResponseEntity.ok(mapOf("meal" to result))
        } catch (e: Exception) {
            log.error("POST /meals/{}/duplicate → 500 | error={}", id, e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }
}
