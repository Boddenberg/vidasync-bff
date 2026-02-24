package com.vidasync_bff.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupabaseFavoriteRow(
    val id: String,
    val foods: String,
    val calories: String?,
    val protein: String?,
    val carbs: String?,
    val fat: String?,
    @JsonProperty("image_url") val imageUrl: String?,
    @JsonProperty("created_at") val createdAt: String
)

data class FavoriteResponse(
    val id: String,
    val foods: String,
    val nutrition: NutritionData,
    val imageUrl: String? = null
) {
    companion object {
        fun from(row: SupabaseFavoriteRow) = FavoriteResponse(
            id = row.id,
            foods = row.foods,
            nutrition = NutritionData(
                calories = row.calories ?: "0 kcal",
                protein = row.protein ?: "0g",
                carbs = row.carbs ?: "0g",
                fat = row.fat ?: "0g"
            ),
            imageUrl = row.imageUrl
        )
    }
}

