package com.vidasync_bff.dto.request

import com.vidasync_bff.dto.response.NutritionData

data class UpdateMealRequest(
    val foods: String? = null,
    val mealType: String? = null,
    val date: String? = null,
    val time: String? = null,
    val nutrition: NutritionData? = null,
    val image: String? = null,
    val imageUrl: String? = null
)
