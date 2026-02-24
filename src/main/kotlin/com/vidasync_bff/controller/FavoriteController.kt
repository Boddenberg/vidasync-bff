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
        return try {
            ResponseEntity.status(HttpStatus.CREATED).body(mapOf("favorite" to favoriteService.create(request)))
        } catch (e: Exception) {
            log.error("Erro ao criar favorito: {}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @GetMapping
    fun getAll(): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(mapOf("favorites" to favoriteService.getAll()))
        } catch (e: Exception) {
            log.error("Erro ao buscar favoritos: {}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Any> {
        return try {
            favoriteService.delete(id)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            log.error("Erro ao deletar favorito: {}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }
}
