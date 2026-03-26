package com.vidasync_bff.dto.response

data class ChatMemoryResponse(
    val totalTurns: Int? = null,
    val shortTermTurns: Int? = null,
    val summarizedTurns: Int? = null,
    val hasSummary: Boolean? = null,
    val updatedAt: String? = null
)

data class ChatResponse(
    val response: String,
    val model: String? = null,
    val conversationId: String? = null,
    val intent: String? = null,
    val confidence: Double? = null,
    val needsReview: Boolean = false,
    val warnings: List<String>? = null,
    val memory: ChatMemoryResponse? = null,
    val disclaimer: String,
    val traceId: String? = null
)
