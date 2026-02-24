package com.vidasync_bff.dto.response

data class NutritionData(
    val calories: String,
    val protein: String,
    val carbs: String,
    val fat: String
)

data class CalorieResponse(
    val nutrition: NutritionData? = null,
    val error: String? = null
)
