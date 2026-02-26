package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseClient
import com.vidasync_bff.client.SupabaseStorageClient
import com.vidasync_bff.dto.request.AuthRequest
import com.vidasync_bff.dto.request.UpdateProfileRequest
import com.vidasync_bff.dto.response.AuthResponse
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuthService(
    private val supabaseClient: SupabaseClient,
    private val storageClient: SupabaseStorageClient
) {

    private val log = LoggerFactory.getLogger(AuthService::class.java)
    private val usernameRegex = Regex("^[a-zA-Z0-9]+$")
    private val passwordEncoder = BCryptPasswordEncoder()

    fun signup(request: AuthRequest): AuthResponse {
        log.info("AUTH SIGNUP | username={}, hasImage={}", request.username, request.profileImage != null)

        validateUsername(request.username)
        if (request.password.length < 6) throw RuntimeException("Senha precisa ter pelo menos 6 caracteres")

        // Check if username already exists
        val existing = supabaseClient.get(
            "user_profiles",
            mapOf("username" to "eq.${request.username.lowercase()}"),
            object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        )
        if (!existing.isNullOrEmpty()) {
            throw RuntimeException("Usuário '${request.username}' já existe")
        }

        val userId = UUID.randomUUID().toString()
        val username = request.username.lowercase()
        val passwordHash = passwordEncoder.encode(request.password)

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

        // Save to user_profiles
        val body = mutableMapOf<String, Any>(
            "user_id" to userId,
            "username" to username,
            "password_hash" to passwordHash
        )
        profileImageUrl?.let { body["profile_image_url"] = it }

        try {
            supabaseClient.post(
                "user_profiles", body,
                object : ParameterizedTypeReference<List<Map<String, Any>>>() {}
            )
        } catch (e: Exception) {
            log.error("AUTH SIGNUP | failed to save profile: {}", e.message)
            throw RuntimeException("Erro ao criar conta")
        }

        val result = AuthResponse(userId = userId, username = username, profileImageUrl = profileImageUrl)
        log.info("AUTH SIGNUP → OK | userId={}, username={}", result.userId, result.username)
        return result
    }

    fun login(request: AuthRequest): AuthResponse {
        log.info("AUTH LOGIN | username={}", request.username)

        val username = request.username.lowercase()

        val rows = supabaseClient.get(
            "user_profiles",
            mapOf("username" to "eq.$username"),
            object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
        )

        val profile = rows?.firstOrNull()
            ?: throw RuntimeException("Usuário ou senha inválidos")

        val storedHash = profile["password_hash"] as? String
            ?: throw RuntimeException("Usuário ou senha inválidos")

        if (!passwordEncoder.matches(request.password, storedHash)) {
            throw RuntimeException("Usuário ou senha inválidos")
        }

        val userId = profile["user_id"] as? String ?: ""
        val profileImageUrl = profile["profile_image_url"] as? String

        val result = AuthResponse(userId = userId, username = username, profileImageUrl = profileImageUrl)
        log.info("AUTH LOGIN → OK | userId={}, username={}", result.userId, result.username)
        return result
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

        // 1. Update username
        request.username?.let { newUsername ->
            validateUsername(newUsername)
            val lowered = newUsername.lowercase()

            // Check if new username is taken by someone else
            val existing = supabaseClient.get(
                "user_profiles",
                mapOf("username" to "eq.$lowered", "user_id" to "neq.$userId"),
                object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}
            )
            if (!existing.isNullOrEmpty()) {
                throw RuntimeException("Username '$newUsername' já está em uso")
            }

            updateProfileField(userId, mapOf("username" to lowered))
            log.info("AUTH UPDATE | username updated to {}", lowered)
        }

        // 2. Update password
        request.password?.let { newPassword ->
            if (newPassword.length < 6) throw RuntimeException("Senha precisa ter pelo menos 6 caracteres")
            val newHash = passwordEncoder.encode(newPassword)
            updateProfileField(userId, mapOf("password_hash" to newHash))
            log.info("AUTH UPDATE | password changed for userId={}", userId)
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

    private fun validateUsername(username: String) {
        if (username.isBlank()) throw RuntimeException("Username não pode ser vazio")
        if (username.length < 3) throw RuntimeException("Username precisa ter pelo menos 3 caracteres")
        if (username.length > 30) throw RuntimeException("Username pode ter no máximo 30 caracteres")
        if (!usernameRegex.matches(username)) throw RuntimeException("Username só pode conter letras e números")
    }
}
