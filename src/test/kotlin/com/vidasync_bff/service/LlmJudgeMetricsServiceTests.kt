package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.response.SupabaseLlmJudgeEvaluationRow
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LlmJudgeMetricsServiceTests {

    private val supabaseClient = mock(SupabaseClient::class.java)
    private val service = LlmJudgeMetricsService(supabaseClient = supabaseClient)

    @Test
    fun `deve agregar metricas do llm judge por periodo`() {
        `when`(
            supabaseClient.get(
                eqValue("llm_judge_evaluations"),
                eqValue(
                    "evaluation_id,created_at,feature,judge_status,idioma,pipeline,handler,source_model,source_duration_ms,source_total_tokens,judge_duration_ms,judge_total_tokens,judge_overall_score,judge_decision,judge_rejection_reasons"
                ),
                eqValue(
                    mapOf(
                        "and" to "(created_at.gte.2026-03-24T00:00Z,created_at.lt.2026-03-27T00:00Z)",
                        "order" to "created_at.desc,evaluation_id.desc",
                        "feature" to "eq.nutrition"
                    )
                ),
                anyEvaluationTypeRef()
            )
        ).thenReturn(
            listOf(
                evaluationRow(
                    evaluationId = "eval-3",
                    createdAt = "2026-03-26T15:30:00Z",
                    feature = "nutrition",
                    judgeStatus = "completed",
                    judgeDecision = "approved",
                    judgeOverallScore = 0.9,
                    sourceDurationMs = 1500.0,
                    judgeDurationMs = 300.0,
                    sourceTotalTokens = 600,
                    judgeTotalTokens = 150
                ),
                evaluationRow(
                    evaluationId = "eval-2",
                    createdAt = "2026-03-25T14:00:00Z",
                    feature = "nutrition",
                    judgeStatus = "failed",
                    judgeDecision = null,
                    judgeOverallScore = null,
                    sourceDurationMs = 1300.0,
                    judgeDurationMs = null,
                    sourceTotalTokens = 550,
                    judgeTotalTokens = null
                ),
                evaluationRow(
                    evaluationId = "eval-1",
                    createdAt = "2026-03-24T12:00:00Z",
                    feature = "nutrition",
                    judgeStatus = "completed",
                    judgeDecision = "rejected",
                    judgeOverallScore = 0.2,
                    sourceDurationMs = 1200.0,
                    judgeDurationMs = 250.0,
                    sourceTotalTokens = 500,
                    judgeTotalTokens = 100,
                    judgeRejectionReasons = listOf("falta contexto", "falta contexto")
                )
            )
        )

        val response = service.getMetrics(
            actorUserId = "admin-1",
            days = null,
            startDate = "2026-03-24",
            endDate = "2026-03-26",
            feature = "nutrition",
            pipeline = null,
            handler = null,
            idioma = null,
            sourceModel = null,
            judgeStatus = null,
            judgeDecision = null
        )

        assertEquals(3, response.summary.totalEvaluations)
        assertEquals(2, response.summary.completedCount)
        assertEquals(1, response.summary.failedCount)
        assertEquals(1, response.summary.approvedCount)
        assertEquals(1, response.summary.rejectedCount)
        assertEquals(66.67, response.summary.completionRatePercent)
        assertEquals(33.33, response.summary.failureRatePercent)
        assertEquals(50.0, response.summary.approvalRatePercent)
        assertEquals(0.55, response.summary.averageOverallScore)
        assertEquals(1333.33, response.summary.averageSourceDurationMs)
        assertEquals(275.0, response.summary.averageJudgeDurationMs)
        assertEquals(550.0, response.summary.averageSourceTotalTokens)
        assertEquals(125.0, response.summary.averageJudgeTotalTokens)
        assertEquals(3, response.daily.size)
        assertEquals("2026-03-24", response.daily.first().date)
        assertEquals("falta contexto", response.topRejectionReasons.first().key)
        assertEquals(2, response.topRejectionReasons.first().count)
        assertEquals("eval-3", response.recentEvaluations.first().evaluationId)
        assertEquals("nutrition", response.byFeature.first().key)
    }

    @Test
    fun `deve retornar approval rate nulo quando nao houver decisoes`() {
        `when`(
            supabaseClient.get(
                eqValue("llm_judge_evaluations"),
                eqValue(
                    "evaluation_id,created_at,feature,judge_status,idioma,pipeline,handler,source_model,source_duration_ms,source_total_tokens,judge_duration_ms,judge_total_tokens,judge_overall_score,judge_decision,judge_rejection_reasons"
                ),
                eqValue(
                    mapOf(
                        "and" to "(created_at.gte.2026-03-24T00:00Z,created_at.lt.2026-03-25T00:00Z)",
                        "order" to "created_at.desc,evaluation_id.desc"
                    )
                ),
                anyEvaluationTypeRef()
            )
        ).thenReturn(
            listOf(
                evaluationRow(
                    evaluationId = "eval-1",
                    createdAt = "2026-03-24T12:00:00Z",
                    feature = "chat",
                    judgeStatus = "pending"
                )
            )
        )

        val response = service.getMetrics(
            actorUserId = "admin-1",
            days = null,
            startDate = "2026-03-24",
            endDate = "2026-03-24",
            feature = null,
            pipeline = null,
            handler = null,
            idioma = null,
            sourceModel = null,
            judgeStatus = null,
            judgeDecision = null
        )

        assertNull(response.summary.approvalRatePercent)
    }

    @Test
    fun `deve validar actor user id`() {
        val exception = assertFailsWith<ResponseStatusException> {
            service.getMetrics(
                actorUserId = " ",
                days = 7,
                startDate = null,
                endDate = null,
                feature = null,
                pipeline = null,
                handler = null,
                idioma = null,
                sourceModel = null,
                judgeStatus = null,
                judgeDecision = null
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals("header X-User-Id obrigatorio para auditoria", exception.reason)
    }

    private fun evaluationRow(
        evaluationId: String,
        createdAt: String,
        feature: String,
        judgeStatus: String,
        judgeDecision: String? = null,
        judgeOverallScore: Double? = null,
        sourceDurationMs: Double? = null,
        judgeDurationMs: Double? = null,
        sourceTotalTokens: Int? = null,
        judgeTotalTokens: Int? = null,
        judgeRejectionReasons: List<Any?> = emptyList()
    ) = SupabaseLlmJudgeEvaluationRow(
        evaluationId = evaluationId,
        createdAt = createdAt,
        feature = feature,
        judgeStatus = judgeStatus,
        idioma = "pt-BR",
        pipeline = "image",
        handler = "calories",
        sourceModel = "gpt-4.1-mini",
        sourceDurationMs = sourceDurationMs,
        sourceTotalTokens = sourceTotalTokens,
        judgeDurationMs = judgeDurationMs,
        judgeTotalTokens = judgeTotalTokens,
        judgeOverallScore = judgeOverallScore,
        judgeDecision = judgeDecision,
        judgeRejectionReasons = judgeRejectionReasons
    )

    private fun <T> eqValue(value: T): T {
        ArgumentMatchers.eq(value)
        return value
    }

    private fun anyEvaluationTypeRef(): ParameterizedTypeReference<List<SupabaseLlmJudgeEvaluationRow>> {
        ArgumentMatchers.any(ParameterizedTypeReference::class.java)
        return uninitialized()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T
}
