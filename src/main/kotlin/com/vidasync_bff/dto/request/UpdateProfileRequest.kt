package com.vidasync_bff.dto.request

data class UpdateProfileRequest(
    val username: String? = null,
    val password: String? = null,
    val profileImage: String? = null
)
