# Proposta de Arquitetura BFA (Back for Agents) para o VidaSync

## Premissas e suposições (defaults assumidos)
- O BFF atual (este repositório) **continua como única porta de entrada do frontend**.
- O novo BFA será um **repositório separado**, inicialmente interno (rede privada), consumido apenas pelo BFF.
- Vamos preservar os contratos atuais do BFF com o front, principalmente `/nutrition/calories` e os fluxos de refeição/favoritos.
- A solução precisa começar simples (MVP) e evoluir para multiagentes + RAG + visão/OCR sem reescrever tudo.
- O domínio inicial de RAG será nutrição em português (alimentos, porções, unidades, aliases regionais como mandioca/macaxeira/aipim).

---

## SEÇÃO A. Diagnóstico do BFF atual (com base no código)

### 1. Resumo da arquitetura atual
- **Stack**: Kotlin + Spring Boot 3.5 + Java 21 (Gradle), com integração OpenAI via `openai-java`.
- **Camadas atuais**:
  - `controller/`: APIs HTTP (`AuthController`, `MealController`, `FavoriteController`, `NutritionController`, `HealthController`).
  - `service/`: regras de negócio e integrações (`NutritionService`, `MealService`, `FavoriteService`, `AuthService`, `IngredientCacheService`).
  - `client/`: acesso Supabase REST e Storage.
  - `dto/request` e `dto/response`: contratos de entrada/saída.
- **Integrações externas**:
  - Supabase (REST para tabelas + Storage para imagens).
  - OpenAI (chamada direta no `NutritionService`).

### 2. Pontos fortes
- Boa separação BFF entre controllers e services.
- Contratos simples e úteis para o front (principalmente `/nutrition/calories`).
- Cache de ingredientes (`ingredient_cache`) reduz custo de LLM.
- Paralelismo com virtual threads para ingredientes múltiplos.
- Base pronta para histórico alimentar (`meals`, `favorite_meals`) e imagem.

### 3. Gargalos para IA multiagente
- Orquestração de IA está concentrada em **um único service** (`NutritionService`) com prompt hardcoded.
- Ausência de camada dedicada de **roteamento de intenção, workflows/grafos, ferramentas e guardrails**.
- Sem separação explícita entre:
  - resposta conversacional;
  - cálculo auditável/determinístico.
- Sem RAG robusto (hoje depende de inferência do modelo + cache pontual).
- Sem observabilidade de IA em nível de agente/nó/tool.

### 4. Onde encaixar o BFA sem quebrar o que já existe
- Preservar o endpoint BFF `POST /nutrition/calories` e mover a inteligência para:
  - `BFF -> BFA /v1/nutrition/calculate`.
- BFF segue com:
  - autenticação com usuário,
  - composição de resposta para front,
  - persistência de refeições/favoritos.
- BFA assume:
  - orquestração de agentes,
  - RAG,
  - validação nutricional,
  - guardrails de IA.

---

## SEÇÃO B. Proposta de arquitetura alvo (BFF + BFA)

### 1. Papel do BFF
- Gateway orientado ao frontend.
- Aplicar autenticação/autorização e contexto do usuário.
- Manter contratos atuais e traduzir para contratos internos BFA (anti-corruption layer).
- Persistir operações de app (meals/favorites/profile) no Supabase.

### 2. Papel do BFA
- Núcleo de IA e orquestração.
- Executar workflows multi-step (LangGraph ou equivalente).
- Resolver cálculo nutricional com RAG + regras + ferramentas determinísticas.
- Emitir resposta estruturada e auditável para o BFF.

### 3. Fluxo de chamadas entre front, BFF e BFA
1. Front chama `POST /nutrition/calories` no BFF (contrato atual).
2. BFF valida básico + cria `correlationId`.
3. BFF chama BFA (`/v1/nutrition/calculate`) com contexto do usuário e pedido normalizado.
4. BFA executa grafo (parse -> retrieval -> cálculo -> validação -> resposta).
5. BFF adapta resposta (mesmo formato existente) e retorna ao front.

### 4. Estratégia de evolução incremental (sem Big Bang)
- Fase 1: BFA com 1 endpoint de cálculo nutricional e 1 workflow simples.
- Fase 2: roteador de intenção + chat nutricional + RAG melhor.
- Fase 3: ingestão de plano alimentar, revisão de dieta, visão/OCR.
- Fase 4: streaming de chat e agentes especializados por domínio.

### 5. Tradeoffs da abordagem escolhida
- **Pró**: separação limpa, escalabilidade da IA, governança e observabilidade melhores.
- **Contra**: mais componentes (rede, deploy, monitoramento), maior latência entre serviços.
- **Mitigação**: timeout/retry/circuit breaker no BFF e cache interno no BFA.

