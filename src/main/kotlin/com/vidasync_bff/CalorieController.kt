package com.vidasync_bff

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class CalorieRequest(val foods: String)
data class CalorieResponse(val result: String)

@RestController
@RequestMapping("/nutrition")
class CalorieController(private val calorieService: CalorieService) {

    @PostMapping("/calories")
    fun calculateCalories(@RequestBody request: CalorieRequest): CalorieResponse {
        val result = calorieService.calculateCalories(request.foods)
        return CalorieResponse(result)
    }
}
