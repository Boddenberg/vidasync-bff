package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.client.SupabaseStorageClient
import com.vidasync_bff.dto.request.CreateFavoriteRequest
import com.vidasync_bff.dto.response.FavoriteResponse
import com.vidasync_bff.dto.response.SupabaseFavoriteRow
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service

@Service
class FavoriteService(
    private val supabaseClient: SupabaseClient,
    private val storageClient: SupabaseStorageClient
) {

    private val log = LoggerFactory.getLogger(FavoriteService::class.java)

    fun create(request: CreateFavoriteRequest): FavoriteResponse {
        log.info("Criando favorito: {}", request.foods)

        // Upload image if provided
        val imageUrl = request.image?.takeIf { it.isNotBlank() }?.let { base64 ->
            try {
                storageClient.uploadBase64Image(base64, "fav")
            } catch (e: Exception) {
                log.error("Erro ao fazer upload da imagem: {}", e.message, e)
                null
            }
        }

        val body = mutableMapOf<String, Any>(
            "foods" to request.foods,
            "calories" to (request.nutrition?.calories ?: ""),
            "protein" to (request.nutrition?.protein ?: ""),
            "carbs" to (request.nutrition?.carbs ?: ""),
            "fat" to (request.nutrition?.fat ?: "")
        )
        imageUrl?.let { body["image_url"] = it }

        val rows = supabaseClient.post(
            "favorite_meals", body,
            object : ParameterizedTypeReference<List<SupabaseFavoriteRow>>() {}
        )

        return FavoriteResponse.from(rows!!.first())
    }

    fun getAll(): List<FavoriteResponse> {
        log.info("Buscando favoritos")

        val rows = supabaseClient.get(
            "favorite_meals",
            mapOf("order" to "created_at.desc"),
            object : ParameterizedTypeReference<List<SupabaseFavoriteRow>>() {}
        ) ?: emptyList()

        return rows.map { FavoriteResponse.from(it) }
    }

    fun delete(id: String) {
        log.info("Deletando favorito: {}", id)
        supabaseClient.delete("favorite_meals", mapOf("id" to "eq.$id"))
    }
}
