# Proposta de Arquitetura BFF + BFA para o VidaSync

> Escopo: diagnóstico do BFF atual e proposta de novo repositório **BFA (Back for Agents)** para orquestração multiagentes, RAG e evolução para visão/OCR.
>
> **Suposições explícitas** (quando faltou contexto):
> 1. O front continuará consumindo o BFF atual em `/meals`, `/favorites`, `/auth`, `/nutrition`.
> 2. O BFA será um serviço HTTP separado, privado (acessível só pelo BFF e jobs internos).
> 3. O objetivo inicial é reduzir acoplamento de IA no BFF sem quebrar contratos existentes.

---

## SEÇÃO A. Diagnóstico do BFF atual (com base no código)

### 1. Resumo da arquitetura atual
- **Stack**: Kotlin + Spring Boot + Gradle (`/build.gradle.kts`), Java 21.
- **Estrutura por camadas**:
  - `controller/`: borda HTTP (`AuthController`, `MealController`, `FavoriteController`, `NutritionController`, `HealthController`)
  - `service/`: regras de negócio e integrações (`NutritionService`, `MealService`, etc.)
  - `client/`: wrappers para Supabase REST e Storage.
  - `dto/request` e `dto/response`: contratos de entrada/saída.
- **Integrações principais**:
  - OpenAI via `OpenAIClient` em `OpenAIConfig.kt`.
  - Supabase REST em `SupabaseClient.kt` e `SupabaseConfig.kt`.
  - Supabase Storage em `SupabaseStorageClient.kt`.
- **Autenticação atual com front**: header `X-User-Id` (sem JWT no cliente), documentado em `FRONTEND_AUTH_GUIDE.md`.

### 2. Pontos fortes
- Contratos de API já claros para o app (guias de frontend detalhados).
- Pipeline de nutrição já tem:
  - cache por ingrediente (`IngredientCacheService`)
  - execução paralela por ingrediente (virtual threads em `NutritionService`)
  - validação de itens inválidos e correção de unidade.
- BFF já concentra bem o que o front precisa (CRUD de refeições/favoritos/perfil).

### 3. Gargalos para IA multiagente
- **Lógica de IA está acoplada ao BFF** (`NutritionService` faz orquestração + prompt + parsing + fallback).
- Prompt único com semântica fixa limita crescimento para fluxos multi-step.
- Falta separação explícita entre:
  - roteamento de intenção,
  - ferramentas,
  - RAG,
  - guardrails,
  - auditoria de resposta.
- Observabilidade de IA ainda básica (logs textuais), sem tracing por etapa de agente.

### 4. Onde encaixar o BFA sem quebrar o que já existe
- **Sem quebrar front**: manter endpoints atuais no BFF.
- **Mudança interna**: BFF passa a chamar BFA para inteligência.
- Primeiro candidato de delegação: `POST /nutrition/calories` (manter contrato atual de resposta `CalorieResponse`).
- Em seguida: fluxos novos (chat, revisão de dieta, interpretação de plano alimentar) entram só no BFA, expostos ao front via BFF.

---

## SEÇÃO B. Proposta de arquitetura alvo (BFF + BFA)

### 1. Papel do BFF
- Gateway para front (contratos estáveis, auth, rate limit por usuário).
- Orquestração **de integração** (não de IA): valida request, chama BFA, adapta resposta para contrato legado.
- Continua dono de CRUD transacional (meals/favorites/profile).

### 2. Papel do BFA
- Orquestração multiagentes.
- RAG nutricional e ferramentas de cálculo.
- Política de segurança/guardrails para IA.
- Auditoria técnica (fontes usadas, evidências, score de confiança, custo).

### 3. Fluxo de chamadas entre front, BFF e BFA
1. Front chama endpoint atual no BFF (ex.: `/nutrition/calories`).
2. BFF gera `correlationId` e repassa contexto para BFA.
3. BFA executa grafo: classificação → retrieval/tooling → validação → resposta final.
4. BFA retorna payload estruturado + metadados de auditoria.
5. BFF mapeia para contrato esperado pelo front.

