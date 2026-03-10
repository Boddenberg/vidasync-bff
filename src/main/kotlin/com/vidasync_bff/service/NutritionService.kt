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
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Service
class NutritionService(
    private val aiGatewayClient: AIGatewayClient,
    private val cacheService: IngredientCacheService,
    private val storageClient: SupabaseStorageClient,
    @Value("\${supabase.pipeline.bucket:pipeline-inputs}") private val pipelineBucket: String,
    @Value("\${nutrition.cache.enabled:true}") private val nutritionCacheEnabled: Boolean,
    @Value("\${nutrition.cache.image-only.enabled:false}") private val imageOnlyCacheEnabled: Boolean,
    @Value("\${nutrition.ai.future-timeout-seconds:90}") private val aiFutureTimeoutSeconds: Long
) {

    private val log = LoggerFactory.getLogger(NutritionService::class.java)

    private data class IngredientGatewayResult(
        val cacheRow: IngredientCacheRow,
        val precisaRevisao: Boolean,
        val warnings: List<String>,
        val traceId: String?,
        val nomePratoDetectado: String? = null
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
                Triple(key, original, executor.submit<List<IngredientGatewayResult>> {
                    callAIGatewayForSingleIngredient(
                        key = key,
                        original = original,
                        imageUrlForAgent = imageUrlForAgent
                    )
                })
            }

            for ((key, original, future) in futures) {
                try {
                    newResults.addAll(future.get(aiFutureTimeoutSeconds, TimeUnit.SECONDS))
                } catch (e: Exception) {
                    future.cancel(true)
                    val warning = if (isTimeoutFailure(e)) {
                        "Tempo limite ao consultar o servico de IA para '$original'."
                    } else {
                        "Nao foi possivel validar o ingrediente '$original' com o servico de IA."
                    }
                    log.error(
                        "Erro ao processar ingrediente via agentes (ingredient='{}', timeout={}): {}",
                        original,
                        isTimeoutFailure(e),
                        e.message,
                        e
                    )
                    newResults.add(
                        buildGatewayFallbackResult(key = key, original = original, warning = warning)
                    )
                }
            }
            executor.shutdown()

            if (shouldUseCache) {
                val cacheableRows = newResults
                    .filter { !it.precisaRevisao && it.warnings.isEmpty() }
                    .map { it.cacheRow }
                if (cacheableRows.isNotEmpty()) {
                    cacheService.saveBatch(cacheableRows)
                } else {
                    log.info("Cache skip: nenhum resultado novo elegivel para persistencia")
                }
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
        var responseDishName: String? = null
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
            if (responseDishName.isNullOrBlank()) {
                responseDishName = result.nomePratoDetectado?.takeIf { it.isNotBlank() }
            }
        }

        if (invalidItems.isNotEmpty()) {
            log.warn("Itens invalidos encontrados: {} -> rejeitando tudo", invalidItems)
            return CalorieResponse(
                nutrition = null,
                invalidItems = invalidItems,
                nomePratoDetectado = responseDishName,
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
            nomePratoDetectado = responseDishName,
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
    ): List<IngredientGatewayResult> {
        log.info(
            "AI Gateway request nutrition ingredient='{}' has_image_url={}",
            original,
            !imageUrlForAgent.isNullOrBlank()
        )
        return try {
            val gatewayResponse = if (!imageUrlForAgent.isNullOrBlank()) {
                val payload = mutableMapOf<String, Any?>(
                    "image_url" to imageUrlForAgent
                )
                if (!original.equals("itens da imagem", ignoreCase = true)) {
                    payload["foods"] = original
                }
                aiGatewayClient.pipelineFotoCalorias(
                    payload = payload,
                    idioma = "pt-BR",
                    traceId = TraceContext.current()
                )
            } else {
                aiGatewayClient.route(
                    contexto = "calcular_calorias_texto",
                    payload = mapOf("foods" to original),
                    idioma = "pt-BR",
                    metadados = mapOf(
                        "origem" to "vidasync-bff",
                        "feature" to "nutrition",
                        "has_image_url" to false
                    )
                )
            }

            if (gatewayResponse.status.equals("erro", ignoreCase = true)) {
                throw IllegalStateException("AI Gateway retornou status=erro para '$original'")
            }

            parseIngredientFromGateway(
                key = key,
                original = original,
                gatewayResponse = gatewayResponse
            )
        } catch (e: AIGatewayClient.AIGatewayRequestException) {
            if (e.statusCode == 422 && !imageUrlForAgent.isNullOrBlank()) {
                log.warn(
                    "AI Gateway retornou 422 no pipeline-foto-calorias para ingrediente='{}' body={}",
                    original,
                    e.responseBody
                )
                return IngredientGatewayResult(
                    cacheRow = IngredientCacheRow(
                        ingredientKey = key,
                        originalInput = original,
                        correctedInput = original,
                        calories = "0 kcal",
                        protein = "0g",
                        carbs = "0g",
                        fat = "0g",
                        isValidFood = false
                    ),
                    precisaRevisao = true,
                    warnings = listOf("Nao foi possivel identificar comida ou porcoes detectaveis na imagem."),
                    traceId = TraceContext.current()
                ).let(::listOf)
            }
            log.error("Erro ao consultar AI Gateway para '{}': {}", original, e.message, e)
            listOf(
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
            )
        } catch (e: Exception) {
            log.error("Erro ao consultar AI Gateway para '{}': {}", original, e.message, e)
            listOf(
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
            )
        }
    }

    private fun parseIngredientFromGateway(
        key: String,
        original: String,
        gatewayResponse: AIGatewayRouteResponse
    ): List<IngredientGatewayResult> {
        val resultMap = resolveGatewayResultMap(gatewayResponse)
        val itens = asMapList(resultMap["itens"]).ifEmpty {
            gatewayResponse.composicao ?: asMapList(resultMap["composicao"])
        }
        val totals = asMap(resultMap["totais"])
        val dishName = toStringValue(resultMap["nome_prato_detectado"])
            ?: toStringValue(gatewayResponse.nomePratoDetectado)

        val gatewayWarnings = (gatewayResponse.warnings ?: emptyList()) + toStringList(resultMap["warnings"])
        val warningNoFood = gatewayWarnings.any { it.contains("Nenhum item alimentar", ignoreCase = true) }
        val gatewayPrecisaRevisao = gatewayResponse.precisaRevisao == true

        val shouldExpandDetectedItems = original.equals("itens da imagem", ignoreCase = true) && itens.size > 1
        if (shouldExpandDetectedItems) {
            val expandedResults = itens.mapIndexed { index, item ->
                val itemName = resolveIngredientName(item, fallback = "item_${index + 1}", dishName = dishName)
                val itemWarnings = (gatewayWarnings + toStringList(item["warnings"])).distinct()

                val caloriesValue = toDoubleValue(item["calorias_kcal"])
                val proteinValue = toDoubleValue(item["proteina_g"])
                val carbsValue = toDoubleValue(item["carboidratos_g"])
                val fatValue = toDoubleValue(item["lipidios_g"])
                val hasAnyMacro = listOf(caloriesValue, proteinValue, carbsValue, fatValue).any { (it ?: 0.0) > 0.0 }

                val isValidFood = (!warningNoFood) && (itemName.isNotBlank() || hasAnyMacro)
                val precisaRevisao = gatewayPrecisaRevisao || itemWarnings.isNotEmpty() || !isValidFood
                if (!isValidFood) {
                    log.warn("AI Gateway marcou item detectado como possivelmente invalido: '{}'", itemName)
                }

                IngredientGatewayResult(
                    cacheRow = IngredientCacheRow(
                        ingredientKey = cacheService.normalizeKey(itemName),
                        originalInput = itemName,
                        correctedInput = itemName,
                        calories = formatKcal(caloriesValue),
                        protein = formatGrams(proteinValue),
                        carbs = formatGrams(carbsValue),
                        fat = formatGrams(fatValue),
                        isValidFood = isValidFood
                    ),
                    precisaRevisao = precisaRevisao,
                    warnings = itemWarnings,
                    traceId = gatewayResponse.traceId,
                    nomePratoDetectado = dishName
                )
            }

            if (expandedResults.isNotEmpty()) {
                return expandedResults
            }
        }

        val firstItem = itens.firstOrNull()
        val itemWarnings = (gatewayWarnings + toStringList(firstItem?.get("warnings"))).distinct()

        val corrected = resolveIngredientName(firstItem, fallback = original, dishName = dishName)

        val caloriesValue = toDoubleValue(firstItem?.get("calorias_kcal") ?: totals["calorias_kcal"])
        val proteinValue = toDoubleValue(firstItem?.get("proteina_g") ?: totals["proteina_g"])
        val carbsValue = toDoubleValue(firstItem?.get("carboidratos_g") ?: totals["carboidratos_g"])
        val fatValue = toDoubleValue(firstItem?.get("lipidios_g") ?: totals["lipidios_g"])

        val hasAnyMacro = listOf(caloriesValue, proteinValue, carbsValue, fatValue).any { (it ?: 0.0) > 0.0 }
        val isValidFood = (!warningNoFood) && (firstItem != null || hasAnyMacro)
        val precisaRevisao = gatewayPrecisaRevisao || itemWarnings.isNotEmpty() || !isValidFood
        val isImagePlaceholderWithDetectedFood =
            original.equals("itens da imagem", ignoreCase = true) && firstItem != null
        val resolvedOriginalInput = if (isImagePlaceholderWithDetectedFood) corrected else original
        val resolvedKey = if (isImagePlaceholderWithDetectedFood) cacheService.normalizeKey(corrected) else key

        if (!isValidFood) {
            log.warn("AI Gateway marcou item como possivelmente invalido: '{}'", original)
        }

        return listOf(
            IngredientGatewayResult(
                cacheRow = IngredientCacheRow(
                    ingredientKey = resolvedKey,
                    originalInput = resolvedOriginalInput,
                    correctedInput = corrected,
                    calories = formatKcal(caloriesValue),
                    protein = formatGrams(proteinValue),
                    carbs = formatGrams(carbsValue),
                    fat = formatGrams(fatValue),
                    isValidFood = isValidFood
                ),
                precisaRevisao = precisaRevisao,
                warnings = itemWarnings,
                traceId = gatewayResponse.traceId,
                nomePratoDetectado = dishName
            ),
        )
    }

    private fun resolveGatewayResultMap(gatewayResponse: AIGatewayRouteResponse): Map<String, Any?> {
        val nestedFromResultado = asMap(gatewayResponse.resultado?.get("calorias_texto"))
        if (nestedFromResultado.isNotEmpty()) {
            return nestedFromResultado
        }

        val directResultado = gatewayResponse.resultado
        if (!directResultado.isNullOrEmpty()) {
            return directResultado
        }

        val caloriasTextoTopLevel = gatewayResponse.caloriasTexto
        if (!caloriasTextoTopLevel.isNullOrEmpty()) {
            return caloriasTextoTopLevel
        }

        return emptyMap()
    }

    private fun buildGatewayFallbackResult(
        key: String,
        original: String,
        warning: String
    ): IngredientGatewayResult {
        return IngredientGatewayResult(
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
            warnings = listOf(warning),
            traceId = TraceContext.current()
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
            val publicUrl = storageClient.buildPublicObjectUrl(
                fileKey = mediaKey,
                targetBucket = pipelineBucket
            )
            log.info(
                "nutrition.input.media resolved via file key (bucket={}, fileKey={}, public_url=true)",
                pipelineBucket,
                mediaKey
            )
            return publicUrl
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
            storageClient.buildPublicObjectUrl(
                fileKey = uploaded.fileKey,
                targetBucket = pipelineBucket
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

    private fun resolveIngredientName(item: Map<String, Any?>?, fallback: String, dishName: String? = null): String {
        val extractedFromDescription = extractNameFromOriginalDescription(toStringValue(item?.get("descricao_original")))
        val nomeAlimento = toStringValue(item?.get("nome_alimento"))
        val alimento = toStringValue(item?.get("alimento"))
        val canonical = toStringValue(item?.get("consulta_canonica"))

        if (!nomeAlimento.isNullOrBlank()) {
            return nomeAlimento
        }

        if (!extractedFromDescription.isNullOrBlank()) {
            return extractedFromDescription
        }

        if (!alimento.isNullOrBlank()) {
            val alimentoIsDish = !dishName.isNullOrBlank() && areEquivalentFoodNames(alimento, dishName)
            if (alimentoIsDish) {
                return canonical ?: fallback
            }
            return alimento
        }

        return canonical ?: fallback
    }

    private fun extractNameFromOriginalDescription(description: String?): String? {
        if (description.isNullOrBlank()) return null

        val unitsPattern =
            "(?:g|kg|mg|ml|l|un|und|unid(?:ade)?s?|colher(?:es)?(?:\\s+de\\s+(?:sopa|cha))?|xicara(?:s)?|fatia(?:s)?|porcao(?:oes)?|pedaco(?:s)?)"
        val match = Regex(
            "^\\s*\\d+[\\d.,/]*\\s*$unitsPattern\\b\\s*(?:de|do|da)?\\s*(.+?)\\s*$",
            RegexOption.IGNORE_CASE
        ).find(description.trim()) ?: return null

        val extracted = match.groupValues
            .getOrNull(1)
            ?.trim()
            ?.replace(Regex("^\\s*(de|do|da)\\s+", RegexOption.IGNORE_CASE), "")
            ?.trim(',', '.', ';', ':')
            ?.trim()

        return extracted?.takeIf { it.isNotBlank() }
    }

    private fun areEquivalentFoodNames(a: String, b: String): Boolean {
        return normalizeFoodNameForComparison(a) == normalizeFoodNameForComparison(b)
    }

    private fun normalizeFoodNameForComparison(value: String): String {
        val withoutAccents = Normalizer
            .normalize(value.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return withoutAccents.replace(Regex("[^a-z0-9]+"), " ").trim()
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

    private fun isTimeoutFailure(failure: Throwable?): Boolean {
        var current = failure
        while (current != null) {
            val name = current::class.java.simpleName.lowercase()
            val message = (current.message ?: "").lowercase()
            if (
                current is TimeoutException ||
                name.contains("timeout") ||
                message.contains("timeout") ||
                message.contains("timed out")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
