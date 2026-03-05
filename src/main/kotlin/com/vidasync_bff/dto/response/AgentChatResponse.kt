package com.vidasync_bff.dto.response

/**
 * Resposta do agente conversacional retornada pelo BFA e repassada ao front.
 *
 * @property reply      Resposta em linguagem natural do agente
 * @property sessionId  ID da sessão de conversa (para continuidade)
 * @property agentUsed  Nome do agente que processou a requisição (para observabilidade)
 * @property sources    Fontes utilizadas pelo RAG (para auditoria/transparência)
 * @property error      Mensagem de erro em caso de falha
 */
data class AgentChatResponse(
    val reply: String? = null,
    val sessionId: String? = null,
    val agentUsed: String? = null,
    val sources: List<String>? = null,
    val error: String? = null
)
