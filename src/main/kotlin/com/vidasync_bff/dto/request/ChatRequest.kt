package com.vidasync_bff.dto.request

data class ChatRequest(
    val prompt: String? = null,
    val conversationId: String? = null
)
