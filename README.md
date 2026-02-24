# 🥗 VidaSync BFF

Backend For Frontend (BFF) do VidaSync — API responsável por intermediar o front-end com serviços externos como OpenAI e Supabase.

---

## 🚀 Stack

- **Kotlin** + **Spring Boot 3.5**
- **Java 21**
- **Gradle**
- **OpenAI Java SDK**
- **Supabase** (via variáveis de ambiente)
- **Deploy:** Railway

---

## 📁 Estrutura do Projeto

```
com.vidasync_bff/
├── VidasyncBffApplication.kt          # Entry point
├── config/
│   └── OpenAIConfig.kt                # Bean do client OpenAI
├── controller/
│   ├── HealthController.kt            # GET /health
│   └── NutritionController.kt         # POST /nutrition/calories
├── dto/
│   ├── request/
│   │   └── CalorieRequest.kt
│   └── response/
│       └── CalorieResponse.kt
└── service/
    └── NutritionService.kt            # Lógica de negócio + OpenAI
```

---

## ⚙️ Variáveis de Ambiente

Configure no Railway ou no arquivo `.env.properties` local:

| Variável | Descrição |
|---|---|
| `OPENAI_API_KEY` | Chave da API da OpenAI |
| `SUPABASE_URL` | URL do projeto Supabase |
| `SUPABASE_ANON_KEY` | Chave anônima do Supabase |

---

## 📡 Rotas

### 🔹 Health Check

```
GET /health
```

**Resposta:**
```json
{ "status": "UP" }
```

---

### 🔹 Calcular Calorias com IA

Recebe uma descrição de alimentos em texto livre e retorna calorias + macronutrientes.

```
POST /nutrition/calories
Content-Type: application/json
```

**Request Body:**
```json
{
  "foods": "2 ovos mexidos, 1 fatia de pão integral, 1 banana"
}
```

**Response (sucesso):**
```json
{
  "nutrition": {
    "calories": "340 kcal",
    "protein": "18g",
    "carbs": "45g",
    "fat": "10g"
  },
  "error": null
}
```

**Response (erro):**
```json
{
  "nutrition": null,
  "error": "mensagem do erro"
}
```

---

## 🏃 Rodando localmente

1. Crie o arquivo `.env.properties` na raiz do projeto:
```properties
OPENAI_API_KEY=sua_chave
SUPABASE_URL=sua_url
SUPABASE_ANON_KEY=sua_chave_anon
```

2. Rode:
```bash
./gradlew bootRun
```

A API estará disponível em: `http://localhost:8080`

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
