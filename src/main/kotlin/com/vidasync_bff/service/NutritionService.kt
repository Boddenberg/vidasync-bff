package com.vidasync_bff.service

import com.vidasync_bff.client.AIGatewayClient
import com.vidasync_bff.client.SupabaseStorageClient
import com.vidasync_bff.dto.ai.AIGatewayRouteResponse
import com.vidasync_bff.dto.request.CalorieRequest
import com.vidasync_bff.dto.response.CalorieResponse
import com.vidasync_bff.dto.response.IngredientCacheRow
import com.vidasync_bff.dto.response.IngredientDetail
import com.vidasync_bff.dto.response.NutritionData
import com.vidasync_bff.dto.response.UnitCorrection
import com.vidasync_bff.observability.TraceContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Service
class NutritionService(
    private val aiGatewayClient: AIGatewayClient,
    private val cacheService: IngredientCacheService,
    private val storageClient: SupabaseStorageClient,
    @Value("\${supabase.pipeline.bucket:pipeline-inputs}") private val pipelineBucket: String,
    @Value("\${supabase.storage.signed-download-ttl-seconds:120}") private val signedDownloadTtlSeconds: Int,
    @Value("\${nutrition.cache.enabled:true}") private val nutritionCacheEnabled: Boolean,
    @Value("\${nutrition.cache.image-only.enabled:false}") private val imageOnlyCacheEnabled: Boolean
) {

    private val log = LoggerFactory.getLogger(NutritionService::class.java)

    private data class IngredientGatewayResult(
        val cacheRow: IngredientCacheRow,
        val precisaRevisao: Boolean,
        val warnings: List<String>,
        val traceId: String?
    )

    /**
     * Metodo principal com cache, validacao e correcao de unidades.
     * O calculo por ingrediente e delegado para a camada de agentes.
     */
    fun calculateNutritionSmart(request: CalorieRequest): CalorieResponse {
        val resolvedImageUrl = resolveInputImageUrl(request)
        val foods = request.foods?.trim().orEmpty()
        return calculateNutritionSmartInternal(
            foodDescription = foods,
            imageUrlForAgent = resolvedImageUrl
        )
    }

    /**
     * Metodo principal com cache, validacao e correcao de unidades.
     * O calculo por ingrediente e delegado para a camada de agentes.
     */
    fun calculateNutritionSmart(foodDescription: String): CalorieResponse {
        return calculateNutritionSmartInternal(foodDescription, null)
    }

    private fun calculateNutritionSmartInternal(
        foodDescription: String,
        imageUrlForAgent: String?
    ): CalorieResponse {
        val traceId = TraceContext.current()
        val startedNs = System.nanoTime()
        val parseStartedNs = System.nanoTime()
        log.info(
            "nutrition.started trace_id={} provider=agents foods='{}' has_image_url={}",
            traceId,
            foodDescription,
            !imageUrlForAgent.isNullOrBlank()
        )

        val parsedIngredients = foodDescription
            .split(",", "+", " e ", " com ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val rawIngredients = if (parsedIngredients.isEmpty() && !imageUrlForAgent.isNullOrBlank()) {
            listOf("itens da imagem")
        } else {
            parsedIngredients
        }
        val isImageOnlyRequest = parsedIngredients.isEmpty() && !imageUrlForAgent.isNullOrBlank()
        val shouldUseCache = nutritionCacheEnabled && (!isImageOnlyRequest || imageOnlyCacheEnabled)
        val parseDurationMs = (System.nanoTime() - parseStartedNs) / 1_000_000.0

        if (rawIngredients.isEmpty()) {
            log.warn(
                "nutrition.invalid_input trace_id={} stage=parse_ingredients duration_ms={} reason=nenhum_alimento",
                traceId,
                String.format(Locale.US, "%.4f", parseDurationMs),
            )
            return CalorieResponse(
                error = "Nenhum alimento informado",
                precisaRevisao = true,
                warnings = listOf("Nenhum alimento foi informado para o calculo."),
                traceId = traceId
            )
        }

        log.info(
            "nutrition.stage trace_id={} stage=parse_ingredients duration_ms={} ingredients={}",
            traceId,
            String.format(Locale.US, "%.4f", parseDurationMs),
            rawIngredients,
        )

        val keyToOriginal = rawIngredients.associateBy { cacheService.normalizeKey(it) }
        val hits = mutableListOf<Pair<String, IngredientCacheRow>>()
        val misses = mutableListOf<Pair<String, String>>() // key -> original

        if (shouldUseCache) {
            val cacheLookupStartedNs = System.nanoTime()
            val cacheHits = cacheService.lookupBatch(keyToOriginal.keys.toList())
            val cacheLookupDurationMs = (System.nanoTime() - cacheLookupStartedNs) / 1_000_000.0

            for ((key, original) in keyToOriginal) {
                val cached = cacheHits[key]
                if (cached != null) {
                    log.info("CACHE HIT: '{}' -> calories={}", key, cached.calories)
                    hits.add(original to cached)
                } else {
                    log.info("CACHE MISS: '{}'", key)
                    misses.add(key to original)
                }
            }
            log.info(
                "nutrition.stage trace_id={} stage=cache_lookup duration_ms={} cache_hits={} cache_misses={}",
                traceId,
                String.format(Locale.US, "%.4f", cacheLookupDurationMs),
                hits.size,
                misses.size,
            )
        } else {
            misses.addAll(keyToOriginal.map { (key, original) -> key to original })
            log.info(
                "nutrition.stage trace_id={} stage=cache_lookup skipped=true reason={} cache_hits=0 cache_misses={}",
                traceId,
                if (isImageOnlyRequest) "image_only_request" else "nutrition_cache_disabled",
                misses.size
            )
        }

        val newResults = mutableListOf<IngredientGatewayResult>()
        val aiEnrichmentStartedNs = System.nanoTime()
        if (misses.isNotEmpty()) {
            log.info("Chamando camada de agentes para {} ingredientes em paralelo", misses.size)

            val executor = Executors.newVirtualThreadPerTaskExecutor()
            val futures = misses.map { (key, original) ->
                executor.submit<IngredientGatewayResult> {
                    callAIGatewayForSingleIngredient(
                        key = key,
                        original = original,
                        imageUrlForAgent = imageUrlForAgent
                    )
                }
            }

            for (future in futures) {
                try {
                    newResults.add(future.get(30, TimeUnit.SECONDS))
                } catch (e: Exception) {
                    log.error("Erro ao processar ingrediente via agentes: {}", e.message, e)
                }
            }
            executor.shutdown()

            if (shouldUseCache) {
                cacheService.saveBatch(newResults.map { it.cacheRow })
            }
        }
        val aiEnrichmentDurationMs = (System.nanoTime() - aiEnrichmentStartedNs) / 1_000_000.0
        log.info(
            "nutrition.stage trace_id={} stage=ai_enrichment duration_ms={} enriched_items={}",
            traceId,
            String.format(Locale.US, "%.4f", aiEnrichmentDurationMs),
            newResults.size,
        )

        val allIngredients = mutableListOf<IngredientDetail>()
        val corrections = mutableListOf<UnitCorrection>()
        val invalidItems = mutableListOf<String>()
        val responseWarnings = linkedSetOf<String>()
        var responseNeedsReview = false
        val aggregationStartedNs = System.nanoTime()

        for ((original, cached) in hits) {
            if (!cached.isValidFood) {
                invalidItems.add(original)
            } else {
                allIngredients.add(
                    IngredientDetail(
                        name = cached.correctedInput ?: original,
                        nutrition = NutritionData(cached.calories, cached.protein, cached.carbs, cached.fat),
                        cached = true,
                        traceId = traceId
                    )
                )
                if (cached.correctedInput != null && cached.correctedInput != cached.originalInput) {
                    corrections.add(UnitCorrection(original = original, corrected = cached.correctedInput))
                }
            }
        }

        for (result in newResults) {
            val cacheRow = result.cacheRow
            if (!cacheRow.isValidFood) {
                invalidItems.add(cacheRow.originalInput)
                responseNeedsReview = true
            } else {
                allIngredients.add(
                    IngredientDetail(
                        name = cacheRow.correctedInput ?: cacheRow.originalInput,
                        nutrition = NutritionData(cacheRow.calories, cacheRow.protein, cacheRow.carbs, cacheRow.fat),
                        cached = false,
                        precisaRevisao = result.precisaRevisao,
                        warnings = result.warnings.ifEmpty { null },
                        traceId = result.traceId ?: traceId
                    )
                )
                if (cacheRow.correctedInput != null && cacheRow.correctedInput != cacheRow.originalInput) {
                    corrections.add(UnitCorrection(original = cacheRow.originalInput, corrected = cacheRow.correctedInput))
                }
            }
            if (result.precisaRevisao) responseNeedsReview = true
            responseWarnings.addAll(result.warnings)
        }

        if (invalidItems.isNotEmpty()) {
            log.warn("Itens invalidos encontrados: {} -> rejeitando tudo", invalidItems)
            return CalorieResponse(
                nutrition = null,
                invalidItems = invalidItems,
                precisaRevisao = true,
                warnings = responseWarnings.toList().ifEmpty {
                    listOf("Foram encontrados itens invalidos na descricao da refeicao.")
                },
                traceId = traceId
            )
        }

        val totalNutrition = sumNutrition(allIngredients.map { it.nutrition })
        val aggregationDurationMs = (System.nanoTime() - aggregationStartedNs) / 1_000_000.0
        val totalDurationMs = (System.nanoTime() - startedNs) / 1_000_000.0
        log.info(
            "nutrition.completed trace_id={} valid_items={} invalid_items={} corrections={} cache_items={} stage_aggregation_ms={} duration_ms={}",
            traceId,
            allIngredients.size,
            invalidItems.size,
            corrections.size,
            allIngredients.count { it.cached },
            String.format(Locale.US, "%.4f", aggregationDurationMs),
            String.format(Locale.US, "%.4f", totalDurationMs),
        )

        return CalorieResponse(
            nutrition = totalNutrition,
            ingredients = allIngredients,
            corrections = corrections.ifEmpty { null },
            invalidItems = invalidItems.ifEmpty { null },
            precisaRevisao = responseNeedsReview,
            warnings = responseWarnings.toList().ifEmpty { null },
            traceId = traceId
        )
    }

    /**
     * Metodo simples (retrocompativel) usado pelo MealService.
     */
    fun calculateNutrition(foodDescription: String): NutritionData {
        val result = calculateNutritionSmart(foodDescription)
        return result.nutrition ?: NutritionData("0 kcal", "0g", "0g", "0g")
    }

    private fun callAIGatewayForSingleIngredient(
        key: String,
        original: String,
        imageUrlForAgent: String?
    ): IngredientGatewayResult {
        log.info("AI Gateway request (calcular_calorias_texto): '{}'", original)
        return try {
            val payload = mutableMapOf<String, Any?>("foods" to original)
            if (!imageUrlForAgent.isNullOrBlank()) {
                payload["image_url"] = imageUrlForAgent
            }
            val gatewayResponse = aiGatewayClient.route(
                contexto = "calcular_calorias_texto",
                payload = payload,
                idioma = "pt-BR",
                metadados = mapOf(
                    "origem" to "vidasync-bff",
                    "feature" to "nutrition",
                    "has_image_url" to !imageUrlForAgent.isNullOrBlank()
                )
            )

            if (gatewayResponse.status.equals("erro", ignoreCase = true)) {
                throw IllegalStateException("AI Gateway retornou status=erro para '$original'")
            }

            parseIngredientFromGateway(
                key = key,
                original = original,
                gatewayResponse = gatewayResponse
            )
        } catch (e: Exception) {
            log.error("Erro ao consultar AI Gateway para '{}': {}", original, e.message, e)
            IngredientGatewayResult(
                cacheRow = IngredientCacheRow(
                    ingredientKey = key,
                    originalInput = original,
                    correctedInput = original,
                    calories = "0 kcal",
                    protein = "0g",
                    carbs = "0g",
                    fat = "0g",
                    isValidFood = true
                ),
                precisaRevisao = true,
                warnings = listOf("Nao foi possivel validar o ingrediente '$original' com o servico de IA."),
                traceId = TraceContext.current()
            )
        }
    }

    private fun parseIngredientFromGateway(
        key: String,
        original: String,
        gatewayResponse: AIGatewayRouteResponse
    ): IngredientGatewayResult {
        val resultMap = gatewayResponse.resultado ?: emptyMap()
        val itens = asMapList(resultMap["itens"])
        val firstItem = itens.firstOrNull()
        val totals = asMap(resultMap["totais"])

        val gatewayWarnings = (gatewayResponse.warnings ?: emptyList()) + toStringList(resultMap["warnings"])
        val warningNoFood = gatewayWarnings.any { it.contains("Nenhum item alimentar", ignoreCase = true) }

        val corrected = toStringValue(firstItem?.get("alimento"))
            ?: toStringValue(firstItem?.get("consulta_canonica"))
            ?: original

        val caloriesValue = toDoubleValue(firstItem?.get("calorias_kcal") ?: totals["calorias_kcal"])
        val proteinValue = toDoubleValue(firstItem?.get("proteina_g") ?: totals["proteina_g"])
        val carbsValue = toDoubleValue(firstItem?.get("carboidratos_g") ?: totals["carboidratos_g"])
        val fatValue = toDoubleValue(firstItem?.get("lipidios_g") ?: totals["lipidios_g"])

        val hasAnyMacro = listOf(caloriesValue, proteinValue, carbsValue, fatValue).any { (it ?: 0.0) > 0.0 }
        val isValidFood = (!warningNoFood) && (firstItem != null || hasAnyMacro)
        val precisaRevisao = gatewayResponse.precisaRevisao == true || gatewayWarnings.isNotEmpty() || !isValidFood

        if (!isValidFood) {
            log.warn("AI Gateway marcou item como possivelmente invalido: '{}'", original)
        }

        return IngredientGatewayResult(
            cacheRow = IngredientCacheRow(
                ingredientKey = key,
                originalInput = original,
                correctedInput = corrected,
                calories = formatKcal(caloriesValue),
                protein = formatGrams(proteinValue),
                carbs = formatGrams(carbsValue),
                fat = formatGrams(fatValue),
                isValidFood = isValidFood
            ),
            precisaRevisao = precisaRevisao,
            warnings = gatewayWarnings.distinct(),
            traceId = gatewayResponse.traceId
        )
    }

    private fun resolveInputImageUrl(request: CalorieRequest): String? {
        val mediaKey = listOf(
            request.imageKey,
            request.audioKey,
            request.pdfKey,
            request.fileKey
        ).firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotBlank) }

        if (!mediaKey.isNullOrBlank()) {
            val signed = storageClient.createSignedDownloadUrl(
                fileKey = mediaKey,
                targetBucket = pipelineBucket,
                expiresInSeconds = signedDownloadTtlSeconds
            )
            log.info(
                "nutrition.input.media resolved via file key (bucket={}, fileKey={}, ttlSeconds={})",
                pipelineBucket,
                mediaKey,
                signedDownloadTtlSeconds
            )
            return signed
        }

        val directImageUrl = request.imageUrl?.trim()?.takeIf(String::isNotBlank)
        if (directImageUrl != null) {
            log.info("nutrition.input.media resolved via image_url")
            return directImageUrl
        }

        val fallbackImage = request.image?.trim()?.takeIf(String::isNotBlank) ?: return null
        return if (fallbackImage.startsWith("http://", ignoreCase = true) ||
            fallbackImage.startsWith("https://", ignoreCase = true)
        ) {
            log.info("nutrition.input.media resolved via legacy image=http(s) URL")
            fallbackImage
        } else {
            log.info("nutrition.input.media resolved via legacy base64 upload fallback")
            val uploaded = storageClient.uploadBase64Object(
                base64Data = fallbackImage,
                fileNamePrefix = "nutrition_fallback",
                targetBucket = pipelineBucket
            )
            storageClient.createSignedDownloadUrl(
                fileKey = uploaded.fileKey,
                targetBucket = pipelineBucket,
                expiresInSeconds = signedDownloadTtlSeconds
            )
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
        val normalized = value.replace(",", ".")
        return Regex("[\\d.]+").find(normalized)?.value?.toDoubleOrNull() ?: 0.0
    }

    private fun formatKcal(value: Double?): String {
        val normalized = value ?: 0.0
        return "${formatNumber(normalized)} kcal"
    }

    private fun formatGrams(value: Double?): String {
        val normalized = value ?: 0.0
        return "${formatNumber(normalized)}g"
    }

    private fun formatNumber(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }

    private fun asMap(value: Any?): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return if (value is Map<*, *>) value as Map<String, Any?> else emptyMap()
    }

    private fun asMapList(value: Any?): List<Map<String, Any?>> {
        if (value !is List<*>) return emptyList()
        return value.mapNotNull { item ->
            @Suppress("UNCHECKED_CAST")
            if (item is Map<*, *>) item as Map<String, Any?> else null
        }
    }

    private fun toStringList(value: Any?): List<String> {
        return when (value) {
            is List<*> -> value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotBlank() }
            is String -> listOf(value.trim()).filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun toStringValue(value: Any?): String? {
        val text = value?.toString()?.trim() ?: return null
        return text.takeIf { it.isNotBlank() }
    }

    private fun toDoubleValue(value: Any?): Double? {
        return when (value) {
            null -> null
            is Number -> value.toDouble()
            is String -> {
                val raw = value.trim().lowercase()
                if (raw.isBlank() || raw in setOf("na", "n/a", "nd", "tr", "-", "--")) return null
                val normalized = raw
                    .replace("kcal", "")
                    .replace("mg", "")
                    .replace("g", "")
                    .trim()
                    .replace(".", "")
                    .replace(",", ".")
                normalized.toDoubleOrNull()
            }
            else -> null
        }
    }
}
