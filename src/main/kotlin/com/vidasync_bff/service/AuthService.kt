package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.client.SupabaseStorageClient
import com.vidasync_bff.dto.request.AuthRequest
import com.vidasync_bff.dto.request.UpdateProfileRequest
import com.vidasync_bff.dto.response.AuthResponse
import com.vidasync_bff.dto.response.SupabaseAuthResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Service
class AuthService(
    @Value("\${supabase.url:}") private val supabaseUrl: String,
    @Value("\${supabase.anon-key:}") private val supabaseAnonKey: String,
    private val supabaseClient: SupabaseClient,
    private val storageClient: SupabaseStorageClient
) {

    private val log = LoggerFactory.getLogger(AuthService::class.java)
    private val usernameRegex = Regex("^[a-zA-Z0-9]+$")

    private val authClient: RestClient by lazy {
        var normalized = supabaseUrl.trim()
        while (normalized.endsWith("/")) normalized = normalized.dropLast(1)
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }

        RestClient.builder()
            .baseUrl("$normalized/auth/v1")
            .defaultHeader("apikey", supabaseAnonKey)
            .defaultHeader("Content-Type", "application/json")
            .build()
    }

    private fun toEmail(username: String): String = "${username.lowercase()}@vidasync.app"
    private fun toUsername(email: String): String = email.substringBefore("@")

    fun signup(request: AuthRequest): AuthResponse {
        log.info("AUTH SIGNUP | username={}, hasImage={}", request.username, request.profileImage != null)

        validateUsername(request.username)

        val email = toEmail(request.username)

        try {
            val supabaseResponse = authClient.post()
                .uri("/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("email" to email, "password" to request.password))
                .retrieve()
                .body(SupabaseAuthResponse::class.java)
                ?: throw RuntimeException("Resposta vazia do Supabase Auth")

            val user = supabaseResponse.user
                ?: throw RuntimeException("Usuário não retornado pelo Supabase")

            val userId = user.id ?: ""
            val username = toUsername(user.email ?: "")

            // Upload profile image if provided
            var profileImageUrl: String? = null
            request.profileImage?.takeIf { it.isNotBlank() }?.let { base64 ->
                try {
                    profileImageUrl = storageClient.uploadBase64Image(base64, "profile_$username")
                    log.info("AUTH SIGNUP | profile image uploaded: {}", profileImageUrl)
                } catch (e: Exception) {
                    log.error("AUTH SIGNUP | failed to upload profile image: {}", e.message)
                }
            }

            // Save profile to user_profiles table
            saveProfile(userId, username, profileImageUrl)

            val result = AuthResponse(userId = userId, username = username, profileImageUrl = profileImageUrl)
            log.info("AUTH SIGNUP → OK | userId={}, username={}", result.userId, result.username)
            return result

        } catch (e: RestClientResponseException) {
            val body = e.responseBodyAsString
            log.error("AUTH SIGNUP → FAILED | status={}, body={}", e.statusCode, body)
            val msg = parseSupabaseError(body)
            if (msg.contains("already been registered", ignoreCase = true)) {
                throw RuntimeException("Usuário '${request.username}' já existe")
            }
            throw RuntimeException(msg)
        }
    }

    fun login(request: AuthRequest): AuthResponse {
        log.info("AUTH LOGIN | username={}", request.username)

        val email = toEmail(request.username)

        try {
            val supabaseResponse = authClient.post()
                .uri("/token?grant_type=password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("email" to email, "password" to request.password))
                .retrieve()
                .body(SupabaseAuthResponse::class.java)
                ?: throw RuntimeException("Resposta vazia do Supabase Auth")

            val user = supabaseResponse.user
                ?: throw RuntimeException("Usuário não retornado pelo Supabase")

            val userId = user.id ?: ""
            val username = toUsername(user.email ?: "")

            // Fetch profile to get image URL
            val profileImageUrl = getProfileImageUrl(userId)

            val result = AuthResponse(userId = userId, username = username, profileImageUrl = profileImageUrl)
            log.info("AUTH LOGIN → OK | userId={}, username={}", result.userId, result.username)
            return result

        } catch (e: RestClientResponseException) {
            val body = e.responseBodyAsString
            log.error("AUTH LOGIN → FAILED | status={}, body={}", e.statusCode, body)
            throw RuntimeException("Usuário ou senha inválidos")
        }
    }

    fun getProfile(userId: String): AuthResponse {
        log.info("AUTH GET PROFILE | userId={}", userId)

        val rows = supabaseClient.get(
            "user_profiles",
            mapOf("user_id" to "eq.$userId"),
            object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        )

        val profile = rows?.firstOrNull()
            ?: throw RuntimeException("Perfil não encontrado")

        return AuthResponse(
            userId = userId,
            username = profile["username"] as? String ?: "",
            profileImageUrl = profile["profile_image_url"] as? String
        )
    }

    fun updateProfile(userId: String, request: UpdateProfileRequest): AuthResponse {
        log.info("AUTH UPDATE PROFILE | userId={}, hasUsername={}, hasPassword={}, hasImage={}",
            userId, request.username != null, request.password != null, request.profileImage != null)

        // 1. Update username (in Supabase Auth + user_profiles)
        request.username?.let { newUsername ->
            validateUsername(newUsername)
            val newEmail = toEmail(newUsername)

            // Update email in Supabase Auth
            try {
                authClient.put()
                    .uri("/admin/users/$userId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $supabaseAnonKey")
                    .body(mapOf("email" to newEmail))
                    .retrieve()
                    .toBodilessEntity()
                log.info("AUTH UPDATE | username changed to {}", newUsername)
            } catch (e: RestClientResponseException) {
                log.error("AUTH UPDATE | failed to change username: {}", e.responseBodyAsString)
                throw RuntimeException("Erro ao alterar username: ${parseSupabaseError(e.responseBodyAsString)}")
            }

            // Update username in user_profiles
            updateProfileField(userId, mapOf("username" to newUsername))
        }

        // 2. Update password in Supabase Auth
        request.password?.let { newPassword ->
            if (newPassword.length < 6) throw RuntimeException("Senha precisa ter pelo menos 6 caracteres")

            try {
                authClient.put()
                    .uri("/admin/users/$userId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $supabaseAnonKey")
                    .body(mapOf("password" to newPassword))
                    .retrieve()
                    .toBodilessEntity()
                log.info("AUTH UPDATE | password changed for userId={}", userId)
            } catch (e: RestClientResponseException) {
                log.error("AUTH UPDATE | failed to change password: {}", e.responseBodyAsString)
                throw RuntimeException("Erro ao alterar senha")
            }
        }

        // 3. Update profile image
        request.profileImage?.let { base64 ->
            if (base64.isNotBlank()) {
                try {
                    val currentProfile = supabaseClient.get(
                        "user_profiles",
                        mapOf("user_id" to "eq.$userId"),
                        object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
                    )?.firstOrNull()
                    val username = currentProfile?.get("username") as? String ?: "user"

                    val imageUrl = storageClient.uploadBase64Image(base64, "profile_$username")
                    updateProfileField(userId, mapOf("profile_image_url" to imageUrl))
                    log.info("AUTH UPDATE | profile image updated: {}", imageUrl)
                } catch (e: Exception) {
                    log.error("AUTH UPDATE | failed to upload image: {}", e.message)
                    throw RuntimeException("Erro ao atualizar foto de perfil")
                }
            }
        }

        // Return updated profile
        return getProfile(userId)
    }

    private fun updateProfileField(userId: String, fields: Map<String, Any>) {
        supabaseClient.patch(
            "user_profiles",
            mapOf("user_id" to "eq.$userId"),
            fields,
            object : ParameterizedTypeReference<List<Map<String, Any>>>() {}
        )
    }

    private fun saveProfile(userId: String, username: String, profileImageUrl: String?) {
        try {
            val body = mutableMapOf<String, Any>(
                "user_id" to userId,
                "username" to username
            )
            profileImageUrl?.let { body["profile_image_url"] = it }

            supabaseClient.post(
                "user_profiles", body,
                object : ParameterizedTypeReference<List<Map<String, Any>>>() {}
            )
            log.info("AUTH | profile saved for userId={}", userId)
        } catch (e: Exception) {
            log.error("AUTH | failed to save profile: {}", e.message)
        }
    }

    private fun getProfileImageUrl(userId: String): String? {
        return try {
            val rows = supabaseClient.get(
                "user_profiles",
                mapOf("user_id" to "eq.$userId"),
                object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
            )
            rows?.firstOrNull()?.get("profile_image_url") as? String
        } catch (e: Exception) {
            log.error("AUTH | failed to fetch profile: {}", e.message)
            null
        }
    }

    private fun validateUsername(username: String) {
        if (username.isBlank()) throw RuntimeException("Username não pode ser vazio")
        if (username.length < 3) throw RuntimeException("Username precisa ter pelo menos 3 caracteres")
        if (username.length > 30) throw RuntimeException("Username pode ter no máximo 30 caracteres")
        if (!usernameRegex.matches(username)) throw RuntimeException("Username só pode conter letras e números")
    }

    private fun parseSupabaseError(body: String): String {
        return try {
            val node = com.fasterxml.jackson.databind.ObjectMapper().readTree(body)
            node.get("error_description")?.asText()
                ?: node.get("msg")?.asText()
                ?: node.get("message")?.asText()
                ?: "Erro de autenticação"
        } catch (_: Exception) {
            "Erro de autenticação"
        }
    }
}
