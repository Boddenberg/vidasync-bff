package com.vidasync_bff.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupabaseAuthResponse(
    @JsonProperty("access_token") val accessToken: String?,
    val user: SupabaseUser?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupabaseUser(
    val id: String?,
    val email: String?,
    @JsonProperty("created_at") val createdAt: String?
)

data class AuthResponse(
    val userId: String,
    val username: String,
    val profileImageUrl: String? = null,
    val accessToken: String? = null
)
