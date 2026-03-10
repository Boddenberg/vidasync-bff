package com.vidasync_bff.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class NutritionData(
    val calories: String,
    val protein: String,
    val carbs: String,
    val fat: String
)

data class UnitCorrection(
    val original: String,
    val corrected: String
)

data class IngredientDetail(
    val name: String,
    val nutrition: NutritionData,
    val cached: Boolean = false,
    @JsonProperty("precisa_revisao")
    val precisaRevisao: Boolean = false,
    val warnings: List<String>? = null,
    @JsonProperty("trace_id")
    val traceId: String? = null
)

data class CalorieResponse(
    val nutrition: NutritionData? = null,
    val ingredients: List<IngredientDetail>? = null,
    @JsonProperty("nome_prato_detectado")
    val nomePratoDetectado: String? = null,
    val corrections: List<UnitCorrection>? = null,
    val invalidItems: List<String>? = null,
    val error: String? = null,
    @JsonProperty("precisa_revisao")
    val precisaRevisao: Boolean = false,
    val warnings: List<String>? = null,
    @JsonProperty("trace_id")
    val traceId: String? = null
)
