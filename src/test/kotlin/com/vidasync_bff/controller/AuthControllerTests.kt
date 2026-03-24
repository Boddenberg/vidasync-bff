package com.vidasync_bff.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.vidasync_bff.dto.request.AuthRequest
import com.vidasync_bff.dto.request.UpdatePasswordRequest
import com.vidasync_bff.dto.request.UpdateUsernameRequest
import com.vidasync_bff.dto.response.AuthResponse
import com.vidasync_bff.service.AuthService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AuthControllerTests {

    private val authService = mock(AuthService::class.java)
    private val mockMvc = MockMvcBuilders.standaloneSetup(AuthController(authService)).build()
    private val objectMapper = ObjectMapper()

    @Test
    fun `deve retornar 201 no signup quando service responder com sucesso`() {
        val request = AuthRequest(
            username = "joao123",
            password = "segredo123",
            profileImage = "base64-image"
        )
        val response = AuthResponse(
            userId = "user-1",
            username = "joao123",
            profileImageUrl = "https://cdn.example.com/profile.jpg"
        )

        `when`(authService.signup(request)).thenReturn(response)

        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.userId") { value("user-1") }
            jsonPath("$.username") { value("joao123") }
            jsonPath("$.profileImageUrl") { value("https://cdn.example.com/profile.jpg") }
        }
    }

    @Test
    fun `deve retornar 400 no signup quando service lancar excecao`() {
        val request = AuthRequest(
            username = "joao123",
            password = "123"
        )

        doThrow(RuntimeException("erro ao criar conta"))
            .`when`(authService)
            .signup(request)

        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("erro ao criar conta") }
        }
    }

    @Test
    fun `deve retornar 200 no login quando service responder com sucesso`() {
        val request = AuthRequest(
            username = "joao123",
            password = "segredo123"
        )
        val response = AuthResponse(
            userId = "user-1",
            username = "joao123",
            profileImageUrl = null
        )

        `when`(authService.login(request)).thenReturn(response)

        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.userId") { value("user-1") }
            jsonPath("$.username") { value("joao123") }
            jsonPath("$.profileImageUrl") { doesNotExist() }
        }
    }

    @Test
    fun `deve retornar 401 no login quando service lancar excecao`() {
        val request = AuthRequest(
            username = "joao123",
            password = "senha-errada"
        )

        doThrow(RuntimeException("usuario ou senha invalidos"))
            .`when`(authService)
            .login(request)

        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("usuario ou senha invalidos") }
        }
    }

    @Test
    fun `deve retornar 200 ao trocar username`() {
        val request = UpdateUsernameRequest(username = "novoNome")
        val response = AuthResponse(
            userId = "user-1",
            username = "novonome",
            profileImageUrl = "https://cdn.example.com/profile.jpg"
        )

        `when`(authService.changeUsername("user-1", request)).thenReturn(response)

        mockMvc.put("/auth/profile/username") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.userId") { value("user-1") }
            jsonPath("$.username") { value("novonome") }
            jsonPath("$.profileImageUrl") { value("https://cdn.example.com/profile.jpg") }
        }
    }

    @Test
    fun `deve retornar 400 ao trocar username quando service lancar excecao`() {
        val request = UpdateUsernameRequest(username = "novoNome")

        doThrow(RuntimeException("username ja esta em uso"))
            .`when`(authService)
            .changeUsername("user-1", request)

        mockMvc.put("/auth/profile/username") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("username ja esta em uso") }
        }
    }

    @Test
    fun `deve retornar 200 ao trocar senha`() {
        val request = UpdatePasswordRequest(
            currentPassword = "senhaAtual123",
            newPassword = "novaSenha123"
        )

        mockMvc.put("/auth/profile/password") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.success") { value(true) }
        }
    }

    @Test
    fun `deve retornar 400 ao trocar senha quando service lancar excecao`() {
        val request = UpdatePasswordRequest(
            currentPassword = "senhaAtual123",
            newPassword = "novaSenha123"
        )

        doThrow(RuntimeException("Senha atual invalida"))
            .`when`(authService)
            .changePassword("user-1", request)

        mockMvc.put("/auth/profile/password") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("Senha atual invalida") }
        }
    }
}
