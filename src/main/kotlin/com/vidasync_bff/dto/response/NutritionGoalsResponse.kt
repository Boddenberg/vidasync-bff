package com.vidasync_bff.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

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

data class DailyNutritionGoalsResponse(
    val id: String?,
    val date: String,
    val goals: NutritionGoalTargets,
    val goalInherited: Boolean,
    val createdAt: String?,
    val updatedAt: String?
) {
    companion object {
        fun from(
            date: String,
            row: SupabaseNutritionGoalsRow?,
            goals: NutritionGoalTargets,
            goalInherited: Boolean
        ): DailyNutritionGoalsResponse {
            return DailyNutritionGoalsResponse(
                id = row?.id,
                date = date,
                goals = goals,
                goalInherited = goalInherited,
                createdAt = row?.createdAt,
                updatedAt = row?.updatedAt
            )
        }
    }
}
