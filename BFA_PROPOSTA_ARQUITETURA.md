# Proposta Arquitetural VidaSync — BFF + BFA (Multiagentes)

> Documento prático para evolução incremental do backend atual para um modelo com **BFF enxuto** + **BFA (Back for Agents)**.
>  
> **Suposições explícitas** (quando faltou contexto):  
> - O front continuará chamando apenas o BFF (`/auth`, `/meals`, `/favorites`, `/nutrition`).  
> - O novo BFA será um serviço HTTP interno (rede privada) inicialmente.  
> - A prioridade é preservar contratos atuais com o front e migrar inteligência gradualmente.

---

## SEÇÃO A. Diagnóstico do BFF atual (com base no código)

### 1. Resumo da arquitetura atual
- **Stack**: Kotlin 2.2 + Spring Boot 3.5 + Java 21 (virtual threads).  
  (Fonte: `build.gradle.kts`, `README.md`)
- **Estrutura**: `controller` (API), `service` (regras), `client` (Supabase/Storage), `dto`.
- **Integrações externas**:
  - OpenAI: `OpenAIConfig.kt`, `NutritionService.kt`
  - Supabase REST: `SupabaseConfig.kt`, `SupabaseClient.kt`
  - Supabase Storage: `SupabaseStorageClient.kt`
- **Padrão atual de IA**:
  - Endpoint público `POST /nutrition/calories`
  - `NutritionService.calculateNutritionSmart(...)` quebra ingredientes, consulta cache, chama OpenAI por ingrediente em paralelo, corrige unidade, valida item inválido.

### 2. Pontos fortes
- Boa separação Controller/Service/Client.
- Cache de ingredientes já reduz custo/latência (`IngredientCacheService`).
- Uso de virtual threads para paralelismo de chamadas OpenAI.
- Contratos estáveis para front já documentados (`FRONTEND_API_GUIDE.md`).
- Capacidade de fallback no fluxo nutricional (modo legado).

### 3. Gargalos para IA multiagente
- Orquestração de IA está acoplada ao BFF (`NutritionService` mistura coordenação + prompt + parsing + regra).
- Não há camada de workflow multi-step explícita (estado, transição, roteamento).
- Observabilidade de IA limitada (não há tracing por etapa/agente/custo token).
- Guardrails de prompt injection e política nutricional ainda básicos.
- Escala futura (chat, OCR, visão, revisão de dieta) tende a inflar o BFF.

### 4. Onde encaixar o BFA sem quebrar o que já existe
- **Sem quebrar front**: BFF mantém os endpoints atuais e passa a delegar apenas inteligência ao BFA.
- **Primeiro encaixe natural**: substituir apenas o núcleo de `/nutrition/calories` por chamada interna ao BFA.
- **Depois**: novos casos (chat, revisão de plano, ingestão OCR/imagem) entram primeiro no BFA e são expostos no BFF de forma controlada.

---

## SEÇÃO B. Proposta de arquitetura alvo (BFF + BFA)

### 1. Papel do BFF
- Camada de experiência do front: autenticação, autorização, composição de resposta, versionamento externo.
- Persistência de dados operacionais (meals/favorites/profile) e regras transacionais já existentes.
- Adaptador de contratos estáveis para o front.

### 2. Papel do BFA
- Orquestração de agentes e fluxos multi-step.
- Ferramentas de IA (nutrição, normalização de alimento, cálculo auditável, RAG, OCR/visão no futuro).
- Governança de prompts/modelos, avaliação e observabilidade de IA.

### 3. Fluxo de chamadas entre front, BFF e BFA
1. Front chama `POST /nutrition/calories` no BFF (contrato atual).
2. BFF valida request + adiciona `correlationId`.
3. BFF chama `POST /v1/nutrition/calculate` no BFA (interno).
4. BFA executa grafo (normalização → retrieval/cálculo → validação → resposta).
5. BFF traduz resposta BFA para o contrato atual e devolve ao front.

