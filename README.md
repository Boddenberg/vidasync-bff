# 🥗 VidaSync BFF

Backend For Frontend (BFF) do VidaSync — API responsável por intermediar o front-end com serviços externos como OpenAI e Supabase.

---

## 🚀 Stack

- **Kotlin** + **Spring Boot 3.5**
- **Java 21**
- **Gradle**
- **OpenAI Java SDK**
- **Supabase** (REST API via PostgREST + Storage)
- **Deploy:** Railway

---

## 📁 Estrutura do Projeto

```
com.vidasync_bff/
├── VidasyncBffApplication.kt
├── client/
│   ├── SupabaseClient.kt          # CRUD via PostgREST (com user token)
│   └── SupabaseStorageClient.kt    # Upload de imagens via Storage
├── config/
│   ├── JwtAuthFilter.kt           # Valida JWT do Supabase Auth
│   ├── OpenAIConfig.kt
│   ├── RequestLoggingFilter.kt    # Log de request/response HTTP
│   ├── SupabaseConfig.kt
│   └── UserContext.kt             # Extension functions p/ userId/userToken
├── controller/
│   ├── AuthController.kt
│   ├── FavoriteController.kt
│   ├── HealthController.kt
│   ├── MealController.kt
│   └── NutritionController.kt
├── dto/
│   ├── request/
│   │   ├── AuthRequest.kt
│   │   ├── CalorieRequest.kt
│   │   ├── CreateFavoriteRequest.kt
│   │   ├── CreateMealRequest.kt
│   │   └── UpdateMealRequest.kt
│   └── response/
│       ├── AuthResponse.kt
│       ├── CalorieResponse.kt
│       ├── FavoriteResponse.kt
│       └── MealResponse.kt
└── service/
    ├── AuthService.kt
    ├── FavoriteService.kt
    ├── MealService.kt
    └── NutritionService.kt
```

---

## ⚙️ Variáveis de Ambiente

| Variável | Descrição | Default |
|---|---|---|
| `OPENAI_API_KEY` | Chave da API da OpenAI | — |
| `SUPABASE_URL` | URL do projeto Supabase | — |
| `SUPABASE_ANON_KEY` | Chave anônima do Supabase | — |
| `SUPABASE_JWT_SECRET` | JWT Secret (Supabase → Settings → API) | — |
| `SUPABASE_STORAGE_BUCKET` | Nome do bucket para imagens | `favorite-images` |
| `PORT` | Porta do servidor | `8080` |

---

## 📡 Endpoints

### 🔹 Health Check

```
GET /health → { "status": "UP" }
```

---

### 🔐 Autenticação

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/auth/signup` | Criar conta (email + senha) |
| `POST` | `/auth/login` | Login (retorna token JWT) |

#### POST /auth/signup

```json
// Request
{ "email": "user@email.com", "password": "minhasenha123" }

// Response (201)
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "abc123...",
  "expiresIn": 3600,
  "user": { "id": "uuid", "email": "user@email.com" }
}
```

#### POST /auth/login

```json
// Request
{ "email": "user@email.com", "password": "minhasenha123" }

// Response (200)
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "abc123...",
  "expiresIn": 3600,
  "user": { "id": "uuid", "email": "user@email.com" }
}
```

> O `accessToken` retornado deve ser enviado em todas as requests protegidas:
> `Authorization: Bearer <accessToken>`

#### Endpoints públicos (sem token):
- `GET /health`
- `POST /nutrition/calories`
- `POST /auth/signup`
- `POST /auth/login`

#### Resposta quando falta/inválido:
```json
{ "error": "Token de autenticação não fornecido" }
{ "error": "Token inválido: ..." }
```

---

### 🔹 Calcular Calorias com IA

```
POST /nutrition/calories
```

```json
// Request
{ "foods": "2 ovos mexidos, 1 banana" }

// Response (200)
{
  "nutrition": { "calories": "270 kcal", "protein": "16g", "carbs": "28g", "fat": "12g" },
  "error": null
}
```

---

### 🍽️ Refeições

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/meals` | Criar refeição |
| `GET` | `/meals?date=YYYY-MM-DD` | Listar refeições do dia (ordenadas por horário) |
| `GET` | `/meals/summary?date=YYYY-MM-DD` | Resumo do dia (timeline + totais de macros) |
| `GET` | `/meals/range?startDate=...&endDate=...` | Buscar por período (para calendário) |
| `PUT` | `/meals/{id}` | Editar refeição (update parcial) |
| `DELETE` | `/meals/{id}` | Deletar refeição |
| `POST` | `/meals/{id}/duplicate` | Duplicar refeição |

#### POST /meals

