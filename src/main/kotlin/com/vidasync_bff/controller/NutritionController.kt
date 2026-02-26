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
        log.info("POST /nutrition/calories | foods={}", request.foods)
        return try {
            val result = nutritionService.calculateNutritionSmart(request.foods)

            // Algum item inválido → 400
            if (result.nutrition == null && !result.invalidItems.isNullOrEmpty()) {
                val msg = when (result.invalidItems.size) {
                    1 -> "\"${result.invalidItems.first()}\" não é um alimento válido. Corrija e tente novamente."
                    else -> "Não foi possível calcular. Revise os ingredientes: ${result.invalidItems.joinToString(", ") { "\"$it\"" }}."
                }
                log.warn("POST /nutrition/calories → 400 | invalidItems={}", result.invalidItems)
                return ResponseEntity.badRequest().body(CalorieResponse(error = msg, invalidItems = result.invalidItems))
            }

            // Erro genérico (sem nutrition e sem invalidItems)
            if (result.nutrition == null && result.error != null) {
                log.warn("POST /nutrition/calories → 400 | error={}", result.error)
                return ResponseEntity.badRequest().body(result)
            }

            log.info("POST /nutrition/calories → 200 | calories={}, ingredients={}, corrections={}, invalidItems={}",
                result.nutrition?.calories, result.ingredients?.size, result.corrections?.size, result.invalidItems?.size)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            log.error("POST /nutrition/calories → 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(CalorieResponse(error = e.message))
        }
    }
}
