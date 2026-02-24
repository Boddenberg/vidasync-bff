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
            .addUserMessage(
                """
                Você é um nutricionista especialista em contagem de calorias.
                Analise os seguintes alimentos e calcule a quantidade aproximada de calorias.
                Seja objetivo e responda em português, listando cada alimento com suas calorias e o total.
                
                Alimentos: $foodDescription
                """.trimIndent()
            )
            .build()

        val response = client.chat().completions().create(params)
        return response.choices().firstOrNull()?.message()?.content()?.orElse("Não foi possível calcular.")
            ?: "Não foi possível calcular."
    }
}
