package com.vidasync_bff.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.vidasync_bff.dto.request.UpsertNutritionGoalsRequest
import com.vidasync_bff.dto.response.DailyNutritionGoalsResponse
import com.vidasync_bff.dto.response.NutritionGoalTargets
import com.vidasync_bff.service.NutritionGoalsService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class NutritionGoalsControllerTests {

    private val nutritionGoalsService = mock(NutritionGoalsService::class.java)
    private val mockMvc = MockMvcBuilders.standaloneSetup(NutritionGoalsController(nutritionGoalsService)).build()
    private val objectMapper = ObjectMapper()

    @Test
    fun `deve retornar 200 no upsert quando service responder com sucesso`() {
        val request = UpsertNutritionGoalsRequest(
            date = "2026-03-19",
            caloriesGoal = 2200,
            proteinGoal = 150,
            carbsGoal = 220,
            fatGoal = 70
        )
        val response = DailyNutritionGoalsResponse(
            id = "goal-1",
            date = "2026-03-19",
            goals = NutritionGoalTargets(
                calories = 2200,
                protein = 150,
                carbs = 220,
                fat = 70
            ),
            goalInherited = false,
            createdAt = "2026-03-19T10:15:30Z",
            updatedAt = "2026-03-19T10:15:30Z"
        )

        `when`(nutritionGoalsService.upsert("user-1", request)).thenReturn(response)

        mockMvc.post("/nutrition-goals") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.nutritionGoals.id") { value("goal-1") }
            jsonPath("$.nutritionGoals.date") { value("2026-03-19") }
            jsonPath("$.nutritionGoals.goals.calories") { value(2200) }
            jsonPath("$.nutritionGoals.goals.protein") { value(150) }
            jsonPath("$.nutritionGoals.goalInherited") { value(false) }
        }
    }

    @Test
    fun `deve retornar 400 no upsert quando service lancar illegal argument`() {
        val request = UpsertNutritionGoalsRequest(
            date = "2026-03-19"
        )

        doThrow(IllegalArgumentException("Informe pelo menos uma meta no POST /nutrition-goals"))
            .`when`(nutritionGoalsService)
            .upsert("user-1", request)

        mockMvc.post("/nutrition-goals") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("Informe pelo menos uma meta no POST /nutrition-goals") }
        }
    }

    @Test
    fun `deve retornar 200 no get quando service responder com metas do dia`() {
        val response = DailyNutritionGoalsResponse(
            id = "goal-1",
            date = "2026-03-19",
            goals = NutritionGoalTargets(
                calories = 2200,
                protein = 150,
                carbs = 220,
                fat = 70
            ),
            goalInherited = true,
            createdAt = "2026-03-18T10:15:30Z",
            updatedAt = "2026-03-18T10:15:30Z"
        )

        `when`(nutritionGoalsService.getDay("user-1", "2026-03-19")).thenReturn(response)

        mockMvc.get("/nutrition-goals") {
            header("X-User-Id", "user-1")
            param("date", "2026-03-19")
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.nutritionGoals.id") { value("goal-1") }
            jsonPath("$.nutritionGoals.goals.fat") { value(70) }
            jsonPath("$.nutritionGoals.goalInherited") { value(true) }
        }
    }

    @Test
    fun `deve retornar 200 no get quando service responder sem metas`() {
        `when`(nutritionGoalsService.getDay("user-1", "2026-03-19")).thenReturn(null)

        mockMvc.get("/nutrition-goals") {
            header("X-User-Id", "user-1")
            param("date", "2026-03-19")
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            content { json("""{"nutritionGoals":null}""") }
        }
    }
}
