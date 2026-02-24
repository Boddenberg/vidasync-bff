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
            Você é um calculador de calorias. Some as calorias de todos os alimentos informados e responda APENAS com o total no formato "X kcal". Nada mais. Sem explicações, sem lista, sem quebra de linha.
            
            Exemplos:
            Entrada: 2 ovos mexidos, 1 banana
            Resposta: 270 kcal
            
            Entrada: 1 paçoquinha
            Resposta: 80 kcal
        """.trimIndent()
    }
}
