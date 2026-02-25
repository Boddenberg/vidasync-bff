package com.vidasync_bff.dto.request

data class AuthRequest(
    val username: String,
    val password: String,
    val profileImage: String? = null
)