---

## SEÇÃO C. Nome do novo repositório (me dê pelo menos 10 opções)

### 1. Sugestões de nome com justificativa
1. `vidasync-bfa` — direto e alinhado ao padrão BFF/BFA.
2. `vidasync-agents` — comunica foco em agentes.
3. `vidasync-ai-orchestrator` — enfatiza orquestração.
4. `vidasync-intelligence-core` — “núcleo de inteligência”.
5. `vidasync-nutrition-ai` — foco no domínio nutricional.
6. `vidasync-agent-runtime` — destaca runtime de workflows.
7. `vidasync-ai-platform` — visão de plataforma evolutiva.
8. `vidasync-cognitive-engine` — branding de motor cognitivo.
9. `vidasync-rag-engine` — foco inicial em retrieval + precisão.
10. `vidasync-nutri-brain` — nome de produto, mais marketing.
11. `vidasync-ai-backend` — simples e legível para times não técnicos.
12. `vidasync-orchestration-service` — semântica operacional clara.

### 2. Sugestão final (top 3)
- **#1 `vidasync-bfa`** (recomendado)
- #2 `vidasync-ai-orchestrator`
- #3 `vidasync-agents`

### 3. Convenção de naming (repo, package, service name, env vars)
- **Repo**: `vidasync-bfa`
- **Package (Kotlin)**: `com.vidasync.bfa`
- **Service name (k8s/compose)**: `vidasync-bfa`
- **Env vars**:
  - `BFA_PORT`
  - `BFA_OPENAI_API_KEY`
  - `BFA_DB_URL`
  - `BFA_VECTOR_DB_URL`
  - `BFA_REDIS_URL`
  - `BFA_LOG_LEVEL`

---

## SEÇÃO D. Stack recomendada para o BFA

### Opção A (MVP simples)
1. **Linguagem/framework principal**: Kotlin + Spring Boot (reuso de stack do BFF, menor fricção)
2. **Orquestração**: LangChain4j (ou workflow interno simples) no início
3. **API**: Spring Web
4. **Schemas**: Kotlin data classes + Bean Validation
5. **Banco/memória/cache**: PostgreSQL + Redis
6. **Vetor store**: pgvector (no próprio Postgres)
7. **Observabilidade**: OpenTelemetry + Micrometer + logs JSON
8. **Testes**: JUnit5 + Testcontainers + WireMock
9. **Lint/format/type-check**: ktlint + detekt
10. **Docker**: Dockerfile + docker-compose
11. **Config (.env)**: Spring config import (`.env.properties`)

- **Complexidade**: baixa/média
- **Custo**: baixo
- **Curva de aprendizado**: baixa para seu time atual

### Opção B (robusta para escalar)
1. **Linguagem/framework principal**: Python + FastAPI (ecossistema mais maduro para agentes)
2. **Orquestração**: LangGraph + LangChain
3. **API**: FastAPI + Pydantic
4. **Schemas**: Pydantic v2
5. **Banco/memória/cache**: Postgres + Redis
6. **Vetor store**: Qdrant ou Weaviate
7. **Observabilidade**: OpenTelemetry + Langfuse + Prometheus/Grafana
8. **Testes**: pytest + pytest-asyncio + testcontainers
9. **Lint/format/type-check**: ruff + mypy + black
10. **Docker**: multi-stage + compose
11. **Config**: pydantic-settings + `.env`

- **Complexidade**: média/alta
- **Custo**: médio
- **Curva de aprendizado**: média/alta (mas excelente para multiagentes)

**Recomendação prática**: começar em **Opção A** (Kotlin) para entregar rápido e preservar consistência com o BFF.

---

## SEÇÃO E. Arquitetura de software em nível de pastas (muito detalhada)

### 1. Estrutura de diretórios do novo repositório (tree)
```text
vidasync-bfa/
  src/main/kotlin/com/vidasync/bfa/
    api/
      controller/
      middleware/
      error/
    application/
      usecase/
      orchestrator/
      dto/
    domain/
      model/
      service/
      rule/
    agents/
      nutrition/
      chat/
      planner/
    graph/
      state/
      nodes/
      transitions/
    tools/
      nutrition/
      unit/
      calculator/
      ingestion/
    rag/
      ingest/
      chunking/
      embedding/
      retrieval/
      rerank/
      citation/
    prompts/
      nutrition/
      chat/
      safety/
    connectors/
      openai/
      postgres/
      vectorstore/
      redis/
      ocr/
      vision/
    guardrails/
      input/
      output/
      policy/
      injection/
    observability/
      logging/
      tracing/
      metrics/
      cost/
    config/
  src/test/
    unit/
    integration/
    e2e/
    contract/
  scripts/
    ingest/
    backfill/
  docker/
  docs/
```

