package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.request.CreateFeedbackRequest
import com.vidasync_bff.dto.response.FeedbackEntryResponse
import com.vidasync_bff.dto.response.SupabaseFeedbackRow
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class FeedbackService(
    private val supabaseClient: SupabaseClient,
    @Value("\${internal.admin.api-key:}") private val internalAdminApiKey: String
) {

    private val log = LoggerFactory.getLogger(FeedbackService::class.java)
    private val tableName = "developer_feedback"
    private val feedbackTypeRef = object : ParameterizedTypeReference<List<SupabaseFeedbackRow>>() {}

    fun create(userId: String, request: CreateFeedbackRequest): FeedbackEntryResponse {
        val normalizedUserId = userId.trim()
        val normalizedUserName = request.userName.trim()
        val normalizedMessage = request.message.trim()
        val normalizedImageUrl = request.imageUrl?.trim()?.takeIf { it.isNotBlank() }

        if (normalizedUserId.isBlank()) {
            throw IllegalArgumentException("header X-User-Id obrigatorio")
        }
        if (normalizedUserName.isBlank()) {
            throw IllegalArgumentException("userName obrigatorio")
        }
        if (normalizedMessage.isBlank()) {
            throw IllegalArgumentException("message obrigatoria")
        }

        log.info(
            "Salvando feedback: userId={}, userName={}, hasImage={}",
            normalizedUserId, normalizedUserName, normalizedImageUrl != null
        )

        val body = mutableMapOf<String, Any>(
            "user_id" to normalizedUserId,
            "user_name" to normalizedUserName,
            "message" to normalizedMessage
        )
        normalizedImageUrl?.let { body["image_url"] = it }

        val rows = supabaseClient.post(
            tableName,
            body,
            feedbackTypeRef
        ) ?: throw RuntimeException("Nao foi possivel salvar o feedback")

        val saved = rows.firstOrNull() ?: throw RuntimeException("Resposta vazia ao salvar o feedback")
        return FeedbackEntryResponse.from(saved)
    }

    fun getAll(actorUserId: String, providedInternalApiKey: String?): List<FeedbackEntryResponse> {
        validateInternalAccess(actorUserId, providedInternalApiKey)

        log.info("Buscando feedbacks para admin: actorUserId={}", actorUserId)

        val rows = supabaseClient.get(
            tableName,
            mapOf(
                "order" to "created_at.desc,id.desc"
            ),
            feedbackTypeRef
        ) ?: emptyList()

        return rows.map(FeedbackEntryResponse::from)
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
}
