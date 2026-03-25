package com.vidasync_bff.dto.response

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationResponseTests {

    @Test
    fun `deve preservar image url e usar body como fallback da message`() {
        val row = SupabaseNotificationRow(
            id = "notif-1",
            userId = "user-1",
            title = "Novo recado",
            body = "Texto da mensagem",
            message = null,
            type = "INFO",
            imageUrl = "https://cdn.exemplo.com/notificacoes/recado.jpg",
            actionLabel = "Abrir recado",
            actionRoute = "/alguma-rota",
            readAt = null,
            isDeleted = false,
            deletedAt = null,
            createdAt = "2026-03-25T16:50:00.000Z"
        )

        val response = NotificationItemResponse.from(row)

        assertEquals("Texto da mensagem", response.message)
        assertEquals("https://cdn.exemplo.com/notificacoes/recado.jpg", response.imageUrl)
        assertEquals("Abrir recado", response.actionLabel)
        assertEquals("/alguma-rota", response.actionRoute)
    }
}
