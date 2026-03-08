package com.vidasync_bff.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class UploadPresignResponse(
    @JsonProperty("uploadUrl")
    val uploadUrl: String,
    @JsonProperty("fileKey")
    val fileKey: String,
    @JsonProperty("expiresIn")
    val expiresIn: Int
)
