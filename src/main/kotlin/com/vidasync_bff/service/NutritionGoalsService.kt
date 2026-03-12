package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.request.UpsertNutritionGoalsRequest
import com.vidasync_bff.dto.response.DailyNutritionGoalsResponse
import com.vidasync_bff.dto.response.SupabaseMealRow
import com.vidasync_bff.dto.response.SupabaseNutritionGoalsRow
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.math.round

@Service
class NutritionGoalsService(
    private val supabaseClient: SupabaseClient
) {

    private val log = LoggerFactory.getLogger(NutritionGoalsService::class.java)
    private val goalsTable = "daily_nutrition_goals"
    private val goalsTypeRef = object : ParameterizedTypeReference<List<SupabaseNutritionGoalsRow>>() {}
    private val mealsTypeRef = object : ParameterizedTypeReference<List<SupabaseMealRow>>() {}

    fun getDay(userId: String, date: String?): DailyNutritionGoalsResponse? {
        val resolvedDate = resolveDate(date)
        log.info("Buscando metas nutricionais do dia: userId={}, date={}", userId, resolvedDate)

        val row = getGoalRow(userId, resolvedDate) ?: return null
        return buildResponse(userId, row)
    }

    fun upsert(userId: String, request: UpsertNutritionGoalsRequest): DailyNutritionGoalsResponse {
        validateRequest(request)
        val resolvedDate = resolveDate(request.date)

        log.info(
            "Atualizando metas nutricionais: userId={}, date={}, caloriesGoal={}, proteinGoal={}, carbsGoal={}, fatGoal={}",
            userId, resolvedDate, request.caloriesGoal, request.proteinGoal, request.carbsGoal, request.fatGoal
        )

        val existing = getGoalRow(userId, resolvedDate)
        val savedRow = if (existing == null) {
            val body = mutableMapOf<String, Any>(
                "user_id" to userId,
                "date" to resolvedDate
            )
            request.caloriesGoal?.let { body["calories_goal"] = it }
            request.proteinGoal?.let { body["protein_goal"] = it }
            request.carbsGoal?.let { body["carbs_goal"] = it }
            request.fatGoal?.let { body["fat_goal"] = it }

            val rows = supabaseClient.post(goalsTable, body, goalsTypeRef)
                ?: throw RuntimeException("Nao foi possivel salvar metas nutricionais")
            rows.firstOrNull() ?: throw RuntimeException("Resposta vazia ao salvar metas nutricionais")
        } else {
            val body = mutableMapOf<String, Any>()
            request.caloriesGoal?.let { body["calories_goal"] = it }
            request.proteinGoal?.let { body["protein_goal"] = it }
            request.carbsGoal?.let { body["carbs_goal"] = it }
            request.fatGoal?.let { body["fat_goal"] = it }

            if (body.isEmpty()) {
                existing
            } else {
                val rows = supabaseClient.patch(
                    goalsTable,
                    mapOf("user_id" to "eq.$userId", "date" to "eq.$resolvedDate"),
                    body,
                    goalsTypeRef
                ) ?: throw RuntimeException("Nao foi possivel atualizar metas nutricionais")
                rows.firstOrNull() ?: throw RuntimeException("Resposta vazia ao atualizar metas nutricionais")
            }
        }

        return buildResponse(userId, savedRow)
    }

    private fun buildResponse(userId: String, goalsRow: SupabaseNutritionGoalsRow): DailyNutritionGoalsResponse {
        val meals = getMealsByDate(userId, goalsRow.date)

        val consumedCalories = roundToOneDecimal(meals.sumOf { extractNumber(it.calories) })
        val consumedProtein = roundToOneDecimal(meals.sumOf { extractNumber(it.protein) })
        val consumedCarbs = roundToOneDecimal(meals.sumOf { extractNumber(it.carbs) })
        val consumedFat = roundToOneDecimal(meals.sumOf { extractNumber(it.fat) })

        return DailyNutritionGoalsResponse.from(
            goalsRow,
            consumedCalories = consumedCalories,
            consumedProtein = consumedProtein,
            consumedCarbs = consumedCarbs,
            consumedFat = consumedFat
        )
    }

    private fun getMealsByDate(userId: String, date: String): List<SupabaseMealRow> {
        return supabaseClient.get(
            "meals",
            mapOf(
                "user_id" to "eq.$userId",
                "date" to "eq.$date"
            ),
            mealsTypeRef
        ) ?: emptyList()
    }

    private fun getGoalRow(userId: String, date: String): SupabaseNutritionGoalsRow? {
        val rows = supabaseClient.get(
            goalsTable,
            mapOf(
                "user_id" to "eq.$userId",
                "date" to "eq.$date",
                "limit" to "1"
            ),
            goalsTypeRef
        ) ?: emptyList()

        return rows.firstOrNull()
    }

    private fun validateRequest(request: UpsertNutritionGoalsRequest) {
        if (
            request.caloriesGoal == null &&
            request.proteinGoal == null &&
            request.carbsGoal == null &&
            request.fatGoal == null
        ) {
            throw IllegalArgumentException("Informe pelo menos uma meta no POST /nutrition-goals")
        }

        if (request.caloriesGoal != null && request.caloriesGoal < 0) {
            throw IllegalArgumentException("caloriesGoal nao pode ser negativo")
        }
        if (request.proteinGoal != null && request.proteinGoal < 0) {
            throw IllegalArgumentException("proteinGoal nao pode ser negativo")
        }
        if (request.carbsGoal != null && request.carbsGoal < 0) {
            throw IllegalArgumentException("carbsGoal nao pode ser negativo")
        }
        if (request.fatGoal != null && request.fatGoal < 0) {
            throw IllegalArgumentException("fatGoal nao pode ser negativo")
        }
    }

    private fun resolveDate(date: String?): String {
        if (date.isNullOrBlank()) return LocalDate.now().toString()
        return try {
            LocalDate.parse(date).toString()
        } catch (_: Exception) {
            throw IllegalArgumentException("date invalida. Use o formato YYYY-MM-DD")
        }
    }

    private fun extractNumber(value: String?): Double {
        if (value.isNullOrBlank()) return 0.0
        val normalized = value.replace(",", ".")
        return Regex("-?\\d+(?:\\.\\d+)?").find(normalized)?.value?.toDoubleOrNull() ?: 0.0
    }

    private fun roundToOneDecimal(value: Double): Double {
        return round(value * 10.0) / 10.0
    }
}
