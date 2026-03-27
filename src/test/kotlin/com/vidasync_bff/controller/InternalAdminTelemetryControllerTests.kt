package com.vidasync_bff.controller

import com.vidasync_bff.dto.response.TelemetryMetricsAgentBreakdownResponse
import com.vidasync_bff.dto.response.TelemetryMetricsDailyPointResponse
import com.vidasync_bff.dto.response.TelemetryMetricsFiltersResponse
import com.vidasync_bff.dto.response.TelemetryMetricsModelBreakdownResponse
import com.vidasync_bff.dto.response.TelemetryMetricsResponse
import com.vidasync_bff.dto.response.TelemetryMetricsSummaryResponse
import com.vidasync_bff.dto.response.TelemetryRecentRunResponse
import com.vidasync_bff.dto.response.TelemetryRunsResponse
import com.vidasync_bff.service.TelemetryService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException

class InternalAdminTelemetryControllerTests {

    private val telemetryService = mock(TelemetryService::class.java)
    private val mockMvc = MockMvcBuilders
        .standaloneSetup(InternalAdminTelemetryController(telemetryService))
        .build()

    @Test
    fun `deve retornar 200 com metricas de telemetry`() {
        val response = TelemetryMetricsResponse(
            filters = TelemetryMetricsFiltersResponse(
                startDate = "2026-03-20",
                endDate = "2026-03-26",
                days = 7,
                agent = null,
                model = null,
                status = null
            ),
            summary = TelemetryMetricsSummaryResponse(
                totalRuns = 12,
                successCount = 10,
                errorCount = 2,
                timeoutCount = 1,
                totalCostUsd = 1.23,
                inputTokens = 1200,
                outputTokens = 340,
                totalTokens = 1540,
                averageDurationMs = 812.4,
                p95DurationMs = 1600.0,
                latestRunAt = "2026-03-26T18:00:00Z",
                oldestRunAt = "2026-03-20T09:00:00Z"
            ),
            daily = listOf(
                TelemetryMetricsDailyPointResponse(
                    dayUtc = "2026-03-26",
                    runCount = 3,
                    successCount = 2,
                    errorCount = 1,
                    timeoutCount = 1,
                    totalCostUsd = 0.42,
                    inputTokens = 500,
                    outputTokens = 140,
                    totalTokens = 640,
                    averageDurationMs = 910.0,
                    p95DurationMs = 1400.0
                )
            ),
            byAgent = listOf(
                TelemetryMetricsAgentBreakdownResponse(
                    agent = "chat",
                    runCount = 7,
                    successCount = 6,
                    errorCount = 1,
                    timeoutCount = 0,
                    totalCostUsd = 0.88,
                    totalTokens = 1200,
                    averageDurationMs = 700.0,
                    p95DurationMs = 1200.0
                )
            ),
            byModel = listOf(
                TelemetryMetricsModelBreakdownResponse(
                    model = "gpt-4.1-mini",
                    agent = "chat",
                    llmCallCount = 7,
                    totalCostUsd = 0.88,
                    inputTokens = 1000,
                    outputTokens = 200,
                    totalTokens = 1200,
                    averageDurationMs = 650.0,
                    p95DurationMs = 980.0
                )
            )
        )

        `when`(
            telemetryService.getMetrics(
                "admin-1",
                "secret-key",
                7,
                null,
                null,
                "chat"
            )
        ).thenReturn(response)

        mockMvc.get("/internal/admin/telemetry/metrics") {
            header("X-User-Id", "admin-1")
            header("X-Internal-Api-Key", "secret-key")
            param("days", "7")
            param("agent", "chat")
        }.andExpect {
            status { isOk() }
            jsonPath("$.metrics.summary.totalRuns") { value(12) }
            jsonPath("$.metrics.daily[0].dayUtc") { value("2026-03-26") }
            jsonPath("$.metrics.byAgent[0].agent") { value("chat") }
            jsonPath("$.metrics.byModel[0].model") { value("gpt-4.1-mini") }
        }
    }

    @Test
    fun `deve retornar status propagado para runs`() {
        `when`(
            telemetryService.getRecentRuns(
                "admin-1",
                "wrong-key",
                null,
                null,
                null,
                null,
                null,
                20
            )
        ).thenThrow(ResponseStatusException(HttpStatus.UNAUTHORIZED, "internal api key invalida"))

        mockMvc.get("/internal/admin/telemetry/runs") {
            header("X-User-Id", "admin-1")
            header("X-Internal-Api-Key", "wrong-key")
            param("limit", "20")
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("internal api key invalida") }
        }
    }

    @Test
    fun `deve retornar 200 com runs recentes`() {
        val response = TelemetryRunsResponse(
            filters = TelemetryMetricsFiltersResponse(
                startDate = "2026-03-20",
                endDate = "2026-03-26",
                days = 7,
                agent = null,
                model = null,
                status = null
            ),
            limit = 2,
            recentRuns = listOf(
                TelemetryRecentRunResponse(
                    runId = "run-1",
                    requestId = "req-1",
                    traceId = "trace-1",
                    agent = "chat",
                    endpoint = "/chat",
                    httpMethod = "POST",
                    httpStatus = 200,
                    status = "success",
                    timeout = false,
                    durationMs = 800.0,
                    totalCostUsd = 0.12,
                    inputTokens = 300,
                    outputTokens = 100,
                    totalTokens = 400,
                    llmCallCount = 1,
                    toolCallCount = 0,
                    stageEventCount = 3,
                    errorMessage = null,
                    startedAt = "2026-03-26T18:00:00Z",
                    finishedAt = "2026-03-26T18:00:01Z",
                    requestContext = mapOf("path" to "/chat")
                )
            )
        )

        `when`(
            telemetryService.getRecentRuns(
                "admin-1",
                "secret-key",
                null,
                null,
                null,
                null,
                null,
                2
            )
        ).thenReturn(response)

        mockMvc.get("/internal/admin/telemetry/runs") {
            header("X-User-Id", "admin-1")
            header("X-Internal-Api-Key", "secret-key")
            param("limit", "2")
        }.andExpect {
            status { isOk() }
            jsonPath("$.runs.limit") { value(2) }
            jsonPath("$.runs.recentRuns[0].runId") { value("run-1") }
            jsonPath("$.runs.recentRuns[0].agent") { value("chat") }
        }
    }
}
