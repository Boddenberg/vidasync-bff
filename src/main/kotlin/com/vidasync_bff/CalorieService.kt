package com.vidasync_bff

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.ChatModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class CalorieService(
    @Value("\${openai.api-key:}") private val apiKey: String
) {

    private val client: OpenAIClient by lazy {
        if (apiKey.isBlank()) throw IllegalStateException("OPENAI_API_KEY não configurada. Adicione a variável de ambiente.")
        OpenAIOkHttpClient.builder()
            .apiKey(apiKey)
            .build()
    }

    fun calculateCalories(foodDescription: String): String {
        val params = ChatCompletionCreateParams.builder()
            .model(ChatModel.GPT_4O_MINI)
            .addSystemMessage(
                """
                Você é um calculador de calorias. Responda APENAS no seguinte formato, sem explicações extras:
                
                alimento1: X kcal
                alimento2: X kcal
                Total: X kcal
                
                Exemplo:
                2 ovos mexidos: 180 kcal
                1 banana: 90 kcal
                Total: 270 kcal
                """.trimIndent()
            )
            .addUserMessage(foodDescription)
            .build()

        val response = client.chat().completions().create(params)
        return response.choices().firstOrNull()?.message()?.content()?.orElse("Não foi possível calcular.")
            ?: "Não foi possível calcular."
    }
}
