package com.vidasync_bff.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.vidasync_bff.dto.request.ChatRequest
import com.vidasync_bff.dto.response.ChatJudgeCriterionResponse
import com.vidasync_bff.dto.response.ChatJudgeEvaluationResponse
import com.vidasync_bff.dto.response.ChatJudgeReferenceResponse
import com.vidasync_bff.dto.response.ChatMemoryResponse
import com.vidasync_bff.dto.response.ChatResponse
import com.vidasync_bff.service.ChatService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException

class ChatControllerTests {

    private val chatService = mock(ChatService::class.java)
    private val mockMvc = MockMvcBuilders.standaloneSetup(ChatController(chatService)).build()
    private val objectMapper = ObjectMapper()

    @Test
    fun `deve retornar 200 quando chat responder com sucesso`() {
        val request = ChatRequest(
            prompt = "preciso beber mais agua?",
            conversationId = "conv-123"
        )
        val response = ChatResponse(
            response = "Sim. Uma meta pratica e entre 2 e 3 litros por dia.",
            model = "gpt-4o-mini",
            conversationId = "conv-123",
            intent = "conversa_geral",
            confidence = 0.55,
            needsReview = false,
            warnings = emptyList(),
            memory = ChatMemoryResponse(
                totalTurns = 4,
                shortTermTurns = 4,
                summarizedTurns = 0,
                hasSummary = false,
                updatedAt = "2026-03-26T05:25:03.465526Z"
            ),
            judge = ChatJudgeReferenceResponse(
                evaluationId = "judge-eval-1",
                status = "pending"
            ),
            disclaimer = ChatService.DEFAULT_DISCLAIMER,
            traceId = "trace-chat-1"
        )

        `when`(chatService.chat("user-1", request)).thenReturn(response)

        mockMvc.post("/chat") {
            header("X-User-Id", "user-1")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.response") { value("Sim. Uma meta pratica e entre 2 e 3 litros por dia.") }
            jsonPath("$.conversationId") { value("conv-123") }
            jsonPath("$.intent") { value("conversa_geral") }
            jsonPath("$.memory.totalTurns") { value(4) }
            jsonPath("$.judge.evaluationId") { value("judge-eval-1") }
            jsonPath("$.judge.status") { value("pending") }
            jsonPath("$.disclaimer") { value(ChatService.DEFAULT_DISCLAIMER) }
            jsonPath("$.traceId") { value("trace-chat-1") }
        }
    }

    @Test
    fun `deve retornar 200 quando judge responder com sucesso`() {
        val response = ChatJudgeEvaluationResponse(
            evaluationId = "judge-eval-1",
            status = "completed",
            overallScore = 92.5,
            approved = true,
            decision = "approved",
            criterionScores = mapOf(
                "quality" to 4.8,
                "coherence" to 4.7
            ),
            criterionReasons = mapOf(
                "quality" to "Resposta clara e bem estruturada.",
                "coherence" to "A resposta foi consistente com o contexto."
            ),
            criteria = listOf(
                ChatJudgeCriterionResponse(
                    key = "quality",
                    score = 4.8,
                    reason = "Resposta clara e bem estruturada.",
                    approved = true
                )
            ),
            score = mapOf("overall" to 92.5),
            approval = mapOf("overall" to true)
        )

        `when`(chatService.judge("judge-eval-1")).thenReturn(response)

        mockMvc.get("/chat/judge/judge-eval-1")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.evaluationId") { value("judge-eval-1") }
                jsonPath("$.status") { value("completed") }
                jsonPath("$.overallScore") { value(92.5) }
                jsonPath("$.approved") { value(true) }
                jsonPath("$.criterionScores.quality") { value(4.8) }
                jsonPath("$.criterionReasons.quality") { value("Resposta clara e bem estruturada.") }
                jsonPath("$.criteria[0].key") { value("quality") }
            }
    }

    @Test
    fun `deve retornar 400 quando service informar erro de validacao`() {
        val request = ChatRequest(prompt = "   ")

        doThrow(IllegalArgumentException("prompt e obrigatorio"))
            .`when`(chatService)
            .chat(null, request)

        mockMvc.post("/chat") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("prompt e obrigatorio") }
        }
    }

    @Test
    fun `deve retornar status propagado quando service lancar response status exception`() {
        val request = ChatRequest(prompt = "me responde")

        doThrow(ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas mensagens em sequencia. Tente novamente em instantes."))
            .`when`(chatService)
            .chat(null, request)

        mockMvc.post("/chat") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isTooManyRequests() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("Muitas mensagens em sequencia. Tente novamente em instantes.") }
        }
    }

    @Test
    fun `deve retornar 500 quando service lancar excecao inesperada`() {
        val request = ChatRequest(prompt = "me responde")

        doThrow(RuntimeException("falha inesperada"))
            .`when`(chatService)
            .chat(null, request)

        mockMvc.post("/chat") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isInternalServerError() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("falha inesperada") }
        }
    }
}
