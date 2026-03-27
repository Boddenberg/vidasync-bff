package com.vidasync_bff.controller

import com.vidasync_bff.dto.response.LlmJudgeMetricsBucketResponse
import com.vidasync_bff.dto.response.LlmJudgeMetricsCountResponse
import com.vidasync_bff.dto.response.LlmJudgeCriterionScoreResponse
import com.vidasync_bff.dto.response.LlmJudgeMetricsDailyPointResponse
import com.vidasync_bff.dto.response.LlmJudgeMetricsFiltersResponse
import com.vidasync_bff.dto.response.LlmJudgeMetricsResponse
import com.vidasync_bff.dto.response.LlmJudgeMetricsSummaryResponse
import com.vidasync_bff.dto.response.LlmJudgeRecentEvaluationResponse
import com.vidasync_bff.service.LlmJudgeMetricsService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException

class InternalAdminLlmJudgeMetricsControllerTests {

    private val llmJudgeMetricsService = mock(LlmJudgeMetricsService::class.java)
    private val mockMvc = MockMvcBuilders
        .standaloneSetup(InternalAdminLlmJudgeMetricsController(llmJudgeMetricsService))
        .build()

    @Test
    fun `deve retornar 200 com metricas do llm judge`() {
        val response = LlmJudgeMetricsResponse(
            filters = LlmJudgeMetricsFiltersResponse(
                startDate = "2026-03-20",
                endDate = "2026-03-26",
                days = 7,
                feature = "nutrition",
                pipeline = null,
                handler = null,
                idioma = null,
                sourceModel = null,
                judgeStatus = null,
                judgeDecision = null
            ),
            summary = LlmJudgeMetricsSummaryResponse(
                totalEvaluations = 12,
                completedCount = 10,
                pendingCount = 1,
                failedCount = 1,
                approvedCount = 8,
                rejectedCount = 2,
                completionRatePercent = 83.33,
                failureRatePercent = 8.33,
                approvalRatePercent = 80.0,
                averageOverallScore = 0.91,
                averageCriteriaScores = listOf(
                    LlmJudgeCriterionScoreResponse(
                        key = "quality",
                        score = 4.8
                    )
                ),
                averageSourceDurationMs = 1320.4,
                averageJudgeDurationMs = 210.5,
                averageSourceTotalTokens = 512.0,
                averageJudgeTotalTokens = 140.0,
                latestEvaluationAt = "2026-03-26T18:00:00Z",
                oldestEvaluationAt = "2026-03-20T09:00:00Z"
            ),
            byFeature = listOf(
                LlmJudgeMetricsBucketResponse(
                    key = "nutrition",
                    totalEvaluations = 12,
                    completedCount = 10,
                    pendingCount = 1,
                    failedCount = 1,
                    approvedCount = 8,
                    rejectedCount = 2,
                    completionRatePercent = 83.33,
                    failureRatePercent = 8.33,
                    approvalRatePercent = 80.0,
                    averageOverallScore = 0.91,
                    averageCriteriaScores = emptyList(),
                    averageSourceDurationMs = 1320.4,
                    averageJudgeDurationMs = 210.5,
                    averageSourceTotalTokens = 512.0,
                    averageJudgeTotalTokens = 140.0
                )
            ),
            byPipeline = emptyList(),
            byHandler = emptyList(),
            byIdioma = emptyList(),
            bySourceModel = emptyList(),
            daily = listOf(
                LlmJudgeMetricsDailyPointResponse(
                    date = "2026-03-26",
                    totalEvaluations = 3,
                    completedCount = 2,
                    pendingCount = 1,
                    failedCount = 0,
                    approvedCount = 2,
                    rejectedCount = 0,
                    completionRatePercent = 66.67,
                    failureRatePercent = 0.0,
                    approvalRatePercent = 100.0,
                    averageOverallScore = 0.97
                )
            ),
            topRejectionReasons = listOf(
                LlmJudgeMetricsCountResponse(
                    key = "falta contexto",
                    count = 2
                )
            ),
            recentEvaluations = listOf(
                LlmJudgeRecentEvaluationResponse(
                    evaluationId = "eval-1",
                    createdAt = "2026-03-26T18:00:00Z",
                    conversationId = "conv-eval-1",
                    userId = "user-eval-1",
                    requestId = "req-eval-1",
                    messageId = "msg-eval-1",
                    feature = "nutrition",
                    judgeStatus = "completed",
                    judgeDecision = "approved",
                    judgeOverallScore = 0.98,
                    judgeSummary = "Resposta boa.",
                    idioma = "pt-BR",
                    pipeline = "image",
                    handler = "calories",
                    sourceModel = "gpt-4.1-mini",
                    sourceDurationMs = 1200.0,
                    judgeDurationMs = 180.0,
                    sourceTotalTokens = 480,
                    judgeTotalTokens = 120,
                    criteria = emptyList(),
                    judgeImprovements = emptyList(),
                    judgeRejectionReasons = emptyList()
                )
            )
        )

        `when`(
            llmJudgeMetricsService.getMetrics(
                "admin-1",
                7,
                null,
                null,
                "nutrition",
                null,
                null,
                null,
                null,
                null,
                null
            )
        ).thenReturn(response)

        mockMvc.get("/internal/admin/llm-judge/metrics") {
            header("X-User-Id", "admin-1")
            param("days", "7")
            param("feature", "nutrition")
        }.andExpect {
            status { isOk() }
            jsonPath("$.metrics.summary.totalEvaluations") { value(12) }
            jsonPath("$.metrics.summary.approvalRatePercent") { value(80.0) }
            jsonPath("$.metrics.byFeature[0].key") { value("nutrition") }
            jsonPath("$.metrics.topRejectionReasons[0].key") { value("falta contexto") }
            jsonPath("$.metrics.recentEvaluations[0].evaluationId") { value("eval-1") }
            jsonPath("$.metrics.recentEvaluations[0].conversationId") { value("conv-eval-1") }
            jsonPath("$.metrics.recentEvaluations[0].requestId") { value("req-eval-1") }
        }
    }

    @Test
    fun `deve retornar status da response status exception`() {
        `when`(
            llmJudgeMetricsService.getMetrics(
                " ",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            )
        ).thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST, "header X-User-Id obrigatorio para auditoria"))

        mockMvc.get("/internal/admin/llm-judge/metrics") {
            header("X-User-Id", " ")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("header X-User-Id obrigatorio para auditoria") }
        }
    }
}