```json
// Request
{
  "foods": "whey + iogurte",
  "mealType": "breakfast",
  "date": "2026-02-24",
  "time": "08:30",
  "nutrition": { "calories": "350 kcal", "protein": "30g", "carbs": "20g", "fat": "12g" }
}

// Response (200)
{
  "meal": {
    "id": "uuid",
    "foods": "whey + iogurte",
    "mealType": "breakfast",
    "date": "2026-02-24",
    "time": "08:30",
    "nutrition": { "calories": "350 kcal", "protein": "30g", "carbs": "20g", "fat": "12g" },
    "createdAt": "2026-02-24T08:30:00Z"
  }
}
```

| Campo | Obrigatório | Observação |
|---|---|---|
| `foods` | ✅ | Texto livre |
| `mealType` | ✅ | `breakfast`, `lunch`, `dinner`, `snack`, `supper` |
| `date` | ✅ | Formato `YYYY-MM-DD` |
| `time` | ❌ | Formato `HH:mm`. Se omitido, usa horário atual |
| `nutrition` | ❌ | Se omitido, a IA calcula automaticamente |

#### GET /meals/summary?date=2026-02-24

```json
// Response (200)
{
  "date": "2026-02-24",
  "totalMeals": 3,
  "meals": [
    { "id": "...", "foods": "...", "mealType": "breakfast", "time": "07:00", "nutrition": { ... } },
    { "id": "...", "foods": "...", "mealType": "lunch", "time": "12:30", "nutrition": { ... } },
    { "id": "...", "foods": "...", "mealType": "snack", "time": "16:00", "nutrition": { ... } }
  ],
  "totals": {
    "calories": "1000 kcal",
    "protein": "78g",
    "carbs": "115g",
    "fat": "20g"
  }
}
```

#### GET /meals/range?startDate=2026-02-01&endDate=2026-02-28

```json
// Response (200)
{ "meals": [ { "date": "2026-02-01", "time": "08:00", ... }, ... ] }
```

#### PUT /meals/{id}

Todos os campos são **opcionais** (update parcial):

```json
// Request — só corrigir horário
{ "time": "14:00" }

// Request — mover para outro dia
{ "date": "2026-02-23", "time": "12:00" }

// Request — corrigir tudo
{
  "foods": "arroz integral + frango",
  "mealType": "lunch",
  "date": "2026-02-23",
  "time": "12:30",
  "nutrition": { "calories": "500 kcal", "protein": "35g", "carbs": "55g", "fat": "10g" }
}
```

#### DELETE /meals/{id}

```json
// Response (200)
{ "success": true }
```

#### POST /meals/{id}/duplicate

```json
// Response (200)
{ "meal": { "id": "novo-uuid", ... } }
```

---

### ⭐ Favoritos

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/favorites` | Criar favorito (com imagem opcional em base64) |
| `GET` | `/favorites` | Listar favoritos |
| `DELETE` | `/favorites/{id}` | Remover favorito |

#### POST /favorites

```json
// Request
{
  "foods": "arroz, feijão, bife",
  "nutrition": { "calories": "450 kcal", "protein": "30g", "carbs": "50g", "fat": "12g" },
  "image": "data:image/jpeg;base64,/9j/4AAQSkZJRg..."
}

// Response (201)
{
  "favorite": {
    "id": "uuid",
    "foods": "arroz, feijão, bife",
    "nutrition": { "calories": "450 kcal", "protein": "30g", "carbs": "50g", "fat": "12g" },
    "imageUrl": "https://xxx.supabase.co/storage/v1/object/public/favorite-images/fav_uuid.jpg"
  }
}
```

| Campo | Obrigatório | Observação |
|---|---|---|
| `foods` | ✅ | Texto livre |
| `nutrition` | ❌ | Macros do alimento |
| `image` | ❌ | Data URI base64 (`data:image/jpeg;base64,...`) ou raw base64. Upload vai para Supabase Storage |

#### GET /favorites

```json
// Response (200)
{
  "favorites": [
    {
      "id": "uuid",
      "foods": "arroz, feijão, bife",
      "nutrition": { ... },
      "imageUrl": "https://xxx.supabase.co/storage/v1/object/public/favorite-images/fav_uuid.jpg"
    }
  ]
}
```

#### DELETE /favorites/{id}

```json
// Response (200)
{ "success": true }
```

---

### Tipos de refeição (mealType)

| Valor | Label |
|---|---|
| `breakfast` | Café da manhã |
| `lunch` | Almoço |
| `snack` | Lanche |
| `dinner` | Jantar |
| `supper` | Ceia |

---

## 🗄️ Tabelas Supabase (SQL)

```sql
CREATE TABLE meals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id),
    meal_type TEXT NOT NULL CHECK (meal_type IN ('breakfast','lunch','snack','dinner','supper')),
    foods TEXT NOT NULL,
    date TEXT NOT NULL,
    time TEXT,
    calories TEXT,
    protein TEXT,
    carbs TEXT,
    fat TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE favorite_meals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id),
    foods TEXT NOT NULL,
    calories TEXT,
    protein TEXT,
    carbs TEXT,
    fat TEXT,
    image_url TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_meals_user_id ON meals(user_id);
