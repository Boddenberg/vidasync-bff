package com.vidasync_bff.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.vidasync_bff.dto.request.CreateFeedbackRequest
import com.vidasync_bff.dto.response.FeedbackEntryResponse
import com.vidasync_bff.service.FeedbackService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException

class FeedbackControllerTests {

    private val feedbackService = mock(FeedbackService::class.java)
    private val mockMvc = MockMvcBuilders.standaloneSetup(FeedbackController(feedbackService)).build()
    private val objectMapper = ObjectMapper()

    @Test
    fun `deve retornar 200 no create quando service responder com sucesso`() {
        val request = CreateFeedbackRequest(
            userName = "Joao",
            message = "Gostei da funcionalidade",
            imageUrl = "https://cdn.example.com/feedback.png"
        )
        val response = FeedbackEntryResponse(
            id = "fb-1",
            userId = "user-1",
            userName = "Joao",
            message = "Gostei da funcionalidade",
            imageUrl = "https://cdn.example.com/feedback.png",
            status = "OPEN",
            developerResponse = null,
            respondedAt = null,
            respondedBy = null,
            responseSeenAt = null,
            createdAt = "2026-03-19T10:15:30Z",
            updatedAt = "2026-03-19T10:15:30Z",
            date = "2026-03-19",
            time = "10:15:30"
        )

        `when`(feedbackService.create("user-1", request)).thenReturn(response)

        mockMvc.post("/feedback") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.feedback.id") { value("fb-1") }
            jsonPath("$.feedback.userId") { value("user-1") }
            jsonPath("$.feedback.userName") { value("Joao") }
            jsonPath("$.feedback.message") { value("Gostei da funcionalidade") }
            jsonPath("$.feedback.status") { value("OPEN") }
            jsonPath("$.feedback.date") { value("2026-03-19") }
            jsonPath("$.feedback.time") { value("10:15:30") }
        }
    }

    @Test
    fun `deve retornar 400 no create quando service lancar illegal argument`() {
        val request = CreateFeedbackRequest(
            userName = "   ",
            message = "mensagem"
        )

        doThrow(IllegalArgumentException("userName obrigatorio"))
            .`when`(feedbackService)
            .create("user-1", request)

        mockMvc.post("/feedback") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("userName obrigatorio") }
        }
    }

    @Test
    fun `deve retornar 200 no get all quando service responder com sucesso`() {
        val response = listOf(
            FeedbackEntryResponse(
                id = "fb-1",
                userId = "user-1",
                userName = "Joao",
                message = "Gostei da funcionalidade",
                imageUrl = null,
                status = "OPEN",
                developerResponse = null,
                respondedAt = null,
                respondedBy = null,
                responseSeenAt = null,
                createdAt = "2026-03-19T10:15:30Z",
                updatedAt = "2026-03-19T10:15:30Z",
                date = "2026-03-19",
                time = "10:15:30"
            )
        )

        `when`(feedbackService.getAll("admin-1")).thenReturn(response)

        mockMvc.get("/feedback") {
            header("X-User-Id", "admin-1")
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.feedbacks[0].id") { value("fb-1") }
            jsonPath("$.feedbacks[0].userId") { value("user-1") }
            jsonPath("$.feedbacks[0].status") { value("OPEN") }
        }
    }

    @Test
    fun `deve retornar status da response status exception no get all`() {
        `when`(feedbackService.getAll(" "))
            .thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST, "header X-User-Id obrigatorio para auditoria"))

        mockMvc.get("/feedback") {
            header("X-User-Id", " ")
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("header X-User-Id obrigatorio para auditoria") }
        }
    }
}
