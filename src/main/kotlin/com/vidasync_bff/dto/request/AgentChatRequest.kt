package com.vidasync_bff.dto.request

/**
 * Requisição de chat conversacional enviada pelo front ao BFF,
 * que a delega ao BFA.
 *
 * @property message  Mensagem do usuário (ex: "Quantas calorias tem 200g de frango?")
 * @property userId   ID do usuário (injetado pelo BFF a partir do header X-User-Id)
 * @property sessionId ID da sessão de conversa (opcional, para memória conversacional no BFA)
 * @property context  Contexto adicional opcional (ex: dados do plano alimentar atual)
 */
data class AgentChatRequest(
    val message: String,
    val userId: String,
    val sessionId: String? = null,
    val context: Map<String, Any>? = null
)
