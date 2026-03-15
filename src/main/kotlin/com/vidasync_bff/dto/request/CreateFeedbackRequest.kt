package com.vidasync_bff.dto.request

data class CreateFeedbackRequest(
    val userName: String,
    val message: String,
    val imageUrl: String? = null
)
