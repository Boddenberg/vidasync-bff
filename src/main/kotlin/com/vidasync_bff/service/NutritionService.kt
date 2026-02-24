package com.vidasync_bff.service

import com.openai.client.OpenAIClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NutritionService(private val openAIClient: OpenAIClient) {

    private val log = LoggerFactory.getLogger(NutritionService::class.java)

    fun calculateCalories(foodDescription: String): String {
        log.info("Calculando calorias para: {}", foodDescription)

        val params = ChatCompletionCreateParams.builder()
            .model(ChatModel.GPT_4O_MINI)
            .addSystemMessage(SYSTEM_PROMPT)
            .addUserMessage(foodDescription)
            .build()

        val response = openAIClient.chat().completions().create(params)
        val result = response.choices().firstOrNull()?.message()?.content()?.orElse(FALLBACK_MESSAGE)
            ?: FALLBACK_MESSAGE

        log.info("Cálculo finalizado com sucesso")
        return result
    }

    companion object {
        private const val FALLBACK_MESSAGE = "Não foi possível calcular."

        private val SYSTEM_PROMPT = """
            Você é um calculador de calorias. Responda APENAS no seguinte formato, sem explicações extras:
            
            alimento1: X kcal
            alimento2: X kcal
            Total: X kcal
            
            Exemplo:
            2 ovos mexidos: 180 kcal
            1 banana: 90 kcal
            Total: 270 kcal
        """.trimIndent()
    }
}
