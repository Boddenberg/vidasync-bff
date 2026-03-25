package com.vidasync_bff.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.vidasync_bff.dto.request.UpdateNotificationsRequest
import com.vidasync_bff.dto.response.NotificationItemResponse
import com.vidasync_bff.dto.response.NotificationMutationResponse
import com.vidasync_bff.dto.response.NotificationStatusResponse
import com.vidasync_bff.dto.response.NotificationsInboxResponse
import com.vidasync_bff.service.NotificationService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class NotificationControllerTests {

    private val notificationService = mock(NotificationService::class.java)
    private val mockMvc = MockMvcBuilders.standaloneSetup(NotificationController(notificationService)).build()
    private val objectMapper = ObjectMapper()

    @Test
    fun `deve retornar 200 no get inbox quando service responder com sucesso`() {
        val response = NotificationsInboxResponse(
            unreadCount = 1,
            notifications = listOf(
                NotificationItemResponse(
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
                    createdAt = "2026-03-15T16:20:00.000Z",
                    date = "2026-03-15",
                    time = "16:20:00"
                ),
                NotificationItemResponse(
                    id = "notif-2",
                    title = "Mensagem removida",
                    message = "Historico preservado.",
                    type = "INFO",
                    imageUrl = null,
                    actionLabel = null,
                    actionRoute = null,
                    readAt = "2026-03-15T16:21:00.000Z",
                    deleted = true,
                    deletedAt = "2026-03-15T16:22:00.000Z",
                    createdAt = "2026-03-15T16:19:00.000Z",
                    date = "2026-03-15",
                    time = "16:19:00"
                )
            )
        )

        `when`(notificationService.getInbox("user-1")).thenReturn(response)

        mockMvc.get("/notifications") {
            header("X-User-Id", "user-1")
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.unreadCount") { value(1) }
            jsonPath("$.notifications[0].id") { value("notif-1") }
            jsonPath("$.notifications[0].imageUrl") { value("https://cdn.exemplo.com/notificacoes/feedback.jpg") }
            jsonPath("$.notifications[0].deleted") { value(false) }
            jsonPath("$.notifications[1].id") { value("notif-2") }
            jsonPath("$.notifications[1].deleted") { value(true) }
        }
    }

    @Test
    fun `deve retornar 200 no mark read quando service responder com sucesso`() {
        val request = UpdateNotificationsRequest(notificationIds = listOf("notif-1"))
        val response = NotificationMutationResponse(
            unreadCount = 0,
            notifications = listOf(
                NotificationStatusResponse(
                    id = "notif-1",
                    readAt = "2026-03-15T16:25:00.000Z",
                    deleted = false,
                    deletedAt = null
                )
            )
        )

        `when`(notificationService.markRead("user-1", request)).thenReturn(response)

        mockMvc.post("/notifications/read") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.unreadCount") { value(0) }
            jsonPath("$.notifications[0].id") { value("notif-1") }
            jsonPath("$.notifications[0].readAt") { value("2026-03-15T16:25:00.000Z") }
            jsonPath("$.notifications[0].deleted") { value(false) }
        }
    }

    @Test
    fun `deve retornar 200 no mark delete quando service responder com sucesso`() {
        val request = UpdateNotificationsRequest(markAll = true)
        val response = NotificationMutationResponse(
            unreadCount = 0,
            notifications = listOf(
                NotificationStatusResponse(
                    id = "notif-1",
                    readAt = null,
                    deleted = true,
                    deletedAt = "2026-03-15T16:30:00.000Z"
                )
            )
        )

        `when`(notificationService.markDeleted("user-1", request)).thenReturn(response)

        mockMvc.post("/notifications/delete") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.unreadCount") { value(0) }
            jsonPath("$.notifications[0].id") { value("notif-1") }
            jsonPath("$.notifications[0].deleted") { value(true) }
            jsonPath("$.notifications[0].deletedAt") { value("2026-03-15T16:30:00.000Z") }
        }
    }

    @Test
    fun `deve retornar 400 no mark read quando service lancar illegal argument`() {
        val request = UpdateNotificationsRequest()

        doThrow(IllegalArgumentException("Informe notificationIds ou markAll=true"))
            .`when`(notificationService)
            .markRead("user-1", request)

        mockMvc.post("/notifications/read") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("Informe notificationIds ou markAll=true") }
        }
    }
}
