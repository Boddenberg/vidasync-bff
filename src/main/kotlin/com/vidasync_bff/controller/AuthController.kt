package com.vidasync_bff.controller

import com.vidasync_bff.dto.request.AuthRequest
import com.vidasync_bff.dto.request.UpdatePasswordRequest
import com.vidasync_bff.dto.request.UpdateProfileRequest
import com.vidasync_bff.dto.request.UpdateUsernameRequest
import com.vidasync_bff.service.AuthService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(private val authService: AuthService) {

    private val log = LoggerFactory.getLogger(AuthController::class.java)

    @PostMapping("/signup")
    fun signup(@RequestBody request: AuthRequest): ResponseEntity<Any> {
        log.info("POST /auth/signup | username={}", request.username)
        return try {
            val result = authService.signup(request)
            log.info("POST /auth/signup -> 201 | userId={}, username={}", result.userId, result.username)
            ResponseEntity.status(HttpStatus.CREATED).body(result)
        } catch (e: Exception) {
            log.error("POST /auth/signup -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/login")
    fun login(@RequestBody request: AuthRequest): ResponseEntity<Any> {
        log.info("POST /auth/login | username={}", request.username)
        return try {
            val result = authService.login(request)
            log.info("POST /auth/login -> 200 | userId={}, username={}", result.userId, result.username)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            log.error("POST /auth/login -> 401 | error={}", e.message)
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/profile")
    fun getProfile(@RequestHeader("X-User-Id") userId: String): ResponseEntity<Any> {
        log.info("GET /auth/profile | userId={}", userId)
        return try {
            val result = authService.getProfile(userId)
            log.info("GET /auth/profile -> 200 | username={}", result.username)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            log.error("GET /auth/profile -> 404 | error={}", e.message)
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        }
    }

    @PutMapping("/profile")
    fun updateProfile(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody request: UpdateProfileRequest
    ): ResponseEntity<Any> {
        log.info(
            "PUT /auth/profile | userId={}, hasUsername={}, hasPassword={}, hasImage={}",
            userId,
            request.username != null,
            request.password != null,
            request.profileImage != null
        )
        return try {
            val result = authService.updateProfile(userId, request)
            log.info("PUT /auth/profile -> 200 | username={}", result.username)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            log.error("PUT /auth/profile -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PutMapping("/profile/username")
    fun changeUsername(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody request: UpdateUsernameRequest
    ): ResponseEntity<Any> {
        log.info("PUT /auth/profile/username | userId={}", userId)
        return try {
            val result = authService.changeUsername(userId, request)
            log.info("PUT /auth/profile/username -> 200 | username={}", result.username)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            log.error("PUT /auth/profile/username -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PutMapping("/profile/password")
    fun changePassword(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody request: UpdatePasswordRequest
    ): ResponseEntity<Any> {
        log.info("PUT /auth/profile/password | userId={}", userId)
        return try {
            authService.changePassword(userId, request)
            log.info("PUT /auth/profile/password -> 200 | password changed")
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            log.error("PUT /auth/profile/password -> 400 | error={}", e.message)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}
