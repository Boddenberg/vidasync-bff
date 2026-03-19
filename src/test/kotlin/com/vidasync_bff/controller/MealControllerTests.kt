package com.vidasync_bff.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.vidasync_bff.dto.request.CreateMealRequest
import com.vidasync_bff.dto.response.MealResponse
import com.vidasync_bff.dto.response.NutritionData
import com.vidasync_bff.service.MealService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class MealControllerTests {

    private val mealService = mock(MealService::class.java)
    private val mockMvc = MockMvcBuilders.standaloneSetup(MealController(mealService)).build()
    private val objectMapper = ObjectMapper()

    @Test
    fun `deve retornar 200 no create quando service responder com sucesso`() {
        val request = CreateMealRequest(
            foods = "200g arroz, 150g frango",
            mealType = "lunch",
            date = "2026-03-19",
            time = "12:30",
            nutrition = NutritionData(
                calories = "610 kcal",
                protein = "35g",
                carbs = "77g",
                fat = "12g"
            ),
            image = "base64-image"
        )
        val response = MealResponse(
            id = "meal-1",
            foods = "200g arroz, 150g frango",
            mealType = "lunch",
            date = "2026-03-19",
            time = "12:30",
            nutrition = NutritionData(
                calories = "610 kcal",
                protein = "35g",
                carbs = "77g",
                fat = "12g"
            ),
            imageUrl = "https://cdn.example.com/meal-1.jpg",
            createdAt = "2026-03-19T12:30:00Z"
        )

        `when`(mealService.create("user-1", request)).thenReturn(response)

        mockMvc.post("/meals") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.meal.id") { value("meal-1") }
            jsonPath("$.meal.mealType") { value("lunch") }
            jsonPath("$.meal.nutrition.calories") { value("610 kcal") }
            jsonPath("$.meal.imageUrl") { value("https://cdn.example.com/meal-1.jpg") }
        }
    }

    @Test
    fun `deve retornar 500 no create quando service lancar excecao`() {
        val request = CreateMealRequest(
            foods = "200g arroz, 150g frango",
            mealType = "lunch",
            date = "2026-03-19"
        )

        doThrow(RuntimeException("erro ao criar refeicao"))
            .`when`(mealService)
            .create("user-1", request)

        mockMvc.post("/meals") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isInternalServerError() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("erro ao criar refeicao") }
        }
    }

    @Test
    fun `deve retornar 200 no get by date quando service responder com sucesso`() {
        val response = listOf(
            MealResponse(
                id = "meal-1",
                foods = "200g arroz, 150g frango",
                mealType = "lunch",
                date = "2026-03-19",
                time = "12:30",
                nutrition = NutritionData(
                    calories = "610 kcal",
                    protein = "35g",
                    carbs = "77g",
                    fat = "12g"
                ),
                imageUrl = null,
                createdAt = "2026-03-19T12:30:00Z"
            )
        )

        `when`(mealService.getByDate("user-1", "2026-03-19")).thenReturn(response)

        mockMvc.get("/meals") {
            header("X-User-Id", "user-1")
            param("date", "2026-03-19")
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.meals[0].id") { value("meal-1") }
            jsonPath("$.meals[0].foods") { value("200g arroz, 150g frango") }
            jsonPath("$.meals[0].time") { value("12:30") }
        }
    }
}
