package com.vidasync_bff.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupabaseMealRow(
    val id: String,
    @JsonProperty("meal_type") val mealType: String,
    val foods: String,
    val date: String,
    val time: String?,
    val calories: String?,
    val protein: String?,
    val carbs: String?,
    val fat: String?,
    @JsonProperty("created_at") val createdAt: String
)

data class MealResponse(
    val id: String,
    val foods: String,
    val mealType: String,
    val date: String,
    val time: String?,
    val nutrition: NutritionData,
    val createdAt: String
) {
    companion object {
        fun from(row: SupabaseMealRow) = MealResponse(
            id = row.id,
            foods = row.foods,
            mealType = row.mealType,
            date = row.date,
            time = row.time,
            nutrition = NutritionData(
                calories = row.calories ?: "0 kcal",
                protein = row.protein ?: "0g",
                carbs = row.carbs ?: "0g",
                fat = row.fat ?: "0g"
            ),
            createdAt = row.createdAt
        )
    }
}

data class DaySummaryResponse(
    val date: String,
    val meals: List<MealResponse>,
    val totalMeals: Int,
    val totals: NutritionData
)

