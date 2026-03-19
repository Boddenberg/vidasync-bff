package com.vidasync_bff.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.vidasync_bff.dto.request.CreateWeightRequest
import com.vidasync_bff.dto.response.WeightEntryResponse
import com.vidasync_bff.service.WeightService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class WeightControllerTests {

    private val weightService = mock(WeightService::class.java)
    private val mockMvc = MockMvcBuilders.standaloneSetup(WeightController(weightService)).build()
    private val objectMapper = ObjectMapper()

    @Test
    fun `deve retornar 200 no create quando service responder com sucesso`() {
        val request = CreateWeightRequest(weightKg = 82.4)
        val response = WeightEntryResponse(
            id = "weight-1",
            weightKg = 82.4,
            measuredAt = "2026-03-19T10:15:30Z",
            date = "2026-03-19",
            time = "10:15:30"
        )

        `when`(weightService.create("user-1", request)).thenReturn(response)

        mockMvc.post("/weight") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.weight.id") { value("weight-1") }
            jsonPath("$.weight.weightKg") { value(82.4) }
            jsonPath("$.weight.measuredAt") { value("2026-03-19T10:15:30Z") }
            jsonPath("$.weight.date") { value("2026-03-19") }
            jsonPath("$.weight.time") { value("10:15:30") }
        }
    }

    @Test
    fun `deve retornar 400 no create quando service lancar illegal argument`() {
        val request = CreateWeightRequest(weightKg = 0.0)

        doThrow(IllegalArgumentException("weightKg deve ser maior que zero"))
            .`when`(weightService)
            .create("user-1", request)

        mockMvc.post("/weight") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("weightKg deve ser maior que zero") }
        }
    }

    @Test
    fun `deve retornar 200 no get all quando service responder com sucesso`() {
        val response = listOf(
            WeightEntryResponse(
                id = "weight-1",
                weightKg = 82.4,
                measuredAt = "2026-03-19T10:15:30Z",
                date = "2026-03-19",
                time = "10:15:30"
            )
        )

        `when`(weightService.getAll("user-1")).thenReturn(response)

        mockMvc.get("/weight") {
            header("X-User-Id", "user-1")
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.weights[0].id") { value("weight-1") }
            jsonPath("$.weights[0].weightKg") { value(82.4) }
            jsonPath("$.weights[0].date") { value("2026-03-19") }
        }
    }
}
