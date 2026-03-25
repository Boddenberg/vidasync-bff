package com.vidasync_bff.dto.request

data class PublishNotificationToUserRequest(
    val userId: String,
    val title: String,
    val message: String,
    val type: String? = null,
    val imageUrl: String? = null,
    val actionLabel: String? = null,
    val actionRoute: String? = null
)

data class PublishNotificationBroadcastRequest(
    val title: String,
    val message: String,
    val type: String? = null,
    val imageUrl: String? = null,
    val actionLabel: String? = null,
    val actionRoute: String? = null
)
