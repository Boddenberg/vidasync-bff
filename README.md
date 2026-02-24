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

## ⚙️ Variáveis de Ambiente

Configure as seguintes variáveis no Railway (ou no seu `.env` local):

| Variável | Descrição |
|---|---|
| `OPENAI_API_KEY` | Chave da API da OpenAI |
| `SUPABASE_URL` | URL do projeto Supabase |
| `SUPABASE_ANON_KEY` | Chave anônima do Supabase |

---

## 📡 Rotas

### 🔹 Hello World

```
GET /hello
```

**Resposta:**
```
Hello, World!
```

---

### 🔹 Calcular Calorias com IA

Recebe uma descrição de alimentos em texto livre e retorna o cálculo de calorias processado pelo GPT.

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

**Response:**
```json
{
  "result": "- 2 ovos mexidos: ~180 kcal\n- 1 fatia de pão integral: ~70 kcal\n- 1 banana: ~90 kcal\n\nTotal: ~340 kcal"
}
```

---

## 🏃 Rodando localmente

```bash
./gradlew bootRun
```

A API estará disponível em: `http://localhost:8080`

---

## 📦 Build

```bash
./gradlew bootJar
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
