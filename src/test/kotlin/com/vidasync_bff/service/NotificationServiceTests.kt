package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.response.SupabaseNotificationRow
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.core.ParameterizedTypeReference
import kotlin.test.assertEquals

class NotificationServiceTests {

    private val supabaseClient = mock(SupabaseClient::class.java)
    private val service = NotificationService(supabaseClient = supabaseClient)

    @Test
    fun `deve excluir fisicamente todas as notificacoes do usuario`() {
        `when`(
            supabaseClient.get(
                eqValue("notifications"),
                eqValue(mapOf("user_id" to "eq.user-1", "order" to "created_at.desc,id.desc")),
                anyNotificationTypeRef()
            )
        ).thenReturn(listOf(notificationRow("notif-1"), notificationRow("notif-2")))

        val response = service.deleteAll(" user-1 ")

        assertEquals(2, response.deletedCount)
        verify(supabaseClient).delete("notifications", mapOf("user_id" to "eq.user-1"))
    }

    @Test
    fun `nao deve chamar delete quando usuario nao possuir notificacoes`() {
        `when`(
            supabaseClient.get(
                eqValue("notifications"),
                eqValue(mapOf("user_id" to "eq.user-1", "order" to "created_at.desc,id.desc")),
                anyNotificationTypeRef()
            )
        ).thenReturn(emptyList())

        val response = service.deleteAll("user-1")

        assertEquals(0, response.deletedCount)
        verify(supabaseClient, never()).delete("notifications", mapOf("user_id" to "eq.user-1"))
    }

    private fun notificationRow(id: String) = SupabaseNotificationRow(
        id = id,
        userId = "user-1",
        title = "Titulo",
        body = "Mensagem",
        message = "Mensagem",
        type = "INFO",
        isRead = false,
        isDeleted = false,
        createdAt = "2026-03-15T16:20:00Z"
    )

    private fun <T> eqValue(value: T): T {
        ArgumentMatchers.eq(value)
        return value
    }

    private fun anyNotificationTypeRef(): ParameterizedTypeReference<List<SupabaseNotificationRow>> {
        ArgumentMatchers.any(ParameterizedTypeReference::class.java)
        return uninitialized()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T
}