### 2. Papel de cada pasta e subpasta
- `api`: entrada HTTP e padronização de erro/headers.
- `application`: casos de uso e coordenação de fluxo.
- `domain`: regras nutricionais, sem dependência de infra.
- `agents`: especializações por responsabilidade.
- `graph`: workflows multi-step e estados.
- `tools`: funções chamáveis pelos agentes.
- `rag`: ingestão, indexação e recuperação de conhecimento.
- `prompts`: prompts versionados por domínio.
- `connectors`: integração com provedores externos.
- `guardrails`: segurança e consistência da geração.
- `observability`: telemetria técnica e de IA.

### 3. Onde ficam os componentes pedidos
- **3.1 Agentes**: `agents/*`
- **3.2 Grafos/workflows**: `graph/*`
- **3.3 Tools**: `tools/*`
- **3.4 Prompts**: `prompts/*`
- **3.5 RAG**: `rag/*`
- **3.6 Conectores**: `connectors/*`
- **3.7 Schemas**: `api/*` + `application/dto/*`
- **3.8 Casos de uso**: `application/usecase/*`
- **3.9 Serviços de domínio**: `domain/service/*`
- **3.10 Guardrails e validação**: `guardrails/*`
- **3.11 Observabilidade**: `observability/*`
- **3.12 Testes**: `src/test/*`
- **3.13 Scripts de ingestão**: `scripts/ingest/*`

### 4. Convenções de nome de arquivo
- Controller: `NutritionController.kt`
- Use case: `CalculateNutritionUseCase.kt`
- Agent: `NutritionCalculatorAgent.kt`
- Node do grafo: `RetrieveNutritionNode.kt`
- Tool: `ParsePortionTool.kt`
- Prompt: `nutrition_system_v1.prompt.md`
- Contract test: `bff_bfa_contract_test.kt`

---

## SEÇÃO F. Desenho multiagentes para o VidaSync

### 1. Quais agentes você recomenda inicialmente (MVP)
- `IntentRouterAgent` (leve)
- `NutritionCalculatorAgent`
- `NutritionValidatorAgent`

### 2. Quais agentes deixar para fase 2 e fase 3
- **Fase 2**: `DietReviewAgent`, `ConversationalCoachAgent`, `MealPlanParserAgent`
- **Fase 3**: `VisionIngredientAgent`, `OCRDietAgent`, `RecipeDecomposerAgent`

### 3. Responsabilidade de cada agente
- Router: identifica intenção (calcular, conversar, revisar plano).
- Calculator: monta itens normalizados e chama tools/cálculo.
- Validator: checa consistência, unidade, intervalos plausíveis.

### 4. Quando usar roteador de intenção e quando não usar
- Use roteador para endpoint genérico (`/chat` ou `/assistant/execute`).
- Não use roteador em endpoint estrito (`/nutrition/calculate`) para reduzir complexidade/latência.

### 5. Como evitar overengineering no começo
- 1 grafo curto (4–6 nós), 2–3 agentes no máximo.
- Sem memória conversacional longa no MVP.
- Sem múltiplos provedores de LLM no início.

### 6. Proposta de grafo de execução (LangGraph) com estados, nós e transições
- **Estado**: `request`, `normalizedItems`, `retrievedFacts`, `calcResult`, `validation`, `finalResponse`, `errors`.
- **Nós**:
  1. `NormalizeInputNode`
  2. `RetrieveNutritionFactsNode`
  3. `ComputeNutritionNode` (determinístico)
  4. `ValidateOutputNode`
  5. `ComposeResponseNode`
- **Transições**:
  - `Normalize -> Retrieve -> Compute -> Validate -> Compose`
  - Em erro: `* -> FallbackNode -> Compose`

### 7. Estratégia para fallback quando o agente falhar
- Timeout por nó + retry controlado.
- Fallback para resposta conservadora: “não foi possível calcular com precisão”, pedindo clarificação.
- Nunca inventar macro sem base recuperada.

### 8. Estratégia para respostas determinísticas em cálculos nutricionais
- Cálculo em função determinística (não por texto livre do LLM).
- LLM apenas para parsing/normalização e resolução semântica.
- Fórmula matemática e arredondamento centralizados em tool de domínio.

