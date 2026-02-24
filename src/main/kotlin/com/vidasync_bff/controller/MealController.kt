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
        return try {
            ResponseEntity.ok(mapOf("meal" to mealService.create(request)))
        } catch (e: Exception) {
            log.error("Erro ao criar refeição: {}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @GetMapping
    fun getByDate(@RequestParam date: String): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(mapOf("meals" to mealService.getByDate(date)))
        } catch (e: Exception) {
            log.error("Erro ao buscar refeições: {}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/summary")
    fun getDaySummary(@RequestParam date: String): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(mealService.getDaySummary(date))
        } catch (e: Exception) {
            log.error("Erro ao gerar resumo do dia: {}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/range")
    fun getByDateRange(
        @RequestParam startDate: String,
        @RequestParam endDate: String
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(mapOf("meals" to mealService.getByDateRange(startDate, endDate)))
        } catch (e: Exception) {
            log.error("Erro ao buscar refeições por período: {}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: String, @RequestBody request: UpdateMealRequest): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(mapOf("meal" to mealService.update(id, request)))
        } catch (e: Exception) {
            log.error("Erro ao atualizar refeição: {}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Any> {
        return try {
            mealService.delete(id)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            log.error("Erro ao deletar refeição: {}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/{id}/duplicate")
    fun duplicate(@PathVariable id: String): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(mapOf("meal" to mealService.duplicate(id)))
        } catch (e: Exception) {
            log.error("Erro ao duplicar refeição: {}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }
}