### 4. Estratégia de evolução incremental (sem Big Bang)
1. Criar BFA com 1 endpoint de cálculo nutricional.
2. BFF com feature flag por endpoint (`USE_BFA_NUTRITION=true/false`).
3. Shadow mode opcional (BFF chama BFA em paralelo sem impactar resposta, apenas compara).
4. Migrar gradualmente prompts e cache de ingrediente para BFA.
5. Depois expandir para chat/RAG/OCR/visão.

### 5. Tradeoffs da abordagem escolhida
- **Prós**: desacoplamento, governança de IA, escala de produto IA.
- **Contras**: mais um serviço para operar; latência de rede extra; exigirá observabilidade forte.
- **Mitigação**: timeout curto, fallback no BFF, cache, retries com backoff e circuit breaker.

---

## SEÇÃO C. Nome do novo repositório (me dê pelo menos 10 opções)

### 1. Sugestões de nome com justificativa
1. `vidasync-bfa` — direto, simples e alinhado ao padrão BFF/BFA.  
2. `vidasync-agents` — comunica foco em agentes.  
3. `vidasync-ai-core` — enfatiza núcleo de inteligência.  
4. `vidasync-orchestrator` — foco em orquestração de fluxos.  
5. `vidasync-nutri-intelligence` — domínio explícito de nutrição.  
6. `vidasync-cognitive-engine` — semântico para evolução multi-modal.  
7. `vidasync-ml-orchestration` — técnico para plataforma.  
8. `vidasync-nutri-brain` — branding amigável.  
9. `vidasync-agent-platform` — visão de plataforma.  
10. `vidasync-reasoning-service` — foco em inferência guiada.  
11. `vidasync-intelligence-hub` — centraliza IA.  
12. `vidasync-bfa-multiagent` — explícito para contexto interno.

### 2. Sugestão final (top 3)
1. **`vidasync-bfa`** (recomendação principal)  
2. `vidasync-agents`  
3. `vidasync-ai-core`

### 3. Convenção de naming (repo, package, service name, env vars)
- Repo: `vidasync-bfa`
- Package Kotlin: `com.vidasync.bfa`
- Service name: `vidasync-bfa`
- Prefixo env vars: `BFA_` e `VIDASYNC_`  
  Ex.: `BFA_PORT`, `BFA_OPENAI_API_KEY`, `BFA_VECTOR_DB_URL`

---

## SEÇÃO D. Stack recomendada para o BFA

### 1. Linguagem e framework principal (explique por que)
**Opção A (MVP, recomendada para começar): Kotlin + Spring Boot**  
- Reuso de stack/time atual (menor curva).  
- Compartilha DTOs/padrões com BFF.

**Opção B (escala IA): Python + FastAPI**  
- Ecossistema de IA mais amplo e rápido para prototipar agentes multi-modais.

### 2. Biblioteca de orquestração (LangGraph/LangChain) e por que
- **LangGraph** para workflows com estado e transição explícita (ideal multi-step auditável).
- **LangChain** para tool abstractions/retrievers.

### 3. Biblioteca de API
- Opção A: Spring Web (Spring Boot).
- Opção B: FastAPI + Pydantic.

### 4. Validação de schemas
- Opção A: Kotlinx Serialization/Jackson + validações Bean Validation.
- Opção B: Pydantic v2.

### 5. Banco de dados / memória / cache
- PostgreSQL para persistência auditável.
- Redis para cache curto (respostas/retrieval/features).

### 6. Vetor store para RAG
- MVP: pgvector no mesmo PostgreSQL.
- Escala: Qdrant/Weaviate/Pinecone.

### 7. Observabilidade (logs, traces, custo, latência)
- OpenTelemetry + Grafana Tempo/Prometheus/Loki.
- Métricas de IA: tokens in/out, custo estimado, taxa de fallback.

### 8. Testes
- Unitário por tool/agent.
- Integração de workflow (graph tests).
- E2E contrato BFF↔BFA.

### 9. Lint/format/type-check
- Opção A: ktlint + detekt + test.
- Opção B: ruff + mypy + pytest.

