package com.vidasync_bff.integration.aigateway.feign

import com.vidasync_bff.integration.aigateway.request.AIGatewayChatFeignRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelineFotoCaloriasFeignRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelinePlanoE2eTemporarioFeignRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayPipelinePlanoImagemFeignRequest
import com.vidasync_bff.integration.aigateway.request.AIGatewayRouteFeignRequest
import com.vidasync_bff.integration.aigateway.response.AIGatewayChatIntegrationResponse
import com.vidasync_bff.integration.aigateway.response.AIGatewayChatJudgeIntegrationResponse
import com.vidasync_bff.integration.aigateway.response.AIGatewayFeignResponse
import com.vidasync_bff.observability.TraceContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader

@FeignClient(
    name = "aiGatewayFeignClient",
    url = "\${ai.gateway.base-url}",
    configuration = [AIGatewayFeignClientConfiguration::class]
)
interface AIGatewayFeignClient {

    @PostMapping(
        value = ["/v1/openai/chat"],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun chat(
        @RequestBody request: AIGatewayChatFeignRequest,
        @RequestHeader(value = TraceContext.TRACE_HEADER, required = false) traceId: String? = null
    ): AIGatewayChatIntegrationResponse

    @GetMapping(value = ["/v1/openai/chat/judge/{evaluationId}"])
    fun chatJudge(
        @PathVariable("evaluationId") evaluationId: String,
        @RequestHeader(value = TraceContext.TRACE_HEADER, required = false) traceId: String? = null
    ): AIGatewayChatJudgeIntegrationResponse

    @PostMapping(
        value = ["/ai/router"],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun route(
        @RequestBody request: AIGatewayRouteFeignRequest,
        @RequestHeader(value = TraceContext.TRACE_HEADER, required = false) traceId: String? = null
    ): AIGatewayFeignResponse

    @PostMapping(
        value = ["/agentes/pipeline-plano-imagem"],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun pipelinePlanoImagem(
        @RequestBody request: AIGatewayPipelinePlanoImagemFeignRequest,
        @RequestHeader(value = TraceContext.TRACE_HEADER, required = false) traceId: String? = null
    ): AIGatewayFeignResponse

    @PostMapping(
        value = ["/agentes/pipeline-plano-e2e-temporario"],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun pipelinePlanoE2eTemporario(
        @RequestBody request: AIGatewayPipelinePlanoE2eTemporarioFeignRequest,
        @RequestHeader(value = TraceContext.TRACE_HEADER, required = false) traceId: String? = null
    ): AIGatewayFeignResponse

    @PostMapping(
        value = ["/agentes/pipeline-foto-calorias"],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun pipelineFotoCalorias(
        @RequestBody request: AIGatewayPipelineFotoCaloriasFeignRequest,
        @RequestHeader(value = TraceContext.TRACE_HEADER, required = false) traceId: String? = null
    ): AIGatewayFeignResponse
}
