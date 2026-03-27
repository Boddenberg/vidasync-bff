package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.dto.response.InternalUserCloneAudit
import com.vidasync_bff.dto.response.InternalUserCloneCopied
import com.vidasync_bff.dto.response.InternalUserCloneResponse
import com.vidasync_bff.dto.response.InternalUserCloneSecurity
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class InternalAdminUserCloneService(
    private val supabaseClient: SupabaseClient
) {

    private val log = LoggerFactory.getLogger(InternalAdminUserCloneService::class.java)
    private val passwordEncoder = BCryptPasswordEncoder()

    fun cloneUser(
        sourceUserId: String,
        dryRun: Boolean,
        clonedBy: String
    ): InternalUserCloneResponse {
        val sourceId = sourceUserId.trim()
        if (sourceId.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "source user id obrigatorio")
        }
        val actor = clonedBy.trim()
        if (actor.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "header X-User-Id obrigatorio para auditoria")
        }

        val sourceProfile = loadSourceProfile(sourceId)
        val sourceMeals = loadSourceMeals(sourceId)
        val sourceFavorites = loadSourceFavorites(sourceId)

        val sourceUsername = sourceProfile["username"]?.toString()?.trim().orEmpty()
        if (sourceUsername.isBlank()) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "source user sem username valido")
        }

        val cloneUserId = UUID.randomUUID().toString()
        val cloneUsername = resolveUniqueCloneUsername(sourceUsername)
        val whenAt = OffsetDateTime.now(ZoneOffset.UTC).toString()

        log.info(
            "internal.user.clone.started source_user_id={} dry_run={} cloned_by={} source_meals={} source_favorites={}",
            sourceId, dryRun, actor, sourceMeals.size, sourceFavorites.size
        )

        var copiedProfile = 0
        var copiedMeals = 0
        var copiedFavorites = 0

        if (!dryRun) {
            copiedProfile = insertClonedProfile(
                sourceProfile = sourceProfile,
                targetUserId = cloneUserId,
                targetUsername = cloneUsername
            )
            copiedMeals = copyMeals(sourceMeals, cloneUserId)
            copiedFavorites = copyFavorites(sourceFavorites, cloneUserId)
        }

        // /**** Mantem trilha de auditoria para dry-run e execucao real. ****/
        insertAudit(
            clonedFrom = sourceId,
            clonedTo = cloneUserId,
            clonedBy = actor,
            dryRun = dryRun,
            whenAt = whenAt
        )

        log.info(
            "internal.user.clone.completed source_user_id={} cloned_user_id={} dry_run={} copied_profile={} copied_meals={} copied_favorites={}",
            sourceId, cloneUserId, dryRun, copiedProfile, copiedMeals, copiedFavorites
        )

        return InternalUserCloneResponse(
            sourceUserId = sourceId,
            clonedUserId = cloneUserId,
            clonedUsername = cloneUsername,
            dryRun = dryRun,
            copied = InternalUserCloneCopied(
                profile = if (dryRun) 1 else copiedProfile,
                meals = if (dryRun) sourceMeals.size else copiedMeals,
                favorites = if (dryRun) sourceFavorites.size else copiedFavorites
            ),
            audit = InternalUserCloneAudit(
                clonedFrom = sourceId,
                clonedBy = actor,
                whenAt = whenAt
            ),
            security = InternalUserCloneSecurity(
                passwordCopied = false,
                sessionsCopied = false
            )
        )
    }

    private fun loadSourceProfile(sourceUserId: String): Map<String, Any?> {
        val rows = supabaseClient.get(
            "user_profiles",
            mapOf("user_id" to "eq.$sourceUserId"),
            object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        ).orEmpty()

        val profile = rows.firstOrNull()
        if (profile == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "source user profile nao encontrado")
        }
        return profile
    }

    private fun loadSourceMeals(sourceUserId: String): List<Map<String, Any?>> {
        return supabaseClient.get(
            "meals",
            mapOf("user_id" to "eq.$sourceUserId"),
            object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        ).orEmpty()
    }

    private fun loadSourceFavorites(sourceUserId: String): List<Map<String, Any?>> {
        return supabaseClient.get(
            "favorite_meals",
            mapOf("user_id" to "eq.$sourceUserId"),
            object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        ).orEmpty()
    }

    private fun resolveUniqueCloneUsername(sourceUsername: String): String {
        repeat(20) {
            val suffix = UUID.randomUUID().toString().replace("-", "").takeLast(6)
            val candidate = buildCloneUsername(sourceUsername, suffix)
            val existing = supabaseClient.get(
                "user_profiles",
                mapOf("username" to "eq.$candidate"),
                object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
            ).orEmpty()
            if (existing.isEmpty()) return candidate
        }
        throw ResponseStatusException(HttpStatus.CONFLICT, "nao foi possivel gerar username unico para clone")
    }

    private fun insertClonedProfile(
        sourceProfile: Map<String, Any?>,
        targetUserId: String,
        targetUsername: String
    ): Int {
        val profileImageUrl = sourceProfile["profile_image_url"]?.toString()?.takeIf { it.isNotBlank() }
        val passwordHash = passwordEncoder.encode(UUID.randomUUID().toString())

        val baseBody = mutableMapOf<String, Any>(
            "user_id" to targetUserId,
            "username" to targetUsername,
        )
        profileImageUrl?.let { baseBody["profile_image_url"] = it }

        // /**** Nao copia password_hash do usuario original; cria hash novo aleatorio. ****/
        val withPasswordBody = baseBody.toMutableMap().apply { put("password_hash", passwordHash) }
        return try {
            val created = supabaseClient.post(
                "user_profiles",
                withPasswordBody,
                object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
            ).orEmpty()
            if (created.isEmpty()) 0 else 1
        } catch (ex: Exception) {
            if (!isMissingColumnError(ex, "password_hash")) throw ex
            log.warn("user_profiles sem coluna password_hash. Persistindo clone sem password_hash.")
            val created = supabaseClient.post(
                "user_profiles",
                baseBody,
                object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
            ).orEmpty()
            if (created.isEmpty()) 0 else 1
        }
    }

    private fun copyMeals(sourceMeals: List<Map<String, Any?>>, targetUserId: String): Int {
        if (sourceMeals.isEmpty()) return 0

        val payload = sourceMeals.map { row ->
            mutableMapOf<String, Any>(
                "user_id" to targetUserId,
                "meal_type" to (row["meal_type"]?.toString().orEmpty()),
                "foods" to (row["foods"]?.toString().orEmpty()),
                "date" to (row["date"]?.toString().orEmpty()),
                "calories" to (row["calories"]?.toString().orEmpty()),
                "protein" to (row["protein"]?.toString().orEmpty()),
                "carbs" to (row["carbs"]?.toString().orEmpty()),
                "fat" to (row["fat"]?.toString().orEmpty()),
            ).apply {
                row["time"]?.toString()?.takeIf { it.isNotBlank() }?.let { put("time", it) }
                row["image_url"]?.toString()?.takeIf { it.isNotBlank() }?.let { put("image_url", it) }
            }
        }

        val inserted = supabaseClient.post(
            "meals",
            payload,
            object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        ).orEmpty()
        return if (inserted.isEmpty()) payload.size else inserted.size
    }

    private fun copyFavorites(sourceFavorites: List<Map<String, Any?>>, targetUserId: String): Int {
        if (sourceFavorites.isEmpty()) return 0

        val payload = sourceFavorites.map { row ->
            mutableMapOf<String, Any>(
                "user_id" to targetUserId,
                "foods" to (row["foods"]?.toString().orEmpty()),
                "calories" to (row["calories"]?.toString().orEmpty()),
                "protein" to (row["protein"]?.toString().orEmpty()),
                "carbs" to (row["carbs"]?.toString().orEmpty()),
                "fat" to (row["fat"]?.toString().orEmpty()),
            ).apply {
                row["image_url"]?.toString()?.takeIf { it.isNotBlank() }?.let { put("image_url", it) }
            }
        }

        val inserted = supabaseClient.post(
            "favorite_meals",
            payload,
            object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        ).orEmpty()
        return if (inserted.isEmpty()) payload.size else inserted.size
    }

    private fun insertAudit(
        clonedFrom: String,
        clonedTo: String,
        clonedBy: String,
        dryRun: Boolean,
        whenAt: String
    ) {
        val body = mapOf(
            "cloned_from" to clonedFrom,
            "cloned_to" to clonedTo,
            "cloned_by" to clonedBy,
            "dry_run" to dryRun,
            "when_at" to whenAt
        )
        supabaseClient.post(
            "user_clone_audit",
            body,
            object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        )
        log.info(
            "internal.user.clone.audit_inserted cloned_from={} cloned_to={} cloned_by={} dry_run={} when_at={}",
            clonedFrom, clonedTo, clonedBy, dryRun, whenAt
        )
    }

    private fun isMissingColumnError(ex: Exception, column: String): Boolean {
        val message = ex.message?.lowercase().orEmpty()
        return message.contains(column.lowercase()) &&
            (message.contains("column") || message.contains("schema cache"))
    }

    companion object {
        internal fun buildCloneUsername(sourceUsername: String, suffix: String): String {
            val token = sourceUsername
                .lowercase()
                .filter { it.isLetterOrDigit() }
                .ifBlank { "user" }
            val normalizedSuffix = suffix.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "000000" }.takeLast(6)
            val baseMaxLen = 30 - "clone".length - normalizedSuffix.length
            val base = token.take(baseMaxLen.coerceAtLeast(1))
            return (base + "clone" + normalizedSuffix).take(30)
        }
    }
}