### 10. Docker / docker compose
- Dockerfile único por serviço.
- `docker-compose` local com BFA + Redis + Postgres/pgvector + observabilidade mínima.

### 11. Gerenciamento de configuração (.env)
- `.env.example` versionado.
- validação de env na inicialização (fail fast).

**Comparativo rápido**
- **Opção A**: complexidade baixa, custo operacional baixo, curva baixa.
- **Opção B**: complexidade média, custo médio, curva média (mas mais agilidade para IA avançada).

---

## SEÇÃO E. Arquitetura de software em nível de pastas (muito detalhada)

### 1. Estrutura de diretórios do novo repositório (tree)
```text
vidasync-bfa/
├─ src/
│  ├─ api/
│  │  ├─ controllers/
│  │  ├─ middleware/
│  │  └─ schemas/
│  ├─ application/
│  │  ├─ usecases/
│  │  └─ orchestrators/
│  ├─ domain/
│  │  ├─ services/
│  │  ├─ models/
│  │  └─ policies/
│  ├─ agents/
│  │  ├─ router/
│  │  ├─ nutrition/
│  │  ├─ rag/
│  │  └─ chat/
│  ├─ workflows/
│  │  ├─ graphs/
│  │  └─ states/
│  ├─ tools/
│  │  ├─ nutrition_calc/
│  │  ├─ retrieval/
│  │  ├─ normalization/
│  │  └─ safety/
│  ├─ rag/
│  │  ├─ ingestion/
│  │  ├─ chunking/
│  │  ├─ embeddings/
│  │  └─ retrievers/
│  ├─ connectors/
│  │  ├─ openai/
│  │  ├─ postgres/
│  │  ├─ redis/
│  │  └─ vectorstore/
│  ├─ prompts/
│  ├─ guardrails/
│  ├─ observability/
│  └─ shared/
├─ scripts/
│  ├─ ingestion/
│  └─ eval/
├─ tests/
│  ├─ unit/
│  ├─ integration/
│  ├─ contract/
│  └─ e2e/
├─ docker/
├─ .env.example
├─ docker-compose.yml
└─ README.md
```

### 2. Papel de cada pasta e subpasta
- `api`: entrada/saída HTTP e contratos externos do BFA.
- `application`: casos de uso e coordenação de domínio.
- `domain`: regra nutricional auditável e entidades.
- `agents`: raciocínio por especialidade.
- `workflows`: grafo de execução.
- `tools`: funções executáveis pelos agentes.
- `rag`: pipeline de base de conhecimento.
- `connectors`: adapters para infra externa.
- `guardrails`: segurança e políticas.
- `observability`: logging/tracing/métricas.
- `tests`: validação por camada.
- `scripts/ingestion`: carga e atualização da base nutricional.

### 3. Onde ficam:
- 3.1 Agentes: `src/agents/*`
- 3.2 Grafos/workflows: `src/workflows/graphs`
- 3.3 Tools: `src/tools/*`
- 3.4 Prompts: `src/prompts/*`
- 3.5 RAG: `src/rag/*`
- 3.6 Conectores: `src/connectors/*`
- 3.7 Schemas: `src/api/schemas/*`
- 3.8 Casos de uso: `src/application/usecases/*`
- 3.9 Serviços de domínio: `src/domain/services/*`
- 3.10 Guardrails e validação: `src/guardrails/*`
- 3.11 Observabilidade: `src/observability/*`
- 3.12 Testes: `tests/*`
- 3.13 Scripts de ingestão: `scripts/ingestion/*`

### 4. Convenções de nome de arquivo
- `snake_case` para arquivos de prompt.
- `PascalCase` para classes.
- Sufixos:
  - `*Controller`, `*UseCase`, `*Agent`, `*Tool`, `*Retriever`, `*Policy`, `*Schema`.

---

## SEÇÃO F. Desenho multiagentes para o VidaSync

### 1. Quais agentes você recomenda inicialmente (MVP)
1. `IntentRouterAgent` (leve, opcional no MVP inicial).  
2. `NutritionCalcAgent` (foco em cálculo auditável).  
3. `NutritionRagAgent` (consulta base nutricional para contexto).

