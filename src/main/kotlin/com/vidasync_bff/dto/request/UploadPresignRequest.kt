package com.vidasync_bff.dto.request

data class UploadPresignRequest(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val kind: String
)
