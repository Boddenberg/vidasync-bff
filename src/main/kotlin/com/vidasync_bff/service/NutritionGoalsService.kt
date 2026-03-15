package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.request.UpsertNutritionGoalsRequest
import com.vidasync_bff.dto.response.DailyNutritionGoalsResponse
import com.vidasync_bff.dto.response.NutritionGoalTargets
import com.vidasync_bff.dto.response.SupabaseNutritionGoalsRow
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class NutritionGoalsService(
    private val supabaseClient: SupabaseClient
) {

    private val log = LoggerFactory.getLogger(NutritionGoalsService::class.java)
    private val goalsTable = "daily_nutrition_goals"
    private val goalsTypeRef = object : ParameterizedTypeReference<List<SupabaseNutritionGoalsRow>>() {}

    fun getDay(userId: String, date: String?): DailyNutritionGoalsResponse? {
        val resolvedDate = resolveDate(date)
        log.info("Buscando metas nutricionais do dia: userId={}, date={}", userId, resolvedDate)

        return buildResponse(userId, resolvedDate)
    }

    fun upsert(userId: String, request: UpsertNutritionGoalsRequest): DailyNutritionGoalsResponse {
        validateRequest(request)
        val resolvedDate = resolveDate(request.date)

        log.info(
            "Atualizando metas nutricionais: userId={}, date={}, caloriesGoal={}, proteinGoal={}, carbsGoal={}, fatGoal={}",
            userId, resolvedDate, request.caloriesGoal, request.proteinGoal, request.carbsGoal, request.fatGoal
        )

        val existing = getGoalRow(userId, resolvedDate)
        val effectiveGoals = resolveEffectiveGoals(userId, resolvedDate)
        val mergedGoals = NutritionGoalTargets(
            calories = request.caloriesGoal ?: effectiveGoals.calories,
            protein = request.proteinGoal ?: effectiveGoals.protein,
            carbs = request.carbsGoal ?: effectiveGoals.carbs,
            fat = request.fatGoal ?: effectiveGoals.fat
        )

        if (!hasAnyGoal(mergedGoals)) {
            throw IllegalArgumentException("Informe pelo menos uma meta no POST /nutrition-goals")
        }

        if (existing == null) {
            val body = mutableMapOf<String, Any>(
                "user_id" to userId,
                "date" to resolvedDate
            )
            applyGoalFields(body, mergedGoals)

            val rows = supabaseClient.post(goalsTable, body, goalsTypeRef)
                ?: throw RuntimeException("Nao foi possivel salvar metas nutricionais")
            rows.firstOrNull() ?: throw RuntimeException("Resposta vazia ao salvar metas nutricionais")
        } else {
            val body = mutableMapOf<String, Any>()
            applyGoalFields(body, mergedGoals)

            val rows = supabaseClient.patch(
                goalsTable,
                mapOf("user_id" to "eq.$userId", "date" to "eq.$resolvedDate"),
                body,
                goalsTypeRef
            ) ?: throw RuntimeException("Nao foi possivel atualizar metas nutricionais")
            rows.firstOrNull() ?: throw RuntimeException("Resposta vazia ao atualizar metas nutricionais")
        }

        return buildResponse(userId, resolvedDate)
            ?: throw RuntimeException("Nao foi possivel montar a resposta de metas nutricionais apos salvar")
    }

    private fun buildResponse(userId: String, date: String): DailyNutritionGoalsResponse? {
        val goalsRow = getGoalRow(userId, date)
        val effectiveGoals = resolveEffectiveGoals(userId, date)
        if (!hasAnyGoal(effectiveGoals)) {
            return null
        }

        return DailyNutritionGoalsResponse.from(
            date = date,
            row = goalsRow,
            goals = effectiveGoals,
            goalInherited = goalsRow == null
        )
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

    private fun resolveEffectiveGoals(userId: String, date: String): NutritionGoalTargets {
        return NutritionGoalTargets(
            calories = getLatestGoalValueOnOrBefore(userId, date, "calories_goal") { it.caloriesGoal },
            protein = getLatestGoalValueOnOrBefore(userId, date, "protein_goal") { it.proteinGoal },
            carbs = getLatestGoalValueOnOrBefore(userId, date, "carbs_goal") { it.carbsGoal },
            fat = getLatestGoalValueOnOrBefore(userId, date, "fat_goal") { it.fatGoal }
        )
    }

    private fun getLatestGoalValueOnOrBefore(
        userId: String,
        date: String,
        fieldName: String,
        extractor: (SupabaseNutritionGoalsRow) -> Int?
    ): Int? {
        val rows = supabaseClient.get(
            goalsTable,
            mapOf(
                "user_id" to "eq.$userId",
                "date" to "lte.$date",
                fieldName to "not.is.null",
                "order" to "date.desc",
                "limit" to "1"
            ),
            goalsTypeRef
        ) ?: emptyList()

        return rows.firstOrNull()?.let(extractor)
    }

    private fun applyGoalFields(
        body: MutableMap<String, Any>,
        goals: NutritionGoalTargets
    ) {
        goals.calories?.let { body["calories_goal"] = it }
        goals.protein?.let { body["protein_goal"] = it }
        goals.carbs?.let { body["carbs_goal"] = it }
        goals.fat?.let { body["fat_goal"] = it }
    }

    private fun hasAnyGoal(goals: NutritionGoalTargets): Boolean {
        return goals.calories != null || goals.protein != null || goals.carbs != null || goals.fat != null
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
}
