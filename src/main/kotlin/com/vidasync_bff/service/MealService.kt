package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.client.SupabaseStorageClient
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
    private val nutritionService: NutritionService,
    private val storageClient: SupabaseStorageClient
) {

    private val log = LoggerFactory.getLogger(MealService::class.java)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val mealImagesBucket = "meal-images"

    fun create(userId: String, request: CreateMealRequest): MealResponse {
        log.info("Criando refeição: userId={}, {} - {}, hasImage={}", userId, request.mealType, request.foods, request.image != null)

        val nutrition = request.nutrition ?: nutritionService.calculateNutrition(request.foods)
        val time = request.time ?: LocalTime.now().format(timeFormatter)

        // Upload image if provided
        val imageUrl = uploadImage(request.image, "meal")

        val body = mutableMapOf<String, Any>(
            "user_id" to userId,
            "meal_type" to request.mealType,
            "foods" to request.foods,
            "date" to request.date,
            "time" to time,
            "calories" to nutrition.calories,
            "protein" to nutrition.protein,
            "carbs" to nutrition.carbs,
            "fat" to nutrition.fat
        )
        imageUrl?.let { body["image_url"] = it }

        val rows = supabaseClient.post(
            "meals", body,
            object : ParameterizedTypeReference<List<SupabaseMealRow>>() {}
        )

        return MealResponse.from(rows!!.first())
    }

    fun getByDate(userId: String, date: String): List<MealResponse> {
        log.info("Buscando refeições: userId={}, date={}", userId, date)

        val rows = supabaseClient.get(
            "meals",
            mapOf(
                "user_id" to "eq.$userId",
                "date" to "eq.$date",
                "order" to "time.asc"
            ),
            object : ParameterizedTypeReference<List<SupabaseMealRow>>() {}
        ) ?: emptyList()

        return rows.map { MealResponse.from(it) }
    }

    fun getByDateRange(userId: String, startDate: String, endDate: String): List<MealResponse> {
        log.info("Buscando refeições: userId={}, de {} a {}", userId, startDate, endDate)

        val rows = supabaseClient.get(
            "meals",
            mapOf(
                "user_id" to "eq.$userId",
                "and" to "(date.gte.$startDate,date.lte.$endDate)",
                "order" to "date.asc,time.asc"
            ),
            object : ParameterizedTypeReference<List<SupabaseMealRow>>() {}
        ) ?: emptyList()

        return rows.map { MealResponse.from(it) }
    }

    fun getDaySummary(userId: String, date: String): DaySummaryResponse {
        log.info("Gerando resumo do dia: userId={}, date={}", userId, date)

        val meals = getByDate(userId, date)
        val totals = sumNutrition(meals)

        return DaySummaryResponse(
            date = date,
            meals = meals,
            totalMeals = meals.size,
            totals = totals
        )
    }

    fun update(userId: String, id: String, request: UpdateMealRequest): MealResponse {
        log.info("Atualizando refeição: userId={}, id={}, hasImage={}", userId, id, request.image != null)

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

        // Upload new image if provided
        val imageUrl = uploadImage(request.image, "meal")
        imageUrl?.let { body["image_url"] = it }

        val rows = supabaseClient.patch(
            "meals",
            mapOf("id" to "eq.$id", "user_id" to "eq.$userId"),
            body,
            object : ParameterizedTypeReference<List<SupabaseMealRow>>() {}
        )

        return MealResponse.from(rows!!.first())
    }

    fun delete(userId: String, id: String) {
        log.info("Deletando refeição: userId={}, id={}", userId, id)
        supabaseClient.delete("meals", mapOf("id" to "eq.$id", "user_id" to "eq.$userId"))
    }

    fun duplicate(userId: String, id: String): MealResponse {
        log.info("Duplicando refeição: userId={}, id={}", userId, id)

        val original = supabaseClient.get(
            "meals",
            mapOf("id" to "eq.$id", "user_id" to "eq.$userId"),
            object : ParameterizedTypeReference<List<SupabaseMealRow>>() {}
        )!!.first()

        val body = mutableMapOf<String, Any>(
            "user_id" to userId,
            "meal_type" to original.mealType,
            "foods" to original.foods,
            "date" to original.date,
            "time" to (original.time ?: LocalTime.now().format(timeFormatter)),
            "calories" to (original.calories ?: ""),
            "protein" to (original.protein ?: ""),
            "carbs" to (original.carbs ?: ""),
            "fat" to (original.fat ?: "")
        )
        original.imageUrl?.let { body["image_url"] = it }

        val rows = supabaseClient.post(
            "meals", body,
            object : ParameterizedTypeReference<List<SupabaseMealRow>>() {}
        )

        return MealResponse.from(rows!!.first())
    }

    private fun resolveImageUrl(base64: String?, directUrl: String?): String? {
        return when {
            !base64.isNullOrBlank() -> {
                log.info("Imagem base64 recebida, fazendo upload...")
                uploadImage(base64, "meal")
            }
            !directUrl.isNullOrBlank() -> {
                log.info("imageUrl recebida diretamente: {}", directUrl)
                directUrl
            }
            else -> {
                log.info("Nenhuma imagem fornecida")
                null
            }
        }
    }

    private fun uploadImage(base64: String?, prefix: String): String? {
        if (base64 == null) {
            log.info("Sem imagem para upload (null)")
            return null
        }
        if (base64.isBlank()) {
            log.info("Sem imagem para upload (blank)")
            return null
        }
        log.info("Imagem recebida para upload: prefix={}, bucket={}, base64Length={}, startsWith={}",
            prefix, mealImagesBucket, base64.length, base64.take(30))
        return try {
            val url = storageClient.uploadBase64Image(base64, prefix, mealImagesBucket)
            log.info("Upload de imagem concluído com sucesso: {}", url)
            url
        } catch (e: Exception) {
            log.error("FALHA no upload da imagem: bucket={}, prefix={}, exception={}, message={}",
                mealImagesBucket, prefix, e.javaClass.simpleName, e.message, e)
            null
        }
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
