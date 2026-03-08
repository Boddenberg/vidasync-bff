package com.vidasync_bff.dto.request

import com.fasterxml.jackson.annotation.JsonProperty

data class CalorieRequest(
    val foods: String,
    val image: String? = null,
    @JsonProperty("image_url")
    val imageUrl: String? = null,
    @JsonProperty("file_key")
    val fileKey: String? = null,
    @JsonProperty("image_key")
    val imageKey: String? = null,
    @JsonProperty("audio_key")
    val audioKey: String? = null,
    @JsonProperty("pdf_key")
    val pdfKey: String? = null
)
