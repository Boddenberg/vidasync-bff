<div align="center">

# 🥗 VidaSync BFF

### Backend-For-Frontend do seu assistente nutricional inteligente

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21_(Virtual_Threads)-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![OpenAI](https://img.shields.io/badge/OpenAI-GPT--4o--mini-412991?logo=openai&logoColor=white)](https://openai.com/)
[![Supabase](https://img.shields.io/badge/Supabase-PostgreSQL_+_Auth_+_Storage-3FCF8E?logo=supabase&logoColor=white)](https://supabase.com/)
[![Deploy](https://img.shields.io/badge/Railway-Deploy-0B0D0E?logo=railway&logoColor=white)](https://railway.app/)

<br/>

**Registre refeições · Calcule macros com IA · Monte sua linha do tempo nutricional**

[Arquitetura](#-arquitetura) · [Endpoints](#-endpoints) · [Setup](#-setup-local) · [Deploy](#-deploy)

</div>

---

## ✨ Destaques

| Feature | Como funciona |
|---|---|
| 🧠 **Cálculo nutricional com IA** | Cada ingrediente é analisado individualmente pelo GPT-4o-mini em chamadas **assíncronas paralelas** via Virtual Threads |
| ⚡ **Cache inteligente de ingredientes** | Resultados da IA são salvos no banco — na próxima vez, **zero chamadas à OpenAI** |
| 🔄 **Correção automática de unidades** | `"250ml de arroz"` → `"250g de arroz"` — a IA corrige e avisa o usuário |
| 🚫 **Validação de alimentos** | `"100g de cadeira"` → **400 Bad Request** com mensagem amigável |
| 📸 **Upload de imagens** | Fotos de refeições e favoritos via base64 → Supabase Storage |
| 📅 **Linha do tempo por dia** | Refeições com horário, resumo diário com soma de macros |
| 🔐 **Autenticação simples** | Login por username + senha via Supabase Auth (sem JWT no cliente) |
| 🩹 **Auto-heal de perfis** | Se o perfil sumir do banco, o login recria automaticamente |

---

## 🏗 Arquitetura

```
┌──────────────┐        ┌──────────────────────────────────────┐
│   Frontend   │───────▶│           VidaSync BFF               │
│  (React Native)       │                                      │
└──────────────┘        │  ┌──────────┐  ┌──────────────────┐  │
                        │  │Controllers│  │    Services       │  │
                        │  │  • Auth   │  │  • NutritionSvc  │  │
                        │  │  • Meals  │──│  • MealSvc       │  │
                        │  │  • Favs   │  │  • FavoriteSvc   │  │
                        │  │  • Nutri  │  │  • AuthSvc       │  │
                        │  │  • Health │  │  • CacheSvc      │  │
                        │  └──────────┘  └────────┬─────────┘  │
                        │                         │            │
                        │         ┌───────────────┼──────┐     │
                        │         ▼               ▼      ▼     │
                        │  ┌──────────┐  ┌──────┐ ┌────────┐   │
                        │  │ OpenAI   │  │Supa  │ │Supa    │   │
                        │  │ GPT-4o   │  │base  │ │base    │   │
                        │  │ mini     │  │REST  │ │Storage │   │
                        │  └──────────┘  └──────┘ └────────┘   │
                        └──────────────────────────────────────┘
```

### Stack tecnológica

| Camada | Tecnologia |
|---|---|
| **Linguagem** | Kotlin 2.2 |
| **Framework** | Spring Boot 3.5 |
| **Runtime** | Java 21 (Virtual Threads para paralelismo) |
| **IA** | OpenAI GPT-4o-mini (`openai-java 2.1.0`) |
| **Banco de dados** | Supabase (PostgreSQL via REST API) |
| **Autenticação** | Supabase Auth (email/password) |
| **Storage** | Supabase Storage (imagens de refeições e perfil) |
| **HTTP Client** | Spring RestClient + Apache HttpClient 5 |
| **Serialização** | Jackson + kotlin-module |
| **Build** | Gradle 9.3 (Kotlin DSL) |
| **Container** | Docker (multi-stage build, Temurin JRE 21) |
| **Deploy** | Railway |

---

## 📡 Endpoints

### 🔓 Rotas públicas

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/health` | Health check |
| `POST` | `/auth/signup` | Criar conta |
| `POST` | `/auth/login` | Login (retorna `userId` + `accessToken`) |
| `POST` | `/nutrition/calories` | Calcular macros com IA |

### 🔒 Rotas autenticadas (header `X-User-Id`)

#### 🍽 Refeições

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/meals` | Criar refeição |
| `GET` | `/meals?date=YYYY-MM-DD` | Listar refeições do dia |
| `GET` | `/meals/summary?date=YYYY-MM-DD` | Resumo do dia (soma de macros) |
| `GET` | `/meals/range?startDate=...&endDate=...` | Refeições por período |
| `PUT` | `/meals/{id}` | Editar refeição (update parcial) |
| `DELETE` | `/meals/{id}` | Deletar refeição |
| `POST` | `/meals/{id}/duplicate` | Duplicar refeição |

#### ⭐ Favoritos

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/favorites` | Criar prato favorito (com foto opcional) |
| `GET` | `/favorites` | Listar favoritos |
| `DELETE` | `/favorites/{id}` | Deletar favorito |

#### 👤 Perfil

| Método | Rota | Headers extras | Descrição |
|---|---|---|---|
| `GET` | `/auth/profile` | — | Ver perfil |
| `PUT` | `/auth/profile` | `X-Access-Token` | Editar perfil/senha/username |

---

## 🧠 Motor de Nutrição Inteligente

O coração do VidaSync é o `NutritionService` — um pipeline que combina IA com cache para calcular macros de forma rápida e econômica:

```
Input: "200g arroz, 150g frango, 1 banana, 100g cadeira"
          │
          ▼
   ┌─────────────┐
   │ 1. SPLIT    │  Separa por  ,  +  e  com
   │             │  → ["200g arroz", "150g frango", "1 banana", "100g cadeira"]
   └──────┬──────┘
          ▼
   ┌─────────────┐
   │ 2. CACHE    │  Busca no ingredient_cache (1 query SQL)
   │   LOOKUP    │  → HIT: "200g arroz" ✅  MISS: "frango", "banana", "cadeira" ❌
   └──────┬──────┘
          ▼
   ┌─────────────┐
   │ 3. OPENAI   │  1 chamada por ingrediente
   │  PARALLEL   │  Todas assíncronas via Virtual Threads (Java 21)
   │             │  → 3 chamadas simultâneas (~1-2s total)
   └──────┬──────┘
          ▼
   ┌─────────────┐
   │ 4. VALIDATE │  GPT retorna is_valid_food = false para "cadeira"
   │             │  → Qualquer inválido = rejeita tudo (HTTP 400)
   └──────┬──────┘
          ▼
   ┌─────────────┐
   │ 5. CACHE    │  Salva os 3 novos no banco (inclusive os inválidos)
   │   SAVE      │  → Próxima vez = 0 chamadas à OpenAI
   └──────┬──────┘
          ▼
   ┌─────────────┐
   │ 6. CORRECT  │  "250ml arroz" → "250g arroz" (IA corrige unidades)
   │   UNITS     │  → Frontend recebe a correção para exibir
   └──────┬──────┘
          ▼
   Output: 400 { error: "\"100g cadeira\" não é um alimento válido." }
           ou
           200 { nutrition: { calories, protein, carbs, fat }, ingredients: [...] }
```

### Exemplos de resposta

**✅ Tudo válido:**
```json
{
  "nutrition": { "calories": "610 kcal", "protein": "35g", "carbs": "77g", "fat": "12g" },
  "ingredients": [
    { "name": "200g de arroz", "nutrition": { "calories": "260 kcal", "protein": "5g", "carbs": "57g", "fat": "0.5g" }, "cached": true },
    { "name": "150g de frango grelhado", "nutrition": { "calories": "350 kcal", "protein": "30g", "carbs": "0g", "fat": "11.5g" }, "cached": false }
  ],
  "corrections": null,
  "invalidItems": null
}
```

**🔄 Com correção de unidade:**
```json
{
  "nutrition": { "calories": "325 kcal", ... },
  "corrections": [
    { "original": "250ml de arroz", "corrected": "250g de arroz" }
  ]
}
```

**🚫 Item inválido (1):**
```json
// HTTP 400
{
  "error": "\"cadeira\" não é um alimento válido. Corrija e tente novamente.",
  "invalidItems": ["cadeira"]
}
```

**🚫 Itens inválidos (2+):**
```json
// HTTP 400
{
  "error": "Não foi possível calcular. Revise os ingredientes: \"cadeira\", \"mesa\".",
  "invalidItems": ["cadeira", "mesa"]
}
```

---

## 🗄 Banco de Dados

### Tabelas

```sql
meals                    -- Refeições do usuário
├── id UUID PK
├── user_id UUID
├── meal_type TEXT       -- breakfast | lunch | snack | dinner
├── foods TEXT
├── date TEXT            -- YYYY-MM-DD
├── time TEXT            -- HH:mm
├── calories TEXT
├── protein TEXT
├── carbs TEXT
├── fat TEXT
├── image_url TEXT
└── created_at TIMESTAMPTZ

favorite_meals           -- Pratos favoritos
├── id UUID PK
├── user_id UUID
├── foods TEXT
├── calories TEXT
├── protein TEXT
├── carbs TEXT
├── fat TEXT
├── image_url TEXT
└── created_at TIMESTAMPTZ

user_profiles            -- Perfis de usuário
├── id UUID PK
├── user_id UUID UNIQUE
├── username TEXT UNIQUE
├── profile_image_url TEXT
└── created_at TIMESTAMPTZ

ingredient_cache         -- Cache de nutrição por ingrediente
├── id UUID PK
├── ingredient_key TEXT UNIQUE  -- key normalizada (lowercase, trim)
├── original_input TEXT
├── corrected_input TEXT        -- após correção de unidade pela IA
├── calories TEXT
├── protein TEXT
├── carbs TEXT
├── fat TEXT
├── is_valid_food BOOLEAN       -- false = "cadeira", "mesa", etc.
└── created_at TIMESTAMPTZ
```

### Storage Buckets

| Bucket | Uso |
|---|---|
| `favorite-images` | Fotos de pratos favoritos + fotos de perfil |
| `meal-images` | Fotos de refeições |

---

## 🚀 Setup Local

### Pré-requisitos

- Java 21+
- Conta no [Supabase](https://supabase.com)
- API Key da [OpenAI](https://platform.openai.com)

### 1. Clone e configure

```bash
git clone https://github.com/seu-usuario/vidasync-bff.git
cd vidasync-bff/vidasync-bff
```

### 2. Crie o `.env.properties`

```properties
OPENAI_API_KEY=sk-proj-...
SUPABASE_URL=https://xxxxx.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIs...
```

### 3. Execute as migrations no Supabase

Copie o conteúdo de `supabase-migrations.sql` e execute no **SQL Editor** do Supabase Dashboard.

### 4. Crie os Storage Buckets

No Supabase Dashboard → Storage:
- Criar bucket `favorite-images` (público)
- Criar bucket `meal-images` (público)
- Adicionar policies de SELECT, INSERT, UPDATE, DELETE para `anon`

### 5. Rode

```bash
./gradlew bootRun
```

O servidor inicia em `http://localhost:8080`

### 6. Teste

```bash
# Health check
curl http://localhost:8080/health

# Calcular calorias
curl -X POST http://localhost:8080/nutrition/calories \
  -H "Content-Type: application/json" \
  -d '{"foods": "200g de arroz, 150g de frango grelhado"}'

# Criar conta
curl -X POST http://localhost:8080/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username": "joao123", "password": "minhasenha"}'
```

---

## 🚂 Deploy

### Railway

1. Conecte o repositório no [Railway](https://railway.app)
2. Configure as variáveis de ambiente:

| Variável | Valor |
|---|---|
| `OPENAI_API_KEY` | `sk-proj-...` |
| `SUPABASE_URL` | `https://xxxxx.supabase.co` |
| `SUPABASE_ANON_KEY` | `eyJhbG...` |

3. Railway detecta o `Dockerfile` e faz o build automaticamente

**Produção:** `https://vidasync-bff-production.up.railway.app`

---

## 📁 Estrutura do Projeto

```
src/main/kotlin/com/vidasync_bff/
├── client/
│   ├── SupabaseClient.kt          # Cliente REST para Supabase (CRUD genérico)
│   └── SupabaseStorageClient.kt   # Upload de imagens para Supabase Storage
├── config/
│   ├── OpenAIConfig.kt            # Bean do OpenAI client
│   ├── SupabaseConfig.kt          # Bean do RestClient configurado para Supabase
│   ├── RequestLoggingFilter.kt    # Log de request/response HTTP
│   └── ...
├── controller/
│   ├── AuthController.kt          # /auth (signup, login, profile)
│   ├── MealController.kt          # /meals (CRUD + summary + range + duplicate)
│   ├── FavoriteController.kt      # /favorites (CRUD)
│   ├── NutritionController.kt     # /nutrition/calories (IA)
│   └── HealthController.kt        # /health
├── dto/
│   ├── request/                    # DTOs de entrada
│   └── response/                   # DTOs de saída + Supabase row mappings
└── service/
    ├── NutritionService.kt         # 🧠 Motor de IA (cache + parallel + validação)
    ├── IngredientCacheService.kt   # Cache de ingredientes no Supabase
    ├── MealService.kt              # Lógica de refeições
    ├── FavoriteService.kt          # Lógica de favoritos
    └── AuthService.kt              # Autenticação + perfil
```

---

## 📄 Licença

Projeto privado — todos os direitos reservados.

---

<div align="center">
  <br/>
  <strong>Feito com 💚 em Kotlin</strong>
  <br/><br/>
  <img src="https://img.shields.io/badge/status-em_desenvolvimento-yellow" />
</div>
