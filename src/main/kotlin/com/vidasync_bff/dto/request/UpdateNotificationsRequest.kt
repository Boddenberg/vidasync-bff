package com.vidasync_bff.dto.request

data class UpdateNotificationsRequest(
    val notificationIds: List<String>? = null,
    val markAll: Boolean? = null
)
