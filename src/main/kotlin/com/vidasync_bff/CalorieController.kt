package com.vidasync_bff

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class CalorieRequest(val foods: String)
data class CalorieResponse(val result: String? = null, val error: String? = null)

@RestController
@RequestMapping("/nutrition")
class CalorieController(private val calorieService: CalorieService) {

    private val log = LoggerFactory.getLogger(CalorieController::class.java)

    @PostMapping("/calories")
    fun calculateCalories(@RequestBody request: CalorieRequest): ResponseEntity<CalorieResponse> {
        log.info("POST /nutrition/calories - foods: {}", request.foods)
        return try {
            val result = calorieService.calculateCalories(request.foods)
            log.info("Resposta OpenAI recebida com sucesso")
            ResponseEntity.ok(CalorieResponse(result = result))
        } catch (e: Exception) {
            log.error("Erro ao calcular calorias: {}", e.message, e)
            ResponseEntity.internalServerError().body(CalorieResponse(error = e.message))
        }
    }
}