### 2. Quais agentes deixar para fase 2 e fase 3
- **Fase 2**: `DietReviewAgent`, `PlanImportAgent`.
- **Fase 3**: `VisionIngredientAgent`, `OCRTranscriptionAgent`, `ConversationCoachAgent`.

### 3. Responsabilidade de cada agente
- Router: classifica intenção.
- Calc: calcula macros/calorias com regras determinísticas + validação.
- RAG: recupera evidências/fatos nutricionais.
- Diet review: avalia plano alimentar e consistência.
- Vision/OCR: transforma imagem/PDF em texto estruturado.

### 4. Quando usar roteador de intenção e quando não usar
- Use quando entrada for aberta (chat livre).
- Não use em endpoint já específico (ex.: `/nutrition/calculate`), para reduzir latência/custo.

### 5. Como evitar overengineering no começo
- Começar com 1 fluxo fixo de nutrição (sem muitos agentes).
- Introduzir router só quando abrir chat multi-intenção.
- Limitar número de tools por fase.

### 6. Proposta de grafo de execução (LangGraph) com estados, nós e transições
- `START` → `sanitize_input` → `normalize_ingredients` → `deterministic_calc`
- Se confiança baixa/ambiguidade: `nutrition_rag_retrieval` → `llm_resolution`
- `validate_output` → `format_response` → `END`
- Estados: `raw_input`, `normalized_items`, `calc_result`, `citations`, `confidence`, `errors`.

### 7. Estratégia para fallback quando o agente falhar
- Timeout por nó.
- Retry com backoff em conectores externos.
- Fallback para cálculo simplificado conhecido.
- Retorno com `degraded=true` + mensagem clara.

### 8. Estratégia para respostas determinísticas em cálculos nutricionais
- Regra: cálculo final por engine determinística (não LLM).
- LLM apenas para normalização/desambiguação textual.
- Tabelas nutricionais versionadas e rastreáveis.

### 9. Estratégia para separar “resposta conversacional” de “cálculo auditável”
- Dois campos na resposta interna:
  - `audit`: números, fontes, fórmula, unidades.
  - `assistant_message`: texto amigável.
- BFF decide quanto expor ao front conforme endpoint.

---

## SEÇÃO G. RAG para nutrição (precisão)

### 1. Quais fontes de conhecimento modelar
- Tabelas nutricionais oficiais (por 100g/porção).
- Regras de porção e medidas caseiras.
- Dicionário de aliases regionais (aipim/macaxeira/mandioca).
- Base de receitas compostas.

### 2. Estratégia de ingestão de dados
- Pipeline batch com validação de schema.
- Versionamento de dataset (`dataset_version`).
- Deduplicação por alimento + unidade + fonte.

### 3. Estratégia de chunking
- Chunk semântico por alimento/porção.
- Manter metadados: `food_id`, `aliases`, `unit`, `source`, `confidence`.

### 4. Estratégia de embeddings
- Embeddings multilíngues (PT-BR primeiro).
- Re-embed apenas delta de dados novos.

### 5. Estratégia de retrieval
- Top-k inicial 5–10.
- Filtro por idioma/região/tipo alimento.
- Rerank opcional quando consulta ambígua.

### 6. Como citar a origem internamente para auditoria da resposta
- Guardar `source_id`, `source_name`, `row_id`, `dataset_version` na resposta interna.
- Persistir trilha de decisão em tabela de auditoria.

### 7. Como lidar com sinônimos
- Camada de normalização lexical antes do retrieval.
- Tabela de aliases com equivalência canônica.

### 8. Como lidar com unidades e medidas caseiras
- Dicionário de conversão (colher, xícara, concha, unidade).
- Converter sempre para base padrão (g/ml) antes do cálculo.

### 9. Como lidar com alimentos compostos (receitas)
- Decompor em ingredientes quando possível.
- Se receita pronta: usar ficha técnica própria + faixa de variação.

