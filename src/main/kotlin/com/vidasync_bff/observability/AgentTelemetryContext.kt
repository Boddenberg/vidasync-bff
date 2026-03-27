package com.vidasync_bff.observability

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class AgentTelemetryRunRecord(
    val runId: String,
    val requestId: String,
    val traceId: String?,
    val agent: String,
    val endpoint: String,
    val httpMethod: String,
    val httpStatus: Int,
    val status: String,
    val timeout: Boolean,
    val durationMs: Double,
    val totalCostUsd: Double,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val llmCallCount: Int,
    val toolCallCount: Int,
    val stageEventCount: Int,
    val errorMessage: String?,
    val requestContext: Map<String, Any?>,
    val startedAt: String,
    val finishedAt: String
)

data class AgentTelemetryLlmCallRecord(
    val callId: String,
    val runId: String,
    val requestId: String,
    val traceId: String?,
    val agent: String,
    val provider: String,
    val operation: String,
    val model: String?,
    val status: String,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val totalTokens: Int?,
    val durationMs: Double,
    val costUsd: Double?,
    val providerResponseId: String?,
    val endpoint: String?,
    val errorMessage: String?,
    val metadata: Map<String, Any?>,
    val createdAt: String
)

data class AgentTelemetryToolCallRecord(
    val toolCallId: String,
    val runId: String,
    val requestId: String,
    val traceId: String?,
    val agent: String,
    val toolName: String,
    val status: String,
    val durationMs: Double?,
    val errorMessage: String?,
    val metadata: Map<String, Any?>,
    val createdAt: String
)

data class AgentTelemetryStageEventRecord(
    val eventId: String,
    val runId: String,
    val requestId: String,
    val traceId: String?,
    val agent: String,
    val stage: String,
    val eventType: String,
    val status: String,
    val durationMs: Double?,
    val detail: String?,
    val payload: Map<String, Any?>,
    val createdAt: String
)

data class AgentTelemetrySnapshot(
    val run: AgentTelemetryRunRecord,
    val llmCalls: List<AgentTelemetryLlmCallRecord>,
    val toolCalls: List<AgentTelemetryToolCallRecord>,
    val stageEvents: List<AgentTelemetryStageEventRecord>
)

object AgentTelemetryContext {

    private val current = object : InheritableThreadLocal<AgentTelemetryCollector?>() {}

    fun startRun(
        requestId: String,
        traceId: String?,
        agent: String,
        endpoint: String,
        httpMethod: String,
        requestContext: Map<String, Any?>
    ): String {
        val collector = AgentTelemetryCollector(
            runId = randomId(),
            requestId = requestId,
            traceId = normalize(traceId),
            agent = normalize(agent) ?: "unknown",
            endpoint = normalize(endpoint) ?: "unknown",
            httpMethod = normalize(httpMethod)?.uppercase() ?: "GET",
            requestContext = requestContext
        )
        current.set(collector)
        collector.recordStageEvent(
            stage = "request",
            eventType = "flow",
            status = "started",
            durationMs = null,
            detail = "request received",
            payload = requestContext
        )
        return collector.runId
    }

    fun currentRunId(): String? = current.get()?.runId

    fun currentRequestId(): String? = current.get()?.requestId

    fun recordLlmCall(
        provider: String,
        operation: String,
        model: String? = null,
        status: String,
        inputTokens: Int? = null,
        outputTokens: Int? = null,
        totalTokens: Int? = null,
        durationMs: Double,
        providerResponseId: String? = null,
        endpoint: String? = null,
        errorMessage: String? = null,
        metadata: Map<String, Any?> = emptyMap()
    ) {
        current.get()?.recordLlmCall(
            provider = provider,
            operation = operation,
            model = model,
            status = status,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            durationMs = durationMs,
            providerResponseId = providerResponseId,
            endpoint = endpoint,
            errorMessage = errorMessage,
            metadata = metadata
        )
    }

    fun recordToolCall(
        toolName: String,
        status: String,
        durationMs: Double? = null,
        errorMessage: String? = null,
        metadata: Map<String, Any?> = emptyMap()
    ) {
        current.get()?.recordToolCall(
            toolName = toolName,
            status = status,
            durationMs = durationMs,
            errorMessage = errorMessage,
            metadata = metadata
        )
    }

    fun recordStageEvent(
        stage: String,
        eventType: String,
        status: String,
        durationMs: Double? = null,
        detail: String? = null,
        payload: Map<String, Any?> = emptyMap()
    ) {
        current.get()?.recordStageEvent(
            stage = stage,
            eventType = eventType,
            status = status,
            durationMs = durationMs,
            detail = detail,
            payload = payload
        )
    }

