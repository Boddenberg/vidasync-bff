package com.vidasync_bff.dto.response

data class ChatMemoryResponse(
    val totalTurns: Int? = null,
    val shortTermTurns: Int? = null,
    val summarizedTurns: Int? = null,
    val hasSummary: Boolean? = null,
    val updatedAt: String? = null
)

data class ChatJudgeReferenceResponse(
    val evaluationId: String,
    val status: String? = null
)

data class ChatJudgeCriterionResponse(
    val key: String,
    val score: Double? = null,
    val reason: String? = null,
    val approved: Boolean? = null
)

data class ChatJudgeEvaluationResponse(
    val evaluationId: String,
    val status: String? = null,
    val overallScore: Double? = null,
    val approved: Boolean? = null,
    val decision: String? = null,
    val criterionScores: Map<String, Double?> = emptyMap(),
    val criterionReasons: Map<String, String> = emptyMap(),
    val criteria: List<ChatJudgeCriterionResponse> = emptyList(),
    val score: Map<String, Any?> = emptyMap(),
    val approval: Map<String, Any?> = emptyMap()
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
    val judge: ChatJudgeReferenceResponse? = null,
    val disclaimer: String,
    val traceId: String? = null
)
