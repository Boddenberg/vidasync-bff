package com.vidasync_bff.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.vidasync_bff.dto.request.UpsertWaterRequest
import com.vidasync_bff.dto.response.WaterDailyResponse
import com.vidasync_bff.dto.response.WaterEventResponse
import com.vidasync_bff.dto.response.WaterHistoryResponse
import com.vidasync_bff.service.WaterService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class WaterControllerTests {

    private val waterService = mock(WaterService::class.java)
    private val mockMvc = MockMvcBuilders.standaloneSetup(WaterController(waterService)).build()
    private val objectMapper = ObjectMapper()

    @Test
    fun `deve retornar 200 no post water quando service responder com sucesso`() {
        val request = UpsertWaterRequest(
            date = "2026-03-19",
            goalMl = 2500,
            deltaMl = 300
        )
        val response = WaterDailyResponse(
            id = "daily-1",
            date = "2026-03-19",
            goalMl = 2500,
            consumedMl = 900,
            remainingMl = 1600,
            progressPercent = 36,
            goalReached = false,
            goalInherited = false,
            createdAt = "2026-03-19T10:15:30Z",
            updatedAt = "2026-03-19T10:20:30Z",
            events = listOf(
                WaterEventResponse(
                    id = "event-1",
                    date = "2026-03-19",
                    deltaMl = 300,
                    action = "ADD",
                    runningConsumedMl = 900
                )
            )
        )

        `when`(waterService.upsert("user-1", request)).thenReturn(response)

        mockMvc.post("/water") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.water.id") { value("daily-1") }
            jsonPath("$.water.date") { value("2026-03-19") }
            jsonPath("$.water.goalMl") { value(2500) }
            jsonPath("$.water.consumedMl") { value(900) }
            jsonPath("$.water.events[0].action") { value("ADD") }
        }
    }

    @Test
    fun `deve retornar 400 no post water quando service lancar illegal argument`() {
        val request = UpsertWaterRequest(
            date = "2026-03-19",
            goalMl = null,
            deltaMl = 0
        )

        doThrow(IllegalArgumentException("deltaMl nao pode ser zero sem uma nova meta"))
            .`when`(waterService)
            .upsert("user-1", request)

        mockMvc.post("/water") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("deltaMl nao pode ser zero sem uma nova meta") }
        }
    }

    @Test
    fun `deve retornar 200 no get water quando service responder com panorama do dia`() {
        val response = WaterDailyResponse(
            id = "daily-1",
            date = "2026-03-19",
            goalMl = 2500,
            consumedMl = 900,
            remainingMl = 1600,
            progressPercent = 36,
            goalReached = false,
            goalInherited = true,
            createdAt = "2026-03-19T10:15:30Z",
            updatedAt = "2026-03-19T10:20:30Z",
            events = emptyList()
        )

        `when`(waterService.getDay("user-1", "2026-03-19")).thenReturn(response)

        mockMvc.get("/water") {
            header("X-User-Id", "user-1")
            param("date", "2026-03-19")
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.water.id") { value("daily-1") }
            jsonPath("$.water.goalInherited") { value(true) }
            jsonPath("$.water.remainingMl") { value(1600) }
        }
    }

    @Test
    fun `deve retornar 200 no get water history quando service responder com historico`() {
        val response = WaterHistoryResponse(
            startDate = "2026-03-18",
            endDate = "2026-03-19",
            days = listOf(
                WaterDailyResponse(
                    id = "daily-1",
                    date = "2026-03-19",
                    goalMl = 2500,
                    consumedMl = 900,
                    remainingMl = 1600,
                    progressPercent = 36,
                    goalReached = false,
                    goalInherited = false,
                    createdAt = "2026-03-19T10:15:30Z",
                    updatedAt = "2026-03-19T10:20:30Z",
                    events = emptyList()
                )
            )
        )

        `when`(waterService.getHistory("user-1", "2026-03-18", "2026-03-19")).thenReturn(response)

        mockMvc.get("/water/history") {
            header("X-User-Id", "user-1")
            param("startDate", "2026-03-18")
            param("endDate", "2026-03-19")
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.waterHistory.startDate") { value("2026-03-18") }
            jsonPath("$.waterHistory.endDate") { value("2026-03-19") }
            jsonPath("$.waterHistory.days[0].date") { value("2026-03-19") }
        }
    }
}
