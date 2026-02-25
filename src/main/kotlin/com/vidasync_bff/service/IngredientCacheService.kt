package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.response.IngredientCacheRow
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service

@Service
class IngredientCacheService(private val supabaseClient: SupabaseClient) {

    private val log = LoggerFactory.getLogger(IngredientCacheService::class.java)

    fun normalizeKey(input: String): String {
        return input.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    fun lookupBatch(keys: List<String>): Map<String, IngredientCacheRow> {
        if (keys.isEmpty()) return emptyMap()

        val keysFilter = keys.joinToString(",") { "\"$it\"" }
        log.info("Cache lookup: {} keys → in.({})", keys.size, keysFilter)

        return try {
            val rows = supabaseClient.get(
                "ingredient_cache",
                mapOf("ingredient_key" to "in.($keysFilter)"),
                object : ParameterizedTypeReference<List<IngredientCacheRow>>() {}
            ) ?: emptyList()

            log.info("Cache hits: {}/{}", rows.size, keys.size)
            rows.associateBy { it.ingredientKey }
        } catch (e: Exception) {
            log.error("Erro ao consultar cache de ingredientes: {}", e.message, e)
            emptyMap()
        }
    }

    fun saveBatch(rows: List<IngredientCacheRow>) {
        if (rows.isEmpty()) return
        log.info("Salvando {} ingredientes no cache", rows.size)

        try {
            for (row in rows) {
                try {
                    supabaseClient.post(
                        "ingredient_cache",
                        mapOf(
                            "ingredient_key" to row.ingredientKey,
                            "original_input" to row.originalInput,
                            "corrected_input" to (row.correctedInput ?: row.originalInput),
                            "calories" to row.calories,
                            "protein" to row.protein,
                            "carbs" to row.carbs,
                            "fat" to row.fat,
                            "is_valid_food" to row.isValidFood
                        ),
                        object : ParameterizedTypeReference<List<IngredientCacheRow>>() {}
                    )
                } catch (e: Exception) {
                    log.warn("Erro ao salvar ingrediente no cache (key={}): {}", row.ingredientKey, e.message)
                }
            }
            log.info("Cache atualizado com sucesso")
        } catch (e: Exception) {
            log.error("Erro ao salvar cache de ingredientes: {}", e.message, e)
        }
    }
}
