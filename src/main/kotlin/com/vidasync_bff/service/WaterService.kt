package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.request.UpsertWaterRequest
import com.vidasync_bff.dto.response.SupabaseWaterDailyRow
import com.vidasync_bff.dto.response.WaterDailyResponse
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class WaterService(
    private val supabaseClient: SupabaseClient
) {

    private val log = LoggerFactory.getLogger(WaterService::class.java)
    private val tableName = "water_daily_intake"
    private val waterTypeRef = object : ParameterizedTypeReference<List<SupabaseWaterDailyRow>>() {}

    fun getDay(userId: String, date: String?): WaterDailyResponse? {
        val resolvedDate = resolveDate(date)
        log.info("Buscando agua do dia: userId={}, date={}", userId, resolvedDate)

        val row = getRow(userId, resolvedDate) ?: return null
        return WaterDailyResponse.from(row)
    }

    fun upsert(userId: String, request: UpsertWaterRequest): WaterDailyResponse {
        val resolvedDate = resolveDate(request.date)
        validateRequest(request)

        log.info(
            "Atualizando agua do dia: userId={}, date={}, goalMl={}, deltaMl={}",
            userId, resolvedDate, request.goalMl, request.deltaMl
        )

        val existing = getRow(userId, resolvedDate)
        val currentConsumed = existing?.consumedMl ?: 0
        val delta = request.deltaMl ?: 0
        val updatedConsumed = (currentConsumed + delta).coerceAtLeast(0)

        val rows = if (existing == null) {
            val body = mutableMapOf<String, Any>(
                "user_id" to userId,
                "date" to resolvedDate,
                "consumed_ml" to updatedConsumed
            )
            request.goalMl?.let { body["goal_ml"] = it }

            supabaseClient.post(tableName, body, waterTypeRef)
        } else {
            val body = mutableMapOf<String, Any>(
                "consumed_ml" to updatedConsumed
            )
            request.goalMl?.let { body["goal_ml"] = it }

            supabaseClient.patch(
                tableName,
                mapOf("user_id" to "eq.$userId", "date" to "eq.$resolvedDate"),
                body,
                waterTypeRef
            )
        } ?: throw RuntimeException("Nao foi possivel salvar a ingestao de agua")

        val saved = rows.firstOrNull() ?: throw RuntimeException("Resposta vazia ao salvar ingestao de agua")
        return WaterDailyResponse.from(saved)
    }

    private fun validateRequest(request: UpsertWaterRequest) {
        if (request.goalMl == null && request.deltaMl == null) {
            throw IllegalArgumentException("Informe goalMl, deltaMl ou ambos no POST /water")
        }
        if (request.goalMl != null && request.goalMl < 0) {
            throw IllegalArgumentException("goalMl nao pode ser negativo")
        }
    }

    private fun getRow(userId: String, date: String): SupabaseWaterDailyRow? {
        val rows = supabaseClient.get(
            tableName,
            mapOf(
                "user_id" to "eq.$userId",
                "date" to "eq.$date",
                "limit" to "1"
            ),
            waterTypeRef
        ) ?: emptyList()

        return rows.firstOrNull()
    }

    private fun resolveDate(date: String?): String {
        if (date.isNullOrBlank()) return LocalDate.now().toString()

        return try {
            LocalDate.parse(date).toString()
        } catch (_: Exception) {
            throw IllegalArgumentException("date invalida. Use o formato YYYY-MM-DD")
        }
    }
}