### 9. Estratégia para separar “resposta conversacional” de “cálculo auditável”
- Contrato `calculation` separado de `explanation`.
- `calculation`: números, fontes, unidades, confiança.
- `explanation`: linguagem natural para UX.

---

## SEÇÃO G. RAG para nutrição (precisão)

### 1. Quais fontes de conhecimento modelar
- Tabelas nutricionais oficiais (por 100g/porção).
- Tabela de equivalência de porções caseiras (colher, xícara, concha).
- Dicionário de aliases regionais (aipim/macaxeira/mandioca).
- Regras de unidade por categoria (líquidos em ml, sólidos em g, exceções).
- Base de receitas compostas (ingredientes + rendimento).

### 2. Estratégia de ingestão de dados
- Pipeline batch idempotente (`source -> normalize -> dedupe -> index`).
- Versionar fonte e data de ingestão por documento.

### 3. Estratégia de chunking
- Chunk semântico por alimento/entrada de tabela.
- Metadados obrigatórios: `food_id`, `source`, `unit_base`, `region_alias`.

### 4. Estratégia de embeddings
- Modelo multilíngue com bom desempenho em PT-BR.
- Armazenar vetores + texto limpo + metadados.

### 5. Estratégia de retrieval (top-k, filtros, reranking se necessário)
- top-k inicial: 5–10.
- filtros por tipo de alimento/unidade.
- reranking quando ambiguidade alta.

### 6. Como citar a origem internamente para auditoria da resposta
- Campo `evidence[]` no output interno com `sourceId`, `title`, `ingestedAt`, `confidence`.
- Persistir trilha por `correlationId`.

### 7. Como lidar com sinônimos (ex.: aipim/macaxeira/mandioca)
- Dicionário de aliases + expansão de consulta antes do retrieval.
- Ex.: `mandioca -> [aipim, macaxeira]`.

### 8. Como lidar com unidades e medidas caseiras
- Tool de normalização de unidade (`UnitNormalizationTool`).
- Tabela de conversão com faixas e incerteza.

### 9. Como lidar com alimentos compostos (receitas)
- Decompor receita em ingredientes base.
- Calcular total por rendimento e porção final.

### 10. Como validar a precisão antes de responder ao usuário
- Checagem de plausibilidade (faixas de macros por porção).
- Se confiança baixa, retornar pedido de clarificação.

---

## SEÇÃO H. Visão, OCR e transcrição (futuro próximo)

### 1. Como desenhar agora para suportar imagem no futuro sem quebrar a arquitetura
- Definir interface de entrada multimodal desde já:
  - `text`, `imageUrl`, `fileUrl` (opcionais).
- Ter conectores placeholders (`connectors/vision`, `connectors/ocr`).

### 2. Pipeline sugerido
#### 2.1 identificação de ingredientes por imagem
- Vision model detecta itens + confiança.

#### 2.2 OCR de plano alimentar
- OCR extrai texto estruturado por refeição/horário.

#### 2.3 normalização do texto extraído
- Agente parser converte para schema canônico.

#### 2.4 validação humana opcional
- Flag `requiresHumanReview` para casos ambíguos.

### 3. Como integrar isso com agentes e RAG
- Vision/OCR alimentam `normalizedItems` no mesmo grafo de cálculo.
- Retrieval e cálculo permanecem iguais (reuso máximo).

### 4. Riscos de precisão e mitigação
- Erro de detecção visual -> usar score mínimo + confirmação do usuário.
- OCR ruído -> validação de unidade/formato e step de revisão.

---

## SEÇÃO I. Contratos de integração BFF <-> BFA

### 1. Endpoints sugeridos do BFA (MVP)
- `POST /v1/nutrition/calculate`
- `POST /v1/nutrition/validate` (opcional)
- `GET /v1/health`

### 2. Requests e responses (schemas detalhados)
**Request (`/v1/nutrition/calculate`)**
```json
{
  "requestId": "uuid",
  "correlationId": "uuid",
  "userId": "uuid",
  "locale": "pt-BR",
  "input": {
    "foodsText": "200g arroz + 150g frango",
    "items": []
  },
  "options": {
    "strictValidation": true,
    "includeEvidence": true
  }
}
```

