package com.vidasync_bff.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import kotlin.math.roundToInt

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupabaseWaterDailyRow(
    val id: String,
    @JsonProperty("user_id") val userId: String? = null,
    val date: String,
    @JsonProperty("goal_ml") val goalMl: Int?,
    @JsonProperty("consumed_ml") val consumedMl: Int?,
    @JsonProperty("created_at") val createdAt: String,
    @JsonProperty("updated_at") val updatedAt: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupabaseWaterEventRow(
    val id: String,
    @JsonProperty("user_id") val userId: String? = null,
    val date: String,
    @JsonProperty("delta_ml") val deltaMl: Int,
    @JsonProperty("event_type") val eventType: String? = null,
    @JsonProperty("created_at") val createdAt: String
)

data class WaterEventResponse(
    val id: String,
    val date: String,
    val deltaMl: Int,
    val action: String,
    val runningConsumedMl: Int
)

data class WaterDailyResponse(
    val id: String?,
    val date: String,
    val goalMl: Int?,
    val consumedMl: Int,
    val remainingMl: Int?,
    val progressPercent: Int?,
    val goalReached: Boolean,
    val goalInherited: Boolean,
    val createdAt: String?,
    val updatedAt: String?,
    val events: List<WaterEventResponse>
) {
    companion object {
        fun from(
            date: String,
            row: SupabaseWaterDailyRow?,
            goalMl: Int?,
            consumedMl: Int,
            goalInherited: Boolean,
            events: List<WaterEventResponse>
        ): WaterDailyResponse {
            val consumed = consumedMl.coerceAtLeast(0)
            val goal = goalMl
            val remaining = goal?.let { (it - consumed).coerceAtLeast(0) }
            val progress = if (goal != null && goal > 0) {
                ((consumed.toDouble() / goal.toDouble()) * 100.0).roundToInt()
            } else {
                null
            }

            return WaterDailyResponse(
                id = row?.id,
                date = date,
                goalMl = goal,
                consumedMl = consumed,
                remainingMl = remaining,
                progressPercent = progress,
                goalReached = goal != null && consumed >= goal,
                goalInherited = goalInherited,
                createdAt = row?.createdAt,
                updatedAt = row?.updatedAt,
                events = events
            )
        }
    }
}

data class WaterHistoryResponse(
    val startDate: String,
    val endDate: String,
    val days: List<WaterDailyResponse>
)