    fun completeRun(
        httpStatus: Int,
        status: String,
        durationMs: Double,
        timeout: Boolean,
        errorMessage: String? = null
    ): AgentTelemetrySnapshot? {
        val collector = current.get() ?: return null
        return try {
            collector.complete(
                httpStatus = httpStatus,
                status = status,
                durationMs = durationMs,
                timeout = timeout,
                errorMessage = errorMessage
            )
        } finally {
            current.remove()
        }
    }

    private fun randomId(): String = UUID.randomUUID().toString().replace("-", "")

    private fun normalize(value: String?): String? {
        val text = value?.trim() ?: return null
        return text.takeIf { it.isNotBlank() }
    }
}

private class AgentTelemetryCollector(
    val runId: String,
    val requestId: String,
    private val traceId: String?,
    private val agent: String,
    private val endpoint: String,
    private val httpMethod: String,
    requestContext: Map<String, Any?>
) {

    private val startedAt = OffsetDateTime.now(ZoneOffset.UTC)
    private val requestContext = requestContext.toMap()
    private val llmCalls = mutableListOf<AgentTelemetryLlmCallRecord>()
    private val toolCalls = mutableListOf<AgentTelemetryToolCallRecord>()
    private val stageEvents = mutableListOf<AgentTelemetryStageEventRecord>()

    @Synchronized
    fun recordLlmCall(
        provider: String,
        operation: String,
        model: String?,
        status: String,
        inputTokens: Int?,
        outputTokens: Int?,
        totalTokens: Int?,
        durationMs: Double,
        providerResponseId: String?,
        endpoint: String?,
        errorMessage: String?,
        metadata: Map<String, Any?>
    ) {
        val resolvedInputTokens = inputTokens ?: 0
        val resolvedOutputTokens = outputTokens ?: 0
        val resolvedTotalTokens = totalTokens ?: (resolvedInputTokens + resolvedOutputTokens).takeIf { it > 0 }
        val resolvedCostUsd = TelemetryPricingCatalog.calculate(
            model = model,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = resolvedTotalTokens
        )

        llmCalls += AgentTelemetryLlmCallRecord(
            callId = UUID.randomUUID().toString().replace("-", ""),
            runId = runId,
            requestId = requestId,
            traceId = traceId,
            agent = agent,
            provider = provider,
            operation = operation,
            model = normalize(model),
            status = normalize(status) ?: "success",
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = resolvedTotalTokens,
            durationMs = round(durationMs),
            costUsd = resolvedCostUsd,
            providerResponseId = normalize(providerResponseId),
            endpoint = normalize(endpoint),
            errorMessage = truncate(normalize(errorMessage), 1000),
            metadata = metadata.toMap(),
            createdAt = nowUtc()
        )
    }

    @Synchronized
    fun recordToolCall(
        toolName: String,
        status: String,
        durationMs: Double?,
        errorMessage: String?,
        metadata: Map<String, Any?>
    ) {
        toolCalls += AgentTelemetryToolCallRecord(
            toolCallId = UUID.randomUUID().toString().replace("-", ""),
            runId = runId,
            requestId = requestId,
            traceId = traceId,
            agent = agent,
            toolName = normalize(toolName) ?: "unknown",
            status = normalize(status) ?: "success",
            durationMs = durationMs?.let(::round),
            errorMessage = truncate(normalize(errorMessage), 1000),
            metadata = metadata.toMap(),
            createdAt = nowUtc()
        )
    }

    @Synchronized
    fun recordStageEvent(
        stage: String,
        eventType: String,
        status: String,
        durationMs: Double?,
        detail: String?,
        payload: Map<String, Any?>
    ) {
        stageEvents += AgentTelemetryStageEventRecord(
            eventId = UUID.randomUUID().toString().replace("-", ""),
            runId = runId,
            requestId = requestId,
            traceId = traceId,
            agent = agent,
            stage = normalize(stage) ?: "unknown",
            eventType = normalize(eventType) ?: "stage",
            status = normalize(status) ?: "completed",
            durationMs = durationMs?.let(::round),
            detail = truncate(normalize(detail), 2000),
            payload = payload.toMap(),
            createdAt = nowUtc()
        )
    }

    @Synchronized
    fun complete(
        httpStatus: Int,
        status: String,
        durationMs: Double,
        timeout: Boolean,
        errorMessage: String?
    ): AgentTelemetrySnapshot {
        recordStageEvent(
            stage = "request",
            eventType = "flow",
            status = "completed",
            durationMs = durationMs,
            detail = "request finished",
            payload = mapOf(
                "httpStatus" to httpStatus,
                "timeout" to timeout
            )
        )

        val llmSnapshot = llmCalls.toList()
        val toolSnapshot = toolCalls.toList()
        val stageSnapshot = stageEvents.toList()
        val totalInputTokens = llmSnapshot.sumOf { it.inputTokens ?: 0 }
        val totalOutputTokens = llmSnapshot.sumOf { it.outputTokens ?: 0 }
        val totalTokens = llmSnapshot.sumOf { it.totalTokens ?: 0 }
        val totalCostUsd = round(llmSnapshot.mapNotNull { it.costUsd }.sum())

        val run = AgentTelemetryRunRecord(
            runId = runId,
            requestId = requestId,
            traceId = traceId,
            agent = agent,
            endpoint = endpoint,
            httpMethod = httpMethod,
            httpStatus = httpStatus,
            status = normalize(status) ?: "success",
            timeout = timeout,
            durationMs = round(durationMs),
            totalCostUsd = totalCostUsd,
            inputTokens = totalInputTokens,
            outputTokens = totalOutputTokens,
            totalTokens = totalTokens,
            llmCallCount = llmSnapshot.size,
            toolCallCount = toolSnapshot.size,
            stageEventCount = stageSnapshot.size,
            errorMessage = truncate(normalize(errorMessage), 1000),
            requestContext = requestContext,
            startedAt = startedAt.toString(),
            finishedAt = nowUtc()
        )

        return AgentTelemetrySnapshot(
            run = run,
            llmCalls = llmSnapshot,
            toolCalls = toolSnapshot,
            stageEvents = stageSnapshot
        )
    }

    private fun normalize(value: String?): String? {
        val text = value?.trim() ?: return null
        return text.takeIf { it.isNotBlank() }
    }

    private fun truncate(value: String?, maxLength: Int): String? {
        val text = value ?: return null
        return if (text.length <= maxLength) text else text.take(maxLength)
    }

    private fun nowUtc(): String = OffsetDateTime.now(ZoneOffset.UTC).toString()

    private fun round(value: Double): Double {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).toDouble()
    }
}

