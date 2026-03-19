package com.vidasync_bff.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.vidasync_bff.dto.request.CalorieRequest
import com.vidasync_bff.dto.response.CalorieResponse
import com.vidasync_bff.dto.response.IngredientDetail
import com.vidasync_bff.dto.response.NutritionData
import com.vidasync_bff.dto.response.UnitCorrection
import com.vidasync_bff.service.NutritionService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class NutritionControllerTests {

    private val nutritionService = mock(NutritionService::class.java)
    private val mockMvc = MockMvcBuilders.standaloneSetup(NutritionController(nutritionService)).build()
    private val objectMapper = ObjectMapper()

    @Test
    fun `deve retornar 200 quando calculo de calorias responder com sucesso`() {
        val request = CalorieRequest(foods = "200g arroz, 150g frango")
        val response = CalorieResponse(
            nutrition = NutritionData(
                calories = "610 kcal",
                protein = "35g",
                carbs = "77g",
                fat = "12g"
            ),
            ingredients = listOf(
                IngredientDetail(
                    name = "200g arroz",
                    nutrition = NutritionData("260 kcal", "5g", "57g", "0.5g"),
                    cached = true,
                    traceId = "trace-1"
                ),
                IngredientDetail(
                    name = "150g frango grelhado",
                    nutrition = NutritionData("350 kcal", "30g", "0g", "11.5g"),
                    cached = false,
                    traceId = "trace-1"
                )
            ),
            corrections = listOf(UnitCorrection("150g frango", "150g frango grelhado")),
            precisaRevisao = false,
            traceId = "trace-1"
        )

        `when`(nutritionService.calculateNutritionSmart(request)).thenReturn(response)

        mockMvc.post("/nutrition/calories") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.nutrition.calories") { value("610 kcal") }
            jsonPath("$.ingredients[0].name") { value("200g arroz") }
            jsonPath("$.corrections[0].corrected") { value("150g frango grelhado") }
            jsonPath("$.trace_id") { value("trace-1") }
        }
    }

    @Test
    fun `deve retornar 400 com mensagem amigavel quando houver item invalido`() {
        val request = CalorieRequest(foods = "200g arroz, cadeira")
        val response = CalorieResponse(
            nutrition = null,
            invalidItems = listOf("cadeira"),
            warnings = listOf("Foram encontrados itens invalidos na descricao da refeicao."),
            traceId = "trace-2"
        )

        `when`(nutritionService.calculateNutritionSmart(request)).thenReturn(response)

        mockMvc.post("/nutrition/calories") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("\"cadeira\" nao e um alimento valido. Corrija e tente novamente.") }
            jsonPath("$.invalidItems[0]") { value("cadeira") }
            jsonPath("$.precisa_revisao") { value(true) }
            jsonPath("$.trace_id") { value("trace-2") }
        }
    }

    @Test
    fun `deve retornar 400 com resposta original quando servico informar erro de request`() {
        val request = CalorieRequest()
        val response = CalorieResponse(
            error = "Nenhum alimento informado",
            precisaRevisao = true,
            warnings = listOf("Nenhum alimento foi informado para o calculo."),
            traceId = "trace-3"
        )

        `when`(nutritionService.calculateNutritionSmart(request)).thenReturn(response)

        mockMvc.post("/nutrition/calories") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("Nenhum alimento informado") }
            jsonPath("$.precisa_revisao") { value(true) }
            jsonPath("$.warnings[0]") { value("Nenhum alimento foi informado para o calculo.") }
            jsonPath("$.trace_id") { value("trace-3") }
        }
    }

    @Test
    fun `deve retornar 500 quando service lancar excecao`() {
        val request = CalorieRequest(foods = "200g arroz")

        doThrow(RuntimeException("falha inesperada"))
            .`when`(nutritionService)
            .calculateNutritionSmart(request)

        mockMvc.post("/nutrition/calories") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isInternalServerError() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("falha inesperada") }
        }
    }
}
