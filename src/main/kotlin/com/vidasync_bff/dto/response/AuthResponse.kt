package com.vidasync_bff.dto.response

data class AuthResponse(
    val userId: String,
    val username: String,
    val profileImageUrl: String? = null
)
