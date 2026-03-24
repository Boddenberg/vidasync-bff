package com.vidasync_bff.dto.request

data class UpdatePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)
