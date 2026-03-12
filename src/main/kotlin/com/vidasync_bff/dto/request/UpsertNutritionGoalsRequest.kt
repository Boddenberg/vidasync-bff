package com.vidasync_bff.dto.request

data class UpsertNutritionGoalsRequest(
    val date: String? = null,
    val caloriesGoal: Int? = null,
    val proteinGoal: Int? = null,
    val carbsGoal: Int? = null,
    val fatGoal: Int? = null
)
