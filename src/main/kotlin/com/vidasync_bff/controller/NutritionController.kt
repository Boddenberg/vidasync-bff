package com.vidasync_bff.controller

import com.vidasync_bff.dto.request.CalorieRequest
import com.vidasync_bff.dto.response.CalorieResponse
import com.vidasync_bff.service.NutritionService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/nutrition")
class NutritionController(private val nutritionService: NutritionService) {

    private val log = LoggerFactory.getLogger(NutritionController::class.java)

    @PostMapping("/calories")
    fun calculateCalories(@RequestBody request: CalorieRequest): ResponseEntity<CalorieResponse> {
        log.info("POST /nutrition/calories - foods: {}", request.foods)
        return try {
            val result = nutritionService.calculateCalories(request.foods)
            ResponseEntity.ok(CalorieResponse(result = result))
        } catch (e: Exception) {
            log.error("Erro ao calcular calorias: {}", e.message, e)
            ResponseEntity.internalServerError().body(CalorieResponse(error = e.message))
        }
    }
}
