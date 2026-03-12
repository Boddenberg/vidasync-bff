package com.vidasync_bff.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import kotlin.math.roundToInt

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupabaseWaterDailyRow(
    val id: String,
    val date: String,
    @JsonProperty("goal_ml") val goalMl: Int?,
    @JsonProperty("consumed_ml") val consumedMl: Int?,
    @JsonProperty("created_at") val createdAt: String,
    @JsonProperty("updated_at") val updatedAt: String
)

data class WaterDailyResponse(
    val id: String,
    val date: String,
    val goalMl: Int?,
    val consumedMl: Int,
    val remainingMl: Int?,
    val progressPercent: Int?,
    val goalReached: Boolean,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(row: SupabaseWaterDailyRow): WaterDailyResponse {
            val consumed = row.consumedMl ?: 0
            val goal = row.goalMl
            val remaining = goal?.let { (it - consumed).coerceAtLeast(0) }
            val progress = if (goal != null && goal > 0) {
                ((consumed.toDouble() / goal.toDouble()) * 100.0).roundToInt()
            } else {
                null
            }

            return WaterDailyResponse(
                id = row.id,
                date = row.date,
                goalMl = goal,
                consumedMl = consumed,
                remainingMl = remaining,
                progressPercent = progress,
                goalReached = goal != null && consumed >= goal,
                createdAt = row.createdAt,
                updatedAt = row.updatedAt
            )
        }
    }
}