### 4. Estratégia de evolução incremental (sem Big Bang)
- **Fase 1**: BFA com endpoint único de nutrição equivalente ao atual.
- **Fase 2**: trocar implementação interna do BFF via feature flag (local fallback no `NutritionService`).
- **Fase 3**: adicionar chat/RAG e novos casos de uso sem mexer nos contratos legados.

### 5. Tradeoffs da abordagem escolhida
- **Pró**: separação de responsabilidades, escalabilidade funcional, governança de IA.
- **Contra**: mais latência de rede BFF→BFA e maior complexidade operacional.
- **Mitigação**: cache, timeouts agressivos, circuit breaker e fallback no BFF.

---

## SEÇÃO C. Nome do novo repositório (me dê pelo menos 10 opções)

### 1. Sugestões de nome com justificativa
1. `vidasync-bfa` — direto, espelha o `vidasync-bff`.
2. `vidasync-agent-core` — foco no núcleo de agentes.
3. `vidasync-ai-orchestrator` — explicita função de orquestração.
4. `vidasync-nutri-intelligence` — destaca domínio nutricional.
5. `vidasync-knowledge-engine` — reforça RAG + conhecimento.
6. `vidasync-diet-ops` — orientação operacional de fluxos alimentares.
7. `vidasync-llm-platform` — visão de plataforma de IA.
8. `vidasync-multiagent-runtime` — foco em execução de grafos/agentes.
9. `vidasync-ai-backend` — nome amplo e simples para equipe.
10. `vidasync-food-reasoner` — foco em inferência nutricional.
11. `vidasync-nutri-graph` — alusão a LangGraph e workflows.
12. `vidasync-intelligence-hub` — central de inteligência.

### 2. Sugestão final (top 3)
1. `vidasync-bfa`
2. `vidasync-agent-core`
3. `vidasync-ai-orchestrator`

### 3. Convenção de naming (repo, package, service name, env vars)
- Repo: `vidasync-bfa`
- Service name: `vidasync-bfa-api`
- Package (Kotlin): `com.vidasync.bfa`
- Env vars:
  - `BFA_PORT`
  - `BFA_OPENAI_API_KEY`
  - `BFA_DB_URL`
  - `BFA_VECTORSTORE_URL`
  - `BFA_LOG_LEVEL`

---

## SEÇÃO D. Stack recomendada para o BFA

### Opção A (MVP simples)
1. **Linguagem/framework principal**: Python + FastAPI (velocidade para IA).
2. **Orquestração**: LangGraph + LangChain (curva boa para grafos e tools).
3. **API**: FastAPI.
4. **Schemas**: Pydantic.
5. **Banco/memória/cache**: Postgres + Redis.
6. **Vector store**: pgvector (mesmo ecossistema do Postgres).
7. **Observabilidade**: OpenTelemetry + Langfuse + logs JSON.
8. **Testes**: pytest + httpx + snapshot de contratos.
9. **Lint/format/type-check**: ruff + black + mypy.
10. **Docker**: Dockerfile + docker-compose.
11. **Config**: `.env` + pydantic-settings.

- Complexidade: baixa/média.
- Custo: baixo (infra enxuta).
- Curva de aprendizado: média (LangGraph + Python stack).

### Opção B (robusta para escalar)
1. **Linguagem/framework principal**: Kotlin + Spring Boot (alinhado ao BFF atual).
2. **Orquestração**: LangGraph via serviço Python dedicado **ou** orquestrador próprio por state machine Kotlin.
3. **API**: Spring WebFlux.
4. **Schemas**: kotlinx.serialization + validador.
5. **Banco/memória/cache**: Postgres + Redis + fila (RabbitMQ/Kafka).
6. **Vector store**: Weaviate/Qdrant (dedicado).
7. **Observabilidade**: OpenTelemetry + Grafana/Tempo/Loki + custo por tenant.
8. **Testes**: JUnit5 + Testcontainers + contract tests.
9. **Lint/format/type-check**: ktlint + detekt + spotless.
10. **Docker**: multi-stage + compose.
11. **Config**: `.env.properties` + Spring profiles.

- Complexidade: média/alta.
- Custo: médio.
- Curva de aprendizado: média para time Kotlin, alta para orquestração multiagente enterprise.

---

## SEÇÃO E. Arquitetura de software em nível de pastas (muito detalhada)

