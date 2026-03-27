package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.response.SupabaseTelemetryAgentRunRow
import com.vidasync_bff.dto.response.SupabaseTelemetryAgentRunsDailyRow
import com.vidasync_bff.dto.response.SupabaseTelemetryLlmModelsDailyRow
import com.vidasync_bff.observability.AgentTelemetryLlmCallRecord
import com.vidasync_bff.observability.AgentTelemetryRunRecord
import com.vidasync_bff.observability.AgentTelemetrySnapshot
import com.vidasync_bff.observability.AgentTelemetryStageEventRecord
import com.vidasync_bff.observability.AgentTelemetryToolCallRecord
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TelemetryServiceTests {

    private val supabaseClient = mock(SupabaseClient::class.java)
    private val service = TelemetryService(
        supabaseClient = supabaseClient,
        internalAdminApiKey = "secret-key",
        maxRawRunsForMetrics = 10000
    )

    @Test
    fun `deve agregar metricas de telemetry por periodo`() {
        `when`(
            supabaseClient.get(
                eqValue("telemetry_agent_runs"),
                eqValue(
                    "run_id,request_id,trace_id,agent,endpoint,http_method,http_status,status,timeout,duration_ms,input_tokens,output_tokens,total_tokens,total_cost_usd,llm_call_count,tool_call_count,stage_event_count,error_message,request_context,started_at,finished_at"
                ),
                eqValue(
                    mapOf(
                        "and" to "(started_at.gte.2026-03-24T00:00Z,started_at.lt.2026-03-27T00:00Z)",
                        "order" to "started_at.desc,run_id.desc",
                        "limit" to "10000"
                    )
                ),
                anyRunsTypeRef()
            )
        ).thenReturn(
            listOf(
                runRow(
                    runId = "run-3",
                    requestId = "req-3",
                    agent = "nutrition",
                    endpoint = "/nutrition/calories",
                    status = "success",
                    timeout = false,
                    durationMs = 950.0,
                    totalTokens = 420,
                    totalCostUsd = 0.0012,
                    llmCallCount = 1,
                    startedAt = "2026-03-26T15:00:00Z"
                ),
                runRow(
                    runId = "run-2",
                    requestId = "req-2",
                    agent = "chat",
                    endpoint = "/chat",
                    status = "error",
                    timeout = false,
                    durationMs = 1500.0,
                    totalTokens = 300,
                    totalCostUsd = 0.0008,
                    llmCallCount = 1,
                    startedAt = "2026-03-25T12:00:00Z"
                ),
                runRow(
                    runId = "run-1",
                    requestId = "req-1",
                    agent = "nutrition",
                    endpoint = "/nutrition/calories",
                    status = "error",
                    timeout = true,
                    durationMs = 2100.0,
                    totalTokens = 0,
                    totalCostUsd = 0.0,
                    llmCallCount = 0,
                    startedAt = "2026-03-24T10:00:00Z"
                )
            )
        )

        `when`(
            supabaseClient.get(
                eqValue("telemetry_agent_runs_daily"),
                eqValue(
                    "day_utc,agent,endpoint,run_count,success_count,error_count,timeout_count,total_cost_usd,total_tokens,avg_duration_ms,p95_duration_ms"
                ),
                eqValue(
                    mapOf(
                        "and" to "(day_utc.gte.2026-03-24,day_utc.lte.2026-03-26)",
                        "order" to "day_utc.asc,agent.asc,endpoint.asc"
                    )
                ),
                anyDailyRunsTypeRef()
            )
        ).thenReturn(
            listOf(
                dailyRunRow(
                    dayUtc = "2026-03-24",
                    agent = "nutrition",
                    endpoint = "/nutrition/calories",
                    runCount = 1,
                    successCount = 0,
                    errorCount = 1,
                    timeoutCount = 1,
                    totalCostUsd = 0.0,
                    totalTokens = 0,
                    avgDurationMs = 2100.0,
                    p95DurationMs = 2100.0
                ),
                dailyRunRow(
                    dayUtc = "2026-03-25",
                    agent = "chat",
                    endpoint = "/chat",
                    runCount = 1,
                    successCount = 0,
                    errorCount = 1,
                    timeoutCount = 0,
                    totalCostUsd = 0.0008,
                    totalTokens = 300,
                    avgDurationMs = 1500.0,
                    p95DurationMs = 1500.0
                ),
                dailyRunRow(
                    dayUtc = "2026-03-26",
                    agent = "nutrition",
                    endpoint = "/nutrition/calories",
                    runCount = 1,
                    successCount = 1,
                    errorCount = 0,
                    timeoutCount = 0,
                    totalCostUsd = 0.0012,
                    totalTokens = 420,
                    avgDurationMs = 950.0,
                    p95DurationMs = 950.0
                )
            )
        )

        `when`(
            supabaseClient.get(
                eqValue("telemetry_llm_models_daily"),
                eqValue(
                    "day_utc,agent,model,llm_call_count,total_cost_usd,input_tokens,output_tokens,total_tokens,avg_duration_ms,p95_duration_ms"
                ),
                eqValue(
                    mapOf(
                        "and" to "(day_utc.gte.2026-03-24,day_utc.lte.2026-03-26)",
                        "order" to "day_utc.asc,agent.asc,model.asc"
                    )
                ),
                anyDailyModelsTypeRef()
            )
        ).thenReturn(
            listOf(
                dailyModelRow(
                    dayUtc = "2026-03-25",
                    agent = "chat",
                    model = "gpt-4o-mini",
                    llmCallCount = 1,
                    totalCostUsd = 0.0008,
                    inputTokens = 120,
                    outputTokens = 180,
                    totalTokens = 300,
                    avgDurationMs = 1500.0,
                    p95DurationMs = 1500.0
                ),
                dailyModelRow(
                    dayUtc = "2026-03-26",
                    agent = "nutrition",
                    model = "gpt-4.1-mini",
                    llmCallCount = 1,
                    totalCostUsd = 0.0012,
                    inputTokens = 200,
                    outputTokens = 220,
                    totalTokens = 420,
                    avgDurationMs = 950.0,
                    p95DurationMs = 950.0
                )
            )
        )

        val response = service.getMetrics(
            actorUserId = "admin-1",
            providedInternalApiKey = "secret-key",
            days = null,
            startDate = "2026-03-24",
            endDate = "2026-03-26",
            agent = null
        )

        assertEquals(3, response.summary.totalRuns)
        assertEquals(1, response.summary.successCount)
        assertEquals(2, response.summary.errorCount)
        assertEquals(1, response.summary.timeoutCount)
        assertEquals(720, response.summary.totalTokens)
        assertEquals(0.0, response.daily.first().totalCostUsd)
        assertEquals("nutrition", response.byAgent.first().agent)
        assertEquals("gpt-4.1-mini", response.byModel.first().model)
    }

    @Test
    fun `deve retornar runs recentes filtrando por status error`() {
        `when`(
            supabaseClient.get(
                eqValue("telemetry_agent_runs"),
                eqValue(
                    "run_id,request_id,trace_id,agent,endpoint,http_method,http_status,status,timeout,duration_ms,input_tokens,output_tokens,total_tokens,total_cost_usd,llm_call_count,tool_call_count,stage_event_count,error_message,request_context,started_at,finished_at"
                ),
                eqValue(
                    mapOf(
                        "and" to "(started_at.gte.2026-03-24T00:00Z,started_at.lt.2026-03-25T00:00Z)",
                        "order" to "started_at.desc,run_id.desc",
                        "limit" to "5",
                        "status" to "eq.error"
                    )
                ),
                anyRunsTypeRef()
            )
        ).thenReturn(
            listOf(
                runRow(
                    runId = "run-timeout",
                    requestId = "req-timeout",
                    agent = "nutrition",
                    endpoint = "/nutrition/calories",
                    status = "error",
                    timeout = true,
                    durationMs = 3000.0,
                    totalTokens = 0,
                    totalCostUsd = 0.0,
                    llmCallCount = 0,
                    startedAt = "2026-03-24T11:00:00Z"
                )
            )
        )

        val response = service.getRecentRuns(
            actorUserId = "admin-1",
            providedInternalApiKey = "secret-key",
            days = null,
            startDate = "2026-03-24",
            endDate = "2026-03-24",
            agent = null,
            status = "error",
            limit = 5
        )

        assertEquals(5, response.limit)
        assertEquals(1, response.recentRuns.size)
        assertEquals("error", response.recentRuns.first().status)
        assertEquals(true, response.recentRuns.first().timeout)
    }

    @Test
    fun `deve fazer flush da telemetry no supabase`() {
        val snapshot = AgentTelemetrySnapshot(
            run = AgentTelemetryRunRecord(
                runId = "run-1",
                requestId = "req-1",
                traceId = "trace-1",
                agent = "chat",
                endpoint = "/chat",
                httpMethod = "POST",
                httpStatus = 200,
                status = "success",
                timeout = false,
                durationMs = 321.5,
                totalCostUsd = 0.0012,
                inputTokens = 100,
                outputTokens = 120,
                totalTokens = 220,
                llmCallCount = 1,
                toolCallCount = 1,
                stageEventCount = 2,
                errorMessage = null,
                requestContext = mapOf("path" to "/chat"),
                startedAt = "2026-03-26T10:00:00Z",
                finishedAt = "2026-03-26T10:00:01Z"
            ),
            llmCalls = listOf(
                AgentTelemetryLlmCallRecord(
                    callId = "call-1",
                    runId = "run-1",
                    requestId = "req-1",
                    traceId = "trace-1",
                    agent = "chat",
                    provider = "openai",
                    operation = "openai_chat",
                    model = "gpt-4o-mini",
                    status = "success",
                    inputTokens = 100,
                    outputTokens = 120,
                    totalTokens = 220,
                    durationMs = 300.0,
                    costUsd = 0.0012,
                    providerResponseId = "resp-1",
                    endpoint = "/v1/openai/chat",
                    errorMessage = null,
                    metadata = mapOf("warningsCount" to 0),
                    createdAt = "2026-03-26T10:00:00Z"
                )
            ),
            toolCalls = listOf(
                AgentTelemetryToolCallRecord(
                    toolCallId = "tool-1",
                    runId = "run-1",
                    requestId = "req-1",
                    traceId = "trace-1",
                    agent = "chat",
                    toolName = "ingredient_cache.lookup_batch",
                    status = "success",
                    durationMs = 10.0,
                    errorMessage = null,
                    metadata = mapOf("hits" to 1),
                    createdAt = "2026-03-26T10:00:00Z"
                )
            ),
            stageEvents = listOf(
                AgentTelemetryStageEventRecord(
                    eventId = "evt-1",
                    runId = "run-1",
                    requestId = "req-1",
                    traceId = "trace-1",
                    agent = "chat",
                    stage = "request",
                    eventType = "flow",
                    status = "started",
                    durationMs = null,
                    detail = "request received",
                    payload = mapOf("path" to "/chat"),
                    createdAt = "2026-03-26T10:00:00Z"
                )
            )
        )

        service.flushQuietly(snapshot)

        verify(supabaseClient, times(1)).post(eqValue("telemetry_agent_runs"), anyBody(), anyWriteTypeRef())
        verify(supabaseClient, times(1)).post(eqValue("telemetry_llm_calls"), anyBody(), anyWriteTypeRef())
        verify(supabaseClient, times(1)).post(eqValue("telemetry_tool_calls"), anyBody(), anyWriteTypeRef())
        verify(supabaseClient, times(1)).post(eqValue("telemetry_stage_events"), anyBody(), anyWriteTypeRef())
    }

    @Test
    fun `deve validar internal api key`() {
        val exception = assertFailsWith<ResponseStatusException> {
            service.getMetrics(
                actorUserId = "admin-1",
                providedInternalApiKey = "wrong-key",
                days = 7,
                startDate = null,
                endDate = null,
                agent = null
            )
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("internal api key invalida", exception.reason)
    }

    private fun runRow(
        runId: String,
        requestId: String,
        agent: String,
        endpoint: String,
        status: String,
        timeout: Boolean,
        durationMs: Double,
        totalTokens: Int,
        totalCostUsd: Double,
        llmCallCount: Int,
        startedAt: String
    ) = SupabaseTelemetryAgentRunRow(
        runId = runId,
        requestId = requestId,
        traceId = "trace-$runId",
        agent = agent,
        endpoint = endpoint,
        httpMethod = "POST",
        httpStatus = if (status == "success") 200 else 504,
        status = status,
        timeout = timeout,
        durationMs = durationMs,
        inputTokens = if (totalTokens > 0) totalTokens / 2 else 0,
        outputTokens = if (totalTokens > 0) totalTokens / 2 else 0,
        totalTokens = totalTokens,
        totalCostUsd = totalCostUsd,
        llmCallCount = llmCallCount,
        toolCallCount = 0,
        stageEventCount = 1,
        errorMessage = null,
        requestContext = emptyMap(),
        startedAt = startedAt,
        finishedAt = startedAt
    )

    private fun dailyRunRow(
        dayUtc: String,
        agent: String,
        endpoint: String,
        runCount: Int,
        successCount: Int,
        errorCount: Int,
        timeoutCount: Int,
        totalCostUsd: Double,
        totalTokens: Int,
        avgDurationMs: Double,
        p95DurationMs: Double
    ) = SupabaseTelemetryAgentRunsDailyRow(
        dayUtc = dayUtc,
        agent = agent,
        endpoint = endpoint,
        runCount = runCount,
        successCount = successCount,
        errorCount = errorCount,
        timeoutCount = timeoutCount,
        totalCostUsd = totalCostUsd,
        totalTokens = totalTokens,
        avgDurationMs = avgDurationMs,
        p95DurationMs = p95DurationMs
    )

    private fun dailyModelRow(
        dayUtc: String,
        agent: String,
        model: String,
        llmCallCount: Int,
        totalCostUsd: Double,
        inputTokens: Int,
        outputTokens: Int,
        totalTokens: Int,
        avgDurationMs: Double,
        p95DurationMs: Double
    ) = SupabaseTelemetryLlmModelsDailyRow(
        dayUtc = dayUtc,
        agent = agent,
        model = model,
        llmCallCount = llmCallCount,
        totalCostUsd = totalCostUsd,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        totalTokens = totalTokens,
        avgDurationMs = avgDurationMs,
        p95DurationMs = p95DurationMs
    )

    private fun <T> eqValue(value: T): T {
        ArgumentMatchers.eq(value)
        return value
    }

    private fun anyRunsTypeRef(): ParameterizedTypeReference<List<SupabaseTelemetryAgentRunRow>> {
        ArgumentMatchers.any(ParameterizedTypeReference::class.java)
        return object : ParameterizedTypeReference<List<SupabaseTelemetryAgentRunRow>>() {}
    }

    private fun anyDailyRunsTypeRef(): ParameterizedTypeReference<List<SupabaseTelemetryAgentRunsDailyRow>> {
        ArgumentMatchers.any(ParameterizedTypeReference::class.java)
        return object : ParameterizedTypeReference<List<SupabaseTelemetryAgentRunsDailyRow>>() {}
    }

    private fun anyDailyModelsTypeRef(): ParameterizedTypeReference<List<SupabaseTelemetryLlmModelsDailyRow>> {
        ArgumentMatchers.any(ParameterizedTypeReference::class.java)
        return object : ParameterizedTypeReference<List<SupabaseTelemetryLlmModelsDailyRow>>() {}
    }

    private fun anyWriteTypeRef(): ParameterizedTypeReference<List<Map<String, Any?>>> {
        ArgumentMatchers.any(ParameterizedTypeReference::class.java)
        return object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
    }

    private fun anyBody(): Any {
        ArgumentMatchers.any<Any>()
        return Any()
    }
}
