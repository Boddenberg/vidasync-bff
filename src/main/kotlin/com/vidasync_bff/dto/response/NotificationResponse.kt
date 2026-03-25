package com.vidasync_bff.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupabaseNotificationRow(
    val id: String,
    @JsonProperty("user_id") val userId: String,
    val title: String,
    val message: String,
    val type: String = "INFO",
    @JsonProperty("image_url") val imageUrl: String? = null,
    @JsonProperty("action_label") val actionLabel: String? = null,
    @JsonProperty("action_route") val actionRoute: String? = null,
    @JsonProperty("read_at") val readAt: String? = null,
    @JsonProperty("is_deleted") val isDeleted: Boolean = false,
    @JsonProperty("deleted_at") val deletedAt: String? = null,
    @JsonProperty("created_at") val createdAt: String,
    @JsonProperty("updated_at") val updatedAt: String? = null
)

data class NotificationItemResponse(
    val id: String,
    val title: String,
    val message: String,
    val type: String,
    val imageUrl: String?,
    val actionLabel: String?,
    val actionRoute: String?,
    val readAt: String?,
    val deleted: Boolean,
    val deletedAt: String?,
    val createdAt: String,
    val date: String,
    val time: String
) {
    companion object {
        fun from(row: SupabaseNotificationRow): NotificationItemResponse {
            val createdAt = OffsetDateTime.parse(row.createdAt)

            return NotificationItemResponse(
                id = row.id,
                title = row.title,
                message = row.message,
                type = row.type,
                imageUrl = row.imageUrl,
                actionLabel = row.actionLabel,
                actionRoute = row.actionRoute,
                readAt = row.readAt,
                deleted = row.isDeleted,
                deletedAt = row.deletedAt,
                createdAt = row.createdAt,
                date = createdAt.toLocalDate().toString(),
                time = createdAt.toLocalTime().withNano(0).toString()
            )
        }
    }
}

data class NotificationsInboxResponse(
    val unreadCount: Int,
    val notifications: List<NotificationItemResponse>
)

data class NotificationStatusResponse(
    val id: String,
    val readAt: String?,
    val deleted: Boolean,
    val deletedAt: String?
) {
    companion object {
        fun from(row: SupabaseNotificationRow): NotificationStatusResponse {
            return NotificationStatusResponse(
                id = row.id,
                readAt = row.readAt,
                deleted = row.isDeleted,
                deletedAt = row.deletedAt
            )
        }
    }
}

data class NotificationMutationResponse(
    val unreadCount: Int,
    val notifications: List<NotificationStatusResponse>
)
