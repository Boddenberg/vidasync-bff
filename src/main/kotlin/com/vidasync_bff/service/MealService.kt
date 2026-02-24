package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.request.CreateMealRequest
import com.vidasync_bff.dto.request.UpdateMealRequest
import com.vidasync_bff.dto.response.DaySummaryResponse
import com.vidasync_bff.dto.response.MealResponse
import com.vidasync_bff.dto.response.NutritionData
import com.vidasync_bff.dto.response.SupabaseMealRow
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Service
class MealService(
    private val supabaseClient: SupabaseClient,
    private val nutritionService: NutritionService
) {

    private val log = LoggerFactory.getLogger(MealService::class.java)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun create(request: CreateMealRequest): MealResponse {
        log.info("Criando refeição: {} - {}", request.mealType, request.foods)

        val nutrition = request.nutrition ?: nutritionService.calculateNutrition(request.foods)
        val time = request.time ?: LocalTime.now().format(timeFormatter)

        val body = mapOf(
            "meal_type" to request.mealType,
            "foods" to request.foods,
            "date" to request.date,
            "time" to time,
            "calories" to nutrition.calories,
            "protein" to nutrition.protein,
            "carbs" to nutrition.carbs,
            "fat" to nutrition.fat
        )

        val rows = supabaseClient.post(
            "meals", body,
            object : ParameterizedTypeReference<List<SupabaseMealRow>>() {}
        )

        return MealResponse.from(rows!!.first())
    }

    fun getByDate(date: String): List<MealResponse> {
        log.info("Buscando refeições para date: {}", date)

        val rows = supabaseClient.get(
            "meals",
            mapOf(
                "date" to "eq.$date",
                "order" to "time.asc"
            ),
            object : ParameterizedTypeReference<List<SupabaseMealRow>>() {}
        ) ?: emptyList()

        return rows.map { MealResponse.from(it) }
    }

    fun getByDateRange(startDate: String, endDate: String): List<MealResponse> {
        log.info("Buscando refeições de {} a {}", startDate, endDate)

        val rows = supabaseClient.get(
            "meals",
            mapOf(
                "and" to "(date.gte.$startDate,date.lte.$endDate)",
                "order" to "date.asc,time.asc"
            ),
            object : ParameterizedTypeReference<List<SupabaseMealRow>>() {}
        ) ?: emptyList()

        return rows.map { MealResponse.from(it) }
    }

    fun getDaySummary(date: String): DaySummaryResponse {
        log.info("Gerando resumo do dia: {}", date)

        val meals = getByDate(date)
        val totals = sumNutrition(meals)

        return DaySummaryResponse(
            date = date,
            meals = meals,
            totalMeals = meals.size,
            totals = totals
        )
    }

    fun update(id: String, request: UpdateMealRequest): MealResponse {
        log.info("Atualizando refeição: {}", id)

        val body = mutableMapOf<String, Any>()
        request.foods?.let { body["foods"] = it }
        request.mealType?.let { body["meal_type"] = it }
        request.date?.let { body["date"] = it }
        request.time?.let { body["time"] = it }
        request.nutrition?.let {
            body["calories"] = it.calories
            body["protein"] = it.protein
            body["carbs"] = it.carbs
            body["fat"] = it.fat
        }

        val rows = supabaseClient.patch(
            "meals",
            mapOf("id" to "eq.$id"),
            body,
            object : ParameterizedTypeReference<List<SupabaseMealRow>>() {}
        )

        return MealResponse.from(rows!!.first())
    }

    fun delete(id: String) {
        log.info("Deletando refeição: {}", id)
        supabaseClient.delete("meals", mapOf("id" to "eq.$id"))
    }

    fun duplicate(id: String): MealResponse {
        log.info("Duplicando refeição: {}", id)

        val original = supabaseClient.get(
            "meals",
            mapOf("id" to "eq.$id"),
            object : ParameterizedTypeReference<List<SupabaseMealRow>>() {}
        )!!.first()

        val body = mapOf(
            "meal_type" to original.mealType,
            "foods" to original.foods,
            "date" to original.date,
            "time" to (original.time ?: LocalTime.now().format(timeFormatter)),
            "calories" to (original.calories ?: ""),
            "protein" to (original.protein ?: ""),
            "carbs" to (original.carbs ?: ""),
            "fat" to (original.fat ?: "")
        )

        val rows = supabaseClient.post(
            "meals", body,
            object : ParameterizedTypeReference<List<SupabaseMealRow>>() {}
        )

        return MealResponse.from(rows!!.first())
    }

    private fun sumNutrition(meals: List<MealResponse>): NutritionData {
        var totalCalories = 0.0
        var totalProtein = 0.0
        var totalCarbs = 0.0
        var totalFat = 0.0

        for (meal in meals) {
            totalCalories += extractNumber(meal.nutrition.calories)
            totalProtein += extractNumber(meal.nutrition.protein)
            totalCarbs += extractNumber(meal.nutrition.carbs)
            totalFat += extractNumber(meal.nutrition.fat)
        }

        return NutritionData(
            calories = "${formatNumber(totalCalories)} kcal",
            protein = "${formatNumber(totalProtein)}g",
            carbs = "${formatNumber(totalCarbs)}g",
            fat = "${formatNumber(totalFat)}g"
        )
    }

    private fun extractNumber(value: String): Double {
        return Regex("[\\d.]+").find(value)?.value?.toDoubleOrNull() ?: 0.0
    }

    private fun formatNumber(value: Double): String {
        return if (value == value.toLong().toDouble()) value.toLong().toString()
        else "%.1f".format(value)
    }
}

