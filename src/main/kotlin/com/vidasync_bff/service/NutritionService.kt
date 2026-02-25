package com.vidasync_bff.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.openai.client.OpenAIClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.vidasync_bff.dto.response.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@Service
class NutritionService(
    private val openAIClient: OpenAIClient,
    private val cacheService: IngredientCacheService
) {

    private val log = LoggerFactory.getLogger(NutritionService::class.java)
    private val mapper = jacksonObjectMapper()

    /**
     * Método principal com cache, validação e correção de unidades.
     * Usado pelo NutritionController.
     */
    fun calculateNutritionSmart(foodDescription: String): CalorieResponse {
        log.info("=== Smart Nutrition: '{}' ===", foodDescription)

        // 1. Separar ingredientes por vírgula
        val rawIngredients = foodDescription
            .split(",", "+", " e ", " com ")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (rawIngredients.isEmpty()) {
            return CalorieResponse(error = "Nenhum alimento informado")
        }

        log.info("Ingredientes identificados: {}", rawIngredients)

        // 2. Normalizar keys e fazer lookup no cache
        val keyToOriginal = rawIngredients.associateBy { cacheService.normalizeKey(it) }
        val cacheHits = cacheService.lookupBatch(keyToOriginal.keys.toList())

        val hits = mutableListOf<Pair<String, IngredientCacheRow>>()
        val misses = mutableListOf<Pair<String, String>>() // key → original

        for ((key, original) in keyToOriginal) {
            val cached = cacheHits[key]
            if (cached != null) {
                log.info("CACHE HIT: '{}' → calories={}", key, cached.calories)
                hits.add(original to cached)
            } else {
                log.info("CACHE MISS: '{}'", key)
                misses.add(key to original)
            }
        }

        // 3. Chamar OpenAI para os misses em paralelo (batches de até 5)
        val newResults = mutableListOf<IngredientCacheRow>()
        if (misses.isNotEmpty()) {
            val batches = misses.chunked(5)
            log.info("Chamando OpenAI para {} ingredientes em {} batch(es)", misses.size, batches.size)

            val executor = Executors.newVirtualThreadPerTaskExecutor()
            val futures = mutableListOf<Future<List<IngredientCacheRow>>>()

            for (batch in batches) {
                futures.add(executor.submit<List<IngredientCacheRow>> {
                    callOpenAIForIngredients(batch)
                })
            }

            for (future in futures) {
                try {
                    newResults.addAll(future.get(30, TimeUnit.SECONDS))
                } catch (e: Exception) {
                    log.error("Erro ao processar batch OpenAI: {}", e.message, e)
                }
            }
            executor.shutdown()

            // 4. Salvar novos resultados no cache
            cacheService.saveBatch(newResults)
        }

        // 5. Montar resposta final
        val allIngredients = mutableListOf<IngredientDetail>()
        val corrections = mutableListOf<UnitCorrection>()
        val invalidItems = mutableListOf<String>()

        // Resultados do cache
        for ((original, cached) in hits) {
            if (!cached.isValidFood) {
                invalidItems.add(original)
            } else {
                allIngredients.add(
                    IngredientDetail(
                        name = cached.correctedInput ?: original,
                        nutrition = NutritionData(cached.calories, cached.protein, cached.carbs, cached.fat),
                        cached = true
                    )
                )
                if (cached.correctedInput != null && cached.correctedInput != cached.originalInput) {
                    corrections.add(UnitCorrection(original = original, corrected = cached.correctedInput))
                }
            }
        }

        // Resultados novos da OpenAI
        for (result in newResults) {
            if (!result.isValidFood) {
                invalidItems.add(result.originalInput)
            } else {
                allIngredients.add(
                    IngredientDetail(
                        name = result.correctedInput ?: result.originalInput,
                        nutrition = NutritionData(result.calories, result.protein, result.carbs, result.fat),
                        cached = false
                    )
                )
                if (result.correctedInput != null && result.correctedInput != result.originalInput) {
                    corrections.add(UnitCorrection(original = result.originalInput, corrected = result.correctedInput))
                }
            }
        }

        // Se QUALQUER item for inválido → rejeita tudo
        if (invalidItems.isNotEmpty()) {
            log.warn("Itens inválidos encontrados: {} → rejeitando tudo", invalidItems)
            return CalorieResponse(
                nutrition = null,
                invalidItems = invalidItems
            )
        }

        // Somar macros
        val totalNutrition = sumNutrition(allIngredients.map { it.nutrition })

        log.info("Smart Nutrition concluído: {} ingredientes válidos, {} inválidos, {} correções, {} do cache",
            allIngredients.size, invalidItems.size, corrections.size, allIngredients.count { it.cached })

        return CalorieResponse(
            nutrition = totalNutrition,
            ingredients = allIngredients,
            corrections = corrections.ifEmpty { null },
            invalidItems = invalidItems.ifEmpty { null }
        )
    }

    /**
     * Método simples (retrocompatível) — usado pelo MealService quando não tem nutrition.
     */
    fun calculateNutrition(foodDescription: String): NutritionData {
        val result = calculateNutritionSmart(foodDescription)
        return result.nutrition ?: NutritionData("0 kcal", "0g", "0g", "0g")
    }

    // ======================
    // OpenAI
    // ======================

    private fun callOpenAIForIngredients(ingredients: List<Pair<String, String>>): List<IngredientCacheRow> {
        val foodsList = ingredients.joinToString("\n") { (_, original) -> "- $original" }
        log.info("OpenAI request para {} ingredientes:\n{}", ingredients.size, foodsList)

        val params = ChatCompletionCreateParams.builder()
            .model(ChatModel.GPT_4O_MINI)
            .addSystemMessage(SMART_SYSTEM_PROMPT)
            .addUserMessage(foodsList)
            .build()

        val response = openAIClient.chat().completions().create(params)
        val raw = response.choices().firstOrNull()?.message()?.content()?.orElse("[]") ?: "[]"
        log.info("OpenAI response: {}", raw)

        return try {
            val parsed: List<OpenAIIngredientResponse> = mapper.readValue(extractJsonArray(raw))

            parsed.mapIndexed { index, item ->
                val key = if (index < ingredients.size) ingredients[index].first
                          else cacheService.normalizeKey(item.ingredient)
                val original = if (index < ingredients.size) ingredients[index].second
                               else item.ingredient

                IngredientCacheRow(
                    ingredientKey = key,
                    originalInput = original,
                    correctedInput = item.correctedInput,
                    calories = item.calories,
                    protein = item.protein,
                    carbs = item.carbs,
                    fat = item.fat,
                    isValidFood = item.isValidFood
                )
            }
        } catch (e: Exception) {
            log.error("Erro ao parsear resposta OpenAI: {}", e.message, e)
            // Fallback: método legado para cada ingrediente
            ingredients.map { (key, original) ->
                try {
                    val fallback = calculateNutritionLegacy(original)
                    IngredientCacheRow(
                        ingredientKey = key,
                        originalInput = original,
                        correctedInput = original,
                        calories = fallback.calories,
                        protein = fallback.protein,
                        carbs = fallback.carbs,
                        fat = fallback.fat,
                        isValidFood = true
                    )
                } catch (ex: Exception) {
                    log.error("Fallback também falhou para '{}': {}", original, ex.message)
                    IngredientCacheRow(
                        ingredientKey = key,
                        originalInput = original,
                        correctedInput = original,
                        calories = "0 kcal",
                        protein = "0g",
                        carbs = "0g",
                        fat = "0g",
                        isValidFood = true
                    )
                }
            }
        }
    }

    private fun calculateNutritionLegacy(foodDescription: String): NutritionData {
        val params = ChatCompletionCreateParams.builder()
            .model(ChatModel.GPT_4O_MINI)
            .addSystemMessage(LEGACY_SYSTEM_PROMPT)
            .addUserMessage(foodDescription)
            .build()

        val response = openAIClient.chat().completions().create(params)
        val raw = response.choices().firstOrNull()?.message()?.content()?.orElse("") ?: ""
        return parseNutritionLegacy(raw)
    }

    private fun parseNutritionLegacy(raw: String): NutritionData {
        val lines = raw.lines().associate { line ->
            val parts = line.split(":", limit = 2)
            parts[0].trim().lowercase() to parts.getOrElse(1) { "0" }.trim()
        }
        return NutritionData(
            calories = lines["calories"] ?: "0 kcal",
            protein = lines["protein"] ?: "0g",
            carbs = lines["carbs"] ?: "0g",
            fat = lines["fat"] ?: "0g"
        )
    }

    // ======================
    // Helpers
    // ======================

    private fun extractJsonArray(raw: String): String {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        return if (start != -1 && end != -1 && end > start) {
            raw.substring(start, end + 1)
        } else {
            raw
        }
    }

    private fun sumNutrition(items: List<NutritionData>): NutritionData {
        var totalCal = 0.0
        var totalPro = 0.0
        var totalCarb = 0.0
        var totalFat = 0.0

        for (item in items) {
            totalCal += extractNumber(item.calories)
            totalPro += extractNumber(item.protein)
            totalCarb += extractNumber(item.carbs)
            totalFat += extractNumber(item.fat)
        }

        return NutritionData(
            calories = "${formatNumber(totalCal)} kcal",
            protein = "${formatNumber(totalPro)}g",
            carbs = "${formatNumber(totalCarb)}g",
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

    // ======================
    // Prompts
    // ======================

    companion object {
        private val SMART_SYSTEM_PROMPT = """
            Você é um nutricionista profissional. Receba uma lista de alimentos e retorne APENAS um JSON array.

            Para CADA alimento na lista, retorne um objeto com:
            - "ingredient": o alimento exatamente como foi escrito
            - "corrected_input": a forma correta (ex: "250ml de arroz" → "250g de arroz", pois arroz se mede em gramas). Se já estiver correto, repita o original.
            - "is_valid_food": true se é um alimento real, false se NÃO é comestível (ex: "cadeira", "mesa", "celular")
            - "calories": "X kcal" (para a quantidade informada)
            - "protein": "Xg"
            - "carbs": "Xg"
            - "fat": "Xg"

            Se is_valid_food for false, coloque "0 kcal", "0g", "0g", "0g" nos macros.

            REGRAS IMPORTANTES:
            1. Arroz, feijão, farinhas → SEMPRE gramas, nunca ml
            2. Leite, sucos, água → ml está correto
            3. Se o item não for comestível, is_valid_food = false
            4. Responda APENAS o JSON array, sem texto extra, sem markdown

            Exemplo de resposta:
            [
              {"ingredient": "200g de arroz", "corrected_input": "200g de arroz", "is_valid_food": true, "calories": "260 kcal", "protein": "5g", "carbs": "57g", "fat": "0.5g"},
              {"ingredient": "100g de cadeira", "corrected_input": "100g de cadeira", "is_valid_food": false, "calories": "0 kcal", "protein": "0g", "carbs": "0g", "fat": "0g"}
            ]
        """.trimIndent()

        private val LEGACY_SYSTEM_PROMPT = """
            Você é um calculador nutricional. Some todos os alimentos informados e responda APENAS neste formato exato, sem mais nada:
            calories: X kcal
            protein: Xg
            carbs: Xg
            fat: Xg
        """.trimIndent()
    }

    // DTO interno para parsear resposta da OpenAI
    data class OpenAIIngredientResponse(
        val ingredient: String = "",
        val corrected_input: String? = null,
        val is_valid_food: Boolean = true,
        val calories: String = "0 kcal",
        val protein: String = "0g",
        val carbs: String = "0g",
        val fat: String = "0g"
    ) {
        val correctedInput: String? get() = corrected_input
        val isValidFood: Boolean get() = is_valid_food
    }
}