**Response**
```json
{
  "requestId": "uuid",
  "correlationId": "uuid",
  "status": "ok",
  "calculation": {
    "totals": {
      "calories": { "value": 610, "unit": "kcal", "display": "610 kcal" },
      "protein": { "value": 35, "unit": "g", "display": "35g" },
      "carbs": { "value": 77, "unit": "g", "display": "77g" },
      "fat": { "value": 12, "unit": "g", "display": "12g" }
    },
    "items": [
      {
        "input": "200g arroz",
        "normalized": "200g de arroz",
        "nutrition": {
          "calories": { "value": 260, "unit": "kcal", "display": "260 kcal" },
          "protein": { "value": 5, "unit": "g", "display": "5g" },
          "carbs": { "value": 57, "unit": "g", "display": "57g" },
          "fat": { "value": 0.5, "unit": "g", "display": "0.5g" }
        },
        "confidence": 0.93
      }
    ]
  },
  "evidence": [
    { "sourceId": "tbca:arroz_branco", "label": "Tabela TBCA", "confidence": 0.95 }
  ],
  "warnings": []
}
```

> Observação: o **BFA interno** pode retornar `value + unit + display` para auditabilidade e rastreabilidade.  
> O **BFF adapta** para o contrato atual do front (`"610 kcal"`, `"35g"`, etc.) sem quebra.

### 3. Idempotência
- Header `Idempotency-Key` para operações não puramente leitura.
- Cache de resposta por chave + hash de request.

### 4. Correlation ID / Request ID
- BFF gera `X-Correlation-Id` se não vier do front.
- BFA sempre ecoa IDs na resposta e logs.

### 5. Tratamento de erros padronizado
```json
{
  "requestId": "uuid",
  "correlationId": "uuid",
  "status": "error",
  "error": {
    "code": "NUTRITION_INVALID_ITEM",
    "message": "Item não reconhecido: cadeira",
    "details": { "invalidItems": ["cadeira"] }
  }
}
```

### 6. Timeouts, retries e circuit breaker
- Timeout BFF->BFA: 2–4s (MVP).
- Retry apenas em erro transitório (1 tentativa).
- Circuit breaker para degradação controlada.

### 7. Como versionar a API
- Prefixo `/v1` no path.
- Evolução compatível por campo opcional; breaking changes em `/v2`.

### 8. Estratégia para streaming (se eu quiser chat streaming no futuro)
- Futuro: `POST /v1/chat/stream` com SSE.
- BFF pode repassar stream ao front sem alterar domínio do BFA.

---

## SEÇÃO J. Segurança, privacidade e guardrails

### 1. Dados sensíveis e cuidado com PII
- Minimizar PII no payload para BFA.
- Não enviar senha/token para BFA.

### 2. Sanitização de entrada
- Limite de tamanho por campo.
- Remoção de caracteres maliciosos e validação de schema.

### 3. Prompt injection (explicar e propor mitigação prática)
- Risco: usuário instruir modelo a ignorar regras.
- Mitigação:
  - prompts de sistema imutáveis;
  - separação entre dados e instruções;
  - tool-calling restrito;
  - validação de output por schema.

### 4. Limites de custo e tokens
- Quotas por usuário e por endpoint.
- Máximo de tokens por requisição.

### 5. Rate limiting
- No BFF (por usuário/IP) + no BFA (por client service).

### 6. Regras para respostas nutricionais seguras (sem substituir profissional)
- Mensagem padrão: “não substitui nutricionista”.
- Bloquear recomendações extremas/perigosas.

### 7. Logs seguros (sem vazar dados sensíveis)
- Não logar payload bruto com dados sensíveis/imagens base64 completas.
- Mascaramento de campos e truncamento.

---

## SEÇÃO K. Observabilidade e qualidade

### 1. Logs estruturados
- JSON logs com `timestamp`, `service`, `requestId`, `correlationId`, `agent`, `node`, `latencyMs`.

### 2. Métricas (latência, erro, custo, tokens, acurácia percebida)
- p95/p99 latência, taxa de erro, custo por request, tokens in/out, cache hit rate, invalid item rate.

### 3. Tracing de fluxo multiagente
- Span por nó do grafo + tool calls + chamadas externas.

### 4. Como medir qualidade de respostas
- Score de consistência (regra + RAG).
- Taxa de correção manual pelo usuário.
- Avaliação humana por amostragem.

### 5. Conjunto mínimo de dashboards
- Saúde da API (RPS/latência/erro).
- Custo e tokens por endpoint.
- Qualidade nutricional (confiança, inválidos, ambiguidades).

### 6. Estratégia de testes e2e com cenários do VidaSync
- Cenários: arroz+frango, receita composta, item inválido, unidade errada, alias regional.

### 7. Testes de contratos com BFF
- Pact/contract tests para garantir compatibilidade do `POST /nutrition/calories` no BFF.

---

## SEÇÃO L. Passo a passo de implementação (receita de bolo)

