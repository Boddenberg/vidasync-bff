package com.vidasync_bff.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupabaseWeightRow(
    val id: String,
    @JsonProperty("user_id") val userId: String? = null,
    @JsonProperty("weight_kg") val weightKg: Double,
    @JsonProperty("measured_at") val measuredAt: String,
    @JsonProperty("created_at") val createdAt: String? = null
)

data class WeightEntryResponse(
    val id: String,
    val weightKg: Double,
    val measuredAt: String,
    val date: String,
    val time: String
) {
    companion object {
        fun from(row: SupabaseWeightRow): WeightEntryResponse {
            val measuredAt = OffsetDateTime.parse(row.measuredAt)

            return WeightEntryResponse(
                id = row.id,
                weightKg = row.weightKg,
                measuredAt = row.measuredAt,
                date = measuredAt.toLocalDate().toString(),
                time = measuredAt.toLocalTime().withNano(0).toString()
            )
        }
    }
}