### 1. Estrutura de diretórios do novo repositório (tree)
```text
vidasync-bfa/
  app/
    api/
      v1/
        endpoints/
          nutrition.py
          chat.py
          plan_review.py
        schemas/
          nutrition_request.py
          nutrition_response.py
          common_errors.py
      deps/
        auth.py
        correlation.py
    orchestration/
      graphs/
        nutrition_graph.py
        chat_graph.py
      state/
        nutrition_state.py
      router/
        intent_router.py
    agents/
      nutrition_calculator_agent.py
      rag_retriever_agent.py
      response_composer_agent.py
      safety_guard_agent.py
    tools/
      nutrition_math_tool.py
      unit_conversion_tool.py
      ingredient_normalizer_tool.py
    rag/
      embeddings/
        embedding_provider.py
      retrieval/
        retriever.py
        reranker.py
      indexing/
        chunker.py
        ingest_pipeline.py
      kb/
        food_aliases.yml
        unit_rules.yml
    connectors/
      openai_client.py
      postgres_client.py
      vector_store_client.py
      redis_client.py
    domain/
      services/
        calorie_service.py
      use_cases/
        calculate_nutrition_use_case.py
      entities/
        food_item.py
    guardrails/
      prompt_injection.py
      pii_redaction.py
      nutrition_safety_policy.py
    observability/
      logger.py
      tracing.py
      metrics.py
  scripts/
    ingest_food_table.py
    ingest_aliases.py
  tests/
    unit/
    integration/
    contract/
    e2e/
  docker/
  docker-compose.yml
  pyproject.toml
  .env.example
  README.md
```

### 2. Papel de cada pasta e subpasta
- `api/`: contrato HTTP e borda externa do BFA.
- `orchestration/`: grafos, estados e roteamento.
- `agents/`: agentes especializados.
- `tools/`: funções determinísticas reutilizáveis.
- `rag/`: ingestão, indexação, recuperação e conhecimento base.
- `connectors/`: adapters de OpenAI, banco, vector store.
- `domain/`: regras de negócio auditáveis.
- `guardrails/`: segurança e políticas de resposta.
- `observability/`: logging/tracing/metrics.
- `scripts/`: pipelines de ingestão.
- `tests/`: cobertura por nível.

### 3. Onde ficam os itens 3.1 a 3.13
- 3.1 Agentes → `app/agents/`
- 3.2 Grafos/workflows → `app/orchestration/graphs/`
- 3.3 Tools → `app/tools/`
- 3.4 Prompts → `app/agents/prompts/` (adicionar pasta)
- 3.5 RAG → `app/rag/`
- 3.6 Conectores → `app/connectors/`
- 3.7 Schemas request/response → `app/api/v1/schemas/`
- 3.8 Casos de uso → `app/domain/use_cases/`
- 3.9 Serviços de domínio → `app/domain/services/`
- 3.10 Guardrails/validação → `app/guardrails/`
- 3.11 Observabilidade → `app/observability/`
- 3.12 Testes → `tests/*`
- 3.13 Ingestão → `scripts/` + `app/rag/indexing/`

### 4. Convenções de nome de arquivo
- Use sufixos claros: `_agent.py`, `_tool.py`, `_use_case.py`, `_graph.py`, `_schema.py`.
- Prompts versionados: `nutrition_calc_v1.txt`, `nutrition_calc_v2.txt`.
- Contratos por versão em `api/v1`, `api/v2`.

---

## SEÇÃO F. Desenho multiagentes para o VidaSync

### 1. Quais agentes você recomenda inicialmente (MVP)
- `IntentRouterAgent`
- `NutritionCalculatorAgent`
- `RagRetrieverAgent`
- `SafetyGuardAgent`
- `ResponseComposerAgent`

### 2. Quais agentes deixar para fase 2 e fase 3
- Fase 2: `DietPlanReviewAgent`, `MealTranscriptionAgent`.
- Fase 3: `ImageIngredientDetectionAgent`, `PortionEstimatorAgent`.

### 3. Responsabilidade de cada agente
- Router: classifica intenção (cálculo x consulta x conversa).
- Calculator: chama tools determinísticas e consolida macros.
- Retriever: busca evidências na base nutricional.
- Safety: bloqueia respostas arriscadas/injetadas.
- Composer: gera resposta final separando parte auditável e parte conversacional.