### 10. Como validar a precisão antes de responder ao usuário
- Regras automáticas: macros não negativos, soma coerente de kcal.
- Score de confiança + flags de ambiguidades.
- Se baixa confiança, pedir confirmação (ex.: “banana prata ou nanica?”).

---

## SEÇÃO H. Visão, OCR e transcrição (futuro próximo)

### 1. Como desenhar agora para suportar imagem no futuro sem quebrar a arquitetura
- Definir interface `DocumentVisionTool` desde já, mesmo com stub.
- Fluxos de entrada multimodal no BFA (`input_type=text|image|pdf`).

### 2. Pipeline sugerido para:
#### 2.1 identificação de ingredientes por imagem
- visão → detecção de itens → normalização de nomes → porções estimadas.

#### 2.2 OCR de plano alimentar
- OCR estruturado → extração de refeições/horários/quantidades.

#### 2.3 normalização do texto extraído
- limpeza de ruído, unificação de unidades, alias mapping.

#### 2.4 validação humana opcional
- etapa “confirmar antes de salvar” no front.

### 3. Como integrar isso com agentes e RAG
- OCR/Vision retornam JSON estruturado para `NutritionCalcAgent`.
- `NutritionRagAgent` complementa macros faltantes e valida nomes.

### 4. Riscos de precisão e mitigação
- Risco: erro de detecção visual.
- Mitigação: confidence threshold + confirmação humana + comparação com base RAG.

---

## SEÇÃO I. Contratos de integração BFF <-> BFA

### 1. Endpoints sugeridos do BFA (MVP)
- `POST /v1/nutrition/calculate`
- `POST /v1/chat/respond` (fase 2)
- `POST /v1/diet/review` (fase 2)

### 2. Requests e responses (schemas detalhados)
**Request MVP**
```json
{
  "requestId": "uuid",
  "userId": "uuid-opcional",
  "locale": "pt-BR",
  "foods": "200g arroz, 150g frango",
  "context": {
    "mealType": "lunch",
    "date": "2026-03-05"
  }
}
```

**Response MVP**
```json
{
  "requestId": "uuid",
  "nutrition": { "calories": "610 kcal", "protein": "35g", "carbs": "77g", "fat": "12g" },
  "ingredients": [
    { "name": "200g de arroz", "nutrition": { "calories": "260 kcal", "protein": "5g", "carbs": "57g", "fat": "0.5g" }, "cached": true }
  ],
  "corrections": [{ "original": "250ml de arroz", "corrected": "250g de arroz" }],
  "invalidItems": [],
  "audit": {
    "datasetVersion": "2026-03",
    "sources": ["tbca:arroz_branco_cozido:100g"]
  },
  "meta": { "latencyMs": 420, "degraded": false }
}
```

### 3. Idempotência
- Header `Idempotency-Key` para operações pesadas (chat/review/import).

### 4. Correlation ID / Request ID
- BFF gera `X-Correlation-Id` e propaga ao BFA.
- BFA responde com mesmo ID.

### 5. Tratamento de erros padronizado
```json
{
  "error": {
    "code": "INVALID_INGREDIENT",
    "message": "\"cadeira\" não é alimento válido",
    "details": { "invalidItems": ["cadeira"] }
  }
}
```

### 6. Timeouts, retries e circuit breaker
- Timeout BFF→BFA: 2–4s no MVP.
- Retry apenas em falha transitória (`5xx`, timeout curto).
- Circuit breaker após falhas consecutivas.

### 7. Como versionar a API
- Prefixo `/v1`.
- Mudança breaking: nova versão `/v2`.

### 8. Estratégia para streaming (se eu quiser chat streaming no futuro)
- Preparar endpoint SSE/WebSocket no BFA.
- BFF pode proxy de stream para o front.

---

## SEÇÃO J. Segurança, privacidade e guardrails

### 1. Dados sensíveis e cuidado com PII
- Evitar enviar dados pessoais desnecessários ao LLM.
- Pseudonimizar `userId` quando possível.

