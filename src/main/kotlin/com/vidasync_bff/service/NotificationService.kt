package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.request.PublishNotificationBroadcastRequest
import com.vidasync_bff.dto.request.PublishNotificationToUserRequest
import com.vidasync_bff.dto.request.UpdateNotificationsRequest
import com.vidasync_bff.dto.response.NotificationBroadcastResponse
import com.vidasync_bff.dto.response.NotificationItemResponse
import com.vidasync_bff.dto.response.NotificationMutationResponse
import com.vidasync_bff.dto.response.NotificationStatusResponse
import com.vidasync_bff.dto.response.NotificationsInboxResponse
import com.vidasync_bff.dto.response.SupabaseNotificationRow
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class NotificationService(
    private val supabaseClient: SupabaseClient,
    @Value("\${internal.admin.api-key:}") private val internalAdminApiKey: String
) {

    private val log = LoggerFactory.getLogger(NotificationService::class.java)
    private val tableName = "notifications"
    private val notificationTypeRef = object : ParameterizedTypeReference<List<SupabaseNotificationRow>>() {}
    private val genericMapTypeRef = object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}

    fun getInbox(userId: String): NotificationsInboxResponse {
        val normalizedUserId = normalizeUserId(userId)
        log.info("Buscando notificacoes: userId={}", normalizedUserId)

        val rows = loadAllRows(normalizedUserId)
        return NotificationsInboxResponse(
            unreadCount = rows.count { !isNotificationRead(it) && !it.isDeleted },
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
            .filter { !isNotificationRead(it) && !it.isDeleted }
            .map { it.id }

        val resultRows = if (idsToUpdate.isEmpty()) {
            targetRows
        } else {
            updateRows(
                userId = normalizedUserId,
                ids = idsToUpdate,
                additionalQueryParams = mapOf(
                    "is_deleted" to "eq.false",
                    "or" to "(read_at.is.null,is_read.is.false)"
                ),
                body = mapOf(
                    "read_at" to nowUtc(),
                    "is_read" to true
                ),
                errorMessage = "Nao foi possivel marcar as notificacoes como lidas"
            )
        }

        return NotificationMutationResponse(
            unreadCount = loadUnreadActiveRows(normalizedUserId).size,
            notifications = resultRows.map(NotificationStatusResponse::from)
        )
    }

    fun publishToUser(
        createdBy: String,
        providedInternalApiKey: String?,
        request: PublishNotificationToUserRequest
    ): NotificationItemResponse {
        validateInternalAccess(createdBy, providedInternalApiKey)

        val targetUserId = normalizeRequiredField(request.userId, "userId obrigatorio")
        val payload = normalizePayload(
            title = request.title,
            message = request.message,
            type = request.type,
            imageUrl = request.imageUrl,
            actionLabel = request.actionLabel,
            actionRoute = request.actionRoute
        )

        ensureUserExists(targetUserId)

        log.info(
            "Publicando notificacao para usuario: actorUserId={}, targetUserId={}, type={}",
            createdBy.trim(),
            targetUserId,
            payload.type
        )

        val rows = supabaseClient.post(
            tableName,
            payload.toDatabaseBody(targetUserId),
            notificationTypeRef
        ) ?: throw RuntimeException("Nao foi possivel publicar a notificacao")

        val saved = rows.firstOrNull() ?: throw RuntimeException("Resposta vazia ao publicar a notificacao")
        return NotificationItemResponse.from(saved)
    }

    fun publishToAll(
        createdBy: String,
        providedInternalApiKey: String?,
        request: PublishNotificationBroadcastRequest
    ): NotificationBroadcastResponse {
        validateInternalAccess(createdBy, providedInternalApiKey)

        val payload = normalizePayload(
            title = request.title,
            message = request.message,
            type = request.type,
            imageUrl = request.imageUrl,
            actionLabel = request.actionLabel,
            actionRoute = request.actionRoute
        )

        val userIds = loadAllUserIds()
        if (userIds.isEmpty()) {
            log.info("Broadcast de notificacoes sem usuarios alvo: actorUserId={}", createdBy.trim())
            return NotificationBroadcastResponse(createdCount = 0)
        }

        val rows = supabaseClient.post(
            tableName,
            userIds.map { userId -> payload.toDatabaseBody(userId) },
            notificationTypeRef
        ) ?: throw RuntimeException("Nao foi possivel publicar a notificacao para todos os usuarios")

        log.info(
            "Broadcast de notificacoes concluido: actorUserId={}, createdCount={}, type={}",
            createdBy.trim(),
            rows.size,
            payload.type
        )

        return NotificationBroadcastResponse(createdCount = rows.size)
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
                "or" to "(read_at.is.null,is_read.is.false)",
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

    private fun normalizeRequiredField(value: String, errorMessage: String): String {
        val normalized = value.trim()
        if (normalized.isBlank()) {
            throw IllegalArgumentException(errorMessage)
        }
        return normalized
    }

    private fun validateInternalAccess(actorUserId: String, providedInternalApiKey: String?) {
        if (actorUserId.trim().isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "header X-User-Id obrigatorio para auditoria")
        }
        if (internalAdminApiKey.isBlank()) {
            return
        }
        if (providedInternalApiKey.isNullOrBlank() || providedInternalApiKey != internalAdminApiKey) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "internal api key invalida")
        }
    }

    private fun ensureUserExists(userId: String) {
        val rows = supabaseClient.get(
            "user_profiles",
            mapOf(
                "user_id" to "eq.$userId",
                "limit" to "1"
            ),
            genericMapTypeRef
        ) ?: emptyList()

        if (rows.isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "usuario destino nao encontrado")
        }
    }

    private fun loadAllUserIds(): List<String> {
        return (supabaseClient.get(
            "user_profiles",
            mapOf("order" to "created_at.asc"),
            genericMapTypeRef
        ) ?: emptyList())
            .mapNotNull { it["user_id"]?.toString()?.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun normalizePayload(
        title: String,
        message: String,
        type: String?,
        imageUrl: String?,
        actionLabel: String?,
        actionRoute: String?
    ): PublishPayload {
        val normalizedTitle = title.trim()
        val normalizedMessage = message.trim()
        val normalizedType = type?.trim()?.takeIf { it.isNotBlank() }?.uppercase() ?: "INFO"
        val normalizedImageUrl = imageUrl?.trim()?.takeIf { it.isNotBlank() }
        val normalizedActionLabel = actionLabel?.trim()?.takeIf { it.isNotBlank() }
        val normalizedActionRoute = actionRoute?.trim()?.takeIf { it.isNotBlank() }

        if (normalizedTitle.isBlank()) {
            throw IllegalArgumentException("title obrigatorio")
        }
        if (normalizedMessage.isBlank()) {
            throw IllegalArgumentException("message obrigatoria")
        }
        if ((normalizedActionLabel == null) != (normalizedActionRoute == null)) {
            throw IllegalArgumentException("actionLabel e actionRoute devem ser informados juntos")
        }

        return PublishPayload(
            title = normalizedTitle,
            message = normalizedMessage,
            type = normalizedType,
            imageUrl = normalizedImageUrl,
            actionLabel = normalizedActionLabel,
            actionRoute = normalizedActionRoute
        )
    }

    private fun buildInFilter(ids: List<String>): String {
        return "in.(${ids.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }})"
    }

    private fun nowUtc(): String {
        return OffsetDateTime.now(ZoneOffset.UTC).toString()
    }

    private fun isNotificationRead(row: SupabaseNotificationRow): Boolean {
        return row.readAt != null || row.isRead == true
    }

    private sealed interface NotificationSelection {
        data object All : NotificationSelection
        data class ByIds(val ids: List<String>) : NotificationSelection
    }

    private data class PublishPayload(
        val title: String,
        val message: String,
        val type: String,
        val imageUrl: String?,
        val actionLabel: String?,
        val actionRoute: String?
    ) {
        fun toDatabaseBody(userId: String): Map<String, Any> {
            val now = OffsetDateTime.now(ZoneOffset.UTC).toString()
            val body = mutableMapOf<String, Any>(
                "user_id" to userId,
                "title" to title,
                "body" to message,
                "message" to message,
                "type" to type,
                "channel" to "push",
                "priority" to "normal",
                "is_read" to false,
                "is_deleted" to false,
                "sent_at" to now
            )
            imageUrl?.let { body["image_url"] = it }
            actionLabel?.let { body["action_label"] = it }
            actionRoute?.let { body["action_route"] = it }
            return body
        }
    }
}