### 4. Quando usar roteador de intenção e quando não usar
- Usar quando endpoint for genérico (`/ai/query`).
- Não usar quando endpoint já define tarefa (ex.: `/nutrition/calculate`).

### 5. Como evitar overengineering no começo
- Começar com 1 grafo de nutrição.
- Máximo 3 tools no MVP.
- Sem memória longa de chat inicialmente.

### 6. Proposta de grafo de execução (LangGraph) com estados, nós e transições
- **Estados**: `input`, `normalized_items`, `retrieved_docs`, `calc_result`, `safety_result`, `final_output`.
- **Nós**:
  1. `normalize_input`
  2. `retrieve_kb`
  3. `compute_macros`
  4. `safety_check`
  5. `compose_response`
- **Transições**:
  - `normalize_input -> retrieve_kb -> compute_macros -> safety_check -> compose_response`
  - se `safety_check=fail` → `fallback_response`.

### 7. Estratégia para fallback quando o agente falhar
- Retornar resposta segura padrão + sugestão de reformulação.
- Se cálculo parcial existir, retornar parcial com aviso de baixa confiança.
- Timeout por nó e fallback determinístico em `nutrition_math_tool`.

### 8. Estratégia para respostas determinísticas em cálculos nutricionais
- Converter tudo para unidade canônica (g/ml/unidade) antes do cálculo.
- Priorizar tabela nutricional interna (não LLM) para matemática.
- LLM só para normalização/entendimento semântica.

### 9. Estratégia para separar “resposta conversacional” de “cálculo auditável”
- Campo `audit`: itens, fatores, fonte, fórmula, confiança.
- Campo `message`: texto amigável ao usuário.
- BFF decide o que exibir para cada tela.

---

## SEÇÃO G. RAG para nutrição (precisão)

### 1. Quais fontes de conhecimento modelar
- Tabela de composição nutricional (por 100g/100ml/unidade).
- Regras de porção e medidas caseiras.
- Dicionário de aliases/sinônimos regionais.
- Base de receitas compostas (ingredientes + rendimento).

### 2. Estratégia de ingestão de dados
- Pipeline ETL versionado (`scripts/ingest_food_table.py`).
- Validar schema, normalizar unidades, gerar IDs estáveis.

### 3. Estratégia de chunking
- Chunk semântico por alimento/receita.
- Metadados ricos: `food_id`, `alias_group`, `unit_type`, `source`, `updated_at`.

### 4. Estratégia de embeddings
- Embedding multilíngue com português forte.
- Reindex incremental por hash do conteúdo.

### 5. Estratégia de retrieval
- Top-k inicial (k=8) com filtro por `domain=nutrition`.
- Re-ranking leve por correspondência de unidade/porção.

### 6. Como citar origem internamente para auditoria da resposta
- Guardar `source_id`, versão da base e trecho recuperado em `audit.sources[]`.

### 7. Como lidar com sinônimos (aipim/macaxeira/mandioca)
- `alias_map` canônico: múltiplos termos -> `canonical_food_id`.
- Normalização antes do retrieval e antes do cálculo.

### 8. Como lidar com unidades e medidas caseiras
- Tabela de conversão (colher, xícara, concha, fatia).
- Conversão depende do alimento (densidade/fator específico).

### 9. Como lidar com alimentos compostos (receitas)
- Decompor receita em ingredientes base + rendimento final.
- Calcular por porção considerando fator de cocção quando disponível.

### 10. Como validar a precisão antes de responder ao usuário
- Validador final com regras:
  - macros não negativas,
  - consistência kcal vs macros,
  - unidade coerente com alimento,
  - score mínimo de confiança.

---

## SEÇÃO H. Visão, OCR e transcrição (futuro próximo)

### 1. Como desenhar agora para suportar imagem no futuro sem quebrar a arquitetura
- Definir interface `InputArtifact` desde já (`text`, `image`, `pdf`).
- Pipeline multimodal desacoplado em módulo próprio.

### 2. Pipeline sugerido
#### 2.1 identificação de ingredientes por imagem
- Modelo de visão detecta alimentos e possíveis porções.

