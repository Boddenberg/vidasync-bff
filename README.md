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
│   ├── SupabaseClient.kt          # CRUD via PostgREST
│   └── SupabaseStorageClient.kt    # Upload de imagens via Storage
├── config/
│   ├── OpenAIConfig.kt
│   └── SupabaseConfig.kt
├── controller/
│   ├── FavoriteController.kt
│   ├── HealthController.kt
│   ├── MealController.kt
│   └── NutritionController.kt
├── dto/
│   ├── request/
│   │   ├── CalorieRequest.kt
│   │   ├── CreateFavoriteRequest.kt
│   │   ├── CreateMealRequest.kt
│   │   └── UpdateMealRequest.kt
│   └── response/
│       ├── CalorieResponse.kt
│       ├── FavoriteResponse.kt
│       └── MealResponse.kt
└── service/
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
| `SUPABASE_STORAGE_BUCKET` | Nome do bucket para imagens | `favorite-images` |
| `PORT` | Porta do servidor | `8080` |

---

## 📡 Endpoints

### 🔹 Health Check

```
GET /health → { "status": "UP" }
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
    foods TEXT NOT NULL,
    calories TEXT,
    protein TEXT,
    carbs TEXT,
    fat TEXT,
    image_url TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE meals ENABLE ROW LEVEL SECURITY;
ALTER TABLE favorite_meals ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow all for anon" ON meals FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all for anon" ON favorite_meals FOR ALL USING (true) WITH CHECK (true);
```

### Supabase Storage

1. Criar bucket **`favorite-images`** (público)
2. Policies no bucket:
   - **INSERT** para `anon`
   - **SELECT** para `anon`

---

## 🔄 Fluxo esperado

1. Usuário digita o que comeu → app chama `POST /nutrition/calories`
2. App mostra resultado → usuário escolhe o tipo de refeição
3. App chama `POST /meals` com `foods` + `mealType` + `date` + `time` (opcional) + `nutrition` (opcional)
4. Timeline do dia: `GET /meals/summary?date=...` retorna refeições ordenadas por horário + totais
5. Calendário: `GET /meals/range?startDate=...&endDate=...` retorna refeições do período
6. Editar/apagar/duplicar são operações sobre o `id` do meal
7. Favoritar: `POST /favorites` com `foods` + `nutrition` + `image` (base64 opcional)

---

## 📋 Changelog

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
  vidasync-bff
```
