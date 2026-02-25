package com.vidasync_bff.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class IngredientCacheRow(
    val id: String? = null,

    @JsonProperty("ingredient_key")
    val ingredientKey: String,

    @JsonProperty("original_input")
    val originalInput: String,

    @JsonProperty("corrected_input")
    val correctedInput: String? = null,

    val calories: String = "0 kcal",
    val protein: String = "0g",
    val carbs: String = "0g",
    val fat: String = "0g",

    @JsonProperty("is_valid_food")
    val isValidFood: Boolean = true,

    @JsonProperty("created_at")
    val createdAt: String? = null
)
