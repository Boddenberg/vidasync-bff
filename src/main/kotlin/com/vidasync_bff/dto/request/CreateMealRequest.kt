package com.vidasync_bff.dto.request

import com.vidasync_bff.dto.response.NutritionData

data class CreateMealRequest(
    val foods: String,
    val mealType: String,
    val date: String,
    val time: String? = null,
    val nutrition: NutritionData? = null,
    val image: String? = null,
    val imageUrl: String? = null
)