#### 2.2 OCR de plano alimentar
- OCR extrai blocos de texto estruturados por refeição/horário.

#### 2.3 normalização do texto extraído
- Agent/tool transforma texto livre em schema canônico de dieta.

#### 2.4 validação humana opcional
- Flag `requires_user_confirmation=true` quando confiança < limiar.

### 3. Como integrar isso com agentes e RAG
- Saída de visão/OCR entra no mesmo grafo como `normalized_items`.
- Retrieval/cálculo reaproveitados, evitando duplicação.

### 4. Riscos de precisão e mitigação
- Risco: erro de reconhecimento visual.
- Mitigação: top-N hipóteses + confirmação do usuário + histórico de correções.

---

## SEÇÃO I. Contratos de integração BFF <-> BFA

### 1. Endpoints sugeridos do BFA (MVP)
- `POST /v1/nutrition/calculate`
- `POST /v1/chat/query` (fase seguinte)
- `POST /v1/plan/review` (fase seguinte)

### 2. Requests e responses (schemas detalhados)
**Request (MVP)**
```json
{
  "requestId": "uuid",
  "userId": "uuid",
  "locale": "pt-BR",
  "foods": "200g arroz + 150g frango",
  "context": {
    "mealType": "lunch",
    "date": "2026-03-05"
  }
}
```

**Response (MVP)**
```json
{
  "requestId": "uuid",
  "nutrition": { "calories": "610 kcal", "protein": "35g", "carbs": "77g", "fat": "12g" },
  "ingredients": [
    { "name": "200g arroz", "nutrition": { "calories": "260 kcal", "protein": "5g", "carbs": "57g", "fat": "0.5g" }, "source": "kb" }
  ],
  "corrections": [],
  "invalidItems": [],
  "audit": {
    "confidence": 0.92,
    "sources": ["tbca:v1:food_123"],
    "latencyMs": 420
  },
  "error": null
}
```

### 3. Idempotência
- Header `Idempotency-Key` para operações com efeito colateral.
- Para cálculo puro, opcional (cache por hash da entrada).

### 4. Correlation ID / Request ID
- BFF gera `X-Correlation-Id` e repassa para BFA.
- BFA sempre devolve `requestId` no body + header.