### 2. Sanitização de entrada
- Limite de tamanho de payload.
- Sanitização de caracteres de controle.

### 3. Prompt injection (explicar e propor mitigação prática)
- Risco: entrada tenta sobrescrever instruções do sistema.
- Mitigação: templates fixos + allowlist de tools + política de bloqueio de instruções fora de escopo.

### 4. Limites de custo e tokens
- `max_tokens` por endpoint.
- orçamento diário por usuário/tenant.

### 5. Rate limiting
- Por IP + usuário + endpoint.

### 6. Regras para respostas nutricionais seguras (sem substituir profissional)
- Mensagem padrão: orientação informativa, não substitui nutricionista.
- Bloquear recomendações de risco clínico.

### 7. Logs seguros (sem vazar dados sensíveis)
- Mascarar tokens/chaves e truncar payloads.
- Desativar log de conteúdo sensível em produção.

---

## SEÇÃO K. Observabilidade e qualidade

### 1. Logs estruturados
- JSON logs com `timestamp`, `level`, `service`, `correlationId`, `endpoint`.

### 2. Métricas (latência, erro, custo, tokens, acurácia percebida)
- P50/P95 latência por nó/agente.
- Erro por tipo (`timeout`, `invalid`, `provider_error`).
- Tokens e custo por request.

### 3. Tracing de fluxo multiagente
- Span por nó do grafo (`normalize`, `retrieve`, `calc`, `validate`).

### 4. Como medir qualidade de respostas
- Dataset de avaliação com casos reais do VidaSync.
- Métrica de precisão macro/calorias vs referência.

### 5. Conjunto mínimo de dashboards
1. Saúde geral API BFA
2. Eficiência de agentes/workflows
3. Custo de LLM e cache hit rate
4. Qualidade nutricional (diferença vs baseline)

### 6. Estratégia de testes e2e com cenários do VidaSync
- Cenários: arroz/frango/banana; unidade incorreta; item inválido; refeição composta.

### 7. Testes de contratos com BFF
- Pact/contract tests para garantir compatibilidade de schema e códigos de erro.

---

## SEÇÃO L. Passo a passo de implementação (receita de bolo)

### 1. Fase 0. Preparação
- **Objetivo**: alinhar arquitetura e domínio.
- **Arquivos**: ADR inicial + backlog técnico.
- **Dependências**: nenhuma.
- **Comandos**: criar repositório.
- **Estrutura mínima**: README + `.env.example`.
- **DoD**: decisão de stack aprovada.
- **Erros comuns**: tentar desenhar tudo antes de validar 1 caso.

### 2. Fase 1. Inicialização do repositório
- **Objetivo**: serviço sobe localmente.
- **Arquivos**: `Dockerfile`, `docker-compose.yml`, app bootstrap.
- **Dependências**: framework escolhido + health endpoint.
- **Comandos**: init do projeto.
- **DoD**: `GET /health` retorna UP.
- **Erros comuns**: não validar env obrigatória na largada.

### 3. Fase 2. API básica do BFA
- **Objetivo**: endpoint `/v1/nutrition/calculate` stub.
- **Arquivos**: controller + schemas request/response.
- **Dependências**: validação de schema.
- **DoD**: contrato estável retornando mock válido.

### 4. Fase 3. Integração com LLM (simples)
- **Objetivo**: primeiro fluxo real com OpenAI.
- **Arquivos**: connector LLM + prompt inicial.
- **DoD**: cálculo básico funcionando com logs.
- **Erros comuns**: prompt sem formato de saída rígido.

### 5. Fase 4. Introdução do grafo multiagentes
- **Objetivo**: separar etapas em nós.
- **Arquivos**: state + graph + nodes.
- **DoD**: fluxo com transições e fallback.

### 6. Fase 5. RAG mínimo viável
- **Objetivo**: retrieval em base nutricional.
- **Arquivos**: ingestion, retriever, embeddings.
- **DoD**: respostas com `audit.sources`.

