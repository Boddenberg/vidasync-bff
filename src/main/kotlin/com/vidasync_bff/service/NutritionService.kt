package com.vidasync_bff.service

import com.openai.client.OpenAIClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.vidasync_bff.dto.response.NutritionData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NutritionService(private val openAIClient: OpenAIClient) {

    private val log = LoggerFactory.getLogger(NutritionService::class.java)

    fun calculateNutrition(foodDescription: String): NutritionData {
        log.info("Calculando nutrição para: {}", foodDescription)

        val params = ChatCompletionCreateParams.builder()
            .model(ChatModel.GPT_4O_MINI)
            .addSystemMessage(SYSTEM_PROMPT)
            .addUserMessage(foodDescription)
            .build()

        val response = openAIClient.chat().completions().create(params)
        val raw = response.choices().firstOrNull()?.message()?.content()?.orElse("")
            ?: ""

        log.info("Resposta GPT: {}", raw)
        return parseNutrition(raw)
    }

    private fun parseNutrition(raw: String): NutritionData {
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

    companion object {
        private val SYSTEM_PROMPT = """
            Você é um calculador nutricional. Some todos os alimentos informados e responda APENAS neste formato exato, sem mais nada:
            calories: X kcal
            protein: Xg
            carbs: Xg
            fat: Xg
        """.trimIndent()
    }
}
