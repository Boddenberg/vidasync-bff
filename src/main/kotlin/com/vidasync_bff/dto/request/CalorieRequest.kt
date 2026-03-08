package com.vidasync_bff.dto.request

import com.fasterxml.jackson.annotation.JsonAlias

data class CalorieRequest(
    val foods: String? = null,
    val image: String? = null,
    @JsonAlias("image_url", "imageUrl")
    val imageUrl: String? = null,
    @JsonAlias("file_key", "fileKey", "key")
    val fileKey: String? = null,
    @JsonAlias("image_key", "imageKey")
    val imageKey: String? = null,
    @JsonAlias("audio_key", "audioKey")
    val audioKey: String? = null,
    @JsonAlias("pdf_key", "pdfKey")
    val pdfKey: String? = null
)