### 7. Fase 6. Integração BFF <-> BFA
- **Objetivo**: BFF delega nutrição para BFA por feature flag.
- **Arquivos no BFF**: novo client BFA + toggle no `NutritionService`.
- **DoD**: front inalterado, contrato preservado.

### 8. Fase 7. Observabilidade e testes
- **Objetivo**: operação confiável.
- **Arquivos**: métricas, traces, testes contrato/e2e.
- **DoD**: dashboards mínimos ativos.

### 9. Fase 8. Hardening (segurança, guardrails)
- **Objetivo**: segurança de produção.
- **Arquivos**: políticas guardrails, rate limit, sanitização.
- **DoD**: checklist de segurança aprovado.

### 10. Fase 9. Expansão para imagem/OCR (esqueleto pronto)
- **Objetivo**: preparar multimodal sem quebrar base.
- **Arquivos**: interfaces/stubs de visão e OCR.
- **DoD**: endpoint aceita `input_type=image|pdf` com fluxo desativado por flag.

---

## SEÇÃO M. Dependências e comandos de setup

### 1. Lista de dependências por categoria
- API, validação, observabilidade, LLM SDK, DB, cache, vector store, testes.

### 2. Comandos de instalação
- **Kotlin/Spring**: `spring init` + dependências Gradle.
- **Python/FastAPI**: `uv init`/`poetry init` + pacotes.

### 3. Arquivos iniciais (.env.example, docker-compose, Makefile ou equivalente)
- `.env.example` com todas envs obrigatórias.
- `docker-compose.yml` com BFA + Redis + Postgres(pgvector).
- `Makefile`: `run`, `test`, `lint`, `up`, `down`.

### 4. Scripts úteis para desenvolvimento
- `make dev`, `make test-contract`, `make ingest-nutrition`.

### 5. Como rodar localmente
- `docker compose up -d` + comando run da API.

### 6. Como testar localmente
- unit + integration + contract com BFF.

### 7. Como integrar localmente com o BFF atual
- No BFF: `BFA_BASE_URL=http://localhost:8090`
- Feature flag ligada apenas em ambiente local.

---

## SEÇÃO N. Skeleton inicial (sem exagerar no código)

### 1. Gere o esqueleto dos arquivos principais
```text
src/api/controllers/NutritionController.*
src/api/schemas/NutritionSchemas.*
src/application/usecases/CalculateNutritionUseCase.*
src/workflows/graphs/NutritionGraph.*
src/agents/nutrition/NutritionCalcAgent.*
src/tools/nutrition_calc/DeterministicNutritionTool.*
src/rag/retrievers/NutritionRetriever.*
src/connectors/openai/OpenAiClient.*
src/guardrails/InputPolicy.*
```

### 2. Mostre apenas o mínimo necessário em cada arquivo para compilar/subir
- Controller recebe request e chama use case.
- Use case chama orchestrator.
- Orchestrator executa grafo simples.
- Resposta inclui `nutrition`, `ingredients`, `invalidItems`.

### 3. Priorize contratos, interfaces e estrutura
- Primeiro interfaces + DTOs.
- Depois implementação interna.

### 4. Evite gerar código gigante de uma vez
- Incremento por endpoint e por nó do grafo.

### 5. Marque claramente TODOs para implementação manual
- TODO: integração real com embeddings.
- TODO: validação clínica adicional.
- TODO: estratégia de reranking.

---

## SEÇÃO O. Plano de migração do que existe hoje

### 1. Como sair de “1 prompt único no BFF”
- Extrair prompt/cálculo para BFA e transformar BFF em adapter.

### 2. Quais responsabilidades mover primeiro para o BFA
1. Normalização de ingredientes.  
2. Cálculo nutricional IA + validação.  
3. Cache semântico e RAG nutricional.

### 3. O que manter no BFF
- Endpoints públicos atuais.
- Autenticação e user context.
- Persistência de refeições/favoritos/perfil.

### 4. Estratégia de feature flag
- `USE_BFA_NUTRITION`, `USE_BFA_CHAT`, `USE_BFA_DIET_REVIEW`.

