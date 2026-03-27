package com.vidasync_bff.controller

import com.vidasync_bff.service.TelemetryService
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
@RequestMapping("/internal/admin/telemetry")
class InternalAdminTelemetryController(
    private val telemetryService: TelemetryService
) {

    private val log = LoggerFactory.getLogger(InternalAdminTelemetryController::class.java)

    @GetMapping("/metrics")
    fun getMetrics(
        @RequestHeader("X-User-Id") actorUserId: String,
        @RequestHeader("X-Internal-Api-Key", required = false) internalApiKey: String?,
        @RequestParam(required = false) days: Int?,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(required = false) agent: String?
    ): ResponseEntity<Any> {
        log.info(
            "GET /internal/admin/telemetry/metrics | actorUserId={} days={} startDate={} endDate={} agent={}",
            actorUserId,
            days,
            startDate,
            endDate,
            agent
        )

        return try {
            val result = telemetryService.getMetrics(
                actorUserId = actorUserId,
                providedInternalApiKey = internalApiKey,
                days = days,
                startDate = startDate,
                endDate = endDate,
                agent = agent
            )
            ResponseEntity.ok(mapOf("metrics" to result))
        } catch (ex: ResponseStatusException) {
            log.warn(
                "GET /internal/admin/telemetry/metrics -> {} | error={}",
                ex.statusCode.value(),
                ex.reason
            )
            ResponseEntity.status(ex.statusCode).body(mapOf("error" to (ex.reason ?: "erro interno")))
        } catch (ex: IllegalArgumentException) {
            log.warn("GET /internal/admin/telemetry/metrics -> 400 | error={}", ex.message)
            ResponseEntity.badRequest().body(mapOf("error" to ex.message))
        } catch (ex: Exception) {
            log.error("GET /internal/admin/telemetry/metrics -> 500 | error={}", ex.message, ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (ex.message ?: "erro interno")))
        }
    }

    @GetMapping("/runs")
    fun getRecentRuns(
        @RequestHeader("X-User-Id") actorUserId: String,
        @RequestHeader("X-Internal-Api-Key", required = false) internalApiKey: String?,
        @RequestParam(required = false) days: Int?,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(required = false) agent: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) limit: Int?
    ): ResponseEntity<Any> {
        log.info(
            "GET /internal/admin/telemetry/runs | actorUserId={} days={} startDate={} endDate={} agent={} status={} limit={}",
            actorUserId,
            days,
            startDate,
            endDate,
            agent,
            status,
            limit
        )

        return try {
            val result = telemetryService.getRecentRuns(
                actorUserId = actorUserId,
                providedInternalApiKey = internalApiKey,
                days = days,
                startDate = startDate,
                endDate = endDate,
                agent = agent,
                status = status,
                limit = limit
            )
            ResponseEntity.ok(mapOf("runs" to result))
        } catch (ex: ResponseStatusException) {
            log.warn(
                "GET /internal/admin/telemetry/runs -> {} | error={}",
                ex.statusCode.value(),
                ex.reason
            )
            ResponseEntity.status(ex.statusCode).body(mapOf("error" to (ex.reason ?: "erro interno")))
        } catch (ex: IllegalArgumentException) {
            log.warn("GET /internal/admin/telemetry/runs -> 400 | error={}", ex.message)
            ResponseEntity.badRequest().body(mapOf("error" to ex.message))
        } catch (ex: Exception) {
            log.error("GET /internal/admin/telemetry/runs -> 500 | error={}", ex.message, ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (ex.message ?: "erro interno")))
        }
    }
}
