package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.request.UpdateNotificationsRequest
import com.vidasync_bff.dto.response.NotificationItemResponse
import com.vidasync_bff.dto.response.NotificationMutationResponse
import com.vidasync_bff.dto.response.NotificationStatusResponse
import com.vidasync_bff.dto.response.NotificationsInboxResponse
import com.vidasync_bff.dto.response.SupabaseNotificationRow
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class NotificationService(
    private val supabaseClient: SupabaseClient
) {

    private val log = LoggerFactory.getLogger(NotificationService::class.java)
    private val tableName = "notifications"
    private val notificationTypeRef = object : ParameterizedTypeReference<List<SupabaseNotificationRow>>() {}

    fun getInbox(userId: String): NotificationsInboxResponse {
        val normalizedUserId = normalizeUserId(userId)
        log.info("Buscando notificacoes: userId={}", normalizedUserId)

        val rows = loadAllRows(normalizedUserId)
        return NotificationsInboxResponse(
            unreadCount = rows.count { it.readAt == null && !it.isDeleted },
            notifications = rows.map(NotificationItemResponse::from)
        )
    }

    fun markRead(userId: String, request: UpdateNotificationsRequest): NotificationMutationResponse {
        val normalizedUserId = normalizeUserId(userId)
        val selection = resolveSelection(request)

        log.info("Marcando notificacoes como lidas: userId={}, markAll={}", normalizedUserId, selection is NotificationSelection.All)

        val targetRows = when (selection) {
            NotificationSelection.All -> loadUnreadActiveRows(normalizedUserId)
            is NotificationSelection.ByIds -> loadRowsByIds(normalizedUserId, selection.ids)
        }

        val idsToUpdate = targetRows
            .filter { it.readAt == null && !it.isDeleted }
            .map { it.id }

        val resultRows = if (idsToUpdate.isEmpty()) {
            targetRows
        } else {
            updateRows(
                userId = normalizedUserId,
                ids = idsToUpdate,
                additionalQueryParams = mapOf(
                    "is_deleted" to "eq.false",
                    "read_at" to "is.null"
                ),
                body = mapOf("read_at" to nowUtc()),
                errorMessage = "Nao foi possivel marcar as notificacoes como lidas"
            )
        }

        return NotificationMutationResponse(
            unreadCount = loadUnreadActiveRows(normalizedUserId).size,
            notifications = resultRows.map(NotificationStatusResponse::from)
        )
    }

    fun markDeleted(userId: String, request: UpdateNotificationsRequest): NotificationMutationResponse {
        val normalizedUserId = normalizeUserId(userId)
        val selection = resolveSelection(request)

        log.info("Marcando notificacoes como deletadas: userId={}, markAll={}", normalizedUserId, selection is NotificationSelection.All)

        val targetRows = when (selection) {
            NotificationSelection.All -> loadActiveRows(normalizedUserId)
            is NotificationSelection.ByIds -> loadRowsByIds(normalizedUserId, selection.ids)
        }

        val idsToUpdate = targetRows
            .filter { !it.isDeleted }
            .map { it.id }

        val resultRows = if (idsToUpdate.isEmpty()) {
            targetRows
        } else {
            updateRows(
                userId = normalizedUserId,
                ids = idsToUpdate,
                additionalQueryParams = mapOf(
                    "is_deleted" to "eq.false"
                ),
                body = mapOf(
                    "is_deleted" to true,
                    "deleted_at" to nowUtc()
                ),
                errorMessage = "Nao foi possivel marcar as notificacoes como deletadas"
            )
        }

        return NotificationMutationResponse(
            unreadCount = loadUnreadActiveRows(normalizedUserId).size,
            notifications = resultRows.map(NotificationStatusResponse::from)
        )
    }

    private fun updateRows(
        userId: String,
        ids: List<String>,
        additionalQueryParams: Map<String, String>,
        body: Map<String, Any>,
        errorMessage: String
    ): List<SupabaseNotificationRow> {
        val queryParams = mutableMapOf<String, String>()
        queryParams["user_id"] = "eq.$userId"
        queryParams["id"] = buildInFilter(ids)
        queryParams.putAll(additionalQueryParams)

        val rows = supabaseClient.patch(
            tableName,
            queryParams,
            body,
            notificationTypeRef
        ) ?: throw RuntimeException(errorMessage)

        if (rows.isEmpty()) {
            throw RuntimeException(errorMessage)
        }

        return rows
    }

    private fun loadAllRows(userId: String): List<SupabaseNotificationRow> {
        return supabaseClient.get(
            tableName,
            mapOf(
                "user_id" to "eq.$userId",
                "order" to "created_at.desc,id.desc"
            ),
            notificationTypeRef
        ) ?: emptyList()
    }

    private fun loadActiveRows(userId: String): List<SupabaseNotificationRow> {
        return supabaseClient.get(
            tableName,
            mapOf(
                "user_id" to "eq.$userId",
                "is_deleted" to "eq.false",
                "order" to "created_at.desc,id.desc"
            ),
            notificationTypeRef
        ) ?: emptyList()
    }

    private fun loadUnreadActiveRows(userId: String): List<SupabaseNotificationRow> {
        return supabaseClient.get(
            tableName,
            mapOf(
                "user_id" to "eq.$userId",
                "is_deleted" to "eq.false",
                "read_at" to "is.null",
                "order" to "created_at.desc,id.desc"
            ),
            notificationTypeRef
        ) ?: emptyList()
    }

    private fun loadRowsByIds(userId: String, ids: List<String>): List<SupabaseNotificationRow> {
        if (ids.isEmpty()) {
            return emptyList()
        }

        return supabaseClient.get(
            tableName,
            mapOf(
                "user_id" to "eq.$userId",
                "id" to buildInFilter(ids),
                "order" to "created_at.desc,id.desc"
            ),
            notificationTypeRef
        ) ?: emptyList()
    }

    private fun resolveSelection(request: UpdateNotificationsRequest): NotificationSelection {
        val ids = request.notificationIds
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val markAll = request.markAll == true

        if (markAll && ids.isNotEmpty()) {
            throw IllegalArgumentException("Informe notificationIds ou markAll=true, nao ambos")
        }
        if (!markAll && ids.isEmpty()) {
            throw IllegalArgumentException("Informe notificationIds ou markAll=true")
        }

        return if (markAll) {
            NotificationSelection.All
        } else {
            NotificationSelection.ByIds(ids)
        }
    }

    private fun normalizeUserId(userId: String): String {
        val normalized = userId.trim()
        if (normalized.isBlank()) {
            throw IllegalArgumentException("header X-User-Id obrigatorio")
        }
        return normalized
    }

    private fun buildInFilter(ids: List<String>): String {
        return "in.(${ids.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }})"
    }

    private fun nowUtc(): String {
        return OffsetDateTime.now(ZoneOffset.UTC).toString()
    }

    private sealed interface NotificationSelection {
        data object All : NotificationSelection
        data class ByIds(val ids: List<String>) : NotificationSelection
    }
}