### 5. Estratégia de rollback
- Toggle imediato para fluxo antigo no BFF.
- Circuit breaker forçando fallback local.

### 6. Plano de transição em entregas pequenas
- Sprint 1: BFA stub + integração desligada.
- Sprint 2: cálculo real ligado para pequena amostra.
- Sprint 3: 100% tráfego + chat fase 2.

---

## SEÇÃO P. Backlog recomendado (próximos passos)

### 1. Roadmap de curto prazo (MVP)
- BFA com cálculo nutricional + auditoria mínima.
- Integração BFF via feature flag.
- Métricas básicas de latência/custo.

### 2. Roadmap de médio prazo
- Chat nutricional contextual.
- RAG robusto com aliases/unidades.
- Revisão de plano alimentar em texto.

### 3. Roadmap de longo prazo
- OCR + visão para plano alimentar e ingredientes.
- Recomendação personalizada com histórico.
- Avaliação contínua de qualidade com feedback do usuário.

### 4. Prioridade sugerida com base em impacto x esforço
1. BFA cálculo nutricional (alto impacto / esforço moderado).  
2. RAG base + aliases (alto impacto / moderado).  
3. Observabilidade IA (alto impacto / baixo-médio).  
4. Chat multiagente (médio impacto / moderado).  
5. OCR/visão (alto impacto / alto esforço).

---

## Resumo em 1 página (executivo)
- Seu BFF atual está funcional e bem estruturado para CRUD + nutrição simples, mas a inteligência está acoplada no `NutritionService`.
- A evolução ideal é criar um **BFA separado** para centralizar orquestração de agentes, RAG, guardrails e observabilidade de IA.
- Faça migração incremental: primeiro somente `/nutrition/calories`, preservando contrato do front.
- Use feature flags e fallback para zerar risco de rollout.
- Mantenha cálculo nutricional final determinístico (auditável) e use LLM para entendimento de linguagem/ambiguidade.
- Estruture desde o início rastreabilidade (`correlationId`, `requestId`, `sources`, `datasetVersion`) para precisão e confiança.
- Prepare interfaces multimodais (imagem/PDF) agora, mesmo com implementação stub, para expansão sem retrabalho.

## Diagrama textual do fluxo (ASCII)
```text
[Frontend]
    |
    | POST /nutrition/calories (contrato atual)
    v
[BFF - VidaSync]
    | valida request + auth + correlationId
    | feature flag (BFA on/off)
    v
[BFA - Multiagents]
    |--> sanitize_input
    |--> normalize_ingredients
    |--> deterministic_calc
    |--> rag_retrieval (se ambíguo)
    |--> validate_output + guardrails
    v
  resposta estruturada + audit
    |
    v
[BFF]
    | adapta para contrato atual
    v
[Frontend]
```

## Checklist inicial de 10 passos para começar hoje
1. Criar repo `vidasync-bfa`.
2. Subir serviço com `/health`.
3. Definir schemas de `POST /v1/nutrition/calculate`.
4. Implementar fluxo stub com resposta mock.
5. Adicionar OpenAI connector básico.
6. Adicionar cálculo determinístico simples.
7. Adicionar feature flag no BFF para delegar cálculo.
8. Implementar timeout/retry/circuit breaker BFF→BFA.
9. Instrumentar logs + correlationId ponta a ponta.
10. Rodar teste de contrato para garantir resposta igual ao endpoint atual.

## “Primeiro corte” de MVP (rápido e com visão futura)
- **Escopo mínimo**:
  - 1 endpoint BFA (`/v1/nutrition/calculate`)
  - 1 workflow (normalizar → calcular → validar)
  - 1 integração BFF com flag
  - 1 dashboard básico (latência/erro/custo)
- **Fora do MVP inicial**:
  - chat livre multi-intenção
  - OCR e visão em produção
  - roteador complexo de agentes
- **Ganhos imediatos**:
  - BFF permanece limpo
  - base pronta para RAG e multiagentes
  - risco controlado com rollback simples
