package com.vidasync_bff.integration.aigateway.translator

import com.vidasync_bff.dto.ai.AIGatewayOpenAIChatResponse
import com.vidasync_bff.integration.aigateway.request.AIGatewayChatIntegrationRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelineFotoCaloriasIntegrationRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelinePlanoE2eTemporarioIntegrationRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayRouteIntegrationRequest
import com.vidasync_bff.integration.aigateway.response.AIGatewayFeignResponse
import com.vidasync_bff.observability.TraceContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class AIGatewayIntegrationTranslatorTests {

    private val translator = AIGatewayIntegrationTranslator()

    @AfterEach
    fun tearDown() {
        TraceContext.clear()
    }

    @Test
    fun `deve montar payload de foto calorias com idioma e trace quando faltarem no payload`() {
        TraceContext.put("trace-mdc-123")

        val payload = translator.toPipelineFotoCaloriasBody(
            AIGatewayPipelineFotoCaloriasIntegrationRequest(
                imageUrl = "https://cdn.example.com/refeicao.jpg",
                foods = "arroz e frango"
            )
        )

        assertEquals("https://cdn.example.com/refeicao.jpg", payload["image_url"])
        assertEquals("arroz e frango", payload["foods"])
        assertEquals("pt-BR", payload["idioma"])
        assertEquals("trace-mdc-123", payload["trace_id"])
    }

    @Test
    fun `deve preservar campos ja existentes ao montar payload de foto calorias`() {
        val payload = translator.toPipelineFotoCaloriasBody(
            AIGatewayPipelineFotoCaloriasIntegrationRequest(
                imageUrl = "https://cdn.example.com/refeicao.jpg",
                foods = "arroz e frango",
                idioma = "pt-BR",
                traceId = "trace-request-999",
                payload = mapOf(
                    "foods" to "payload-prioritario",
                    "idioma" to "en-US",
                    "trace_id" to "trace-payload-111"
                )
            )
        )

        assertEquals("payload-prioritario", payload["foods"])
        assertEquals("en-US", payload["idioma"])
        assertEquals("trace-payload-111", payload["trace_id"])
        assertEquals("https://cdn.example.com/refeicao.jpg", payload["image_url"])
    }

    @Test
    fun `deve traduzir request route e resposta feign para o contrato da integracao`() {
        val feignRequest = translator.toRouteFeignRequest(
            AIGatewayRouteIntegrationRequest(
                contexto = "calcular_calorias_texto",
                payload = mapOf("foods" to "100g arroz"),
                traceId = "trace-route-321",
                metadados = mapOf("origem" to "teste")
            )
        )
        val integrationResponse = translator.toIntegrationResponse(
            AIGatewayFeignResponse(
                traceId = "trace-route-321",
                contexto = "calcular_calorias_texto",
                status = "sucesso",
                warnings = listOf("ajuste"),
                precisaRevisao = true,
                resultado = mapOf("ok" to true)
            )
        )
        val e2ePayload = translator.toPipelinePlanoE2eTemporarioBody(
            AIGatewayPipelinePlanoE2eTemporarioIntegrationRequest(
                payload = mapOf("arquivo" to "abc"),
                traceId = "trace-e2e-456"
            )
        )

        assertEquals("trace-route-321", feignRequest.traceId)
        assertEquals("100g arroz", feignRequest.payload["foods"])
        assertEquals("teste", feignRequest.metadados["origem"])
        assertEquals("trace-route-321", integrationResponse.traceId)
        assertEquals("sucesso", integrationResponse.status)
        assertEquals(true, integrationResponse.resultado?.get("ok"))
        assertFalse(integrationResponse.warnings.isNullOrEmpty())
        assertEquals("trace-e2e-456", e2ePayload["trace_id"])
    }

    @Test
    fun `deve traduzir request e resposta de chat preservando conversation id`() {
        TraceContext.put("trace-chat-mdc")

        val feignRequest = translator.toChatFeignRequest(
            AIGatewayChatIntegrationRequest(
                prompt = "preciso beber mais agua?",
                conversationId = "conv-abc"
            )
        )
        val integrationResponse = translator.toChatIntegrationResponse(
            AIGatewayOpenAIChatResponse(
                model = "gpt-4o-mini",
                response = "Sim, e importante.",
                conversationId = "conv-abc",
                intencaoDetectada = mapOf("intencao" to "conversa_geral", "confianca" to 0.55),
                roteamento = mapOf("precisa_revisao" to false, "warnings" to emptyList<String>()),
                memoria = mapOf("total_turnos" to 2),
                traceId = "trace-chat-1"
            )
        )

        assertEquals("preciso beber mais agua?", feignRequest.prompt)
        assertEquals("conv-abc", feignRequest.conversationId)
        assertEquals("trace-chat-mdc", feignRequest.traceId)
        assertEquals("conv-abc", integrationResponse.conversationId)
        assertEquals("gpt-4o-mini", integrationResponse.model)
        assertEquals("conversa_geral", integrationResponse.intencaoDetectada?.get("intencao"))
        assertEquals(2, integrationResponse.memoria?.get("total_turnos"))
        assertEquals("trace-chat-1", integrationResponse.traceId)
    }
}