### 1. Fase 0. Preparação
1. **Objetivo**: alinhar contratos e escopo MVP.
2. **Arquivos a criar/editar**: ADR inicial + contrato BFF/BFA.
3. **Dependências para instalar**: nenhuma nova.
4. **Comandos de inicialização**: `git init` do novo repo.
5. **Exemplo de estrutura mínima**: `docs/adr/0001-bfa-mvp.md`.
6. **Critérios de pronto (Definition of Done)**: contrato aprovado e backlog MVP fechado.
7. **Erros comuns e como evitar**: escopo grande demais; evitar com definição de “não fazer”.

### 2. Fase 1. Inicialização do repositório
1. **Objetivo**: bootstrap do serviço.
2. **Arquivos a criar/editar**: `build.gradle.kts`, `application.properties`, `Dockerfile`.
3. **Dependências para instalar**: Spring Web, Jackson, Observability básicas.
4. **Comandos de inicialização**: `gradle init` / Spring Initializr.
5. **Exemplo de estrutura mínima**: API health.
6. **Critérios de pronto (Definition of Done)**: `GET /health` funcionando.
7. **Erros comuns e como evitar**: env vars não configuradas.

### 3. Fase 2. API básica do BFA
1. **Objetivo**: endpoint `/v1/nutrition/calculate` mockado.
2. **Arquivos a criar/editar**: controller + dto + error handler.
3. **Dependências para instalar**: validação de schema.
4. **Comandos de inicialização**: `./gradlew bootRun`.
5. **Exemplo de estrutura mínima**: request/response validados.
6. **Critérios de pronto (Definition of Done)**: contrato estável e teste de contrato passando.
7. **Erros comuns e como evitar**: quebrar compatibilidade do BFF.

### 4. Fase 3. Integração com LLM (simples)
1. **Objetivo**: parsing via LLM para ingredientes.
2. **Arquivos a criar/editar**: connector OpenAI + prompt v1.
3. **Dependências para instalar**: SDK OpenAI.
4. **Comandos de inicialização**: testes de integração.
5. **Exemplo de estrutura mínima**: parser retorna itens normalizados.
6. **Critérios de pronto (Definition of Done)**: cenários básicos funcionam.
7. **Erros comuns e como evitar**: depender do LLM para cálculo final.

### 5. Fase 4. Introdução do grafo multiagentes
1. **Objetivo**: pipeline com nós claros.
2. **Arquivos a criar/editar**: `graph/state`, `graph/nodes`.
3. **Dependências para instalar**: LangGraph/LangChain4j.
4. **Comandos de inicialização**: e2e do fluxo principal.
5. **Exemplo de estrutura mínima**: normalize->retrieve->compute->validate.
6. **Critérios de pronto (Definition of Done)**: tracing por nó funcionando.
7. **Erros comuns e como evitar**: grafo complexo demais cedo.

### 6. Fase 5. RAG mínimo viável
1. **Objetivo**: retrieval de base nutricional confiável.
2. **Arquivos a criar/editar**: ingest script + retriever.
3. **Dependências para instalar**: pgvector/Qdrant.
4. **Comandos de inicialização**: `scripts/ingest/load_initial_dataset`.
5. **Exemplo de estrutura mínima**: top-k com filtros por unidade.
6. **Critérios de pronto (Definition of Done)**: evidência retornada em resposta interna.
7. **Erros comuns e como evitar**: chunking sem metadados.

### 7. Fase 6. Integração BFF <-> BFA
1. **Objetivo**: BFF delegar `/nutrition/calories` para BFA por feature flag.
2. **Arquivos a criar/editar**: novo client BFA no BFF + adapter de resposta.
3. **Dependências para instalar**: HTTP client resiliente.
4. **Comandos de inicialização**: teste integrado local BFF+BFA.
5. **Exemplo de estrutura mínima**: fallback para engine antiga.
6. **Critérios de pronto (Definition of Done)**: contrato front preservado.
7. **Erros comuns e como evitar**: timeout sem fallback.

### 8. Fase 7. Observabilidade e testes
1. **Objetivo**: medir latência/custo/qualidade.
2. **Arquivos a criar/editar**: dashboards, métricas e e2e.
3. **Dependências para instalar**: OpenTelemetry.
4. **Comandos de inicialização**: suíte de integração + e2e.
5. **Exemplo de estrutura mínima**: dashboard de latência e erro.
6. **Critérios de pronto (Definition of Done)**: SLO inicial definido.
7. **Erros comuns e como evitar**: ausência de correlação de logs.

