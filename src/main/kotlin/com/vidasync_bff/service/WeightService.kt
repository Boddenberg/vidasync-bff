package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.request.CreateWeightRequest
import com.vidasync_bff.dto.response.SupabaseWeightRow
import com.vidasync_bff.dto.response.WeightEntryResponse
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service

@Service
class WeightService(
    private val supabaseClient: SupabaseClient
) {

    private val log = LoggerFactory.getLogger(WeightService::class.java)
    private val tableName = "weight_entries"
    private val weightTypeRef = object : ParameterizedTypeReference<List<SupabaseWeightRow>>() {}

    fun create(userId: String, request: CreateWeightRequest): WeightEntryResponse {
        validateRequest(request)

        log.info("Salvando peso: userId={}, weightKg={}", userId, request.weightKg)

        val rows = supabaseClient.post(
            tableName,
            mapOf(
                "user_id" to userId,
                "weight_kg" to request.weightKg
            ),
            weightTypeRef
        ) ?: throw RuntimeException("Nao foi possivel salvar o peso")

        val saved = rows.firstOrNull() ?: throw RuntimeException("Resposta vazia ao salvar o peso")
        return WeightEntryResponse.from(saved)
    }

    fun getAll(userId: String): List<WeightEntryResponse> {
        log.info("Buscando historico de peso: userId={}", userId)

        val rows = supabaseClient.get(
            tableName,
            mapOf(
                "user_id" to "eq.$userId",
                "order" to "measured_at.asc,id.asc"
            ),
            weightTypeRef
        ) ?: emptyList()

        return rows.map(WeightEntryResponse::from)
    }

    private fun validateRequest(request: CreateWeightRequest) {
        if (request.weightKg <= 0.0) {
            throw IllegalArgumentException("weightKg deve ser maior que zero")
        }
    }
}
