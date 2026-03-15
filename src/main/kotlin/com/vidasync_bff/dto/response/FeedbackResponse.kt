package com.vidasync_bff.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupabaseFeedbackRow(
    val id: String,
    @JsonProperty("user_id") val userId: String,
    @JsonProperty("user_name") val userName: String,
    val message: String,
    @JsonProperty("image_url") val imageUrl: String? = null,
    val status: String,
    @JsonProperty("developer_response") val developerResponse: String? = null,
    @JsonProperty("responded_at") val respondedAt: String? = null,
    @JsonProperty("responded_by") val respondedBy: String? = null,
    @JsonProperty("response_seen_at") val responseSeenAt: String? = null,
    @JsonProperty("created_at") val createdAt: String,
    @JsonProperty("updated_at") val updatedAt: String
)

data class FeedbackEntryResponse(
    val id: String,
    val userId: String,
    val userName: String,
    val message: String,
    val imageUrl: String?,
    val status: String,
    val developerResponse: String?,
    val respondedAt: String?,
    val respondedBy: String?,
    val responseSeenAt: String?,
    val createdAt: String,
    val updatedAt: String,
    val date: String,
    val time: String
) {
    companion object {
        fun from(row: SupabaseFeedbackRow): FeedbackEntryResponse {
            val createdAt = OffsetDateTime.parse(row.createdAt)

            return FeedbackEntryResponse(
                id = row.id,
                userId = row.userId,
                userName = row.userName,
                message = row.message,
                imageUrl = row.imageUrl,
                status = row.status,
                developerResponse = row.developerResponse,
                respondedAt = row.respondedAt,
                respondedBy = row.respondedBy,
                responseSeenAt = row.responseSeenAt,
                createdAt = row.createdAt,
                updatedAt = row.updatedAt,
                date = createdAt.toLocalDate().toString(),
                time = createdAt.toLocalTime().withNano(0).toString()
            )
        }
    }
}