### 9. Fase 8. Hardening (segurança, guardrails)
1. **Objetivo**: reduzir riscos de injection/custo.
2. **Arquivos a criar/editar**: policies e validadores.
3. **Dependências para instalar**: rate-limit/circuit-breaker.
4. **Comandos de inicialização**: testes de segurança.
5. **Exemplo de estrutura mínima**: bloqueios e limites ativos.
6. **Critérios de pronto (Definition of Done)**: checklist de segurança aprovado.
7. **Erros comuns e como evitar**: logar dados sensíveis.

### 10. Fase 9. Expansão para imagem/OCR (esqueleto pronto)
1. **Objetivo**: deixar interface multimodal habilitada.
2. **Arquivos a criar/editar**: connectors `vision` e `ocr` + nós no grafo.
3. **Dependências para instalar**: OCR/Vision provider.
4. **Comandos de inicialização**: testes com mocks.
5. **Exemplo de estrutura mínima**: pipeline desligado por feature flag.
6. **Critérios de pronto (Definition of Done)**: endpoint aceita imageUrl/fileUrl sem quebrar.
7. **Erros comuns e como evitar**: acoplamento forte ao fornecedor.

---

## SEÇÃO M. Dependências e comandos de setup

### 1. Lista de dependências por categoria
- API: Spring Web
- IA: OpenAI SDK
- Dados: Postgres driver + Redis client
- Vetor: pgvector client (ou HTTP Qdrant)
- Observabilidade: Micrometer + OTel
- Qualidade: JUnit/Testcontainers/ktlint/detekt

### 2. Comandos de instalação
```bash
# Kotlin/Spring
./gradlew build

# Subir dependências locais
docker compose up -d
```

### 3. Arquivos iniciais (.env.example, docker-compose, Makefile ou equivalente)
- `.env.example`
- `docker-compose.yml`
- `Makefile`

### 4. Scripts úteis para desenvolvimento
- `make run`
- `make test`
- `make lint`
- `make ingest`

### 5. Como rodar localmente
```bash
cp .env.example .env
./gradlew bootRun
```

### 6. Como testar localmente
```bash
./gradlew test
./gradlew integrationTest
```

### 7. Como integrar localmente com o BFF atual
- Rodar BFA em `:8081`.
- Configurar BFF com `BFA_BASE_URL=http://localhost:8081`.
- Ativar `FEATURE_BFA_NUTRITION=true`.

---

## SEÇÃO N. Skeleton inicial (sem exagerar no código)

### 1. Gere o esqueleto dos arquivos principais
```text
src/main/kotlin/com/vidasync/bfa/
  api/controller/NutritionController.kt
  application/usecase/CalculateNutritionUseCase.kt
  application/dto/NutritionContracts.kt
  graph/NutritionGraph.kt
  tools/calculator/DeterministicNutritionCalculator.kt
  rag/retrieval/NutritionRetriever.kt
  connectors/openai/OpenAIAdapter.kt
  guardrails/output/NutritionOutputValidator.kt
```

### 2. Mostre apenas o mínimo necessário em cada arquivo para compilar/subir
```kotlin
// NutritionController.kt
@RestController
@RequestMapping("/v1/nutrition")
class NutritionController(private val useCase: CalculateNutritionUseCase) {
  @PostMapping("/calculate")
  fun calculate(@RequestBody req: NutritionRequest): NutritionResponse = useCase.execute(req)
}
```

```kotlin
// CalculateNutritionUseCase.kt
class CalculateNutritionUseCase(private val graph: NutritionGraph) {
  fun execute(req: NutritionRequest): NutritionResponse {
    // TODO: orchestrate graph
    return NutritionResponse(status = "ok")
  }
}
```

```kotlin
// NutritionOutputValidator.kt
class NutritionOutputValidator {
  fun validate(resp: NutritionResponse): NutritionResponse {
    // TODO: range checks and required evidence
    return resp
  }
}
```

### 3. Priorize contratos, interfaces e estrutura
- Contratos primeiro, lógica complexa depois.

### 4. Evite gerar código gigante de uma vez
- Implementar por nó de grafo e por feature flag.

### 5. Marque claramente TODOs para implementação manual
- `TODO: parsing robusto`
- `TODO: retrieval com filtro de unidade`
- `TODO: fallback policy`

---

## SEÇÃO O. Plano de migração do que existe hoje

### 1. Como sair de “1 prompt único no BFF”
- Manter endpoint atual e trocar engine por adapter BFA atrás de flag.

### 2. Quais responsabilidades mover primeiro para o BFA
1. parsing de ingredientes;
2. cálculo nutricional com validação;
3. correções de unidade;
4. gestão de evidências/citações.

