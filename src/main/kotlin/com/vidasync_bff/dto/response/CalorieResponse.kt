package com.vidasync_bff.dto.response

data class NutritionData(
    val calories: String,
    val protein: String,
    val carbs: String,
    val fat: String
)

data class UnitCorrection(
    val original: String,
    val corrected: String
)

data class IngredientDetail(
    val name: String,
    val nutrition: NutritionData,
    val cached: Boolean = false
)

data class CalorieResponse(
    val nutrition: NutritionData? = null,
    val ingredients: List<IngredientDetail>? = null,
    val corrections: List<UnitCorrection>? = null,
    val invalidItems: List<String>? = null,
    val error: String? = null
)
