package com.vidasync_bff.controller

import com.vidasync_bff.dto.request.CreateFavoriteRequest
import com.vidasync_bff.service.FavoriteService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/favorites")
class FavoriteController(private val favoriteService: FavoriteService) {

    private val log = LoggerFactory.getLogger(FavoriteController::class.java)

    @PostMapping
    fun create(@RequestBody request: CreateFavoriteRequest): ResponseEntity<Any> {
        log.info("POST /favorites | foods={}, hasNutrition={}, hasImage={}",
            request.foods, request.nutrition != null, request.image != null)
        return try {
            val result = favoriteService.create(request)
            log.info("POST /favorites → 201 | id={}, imageUrl={}", result.id, result.imageUrl)
            ResponseEntity.status(HttpStatus.CREATED).body(mapOf("favorite" to result))
        } catch (e: Exception) {
            log.error("POST /favorites → 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @GetMapping
    fun getAll(): ResponseEntity<Any> {
        log.info("GET /favorites")
        return try {
            val result = favoriteService.getAll()
            log.info("GET /favorites → 200 | count={}", result.size)
            ResponseEntity.ok(mapOf("favorites" to result))
        } catch (e: Exception) {
            log.error("GET /favorites → 500 | error={}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Any> {
        log.info("DELETE /favorites/{}", id)
        return try {
            favoriteService.delete(id)
            log.info("DELETE /favorites/{} → 200 | deleted", id)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            log.error("DELETE /favorites/{} → 500 | error={}", id, e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }
}
