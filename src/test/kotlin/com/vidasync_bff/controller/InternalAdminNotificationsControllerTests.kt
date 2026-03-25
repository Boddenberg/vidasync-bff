package com.vidasync_bff.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.vidasync_bff.dto.request.PublishNotificationBroadcastRequest
import com.vidasync_bff.dto.request.PublishNotificationToUserRequest
import com.vidasync_bff.dto.response.NotificationBroadcastResponse
import com.vidasync_bff.dto.response.NotificationItemResponse
import com.vidasync_bff.service.NotificationService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException

class InternalAdminNotificationsControllerTests {

    private val notificationService = mock(NotificationService::class.java)
    private val mockMvc = MockMvcBuilders
        .standaloneSetup(InternalAdminNotificationsController(notificationService))
        .build()
    private val objectMapper = ObjectMapper()

    @Test
    fun `deve retornar 201 no publish to user quando service responder com sucesso`() {
        val request = PublishNotificationToUserRequest(
            userId = "user-1",
            title = "Resposta da equipe",
            message = "Respondemos seu feedback.",
            type = "INFO",
            imageUrl = "https://cdn.exemplo.com/notificacoes/feedback.jpg",
            actionLabel = "Abrir feedback",
            actionRoute = "/feedback"
        )
        val response = NotificationItemResponse(
            id = "notif-1",
            title = "Resposta da equipe",
            message = "Respondemos seu feedback.",
            type = "INFO",
            imageUrl = "https://cdn.exemplo.com/notificacoes/feedback.jpg",
            actionLabel = "Abrir feedback",
            actionRoute = "/feedback",
            readAt = null,
            deleted = false,
            deletedAt = null,
            createdAt = "2026-03-25T12:00:00.000Z",
            date = "2026-03-25",
            time = "12:00:00"
        )

        `when`(notificationService.publishToUser("admin-1", "secret-key", request)).thenReturn(response)

        mockMvc.post("/internal/admin/notifications") {
            header("X-User-Id", "admin-1")
            header("X-Internal-Api-Key", "secret-key")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.notification.id") { value("notif-1") }
            jsonPath("$.notification.title") { value("Resposta da equipe") }
            jsonPath("$.notification.imageUrl") { value("https://cdn.exemplo.com/notificacoes/feedback.jpg") }
            jsonPath("$.notification.deleted") { value(false) }
        }
    }

    @Test
    fun `deve retornar 200 no publish to all quando service responder com sucesso`() {
        val request = PublishNotificationBroadcastRequest(
            title = "Comunicado geral",
            message = "Hoje teremos manutencao programada.",
            type = "WARNING"
        )
        val response = NotificationBroadcastResponse(createdCount = 42)

        `when`(notificationService.publishToAll("admin-1", "secret-key", request)).thenReturn(response)

        mockMvc.post("/internal/admin/notifications/broadcast") {
            header("X-User-Id", "admin-1")
            header("X-Internal-Api-Key", "secret-key")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.createdCount") { value(42) }
        }
    }

    @Test
    fun `deve retornar status da response status exception no publish to user`() {
        val request = PublishNotificationToUserRequest(
            userId = "user-inexistente",
            title = "Teste",
            message = "Teste"
        )

        `when`(notificationService.publishToUser("admin-1", "wrong-key", request))
            .thenThrow(ResponseStatusException(HttpStatus.UNAUTHORIZED, "internal api key invalida"))

        mockMvc.post("/internal/admin/notifications") {
            header("X-User-Id", "admin-1")
            header("X-Internal-Api-Key", "wrong-key")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("internal api key invalida") }
        }
    }

    @Test
    fun `deve retornar 400 no publish to all quando service lancar illegal argument`() {
        val request = PublishNotificationBroadcastRequest(
            title = " ",
            message = "Mensagem"
        )

        doThrow(IllegalArgumentException("title obrigatorio"))
            .`when`(notificationService)
            .publishToAll("admin-1", "secret-key", request)

        mockMvc.post("/internal/admin/notifications/broadcast") {
            header("X-User-Id", "admin-1")
            header("X-Internal-Api-Key", "secret-key")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("title obrigatorio") }
        }
    }
}
