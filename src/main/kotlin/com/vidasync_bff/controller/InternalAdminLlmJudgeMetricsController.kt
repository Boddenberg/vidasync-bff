package com.vidasync_bff.controller

import com.vidasync_bff.service.LlmJudgeMetricsService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/internal/admin/llm-judge")
class InternalAdminLlmJudgeMetricsController(
    private val llmJudgeMetricsService: LlmJudgeMetricsService
) {

    private val log = LoggerFactory.getLogger(InternalAdminLlmJudgeMetricsController::class.java)

    @GetMapping("/metrics")
    fun getMetrics(
        @RequestHeader("X-User-Id") actorUserId: String,
        @RequestHeader("X-Internal-Api-Key", required = false) internalApiKey: String?,
        @RequestParam(required = false) days: Int?,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(required = false) feature: String?,
        @RequestParam(required = false) pipeline: String?,
        @RequestParam(required = false) handler: String?,
        @RequestParam(required = false) idioma: String?,
        @RequestParam(required = false) sourceModel: String?,
        @RequestParam(required = false) judgeStatus: String?,
        @RequestParam(required = false) judgeDecision: String?
    ): ResponseEntity<Any> {
        log.info(
            "GET /internal/admin/llm-judge/metrics | actorUserId={}, days={}, startDate={}, endDate={}, feature={}, pipeline={}, handler={}, idioma={}, sourceModel={}, judgeStatus={}, judgeDecision={}",
            actorUserId,
            days,
            startDate,
            endDate,
            feature,
            pipeline,
            handler,
            idioma,
            sourceModel,
            judgeStatus,
            judgeDecision
        )

        return try {
            val result = llmJudgeMetricsService.getMetrics(
                actorUserId = actorUserId,
                providedInternalApiKey = internalApiKey,
                days = days,
                startDate = startDate,
                endDate = endDate,
                feature = feature,
                pipeline = pipeline,
                handler = handler,
                idioma = idioma,
                sourceModel = sourceModel,
                judgeStatus = judgeStatus,
                judgeDecision = judgeDecision
            )

            log.info(
                "GET /internal/admin/llm-judge/metrics -> 200 | actorUserId={}, totalEvaluations={}, recentCount={}",
                actorUserId,
                result.summary.totalEvaluations,
                result.recentEvaluations.size
            )

            ResponseEntity.ok(mapOf("metrics" to result))
        } catch (ex: ResponseStatusException) {
            log.warn(
                "GET /internal/admin/llm-judge/metrics -> {} | error={}",
                ex.statusCode.value(),
                ex.reason
            )
            ResponseEntity.status(ex.statusCode).body(mapOf("error" to (ex.reason ?: "erro interno")))
        } catch (ex: IllegalArgumentException) {
            log.warn("GET /internal/admin/llm-judge/metrics -> 400 | error={}", ex.message)
            ResponseEntity.badRequest().body(mapOf("error" to ex.message))
        } catch (ex: Exception) {
            log.error("GET /internal/admin/llm-judge/metrics -> 500 | error={}", ex.message, ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (ex.message ?: "erro interno")))
        }
    }
}
