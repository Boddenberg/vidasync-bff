package com.vidasync_bff.controller

import com.vidasync_bff.service.InternalAdminUserCloneService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/internal/admin/users")
class InternalAdminUsersController(
    private val cloneService: InternalAdminUserCloneService
) {

    private val log = LoggerFactory.getLogger(InternalAdminUsersController::class.java)

    @PostMapping("/{id}/clone")
    fun cloneUser(
        @PathVariable("id") sourceUserId: String,
        @RequestParam(name = "dry_run", defaultValue = "true") dryRun: Boolean,
        @RequestHeader("X-User-Id") clonedBy: String,
        @RequestHeader("X-Internal-Api-Key", required = false) internalApiKey: String?
    ): ResponseEntity<Any> {
        log.info(
            "POST /internal/admin/users/{}/clone dry_run={} cloned_by={}",
            sourceUserId, dryRun, clonedBy
        )
        return try {
            val result = cloneService.cloneUser(
                sourceUserId = sourceUserId,
                dryRun = dryRun,
                clonedBy = clonedBy,
                providedInternalApiKey = internalApiKey
            )
            val status = if (dryRun) HttpStatus.OK else HttpStatus.CREATED
            log.info(
                "POST /internal/admin/users/{}/clone -> {} cloned_user_id={}",
                sourceUserId, status.value(), result.clonedUserId
            )
            ResponseEntity.status(status).body(result)
        } catch (ex: ResponseStatusException) {
            log.warn(
                "POST /internal/admin/users/{}/clone -> {} error={}",
                sourceUserId, ex.statusCode.value(), ex.reason
            )
            ResponseEntity.status(ex.statusCode).body(mapOf("error" to (ex.reason ?: "erro interno")))
        } catch (ex: Exception) {
            log.error(
                "POST /internal/admin/users/{}/clone -> 500 error={}",
                sourceUserId, ex.message, ex
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (ex.message ?: "erro interno")))
        }
    }
}
