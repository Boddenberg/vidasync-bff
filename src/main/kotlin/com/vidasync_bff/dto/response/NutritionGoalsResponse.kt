package com.vidasync_bff.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import kotlin.math.roundToInt

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupabaseNutritionGoalsRow(
    val id: String,
    val date: String,
    @JsonProperty("calories_goal") val caloriesGoal: Int?,
    @JsonProperty("protein_goal") val proteinGoal: Int?,
    @JsonProperty("carbs_goal") val carbsGoal: Int?,
    @JsonProperty("fat_goal") val fatGoal: Int?,
    @JsonProperty("created_at") val createdAt: String,
    @JsonProperty("updated_at") val updatedAt: String
)

data class NutritionGoalTargets(
    val calories: Int?,
    val protein: Int?,
    val carbs: Int?,
    val fat: Int?
)

data class NutritionGoalValues(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double
)

data class NutritionGoalNullableValues(
    val calories: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?
)

data class NutritionGoalProgressPercent(
    val calories: Int?,
    val protein: Int?,
    val carbs: Int?,
    val fat: Int?
)

data class NutritionGoalReached(
    val calories: Boolean,
    val protein: Boolean,
    val carbs: Boolean,
    val fat: Boolean
)

data class DailyNutritionGoalsResponse(
    val id: String,
    val date: String,
    val goals: NutritionGoalTargets,
    val consumed: NutritionGoalValues,
    val remaining: NutritionGoalNullableValues,
    val progressPercent: NutritionGoalProgressPercent,
    val goalReached: NutritionGoalReached,
    val allGoalsReached: Boolean,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(
            row: SupabaseNutritionGoalsRow,
            consumedCalories: Double,
            consumedProtein: Double,
            consumedCarbs: Double,
            consumedFat: Double
        ): DailyNutritionGoalsResponse {
            val caloriesReached = row.caloriesGoal?.let { consumedCalories >= it } ?: false
            val proteinReached = row.proteinGoal?.let { consumedProtein >= it } ?: false
            val carbsReached = row.carbsGoal?.let { consumedCarbs >= it } ?: false
            val fatReached = row.fatGoal?.let { consumedFat >= it } ?: false

            val hasAnyGoal = row.caloriesGoal != null || row.proteinGoal != null || row.carbsGoal != null || row.fatGoal != null
            val allReached = hasAnyGoal && listOfNotNull(
                row.caloriesGoal?.let { caloriesReached },
                row.proteinGoal?.let { proteinReached },
                row.carbsGoal?.let { carbsReached },
                row.fatGoal?.let { fatReached }
            ).all { it }

            return DailyNutritionGoalsResponse(
                id = row.id,
                date = row.date,
                goals = NutritionGoalTargets(
                    calories = row.caloriesGoal,
                    protein = row.proteinGoal,
                    carbs = row.carbsGoal,
                    fat = row.fatGoal
                ),
                consumed = NutritionGoalValues(
                    calories = consumedCalories,
                    protein = consumedProtein,
                    carbs = consumedCarbs,
                    fat = consumedFat
                ),
                remaining = NutritionGoalNullableValues(
                    calories = row.caloriesGoal?.let { (it - consumedCalories).coerceAtLeast(0.0) },
                    protein = row.proteinGoal?.let { (it - consumedProtein).coerceAtLeast(0.0) },
                    carbs = row.carbsGoal?.let { (it - consumedCarbs).coerceAtLeast(0.0) },
                    fat = row.fatGoal?.let { (it - consumedFat).coerceAtLeast(0.0) }
                ),
                progressPercent = NutritionGoalProgressPercent(
                    calories = percentage(consumedCalories, row.caloriesGoal),
                    protein = percentage(consumedProtein, row.proteinGoal),
                    carbs = percentage(consumedCarbs, row.carbsGoal),
                    fat = percentage(consumedFat, row.fatGoal)
                ),
                goalReached = NutritionGoalReached(
                    calories = caloriesReached,
                    protein = proteinReached,
                    carbs = carbsReached,
                    fat = fatReached
                ),
                allGoalsReached = allReached,
                createdAt = row.createdAt,
                updatedAt = row.updatedAt
            )
        }

        private fun percentage(consumed: Double, goal: Int?): Int? {
            if (goal == null || goal <= 0) return null
            return ((consumed / goal.toDouble()) * 100.0).roundToInt()
        }
    }
}
