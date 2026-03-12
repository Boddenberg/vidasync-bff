package com.vidasync_bff.dto.request

data class UpsertWaterRequest(
    val date: String? = null,
    val goalMl: Int? = null,
    val deltaMl: Int? = null
)
