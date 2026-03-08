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
        log.info(
            "POST /nutrition/calories | foods={}, hasImage={}, hasImageUrl={}, hasFileKey={}, hasImageKey={}, hasAudioKey={}, hasPdfKey={}",
            request.foods ?: "",
            !request.image.isNullOrBlank(),
            !request.imageUrl.isNullOrBlank(),
            !request.fileKey.isNullOrBlank(),
            !request.imageKey.isNullOrBlank(),
            !request.audioKey.isNullOrBlank(),
            !request.pdfKey.isNullOrBlank()
        )
        return try {
            val result = nutritionService.calculateNutritionSmart(request)

            // Any invalid item should return 400 with a user-friendly message.
            if (result.nutrition == null && !result.invalidItems.isNullOrEmpty()) {
                val msg = when (result.invalidItems.size) {
                    1 -> "\"${result.invalidItems.first()}\" nao e um alimento valido. Corrija e tente novamente."
                    else -> "Nao foi possivel calcular. Revise os ingredientes: ${
                        result.invalidItems.joinToString(", ") { "\"$it\"" }
                    }."
                }
                log.warn(
                    "POST /nutrition/calories -> 400 | invalidItems={} warnings={} trace_id={}",
                    result.invalidItems,
                    result.warnings?.size ?: 0,
                    result.traceId
                )
                return ResponseEntity.badRequest().body(
                    CalorieResponse(
                        error = msg,
                        invalidItems = result.invalidItems,
                        precisaRevisao = true,
                        warnings = result.warnings,
                        traceId = result.traceId
                    )
                )
            }

            // Generic invalid request path.
            if (result.nutrition == null && result.error != null) {
                log.warn(
                    "POST /nutrition/calories -> 400 | error={} warnings={} trace_id={}",
                    result.error,
                    result.warnings?.size ?: 0,
                    result.traceId
                )
                return ResponseEntity.badRequest().body(result)
            }

            log.info(
                "POST /nutrition/calories -> 200 | calories={} ingredients={} corrections={} invalidItems={} precisa_revisao={} warnings={} trace_id={}",
                result.nutrition?.calories,
                result.ingredients?.size,
                result.corrections?.size,
                result.invalidItems?.size,
                result.precisaRevisao,
                result.warnings?.size ?: 0,
                result.traceId
            )
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            log.error("POST /nutrition/calories -> 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(CalorieResponse(error = e.message))
        }
    }
}