### 3. O que manter no BFF
- Contratos com frontend.
- Autenticação/identidade/contexto do usuário.
- CRUD de refeições/favoritos e integração Supabase principal.

### 4. Estratégia de feature flag
- `FEATURE_BFA_NUTRITION` no BFF.
- rollout por porcentagem de usuários (canário).

### 5. Estratégia de rollback
- Em erro do BFA, fallback automático para `NutritionService` legado.
- Desativar flag sem redeploy pesado.

### 6. Plano de transição em entregas pequenas
- Sprint 1: contrato + mock BFA.
- Sprint 2: BFA real para cálculo.
- Sprint 3: RAG mínimo.
- Sprint 4: roteador de intenção + chat.

---

## SEÇÃO P. Backlog recomendado (próximos passos)

### 1. Roadmap de curto prazo (MVP)
- BFA básico com `/v1/nutrition/calculate`.
- Integração BFF por feature flag.
- Observabilidade mínima e testes de contrato.

### 2. Roadmap de médio prazo
- RAG robusto com aliases e porções.
- Chat conversacional com memória curta.
- Revisão de plano alimentar em texto.

### 3. Roadmap de longo prazo
- OCR/PDF ingestion de dieta.
- Visão para identificação de ingredientes.
- Auditoria avançada + painéis de qualidade por coorte.

### 4. Prioridade sugerida com base em impacto x esforço
1. Preservar contrato do front (alto impacto/baixo esforço)
2. BFA cálculo nutricional (alto impacto/médio)
3. RAG mínimo (alto impacto/médio)
4. Observabilidade IA (médio impacto/médio)
5. Chat avançado (médio impacto/alto)
6. Visão/OCR (alto impacto/alto)

---

## Entregáveis finais pedidos

### 1) Uma versão resumida da arquitetura em 1 página
- **Hoje**: BFF concentra API + negócio + IA (OpenAI direta no `NutritionService`).
- **Alvo**: BFF permanece interface do app; BFA concentra inteligência/orquestração/RAG/guardrails.
- **Ganhos**: desacoplamento, evolução incremental, melhor precisão auditável, observabilidade de IA.
- **Passo inicial**: criar BFA com um endpoint de cálculo nutricional e ligar no BFF por feature flag.
- **Princípio-chave**: cálculo numérico determinístico + LLM para interpretação/normalização.
- **Escalabilidade**: adicionar novos agentes por fluxo (chat, revisão de dieta, OCR/visão) sem quebrar contratos do app.

### 2) Um diagrama textual do fluxo (ASCII)
```text
[Frontend]
    |
    | POST /nutrition/calories
    v
[BFF - vidasync-bff]
    | validate + auth context + correlationId
    | (feature flag: FEATURE_BFA_NUTRITION)
    v
[BFA - vidasync-bfa]
    |--> NormalizeInputNode (LLM/parser)
    |--> RetrieveNutritionFactsNode (RAG/vector + aliases + units)
    |--> ComputeNutritionNode (deterministic calculator)
    |--> ValidateOutputNode (guardrails + plausibility)
    `--> ComposeResponseNode (structured + evidence)
    |
    v
[BFF adapter -> contrato atual]
    |
    v
[Frontend response: nutrition + ingredients + corrections + invalidItems]
```

### 3) Um checklist inicial de 10 passos para eu começar hoje
1. Criar novo repo `vidasync-bfa`.
2. Bootstrap Spring Boot/Kotlin com `/health`.
3. Definir contrato `/v1/nutrition/calculate`.
4. Implementar response mock compatível com BFF.
5. Adicionar client BFA no BFF.
6. Introduzir `FEATURE_BFA_NUTRITION=false` por padrão.
7. Implementar adapter BFF para manter contrato atual de `/nutrition/calories`.
8. Ativar logs com `correlationId` em BFF e BFA.
9. Criar 5 cenários e2e de nutrição (válido, inválido, unidade errada, alias, receita simples).
10. Fazer rollout canário com fallback automático para engine legada.

### 4) Um “primeiro corte” de MVP que eu consiga subir rápido, sem perder a visão futura
- BFA com 3 endpoints: `GET /v1/health`, `POST /v1/nutrition/calculate`, `GET /v1/version`.
- Workflow inicial em 4 passos:
  1. parse simples de ingredientes;
  2. consulta de cache/tabela base;
  3. cálculo determinístico;
  4. validação + resposta estruturada.
- Sem chat/visão no primeiro deploy, mas com interfaces prontas (`imageUrl/fileUrl`) para evolução.
- Integração no BFF por feature flag + fallback imediato para `NutritionService` atual.