### 5. Tratamento de erros padronizado
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Ingrediente inválido",
    "details": {"invalidItems": ["cadeira"]}
  }
}
```

### 6. Timeouts, retries e circuit breaker
- Timeout BFF→BFA: 2s-4s no MVP.
- Retry apenas para erros transitórios (429/5xx).
- Circuit breaker com fallback para cálculo legado no BFF.

### 7. Como versionar a API
- Prefixo `/v1` e evolução aditiva.
- Breaking changes somente em `/v2`.

### 8. Estratégia para streaming
- Preparar endpoint SSE/WebSocket para chat (`/v1/chat/stream`).
- BFF atua como proxy de streaming para o front no futuro.

---

## SEÇÃO J. Segurança, privacidade e guardrails

### 1. Dados sensíveis e cuidado com PII
- Tratar username, imagens e textos de dieta como dados sensíveis.
- Evitar logar payload completo de imagem/base64.

### 2. Sanitização de entrada
- Limite de tamanho de texto.
- Remoção de caracteres de controle e validação de schema estrita.

### 3. Prompt injection (explicar e propor mitigação prática)
- Risco: usuário tentar instruir modelo a ignorar políticas.
- Mitigações:
  - separar instruções de sistema das entradas,
  - classificador de injeção,
  - allowlist de tools,
  - validação pós-resposta por regras de domínio.

### 4. Limites de custo e tokens
- Budget por request e por usuário/dia.
- Abort de fluxo ao estourar limite.

### 5. Rate limiting
- No BFF (por usuário) e no BFA (por IP/serviço).

### 6. Regras para respostas nutricionais seguras
- Sempre incluir disclaimer de apoio informativo.
- Não prescrever condutas clínicas personalizadas sem nutricionista/médico.

### 7. Logs seguros
- Redação de PII, hash de userId em logs analíticos.
- Retenção limitada e criptografia em trânsito/repouso.

---

## SEÇÃO K. Observabilidade e qualidade

### 1. Logs estruturados
- JSON logs com `timestamp`, `service`, `correlationId`, `node`, `latencyMs`, `costUsd`.

### 2. Métricas
- Latência p50/p95/p99, taxa de erro, custo por request, tokens in/out.
- Métrica de precisão percebida (feedback do usuário).

### 3. Tracing de fluxo multiagente
- Span por nó do grafo (normalize/retrieve/compute/safety/compose).

### 4. Como medir qualidade de respostas
- Dataset ouro de casos de nutrição.
- Avaliação offline (desvio de kcal/macros e taxa de correção correta de unidades).

### 5. Conjunto mínimo de dashboards
1. Saúde do serviço (RPS, erro, latência).
2. Custo LLM por endpoint.
3. Qualidade nutricional (erro médio de macro).
4. Segurança (injeções detectadas/bloqueadas).

### 6. Estratégia de testes e2e com cenários do VidaSync
- Cenários reais: arroz+frango, whey+banana, “aipim/macaxeira”.
- Cenários inválidos: “100g cadeira”.

### 7. Testes de contratos com BFF
- Contract tests entre DTO do BFF e schema de resposta do BFA.

---

## SEÇÃO L. Passo a passo de implementação (receita de bolo)

### 1. Fase 0. Preparação
- **Objetivo**: alinhar escopo e contratos.
- **Arquivos**: ADR inicial + `docs/contracts-v1.md`.
- **Dependências**: nenhuma.
- **Comandos**: N/A.
- **Estrutura mínima**: pasta `docs/`.
- **DoD**: contratos MVP aprovados.
- **Erros comuns**: começar por chat completo e atrasar nutrição básica.

### 2. Fase 1. Inicialização do repositório
- **Objetivo**: subir serviço vazio com healthcheck.
- **Arquivos**: `app/main.py`, `pyproject.toml`, `Dockerfile`, `.env.example`.
- **Dependências**: fastapi, uvicorn.
- **Comandos**: `uvicorn app.main:app --reload`.
- **DoD**: `/health` responde 200.
- **Erro comum**: sem padronização de env.

### 3. Fase 2. API básica do BFA
- **Objetivo**: endpoint `/v1/nutrition/calculate` com mock.
- **Arquivos**: schemas request/response + endpoint.
- **Dependências**: pydantic.
- **Comandos**: run + teste de contrato.
- **DoD**: contrato estável equivalente ao BFF.
- **Erro comum**: retornar estrutura diferente da esperada.

### 4. Fase 3. Integração com LLM (simples)
- **Objetivo**: normalizar ingredientes via LLM.
- **Arquivos**: connector OpenAI + tool normalizer.
- **Dependências**: openai/langchain.
- **DoD**: cálculo simples funcionando para 10 casos base.
- **Erro comum**: depender do LLM para cálculo final.

### 5. Fase 4. Introdução do grafo multiagentes
- **Objetivo**: mover fluxo para LangGraph.
- **Arquivos**: `nutrition_graph.py`, `nutrition_state.py`.
- **Dependências**: langgraph.
- **DoD**: tracing por nó e fallback funcionando.
- **Erro comum**: grafo complexo cedo demais.

### 6. Fase 5. RAG mínimo viável
- **Objetivo**: retrieval com base nutricional inicial.
- **Arquivos**: ingest pipeline, retriever, kb inicial.
- **Dependências**: pgvector/driver.
- **DoD**: respostas com `audit.sources`.
- **Erro comum**: chunking ruim sem metadados.

### 7. Fase 6. Integração BFF <-> BFA
- **Objetivo**: BFF delegar `/nutrition/calories` ao BFA.
- **Arquivos**: novo client no BFF + feature flag.
- **Dependências**: cliente HTTP no BFF.
- **DoD**: contrato do front inalterado.
- **Erro comum**: não tratar timeout/circuit breaker.

### 8. Fase 7. Observabilidade e testes
- **Objetivo**: métricas, traces e testes e2e.
- **Arquivos**: `observability/*`, `tests/e2e/*`.
- **Dependências**: opentelemetry, pytest.
- **DoD**: dashboard mínimo com latência/custo/erro.
- **Erro comum**: sem correlation-id ponta a ponta.

### 9. Fase 8. Hardening (segurança, guardrails)
- **Objetivo**: proteção contra injection e abuso.
- **Arquivos**: `guardrails/*`.
- **Dependências**: libs de validação/sanitização.
- **DoD**: testes de segurança passando.
- **Erro comum**: logar PII sem máscara.

### 10. Fase 9. Expansão para imagem/OCR (esqueleto pronto)
- **Objetivo**: interface multimodal preparada.
- **Arquivos**: `vision_pipeline.py`, `ocr_pipeline.py` (skeleton).
- **Dependências**: tesseract/cloud vision (futuro).
- **DoD**: contratos prontos, implementação behind feature flag.
- **Erro comum**: acoplar OCR diretamente ao endpoint principal.

---

## SEÇÃO M. Dependências e comandos de setup

### 1. Lista de dependências por categoria
- API: fastapi, uvicorn.
- Orquestração: langgraph, langchain.
- Dados: sqlalchemy/psycopg, redis.
- Vetor: pgvector client.
- Observabilidade: opentelemetry, structlog.
- Qualidade: pytest, ruff, mypy.

### 2. Comandos de instalação
```bash
pip install fastapi uvicorn langgraph langchain pydantic-settings
pip install sqlalchemy psycopg redis pgvector
pip install opentelemetry-sdk structlog
pip install pytest pytest-asyncio httpx ruff mypy
```

### 3. Arquivos iniciais
- `.env.example`
- `docker-compose.yml`
- `Makefile`

### 4. Scripts úteis para desenvolvimento
- `make dev`
- `make test`
- `make lint`
- `make ingest-kb`

### 5. Como rodar localmente
```bash
cp .env.example .env
make dev
```

### 6. Como testar localmente
```bash
make test
pytest tests/contract -q
```

### 7. Como integrar localmente com o BFF atual
- No BFF, adicionar `BFA_BASE_URL=http://localhost:8090`.
- Ativar flag `FEATURE_BFA_NUTRITION=true`.

---

## SEÇÃO N. Skeleton inicial (sem exagerar no código)

### 1. Gere o esqueleto dos arquivos principais
```text
app/main.py
app/api/v1/endpoints/nutrition.py
app/api/v1/schemas/nutrition_request.py
app/api/v1/schemas/nutrition_response.py
app/orchestration/graphs/nutrition_graph.py
app/tools/nutrition_math_tool.py
```

### 2. Mostre apenas o mínimo necessário em cada arquivo para compilar/subir
```python
# app/main.py
from fastapi import FastAPI
from app.api.v1.endpoints.nutrition import router as nutrition_router

app = FastAPI(title="VidaSync BFA")
app.include_router(nutrition_router, prefix="/v1")

@app.get("/health")
def health():
    return {"status": "UP"}
```

```python
# app/api/v1/endpoints/nutrition.py
from fastapi import APIRouter
from app.api.v1.schemas.nutrition_request import NutritionRequest
from app.api.v1.schemas.nutrition_response import NutritionResponse, NutritionData

router = APIRouter()

@router.post("/nutrition/calculate", response_model=NutritionResponse)
def calculate(req: NutritionRequest):
    # TODO: integrar grafo multiagente
    return NutritionResponse(
        requestId=req.requestId,
        nutrition=NutritionData(calories="0 kcal", protein="0g", carbs="0g", fat="0g"),
        ingredients=[],
        corrections=[],
        invalidItems=[],
        error=None,
    )
```

### 3. Priorize contratos, interfaces e estrutura
- Primeiro entrega schemas estáveis + endpoint funcional.

### 4. Evite gerar código gigante de uma vez
- Evolução por PRs pequenos (1 fase por PR).

### 5. Marque claramente TODOs para implementação manual
- `# TODO: integrar RAG`
- `# TODO: adicionar guardrails`
- `# TODO: incluir auditoria`

---

## SEÇÃO O. Plano de migração do que existe hoje

### 1. Como sair de “1 prompt único no BFF”
- Extrair para adapter no BFF (`NutritionIntelligencePort`).
- Implementações:
  - `LegacyOpenAiNutritionAdapter` (atual)
  - `BfaNutritionAdapter` (novo)

### 2. Quais responsabilidades mover primeiro para o BFA
1. Parsing/normalização de ingredientes.
2. Retrieval de base nutricional.
3. Orquestração de cálculo e validação.

### 3. O que manter no BFF
- Auth, CRUD, upload de imagem, contratos com front.

### 4. Estratégia de feature flag
- `FEATURE_BFA_NUTRITION` (off por padrão).
- Rollout por percentual de usuários.

### 5. Estratégia de rollback
- Circuit breaker abre → fallback automático para adapter legado.
- Chave global por variável de ambiente para retorno imediato.

### 6. Plano de transição em entregas pequenas
- Sprint 1: BFA MVP + contrato.
- Sprint 2: BFF integração opcional.
- Sprint 3: habilitar para tráfego interno.
- Sprint 4: habilitar gradualmente para produção.

---

## SEÇÃO P. Backlog recomendado (próximos passos)

### 1. Roadmap de curto prazo (MVP)
- Endpoint nutricional no BFA.
- Grafo simples + 3 tools.
- Contract tests BFF↔BFA.

### 2. Roadmap de médio prazo
- Chat contextual com memória curta.
- Revisão de plano alimentar textual.
- Painel de qualidade/custos.

### 3. Roadmap de longo prazo
- OCR de dieta + imagem de ingredientes.
- Estimativa de porção por imagem.
- Recomendação personalizada com histórico.

### 4. Prioridade sugerida com base em impacto x esforço
1. BFA cálculo nutricional (alto impacto, esforço médio)
2. RAG confiável + auditoria (alto impacto, esforço médio)
3. Chat avançado (médio impacto, esforço médio)
4. Visão/OCR (alto impacto, esforço alto)

---

## Versão resumida da arquitetura em 1 página
- **BFF** continua como API oficial para front (auth, CRUD, contratos).
- **BFA** vira motor de inteligência (agentes, RAG, guardrails, auditoria).
- Migração sem Big Bang: trocar implementação interna por feature flag.
- Cálculo nutricional deve ser determinístico: LLM ajuda a interpretar, mas math vem de tool/regras/tabela.
- RAG com aliases + unidades + receitas para precisão.
- Observabilidade obrigatória ponta a ponta (`correlationId`, latência, custo, qualidade).
- Segurança por camadas: sanitização, anti prompt-injection, limite de custo/tokens, logs sem PII.
- Preparar agora interfaces multimodais para plugar OCR/visão sem reescrever tudo.

## Diagrama textual do fluxo (ASCII)
```text
[Mobile/Web Front]
      |
      v
[VidaSync BFF]
  - Auth, Meals, Favorites, Contracts
  - Feature Flag: BFA on/off
      |
      | HTTP (X-Correlation-Id)
      v
[VidaSync BFA]
  [Intent Router]
      -> [Normalize Tool]
      -> [RAG Retriever]
      -> [Nutrition Math Tool]
      -> [Safety Guard]
      -> [Response Composer]
      |
      v
[Response + Audit Metadata]
      |
      v
[BFF maps to existing DTOs]
      |
      v
[Front unchanged]
```

## Checklist inicial de 10 passos para começar hoje
1. Definir nome final do repo BFA (`vidasync-bfa`).
2. Criar repo e bootstrap FastAPI mínimo.
3. Publicar `POST /v1/nutrition/calculate` com schema estável.
4. Implementar `correlationId` e logs JSON.
5. Criar tool determinística de soma de macros.
6. Implementar normalização de ingredientes (LLM simples).
7. Adicionar RAG mínimo (pgvector + alias map).
8. Criar contract tests de resposta para o BFF.
9. Integrar BFF via feature flag `FEATURE_BFA_NUTRITION`.
10. Executar rollout controlado com fallback legado.

## Primeiro corte de MVP (rápido, sem perder visão futura)
- BFA com 1 endpoint de cálculo nutricional.
- 1 grafo simples (normalize -> retrieve -> compute -> safety -> response).
- 3 tools: normalização, conversão de unidade, cálculo de macro.
- Auditoria mínima: `confidence`, `sources`, `latencyMs`.
- BFF mantém endpoint atual e apenas delega internamente quando flag ligada.
- Sem chat e sem imagem inicialmente, mas interfaces já prontas para extensão.
