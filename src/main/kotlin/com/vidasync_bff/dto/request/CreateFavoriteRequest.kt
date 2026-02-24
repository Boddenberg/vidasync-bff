package com.vidasync_bff.dto.request

import com.vidasync_bff.dto.response.NutritionData

data class CreateFavoriteRequest(
    val foods: String,
    val nutrition: NutritionData? = null,
    val image: String? = null
)