private object TelemetryPricingCatalog {

    private data class ModelPricing(
        val inputUsdPerMillionTokens: BigDecimal,
        val outputUsdPerMillionTokens: BigDecimal
    )

    private val catalog = mapOf(
        "gpt-4.1-mini" to ModelPricing(
            inputUsdPerMillionTokens = BigDecimal("0.40"),
            outputUsdPerMillionTokens = BigDecimal("1.60")
        ),
        "gpt-4o-mini" to ModelPricing(
            inputUsdPerMillionTokens = BigDecimal("0.15"),
            outputUsdPerMillionTokens = BigDecimal("0.60")
        ),
        "gpt-4.1" to ModelPricing(
            inputUsdPerMillionTokens = BigDecimal("2.00"),
            outputUsdPerMillionTokens = BigDecimal("8.00")
        ),
        "gpt-4o" to ModelPricing(
            inputUsdPerMillionTokens = BigDecimal("2.50"),
            outputUsdPerMillionTokens = BigDecimal("10.00")
        )
    )

    fun calculate(
        model: String?,
        inputTokens: Int?,
        outputTokens: Int?,
        totalTokens: Int?
    ): Double? {
        val normalizedModel = model?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        val pricing = catalog[normalizedModel] ?: return null
        val resolvedInputTokens = inputTokens ?: 0
        val resolvedOutputTokens = outputTokens ?: 0
        if (resolvedInputTokens == 0 && resolvedOutputTokens == 0 && (totalTokens ?: 0) == 0) {
            return null
        }

        val inputCost = pricing.inputUsdPerMillionTokens
            .multiply(BigDecimal.valueOf(resolvedInputTokens.toLong()))
            .divide(BigDecimal("1000000"), 8, RoundingMode.HALF_UP)
        val outputCost = pricing.outputUsdPerMillionTokens
            .multiply(BigDecimal.valueOf(resolvedOutputTokens.toLong()))
            .divide(BigDecimal("1000000"), 8, RoundingMode.HALF_UP)

        return inputCost
            .add(outputCost)
            .setScale(6, RoundingMode.HALF_UP)
            .toDouble()
    }
}
