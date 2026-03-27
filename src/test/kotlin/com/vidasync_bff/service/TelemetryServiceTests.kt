package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.response.SupabaseTelemetryAgentRunRow
import com.vidasync_bff.dto.response.SupabaseTelemetryAgentRunsDailyRow
import com.vidasync_bff.dto.response.SupabaseTelemetryLlmCallRow
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
        maxRawRunsForMetrics = 10000
    )

    @Test
    fun `deve agregar metricas de telemetry por periodo`() {
        `when`(
            supabaseClient.get(
                eqValue("telemetry_agent_runs"),
                eqValue(currentRunsSelect()),
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
                eqValue(currentDailyRunsSelect()),
                eqValue(
                    mapOf(
                        "and" to "(day_utc.gte.2026-03-24,day_utc.lte.2026-03-26)",
                        "order" to "day_utc.asc,agent.asc,status.asc"
                    )
                ),
                anyDailyRunsTypeRef()
            )
        ).thenReturn(
            listOf(
                currentDailyRunRow(
                    dayUtc = "2026-03-24",
                    agent = "nutrition",
                    status = "timeout",
                    runCount = 1,
                    llmCallCount = 0,
                    toolCallCount = 0,
                    stageEventCount = 1,
                    totalCostUsd = 0.0,
                    inputTokens = 0,
                    outputTokens = 0,
                    totalTokens = 0,
                    avgDurationMs = 2100.0
                ),
                currentDailyRunRow(
                    dayUtc = "2026-03-25",
                    agent = "chat",
                    status = "error",
                    runCount = 1,
                    llmCallCount = 1,
                    toolCallCount = 0,
                    stageEventCount = 1,
                    totalCostUsd = 0.0008,
                    inputTokens = 120,
                    outputTokens = 180,
                    totalTokens = 300,
                    avgDurationMs = 1500.0
                ),
                currentDailyRunRow(
                    dayUtc = "2026-03-26",
                    agent = "nutrition",
                    status = "success",
                    runCount = 1,
                    llmCallCount = 1,
                    toolCallCount = 0,
                    stageEventCount = 1,
                    totalCostUsd = 0.0012,
                    inputTokens = 200,
                    outputTokens = 220,
                    totalTokens = 420,
                    avgDurationMs = 950.0
                )
            )
        )

        `when`(
            supabaseClient.get(
                eqValue("telemetry_llm_calls"),
                eqValue("run_id,provider,operation,model,status,input_tokens,output_tokens,total_tokens,duration_ms,cost_usd,created_at"),
                eqValue(
                    mapOf(
                        "run_id" to "in.(run-3,run-2,run-1)",
                        "order" to "created_at.desc"
                    )
                ),
                anyLlmCallsTypeRef()
            )
        ).thenReturn(
            listOf(
                llmCallRow(
                    runId = "run-2",
                    model = "gpt-4o-mini",
                    inputTokens = 120,
                    outputTokens = 180,
                    totalTokens = 300,
                    durationMs = 1500.0,
                    costUsd = 0.0008,
                    createdAt = "2026-03-25T12:00:00Z"
                ),
                llmCallRow(
                    runId = "run-3",
                    model = "gpt-4.1-mini",
                    inputTokens = 200,
                    outputTokens = 220,
                    totalTokens = 420,
                    durationMs = 950.0,
                    costUsd = 0.0012,
                    createdAt = "2026-03-26T15:00:00Z"
                )
            )
        )

        val response = service.getMetrics(
            actorUserId = "admin-1",
            days = null,
            startDate = "2026-03-24",
            endDate = "2026-03-26",
            agent = null
        )

        assertEquals(3, response.summary.totalRuns)
        assertEquals(1, response.summary.successCount)
        assertEquals(1, response.summary.errorCount)
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
                eqValue(currentRunsSelect()),
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
    fun `deve fazer fallback para schema legado com endpoint na tabela de runs`() {
        `when`(
            supabaseClient.get(
                eqValue("telemetry_agent_runs"),
                eqValue(currentRunsSelect()),
                eqValue(
                    mapOf(
                        "and" to "(started_at.gte.2026-03-24T00:00Z,started_at.lt.2026-03-25T00:00Z)",
                        "order" to "started_at.desc,run_id.desc",
                        "limit" to "5"
                    )
                ),
                anyRunsTypeRef()
            )
        ).thenThrow(RuntimeException("column telemetry_agent_runs.http_status_code does not exist"))

        `when`(
            supabaseClient.get(
                eqValue("telemetry_agent_runs"),
                eqValue(runsSelect("entrypoint")),
                eqValue(
                    mapOf(
                        "and" to "(started_at.gte.2026-03-24T00:00Z,started_at.lt.2026-03-25T00:00Z)",
                        "order" to "started_at.desc,run_id.desc",
                        "limit" to "5"
                    )
                ),
                anyRunsTypeRef()
            )
        ).thenThrow(RuntimeException("column telemetry_agent_runs.entrypoint does not exist"))

        `when`(
            supabaseClient.get(
                eqValue("telemetry_agent_runs"),
                eqValue(runsSelect("endpoint")),
                eqValue(
                    mapOf(
                        "and" to "(started_at.gte.2026-03-24T00:00Z,started_at.lt.2026-03-25T00:00Z)",
                        "order" to "started_at.desc,run_id.desc",
                        "limit" to "5"
                    )
                ),
                anyRunsTypeRef()
            )
        ).thenReturn(
            listOf(
                runRow(
                    runId = "run-legacy",
                    requestId = "req-legacy",
                    agent = "chat",
                    endpoint = "/chat",
                    status = "success",
                    timeout = false,
                    durationMs = 120.0,
                    totalTokens = 42,
                    totalCostUsd = 0.0001,
                    llmCallCount = 1,
                    startedAt = "2026-03-24T11:00:00Z"
                )
            )
        )

        val response = service.getRecentRuns(
            actorUserId = "admin-1",
            days = null,
            startDate = "2026-03-24",
            endDate = "2026-03-24",
            agent = null,
            status = null,
            limit = 5
        )

        assertEquals(1, response.recentRuns.size)
        assertEquals("/chat", response.recentRuns.first().endpoint)
    }

    @Test
    fun `deve fazer fallback para schema legado com endpoint na view diaria`() {
        `when`(
            supabaseClient.get(
                eqValue("telemetry_agent_runs"),
                eqValue(currentRunsSelect()),
                eqValue(
                    mapOf(
                        "and" to "(started_at.gte.2026-03-24T00:00Z,started_at.lt.2026-03-25T00:00Z)",
                        "order" to "started_at.desc,run_id.desc",
                        "limit" to "10000"
                    )
                ),
                anyRunsTypeRef()
            )
        ).thenReturn(
            listOf(
                runRow(
                    runId = "run-1",
                    requestId = "req-1",
                    agent = "nutrition",
                    endpoint = "/nutrition/calories",
                    status = "success",
                    timeout = false,
                    durationMs = 400.0,
                    totalTokens = 200,
                    totalCostUsd = 0.001,
                    llmCallCount = 1,
                    startedAt = "2026-03-24T09:00:00Z"
                )
            )
        )

        `when`(
            supabaseClient.get(
                eqValue("telemetry_agent_runs_daily"),
                eqValue(currentDailyRunsSelect()),
                eqValue(
                    mapOf(
                        "and" to "(day_utc.gte.2026-03-24,day_utc.lte.2026-03-24)",
                        "order" to "day_utc.asc,agent.asc,status.asc"
                    )
                ),
                anyDailyRunsTypeRef()
            )
        ).thenThrow(RuntimeException("column telemetry_agent_runs_daily.runs_count does not exist"))

        `when`(
            supabaseClient.get(
                eqValue("telemetry_agent_runs_daily"),
                eqValue(dailyRunsSelect("entrypoint")),
                eqValue(
                    mapOf(
                        "and" to "(day_utc.gte.2026-03-24,day_utc.lte.2026-03-24)",
                        "order" to "day_utc.asc,agent.asc,entrypoint.asc"
                    )
                ),
                anyDailyRunsTypeRef()
            )
        ).thenThrow(RuntimeException("column telemetry_agent_runs_daily.entrypoint does not exist"))

        `when`(
            supabaseClient.get(
                eqValue("telemetry_agent_runs_daily"),
                eqValue(dailyRunsSelect("endpoint")),
                eqValue(
                    mapOf(
                        "and" to "(day_utc.gte.2026-03-24,day_utc.lte.2026-03-24)",
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
                    successCount = 1,
                    errorCount = 0,
                    timeoutCount = 0,
                    totalCostUsd = 0.001,
                    totalTokens = 200,
                    avgDurationMs = 400.0,
                    p95DurationMs = 400.0
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
                        "and" to "(day_utc.gte.2026-03-24,day_utc.lte.2026-03-24)",
                        "order" to "day_utc.asc,agent.asc,model.asc"
                    )
                ),
                anyDailyModelsTypeRef()
            )
        ).thenReturn(emptyList())

        val response = service.getMetrics(
            actorUserId = "admin-1",
            days = null,
            startDate = "2026-03-24",
            endDate = "2026-03-24",
            agent = null
        )

        assertEquals(1, response.summary.totalRuns)
        assertEquals(1, response.daily.first().runCount)
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

        verify(supabaseClient, times(1)).post(
            eqValue("telemetry_agent_runs"),
            eqValue(listOf(currentRunWriteMap(snapshot.run))),
            anyWriteTypeRef()
        )
        verify(supabaseClient, times(1)).post(eqValue("telemetry_llm_calls"), anyBody(), anyWriteTypeRef())
        verify(supabaseClient, times(1)).post(eqValue("telemetry_tool_calls"), anyBody(), anyWriteTypeRef())
        verify(supabaseClient, times(1)).post(eqValue("telemetry_stage_events"), anyBody(), anyWriteTypeRef())
    }

    @Test
    fun `deve fazer fallback para escrita legada quando schema atual nao aceitar colunas novas`() {
        val run = AgentTelemetryRunRecord(
            runId = "run-legacy-write",
            requestId = "req-legacy-write",
            traceId = "trace-legacy-write",
            agent = "chat",
            endpoint = "/chat",
            httpMethod = "POST",
            httpStatus = 200,
            status = "success",
            timeout = false,
            durationMs = 100.0,
            totalCostUsd = 0.0001,
            inputTokens = 10,
            outputTokens = 12,
            totalTokens = 22,
            llmCallCount = 1,
            toolCallCount = 0,
            stageEventCount = 1,
            errorMessage = null,
            requestContext = mapOf("path" to "/chat"),
            startedAt = "2026-03-26T10:00:00Z",
            finishedAt = "2026-03-26T10:00:01Z"
        )

        `when`(
            supabaseClient.post(
                eqValue("telemetry_agent_runs"),
                eqValue(listOf(currentRunWriteMap(run))),
                anyWriteTypeRef()
            )
        ).thenThrow(RuntimeException("column telemetry_agent_runs.http_status_code does not exist"))

        `when`(
            supabaseClient.post(
                eqValue("telemetry_agent_runs"),
                eqValue(listOf(legacyRunWriteMap(run, "entrypoint"))),
                anyWriteTypeRef()
            )
        ).thenThrow(RuntimeException("column telemetry_agent_runs.entrypoint does not exist"))

        service.flushQuietly(
            AgentTelemetrySnapshot(
                run = run,
                llmCalls = emptyList(),
                toolCalls = emptyList(),
                stageEvents = emptyList()
            )
        )

        verify(supabaseClient, times(1)).post(
            eqValue("telemetry_agent_runs"),
            eqValue(listOf(currentRunWriteMap(run))),
            anyWriteTypeRef()
        )
        verify(supabaseClient, times(1)).post(
            eqValue("telemetry_agent_runs"),
            eqValue(listOf(legacyRunWriteMap(run, "entrypoint"))),
            anyWriteTypeRef()
        )
        verify(supabaseClient, times(1)).post(
            eqValue("telemetry_agent_runs"),
            eqValue(listOf(legacyRunWriteMap(run, "endpoint"))),
            anyWriteTypeRef()
        )
    }

    @Test
    fun `deve validar actor user id`() {
        val exception = assertFailsWith<ResponseStatusException> {
            service.getMetrics(
                actorUserId = " ",
                days = 7,
                startDate = null,
                endDate = null,
                agent = null
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals("header X-User-Id obrigatorio para auditoria", exception.reason)
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

    private fun currentDailyRunRow(
        dayUtc: String,
        agent: String,
        status: String,
        runCount: Int,
        llmCallCount: Int,
        toolCallCount: Int,
        stageEventCount: Int,
        totalCostUsd: Double,
        inputTokens: Int,
        outputTokens: Int,
        totalTokens: Int,
        avgDurationMs: Double
    ) = SupabaseTelemetryAgentRunsDailyRow(
        dayUtc = dayUtc,
        agent = agent,
        status = status,
        runCount = runCount,
        llmCallCount = llmCallCount,
        toolCallCount = toolCallCount,
        stageEventCount = stageEventCount,
        totalCostUsd = totalCostUsd,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        totalTokens = totalTokens,
        avgDurationMs = avgDurationMs
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

    private fun llmCallRow(
        runId: String,
        model: String,
        inputTokens: Int,
        outputTokens: Int,
        totalTokens: Int,
        durationMs: Double,
        costUsd: Double,
        createdAt: String
    ) = SupabaseTelemetryLlmCallRow(
        runId = runId,
        provider = "openai",
        operation = "openai_chat",
        model = model,
        status = "success",
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        totalTokens = totalTokens,
        durationMs = durationMs,
        costUsd = costUsd,
        createdAt = createdAt
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

    private fun anyLlmCallsTypeRef(): ParameterizedTypeReference<List<SupabaseTelemetryLlmCallRow>> {
        ArgumentMatchers.any(ParameterizedTypeReference::class.java)
        return object : ParameterizedTypeReference<List<SupabaseTelemetryLlmCallRow>>() {}
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

    private fun runsSelect(column: String): String {
        val endpointSelect = if (column == "entrypoint") "endpoint:entrypoint" else "endpoint"
        return "run_id,request_id,trace_id,agent,$endpointSelect,http_method,http_status,status,timeout,duration_ms,input_tokens,output_tokens,total_tokens,total_cost_usd,llm_call_count,tool_call_count,stage_event_count,error_message,request_context,started_at,finished_at"
    }

    private fun dailyRunsSelect(column: String): String {
        val endpointSelect = if (column == "entrypoint") "endpoint:entrypoint" else "endpoint"
        return "day_utc,agent,$endpointSelect,run_count,success_count,error_count,timeout_count,total_cost_usd,total_tokens,avg_duration_ms,p95_duration_ms"
    }

    private fun currentRunsSelect(): String {
        return "run_id,request_id,trace_id,agent,endpoint:entrypoint,http_method,http_status:http_status_code,status,timeout,duration_ms,input_tokens:total_input_tokens,output_tokens:total_output_tokens,total_tokens,total_cost_usd,llm_call_count:llm_calls_count,tool_call_count:tool_calls_count,stage_event_count:stage_events_count,error_message,request_context:metadata_json,started_at,finished_at"
    }

    private fun currentDailyRunsSelect(): String {
        return "day_utc,agent,status,run_count:runs_count,llm_call_count:llm_calls_count,tool_call_count:tool_calls_count,stage_event_count:stage_events_count,total_cost_usd,input_tokens:total_input_tokens,output_tokens:total_output_tokens,total_tokens,avg_duration_ms,review_rate"
    }

    private fun currentRunWriteMap(run: AgentTelemetryRunRecord): Map<String, Any?> {
        return mapOf(
            "run_id" to run.runId,
            "request_id" to run.requestId,
            "trace_id" to run.traceId,
            "agent" to run.agent,
            "entrypoint" to run.endpoint,
            "http_method" to run.httpMethod,
            "http_status_code" to run.httpStatus,
            "status" to run.status,
            "timeout" to run.timeout,
            "duration_ms" to run.durationMs,
            "total_cost_usd" to run.totalCostUsd,
            "total_input_tokens" to run.inputTokens,
            "total_output_tokens" to run.outputTokens,
            "total_tokens" to run.totalTokens,
            "llm_calls_count" to run.llmCallCount,
            "tool_calls_count" to run.toolCallCount,
            "stage_events_count" to run.stageEventCount,
            "error_message" to run.errorMessage,
            "metadata_json" to run.requestContext,
            "started_at" to run.startedAt,
            "finished_at" to run.finishedAt
        )
    }

    private fun legacyRunWriteMap(run: AgentTelemetryRunRecord, column: String): Map<String, Any?> {
        return mapOf(
            "run_id" to run.runId,
            "request_id" to run.requestId,
            "trace_id" to run.traceId,
            "agent" to run.agent,
            column to run.endpoint,
            "http_method" to run.httpMethod,
            "http_status" to run.httpStatus,
            "status" to run.status,
            "timeout" to run.timeout,
            "duration_ms" to run.durationMs,
            "total_cost_usd" to run.totalCostUsd,
            "input_tokens" to run.inputTokens,
            "output_tokens" to run.outputTokens,
            "total_tokens" to run.totalTokens,
            "llm_call_count" to run.llmCallCount,
            "tool_call_count" to run.toolCallCount,
            "stage_event_count" to run.stageEventCount,
            "error_message" to run.errorMessage,
            "request_context" to run.requestContext,
            "started_at" to run.startedAt,
            "finished_at" to run.finishedAt
        )
    }
}