CREATE INDEX idx_favorite_meals_user_id ON favorite_meals(user_id);

ALTER TABLE meals ENABLE ROW LEVEL SECURITY;
ALTER TABLE favorite_meals ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can select own meals" ON meals FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own meals" ON meals FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own meals" ON meals FOR UPDATE USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own meals" ON meals FOR DELETE USING (auth.uid() = user_id);

CREATE POLICY "Users can select own favorites" ON favorite_meals FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own favorites" ON favorite_meals FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own favorites" ON favorite_meals FOR UPDATE USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete own favorites" ON favorite_meals FOR DELETE USING (auth.uid() = user_id);
```

### Supabase Storage

1. Criar bucket **`favorite-images`** (público)
2. Policies no bucket:
   - **INSERT** para `anon`
   - **SELECT** para `anon`

---

## 🔄 Fluxo esperado

1. Usuário cria conta → `POST /auth/signup`
2. Usuário faz login → `POST /auth/login` → recebe `accessToken`
3. Todas as requests seguintes enviam `Authorization: Bearer <accessToken>`
4. Usuário digita o que comeu → app chama `POST /nutrition/calories`
5. App mostra resultado → usuário escolhe o tipo de refeição
6. App chama `POST /meals` com `foods` + `mealType` + `date` + `time` + `nutrition`
7. Timeline do dia: `GET /meals/summary?date=...`
8. Calendário: `GET /meals/range?startDate=...&endDate=...`
9. Editar/apagar/duplicar são operações sobre o `id` do meal
10. Favoritar: `POST /favorites` com `foods` + `nutrition` + `image` (base64 opcional)

---

## 📋 Changelog

### v0.4.0 — Autenticação (2026-02-24)
- Novos endpoints `POST /auth/signup` e `POST /auth/login` (email + senha via Supabase Auth)
- `JwtAuthFilter` valida JWT em todas as rotas protegidas
- Token do usuário forwarded ao Supabase PostgREST → RLS ativo no banco
- `user_id` incluído em todos os INSERTs e filtros de queries
- Cada usuário só vê/edita/deleta seus próprios dados
- Endpoints públicos: `/health`, `/nutrition/calories`, `/auth/*`
- Nova coluna `user_id UUID` em `meals` e `favorite_meals`
- RLS policies por usuário (SELECT/INSERT/UPDATE/DELETE)
- Dependência: `com.auth0:java-jwt:4.4.0`
- Nova variável: `SUPABASE_JWT_SECRET`

### v0.3.0 — Imagens nos Favoritos (2026-02-24)
- `POST /favorites` aceita campo `image` (base64) — upload automático para Supabase Storage
- Resposta dos favoritos agora inclui `imageUrl` (URL pública da imagem)
- Novo client `SupabaseStorageClient` para upload de imagens
- `POST /favorites` retorna **201 Created**
- Limite de request body: 10MB
- Nova coluna `image_url` na tabela `favorite_meals`
- Novo bucket `favorite-images` no Supabase Storage

### v0.2.0 — Timeline, Horários e Resumo do Dia (2026-02-24)
- Novo campo `time` (HH:mm) em refeições — opcional no input, default = horário atual
- `PUT /meals/{id}` agora aceita `date` e `time` para correção retroativa
- Novo endpoint `GET /meals/summary?date=` — retorna timeline + soma de macros do dia
- Novo endpoint `GET /meals/range?startDate=&endDate=` — busca por período para calendário
- Refeições ordenadas por `time` (ASC) em vez de `created_at`
- Nova coluna `time` na tabela `meals`

### v0.1.0 — Versão inicial (2026-02-23)
- CRUD de refeições (`/meals`)
- CRUD de favoritos (`/favorites`)
- Cálculo de calorias via OpenAI (`/nutrition/calories`)
- Integração com Supabase via PostgREST
- Deploy no Railway

---

## 🏃 Rodando localmente

1. Crie `.env.properties` na raiz do módulo:
```properties
OPENAI_API_KEY=sua_chave
SUPABASE_URL=sua_url
SUPABASE_ANON_KEY=sua_chave_anon
SUPABASE_JWT_SECRET=seu_jwt_secret
```

2. Rode:
```bash
./gradlew bootRun
```

---

## 🐳 Docker

```bash
docker build -t vidasync-bff .
docker run -p 8080:8080 \
  -e OPENAI_API_KEY=sua_chave \
  -e SUPABASE_URL=sua_url \
  -e SUPABASE_ANON_KEY=sua_chave_anon \
  -e SUPABASE_JWT_SECRET=seu_jwt_secret \
  vidasync-bff
```
