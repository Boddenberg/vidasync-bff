# 🍽️ VidaSync BFF — Guia de Integração Frontend

## Base URL

```
http://localhost:8080
```

## Contrato de dominio (obrigatorio)

- O app deve chamar somente rotas do BFF (`/nutrition`, `/meals`, `/favorites`, `/auth`, `/chat`).
- O app nao deve chamar a camada de agentes diretamente.
- O BFF e o unico ponto de orquestracao de IA para o app.

### Revisao no app (`precisa_revisao`)

Quando uma resposta de calorias vier com `precisa_revisao=true`, o app deve abrir uma tela de revisao antes de salvar refeicao.

Campos para usar nessa tela:

- `precisa_revisao` (top-level): habilita fluxo de revisao.
- `warnings`: mensagens para contexto do usuario.
- `ingredients[].precisa_revisao`: destaca itens individuais.
- `ingredients[].warnings`: explica o motivo da revisao por item.
- `trace_id`: usar para suporte/troubleshooting.

## Chat conversacional com o agente

Use esta rota quando a tela precisar conversar com o agente de IA sem chamar a camada de IA diretamente.

```
POST /chat
Content-Type: application/json
```

### Body

```json
{
  "prompt": "preciso beber mais agua?",
  "conversationId": "opcional-para-continuar-o-mesmo-chat"
}
```

| Campo | Obrigatorio | Observacao |
| --- | --- | --- |
| `prompt` | ✅ | Mensagem atual do usuario. |
| `conversationId` | ❌ | Reenvie o valor retornado pela API para manter contexto entre turnos. |

### Resposta (200)

```json
{
  "response": "Sim. Uma meta pratica e entre 2 e 3 litros por dia.",
  "model": "gpt-4o-mini",
  "conversationId": "0105e71ede0e4a4cb1b48557ed6ff89c",
  "intent": "conversa_geral",
  "confidence": 0.55,
  "needsReview": false,
  "warnings": [],
  "memory": {
    "totalTurns": 4,
    "shortTermTurns": 4,
    "summarizedTurns": 0,
    "hasSummary": false,
    "updatedAt": "2026-03-26T05:25:03.465526Z"
  },
  "disclaimer": "Informacao geral. Para orientacao personalizada, consulte um nutricionista.",
  "traceId": "e5bc8b4d1c7f4b8e8d2ec5edaf8f14b9"
}
```

### Como o front deve chamar

- No primeiro turno, envie apenas `prompt`.
- Guarde `conversationId` no estado da tela.
- Nos turnos seguintes, envie `prompt` + `conversationId`.
- Para reiniciar a conversa, limpe o `conversationId`.
- Se o app ja envia `X-User-Id` globalmente, pode continuar enviando. Essa rota aceita o header, mas nao exige.
- Para troubleshooting, o front pode ler o header `X-Request-ID` da resposta ou usar `traceId` do body.

### Exemplo fetch

```javascript
const res = await fetch(`${BASE_URL}/chat`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-User-Id': userId
  },
  body: JSON.stringify({
    prompt: inputValue,
    conversationId: currentConversationId || undefined
  })
});

const data = await res.json();

setMessages((prev) => [
  ...prev,
  { role: 'assistant', content: data.response }
]);
setCurrentConversationId(data.conversationId);
```

### Exemplo curl

```bash
curl --request POST \
  --url http://localhost:8080/chat \
  --header 'Content-Type: application/json' \
  --data '{
  "prompt": "preciso beber mais água?"
}'
```

### Exemplo curl continuando a mesma conversa

```bash
curl --request POST \
  --url http://localhost:8080/chat \
  --header 'Content-Type: application/json' \
  --data '{
  "prompt": "e quanto por dia?",
  "conversationId": "0105e71ede0e4a4cb1b48557ed6ff89c"
}'
```

---

## 📌 Alteração necessária no Supabase

Você precisa adicionar a coluna `time` na tabela `meals`. Execute esse SQL no **SQL Editor** do Supabase:

```sql
ALTER TABLE meals ADD COLUMN time text;
```

A tabela `meals` completa deve ficar assim:

| Coluna      | Tipo        | Observação                          |
|-------------|-------------|-------------------------------------|
| id          | uuid        | PK, default `gen_random_uuid()`     |
| meal_type   | text        | breakfast, lunch, dinner, snack     |
| foods       | text        | Descrição dos alimentos             |
| date        | text        | Formato `YYYY-MM-DD`                |
| time        | text        | Formato `HH:mm` (ex: `08:30`)      |
| calories    | text        | Ex: `300 kcal`                      |
| protein     | text        | Ex: `24g`                           |
| carbs       | text        | Ex: `30g`                           |
| fat         | text        | Ex: `12g`                           |
| created_at  | timestamptz | default `now()`                     |

---

## 🔵 1. Criar Refeição

```
POST /meals
Content-Type: application/json
```

### Body

```json
{
  "foods": "3 ovos mexidos + café com leite",
  "mealType": "breakfast",
  "date": "2026-02-24",
  "time": "08:30",
  "nutrition": {
    "calories": "350 kcal",
    "protein": "24g",
    "carbs": "5g",
    "fat": "21g"
  }
}
```

| Campo       | Obrigatório | Observação                                         |
|-------------|-------------|-----------------------------------------------------|
| foods       | ✅          | Texto livre                                         |
| mealType    | ✅          | `breakfast`, `lunch`, `dinner`, `snack`             |
| date        | ✅          | Formato `YYYY-MM-DD`                                |
| time        | ❌          | Formato `HH:mm`. Se omitido, usa o horário atual   |
| nutrition   | ❌          | Se omitido, a API calcula via IA (BFF -> AI Gateway) automaticamente |

### Resposta (200)

```json
{
  "meal": {
    "id": "a1b2c3d4-...",
    "foods": "3 ovos mexidos + café com leite",
    "mealType": "breakfast",
    "date": "2026-02-24",
    "time": "08:30",
    "nutrition": {
      "calories": "350 kcal",
      "protein": "24g",
      "carbs": "5g",
      "fat": "21g"
    },
    "createdAt": "2026-02-24T11:30:00.000Z"
  }
}
```

### Exemplo curl

```bash
curl --request POST \
  --url http://localhost:8080/meals \
  --header 'Content-Type: application/json' \
  --data '{
  "foods": "3 ovos mexidos + café com leite",
  "mealType": "breakfast",
  "date": "2026-02-24",
  "time": "08:30",
  "nutrition": {
    "calories": "350 kcal",
    "protein": "24g",
    "carbs": "5g",
    "fat": "21g"
  }
}'
```

**Sem nutrition (API calcula via IA (BFF -> AI Gateway)):**

```bash
curl --request POST \
  --url http://localhost:8080/meals \
  --header 'Content-Type: application/json' \
  --data '{
  "foods": "3 ovos mexidos + café com leite",
  "mealType": "breakfast",
  "date": "2026-02-24",
  "time": "08:30"
}'
```

**Sem time (usa horário atual):**

```bash
curl --request POST \
  --url http://localhost:8080/meals \
  --header 'Content-Type: application/json' \
  --data '{
  "foods": "whey + banana",
  "mealType": "snack",
  "date": "2026-02-24"
}'
```

### Exemplo fetch

```javascript
const res = await fetch(`${BASE_URL}/meals`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    foods: '3 ovos mexidos + café com leite',
    mealType: 'breakfast',
    date: '2026-02-24',
    time: '08:30'
  })
});
const data = await res.json();
// data.meal → refeição criada
```

---

## 🟢 2. Listar Refeições do Dia (para a timeline)

```
GET /meals?date=2026-02-24
```

### Resposta (200)

```json
{
  "meals": [
    {
      "id": "...",
      "foods": "café + torrada",
      "mealType": "breakfast",
      "date": "2026-02-24",
      "time": "07:00",
      "nutrition": { "calories": "200 kcal", "protein": "8g", "carbs": "30g", "fat": "5g" },
      "createdAt": "..."
    },
    {
      "id": "...",
      "foods": "arroz + frango grelhado",
      "mealType": "lunch",
      "date": "2026-02-24",
      "time": "12:30",
      "nutrition": { "calories": "550 kcal", "protein": "40g", "carbs": "60g", "fat": "12g" },
      "createdAt": "..."
    }
  ]
}
```

> ⏱️ As refeições já vêm **ordenadas por horário** (`time` ASC).

### Exemplo curl

```bash
curl --request GET \
  --url 'http://localhost:8080/meals?date=2026-02-24'
```

### Exemplo fetch

```javascript
const res = await fetch(`${BASE_URL}/meals?date=2026-02-24`);
const data = await res.json();
// data.meals → array ordenado por horário
```

---

## 🟣 3. Resumo do Dia (Timeline + Totais)

**Endpoint ideal para montar a tela de panorama do dia.**

```
GET /meals/summary?date=2026-02-24
```

### Resposta (200)

```json
{
  "date": "2026-02-24",
  "totalMeals": 3,
  "meals": [
    {
      "id": "...",
      "foods": "café + torrada",
      "mealType": "breakfast",
      "date": "2026-02-24",
      "time": "07:00",
      "nutrition": { "calories": "200 kcal", "protein": "8g", "carbs": "30g", "fat": "5g" }
    },
    {
      "id": "...",
      "foods": "arroz + frango",
      "mealType": "lunch",
      "date": "2026-02-24",
      "time": "12:30",
      "nutrition": { "calories": "550 kcal", "protein": "40g", "carbs": "60g", "fat": "12g" }
    },
    {
      "id": "...",
      "foods": "whey + banana",
      "mealType": "snack",
      "date": "2026-02-24",
      "time": "16:00",
      "nutrition": { "calories": "250 kcal", "protein": "30g", "carbs": "25g", "fat": "3g" }
    }
  ],
  "totals": {
    "calories": "1000 kcal",
    "protein": "78g",
    "carbs": "115g",
    "fat": "20g"
  }
}
```

### Exemplo curl

```bash
curl --request GET \
  --url 'http://localhost:8080/meals/summary?date=2026-02-24'
```

### Exemplo fetch

```javascript
const res = await fetch(`${BASE_URL}/meals/summary?date=2026-02-24`);
const data = await res.json();

// data.meals     → array das refeições (ordenadas por horário)
// data.totalMeals → quantidade de refeições
// data.totals    → soma dos macros do dia
//   data.totals.calories → "1000 kcal"
//   data.totals.protein  → "78g"
//   data.totals.carbs    → "115g"
//   data.totals.fat      → "20g"
```

---

## 🗓️ 4. Buscar por Período (para Calendário)

```
GET /meals/range?startDate=2026-02-01&endDate=2026-02-28
```

Retorna todas as refeições do período, ordenadas por data e horário.

### Resposta (200)

```json
{
  "meals": [
    { "date": "2026-02-01", "time": "08:00", "mealType": "breakfast", ... },
    { "date": "2026-02-01", "time": "12:30", "mealType": "lunch", ... },
    { "date": "2026-02-03", "time": "07:45", "mealType": "breakfast", ... }
  ]
}
```

> 💡 **Dica para o front**: agrupe por `date` para saber quais dias têm refeições (para marcar no calendário).

### Exemplo curl

```bash
curl --request GET \
  --url 'http://localhost:8080/meals/range?startDate=2026-02-01&endDate=2026-02-28'
```

### Exemplo fetch

```javascript
const res = await fetch(`${BASE_URL}/meals/range?startDate=2026-02-01&endDate=2026-02-28`);
const data = await res.json();

// Agrupar por data para o calendário
const byDate = {};
data.meals.forEach(meal => {
  if (!byDate[meal.date]) byDate[meal.date] = [];
  byDate[meal.date].push(meal);
});
// byDate['2026-02-01'] → [{...}, {...}]
// byDate['2026-02-03'] → [{...}]
```

---

## 🟡 5. Atualizar Refeição (Editar / Corrigir)

```
PUT /meals/{id}
Content-Type: application/json
```

**Todos os campos são opcionais** — envie só o que quer alterar.

### Body

```json
{
  "foods": "3 ovos mexidos (corrigido)",
  "mealType": "lunch",
  "date": "2026-02-23",
  "time": "13:00",
  "nutrition": {
    "calories": "300 kcal",
    "protein": "24g",
    "carbs": "0g",
    "fat": "21g"
  }
}
```

| Campo     | Observação                                |
|-----------|-------------------------------------------|
| foods     | Corrigir a descrição                      |
| mealType  | Mudar o tipo da refeição                  |
| date      | Mover para outro dia                      |
| time      | Corrigir o horário                        |
| nutrition | Corrigir os macros manualmente            |

### Exemplos de uso

**Só corrigir horário:**
```json
{ "time": "14:00" }
```

**Mover para outro dia:**
```json
{ "date": "2026-02-23", "time": "12:00" }
```

**Corrigir tudo:**
```json
{
  "foods": "arroz integral + frango",
  "mealType": "lunch",
  "date": "2026-02-23",
  "time": "12:30",
  "nutrition": { "calories": "500 kcal", "protein": "35g", "carbs": "55g", "fat": "10g" }
}
```

### Exemplo curl

**Só corrigir horário:**

```bash
curl --request PUT \
  --url http://localhost:8080/meals/SEU_MEAL_ID_AQUI \
  --header 'Content-Type: application/json' \
  --data '{ "time": "14:00" }'
```

**Mover para outro dia:**

```bash
curl --request PUT \
  --url http://localhost:8080/meals/SEU_MEAL_ID_AQUI \
  --header 'Content-Type: application/json' \
  --data '{ "date": "2026-02-23", "time": "12:00" }'
```

**Corrigir tudo:**

```bash
curl --request PUT \
  --url http://localhost:8080/meals/SEU_MEAL_ID_AQUI \
  --header 'Content-Type: application/json' \
  --data '{
  "foods": "arroz integral + frango",
  "mealType": "lunch",
  "date": "2026-02-23",
  "time": "12:30",
  "nutrition": {
    "calories": "500 kcal",
    "protein": "35g",
    "carbs": "55g",
    "fat": "10g"
  }
}'
```

### Exemplo fetch

```javascript
const mealId = 'a1b2c3d4-...'; // id real da refeição
const res = await fetch(`${BASE_URL}/meals/${mealId}`, {
  method: 'PUT',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ time: '14:00' }) // só corrigir o horário
});
const data = await res.json();
// data.meal → refeição atualizada
```

---

## 🔴 6. Deletar Refeição

```
DELETE /meals/{id}
```

### Resposta (200)

```json
{ "success": true }
```

### Exemplo curl

```bash
curl --request DELETE \
  --url http://localhost:8080/meals/SEU_MEAL_ID_AQUI
```

### Exemplo fetch

```javascript
await fetch(`${BASE_URL}/meals/${mealId}`, { method: 'DELETE' });
```

---

## 🔁 7. Duplicar Refeição

```
POST /meals/{id}/duplicate
```

Cria uma cópia idêntica da refeição (útil para refeições que se repetem).

### Resposta (200)

```json
{
  "meal": {
    "id": "novo-uuid-...",
    "foods": "...",
    "mealType": "...",
    "date": "...",
    "time": "...",
    "nutrition": { ... },
    "createdAt": "..."
  }
}
```

### Exemplo curl

```bash
curl --request POST \
  --url http://localhost:8080/meals/SEU_MEAL_ID_AQUI/duplicate
```

### Exemplo fetch

```javascript
const res = await fetch(`${BASE_URL}/meals/${mealId}/duplicate`, { method: 'POST' });
const data = await res.json();
// data.meal → nova refeição duplicada
```

---

## 🧭 Fluxo Sugerido para o Frontend

### Tela Timeline (panorama do dia)

1. Usuário seleciona data no calendário
2. Chama `GET /meals/summary?date=2026-02-24`
3. Renderiza a timeline com `meals` ordenadas por `time`
4. Mostra os totais do dia no topo/fundo

### Tela Calendário

1. Ao abrir o mês, chama `GET /meals/range?startDate=2026-02-01&endDate=2026-02-28`
2. Agrupa por `date` e marca os dias que têm refeições
3. Ao clicar num dia, chama `GET /meals/summary?date=...`

### Fluxo de criação

1. Usuário digita o que comeu → chama `POST /nutrition/calories` (retorna macros)
2. Mostra macros → usuário escolhe tipo, confirma
3. Chama `POST /meals` com `foods`, `mealType`, `date`, `time` (opcional), `nutrition`

### Fluxo de edição

1. Na timeline, usuário toca em "editar" numa refeição
2. Abre formulário preenchido com os dados atuais
3. Ao salvar, chama `PUT /meals/{id}` com os campos alterados
4. Recarrega `GET /meals/summary?date=...` para atualizar a tela

---

## ⚠️ Erros

Todos os endpoints retornam este formato em caso de erro:

```json
{ "error": "mensagem de erro" }
```

HTTP Status: `500` para erros internos.

---

## `POST /nutrition/calories` com revisao

### Resposta de sucesso com revisao obrigatoria

```json
{
  "nutrition": {
    "calories": "390 kcal",
    "protein": "22g",
    "carbs": "30g",
    "fat": "18g"
  },
  "ingredients": [
    {
      "name": "1 porcao de frango",
      "nutrition": { "calories": "220 kcal", "protein": "20g", "carbs": "0g", "fat": "14g" },
      "cached": false,
      "precisa_revisao": true,
      "warnings": ["Quantidade aproximada detectada."],
      "trace_id": "3f8f7f1b9c0b4b4c8a0e0d2f3f44f0a1"
    }
  ],
  "precisa_revisao": true,
  "warnings": ["Quantidade aproximada detectada."],
  "trace_id": "3f8f7f1b9c0b4b4c8a0e0d2f3f44f0a1"
}
```

### Comportamento esperado do app

1. Chamar `POST /nutrition/calories`.
2. Se `precisa_revisao=true`, abrir tela de revisao com os itens.
3. Usuario confirma ou edita os itens.
4. Somente depois enviar `POST /meals`.

---

## Agua - meta diaria e ingestao

### Header obrigatorio

Todas as chamadas de agua precisam enviar:

```http
X-User-Id: <uuid do usuario>
```

---

### 1. Salvar meta e/ou somar/subtrair agua

```
POST /water
Content-Type: application/json
X-User-Id: <user-id>
```

Body (todos opcionais, mas envie pelo menos um campo):

```json
{
  "date": "2026-03-11",
  "goalMl": 2500,
  "deltaMl": 200
}
```

Regras:
- `goalMl`: define ou atualiza a meta do dia.
- `deltaMl`: soma (positivo) ou remove (negativo) da agua ingerida.
- `date`: opcional, formato `YYYY-MM-DD`. Se nao enviar, usa o dia atual do servidor.
- O total nunca fica negativo. Se o usuario tentar remover mais agua do que existe no dia, o backend limita o saldo em `0`.
- Cada `deltaMl` gera um item no historico (`events`) para aquele dia.
- Se o dia ainda nao tiver meta propria, o backend reaproveita a ultima meta configurada em uma data anterior.

Resposta (200):

```json
{
  "water": {
    "id": "uuid",
    "date": "2026-03-11",
    "goalMl": 2500,
    "consumedMl": 600,
    "remainingMl": 1900,
    "progressPercent": 24,
    "goalReached": false,
    "goalInherited": false,
    "createdAt": "2026-03-11T09:00:00Z",
    "updatedAt": "2026-03-11T10:15:00Z",
    "events": [
      {
        "id": "event-1",
        "date": "2026-03-11",
        "deltaMl": 200,
        "action": "ADD",
        "runningConsumedMl": 200
      },
      {
        "id": "event-2",
        "date": "2026-03-11",
        "deltaMl": 400,
        "action": "ADD",
        "runningConsumedMl": 600
      }
    ]
  }
}
```

Exemplos para botoes:

```javascript
// Definir meta (ex: quando o usuario salva no input)
await fetch(`${BASE_URL}/water`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-User-Id': userId
  },
  body: JSON.stringify({ date: selectedDate, goalMl: 2500 })
});

// Botao +200ml
await fetch(`${BASE_URL}/water`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-User-Id': userId
  },
  body: JSON.stringify({ date: selectedDate, deltaMl: 200 })
});

// Botao -15ml
await fetch(`${BASE_URL}/water`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-User-Id': userId
  },
  body: JSON.stringify({ date: selectedDate, deltaMl: -15 })
});
```

---

### 2. Buscar panorama do dia

```
GET /water?date=2026-03-11
X-User-Id: <user-id>
```

Resposta quando existe registro no dia:

```json
{
  "water": {
    "id": "uuid",
    "date": "2026-03-11",
    "goalMl": 2500,
    "consumedMl": 600,
    "remainingMl": 1900,
    "progressPercent": 24,
    "goalReached": false,
    "goalInherited": false,
    "createdAt": "2026-03-11T09:00:00Z",
    "updatedAt": "2026-03-11T10:15:00Z",
    "events": [
      {
        "id": "event-1",
        "date": "2026-03-11",
        "deltaMl": 200,
        "action": "ADD",
        "runningConsumedMl": 200
      },
      {
        "id": "event-2",
        "date": "2026-03-11",
        "deltaMl": 400,
        "action": "ADD",
        "runningConsumedMl": 600
      }
    ]
  }
}
```

Resposta quando ainda nao existe linha no dia, mas ja existe uma meta anterior:

```json
{
  "water": {
    "id": null,
    "date": "2026-03-12",
    "goalMl": 2500,
    "consumedMl": 0,
    "remainingMl": 2500,
    "progressPercent": 0,
    "goalReached": false,
    "goalInherited": true,
    "createdAt": null,
    "updatedAt": null,
    "events": []
  }
}
```

Resposta quando o usuario ainda nao tem nenhuma meta nem nenhum consumo de agua:

```json
{
  "water": null
}
```

Exemplo fetch:

```javascript
const res = await fetch(`${BASE_URL}/water?date=${selectedDate}`, {
  headers: { 'X-User-Id': userId }
});

const data = await res.json();

if (!data.water) {
  // usuario ainda nao configurou nenhuma meta de agua
} else {
  // renderizar panorama do dia com data.water.goalMl, data.water.consumedMl e data.water.events
}
```

---

### 3. Buscar historico de agua

Por padrao, retorna do primeiro dia relevante ate `endDate` (ou hoje, se `endDate` nao for enviado).

```
GET /water/history?startDate=2026-03-01&endDate=2026-03-12
X-User-Id: <user-id>
```

Resposta (200):

```json
{
  "waterHistory": {
    "startDate": "2026-03-01",
    "endDate": "2026-03-12",
    "days": [
      {
        "id": "uuid-dia-1",
        "date": "2026-03-11",
        "goalMl": 3000,
        "consumedMl": 2500,
        "remainingMl": 500,
        "progressPercent": 83,
        "goalReached": false,
        "goalInherited": false,
        "createdAt": "2026-03-11T09:00:00Z",
        "updatedAt": "2026-03-11T18:30:00Z",
        "events": [
          {
            "id": "event-1",
            "date": "2026-03-11",
            "deltaMl": 500,
            "action": "ADD",
            "runningConsumedMl": 500
          },
          {
            "id": "event-2",
            "date": "2026-03-11",
            "deltaMl": 500,
            "action": "ADD",
            "runningConsumedMl": 1000
          },
          {
            "id": "event-3",
            "date": "2026-03-11",
            "deltaMl": -500,
            "action": "REMOVE",
            "runningConsumedMl": 500
          }
        ]
      },
      {
        "id": null,
        "date": "2026-03-12",
        "goalMl": 3000,
        "consumedMl": 0,
        "remainingMl": 3000,
        "progressPercent": 0,
        "goalReached": false,
        "goalInherited": true,
        "createdAt": null,
        "updatedAt": null,
        "events": []
      }
    ]
  }
}
```

Observacoes:
- `days` vem em ordem crescente de data.
- Cada item de `events` representa uma movimentacao real do dia.
- `action` pode vir como `ADD`, `REMOVE` ou `ADJUSTMENT`.
- `ADJUSTMENT` pode aparecer em dias antigos, quando existia apenas o saldo consolidado e o backend precisou representar esse legado como um ajuste unico.

Exemplo fetch:

```javascript
const res = await fetch(
  `${BASE_URL}/water/history?startDate=2026-03-01&endDate=2026-03-12`,
  {
    headers: { 'X-User-Id': userId }
  }
);

const data = await res.json();
const days = data.waterHistory.days;
```

---

### Fluxo recomendado da tela de agua

1. Abrir tela e chamar `GET /water?date=<hoje>`.
2. Se `water == null`, mostrar input de meta e botao "Comecar".
3. Se `water.goalInherited == true`, usar a ultima meta configurada normalmente no panorama do dia.
4. Ao salvar meta, chamar `POST /water` com `goalMl`.
5. Botoes rapidos chamam `POST /water` com `deltaMl` positivo/negativo.
6. Atualizar a UI usando a resposta do proprio `POST`.
7. Para montar calendario, tabela ou relatorio, chamar `GET /water/history`.

Observacao:
- O panorama do dia continua em `GET /water`.
- O historico detalhado por dias e movimentos fica em `GET /water/history`.
- A meta diaria fica congelada por data. Se a pessoa mudar a meta amanha, os dias anteriores nao sao alterados.

---

## Metas de calorias e macros (calorias, proteina, carbo e gordura)

### Header obrigatorio

Todas as chamadas precisam enviar:

```http
X-User-Id: <uuid do usuario>
```

---

### 1. Salvar/atualizar metas do dia

```
POST /nutrition-goals
Content-Type: application/json
X-User-Id: <user-id>
```

Body (envie pelo menos um campo de meta):

```json
{
  "date": "2026-03-11",
  "caloriesGoal": 2200,
  "proteinGoal": 160
}
```

Regras:
- `date` e opcional, formato `YYYY-MM-DD`.
- Se nao enviar `date`, usa o dia atual do servidor.
- Pode enviar apenas as metas que quer alterar.
- Se um campo nao for enviado, o backend reaproveita a ultima meta efetiva daquele campo ate essa data.
- Se for a primeira configuracao da pessoa, os campos nao enviados continuam `null`.
- Alterar a meta de hoje nao altera automaticamente dias passados.
- Para corrigir um dia especifico, basta reenviar o `POST /nutrition-goals` com a `date` daquele dia.
- Metas negativas retornam erro 400.

Resposta (200):

```json
{
  "nutritionGoals": {
    "id": "uuid",
    "date": "2026-03-11",
    "goals": {
      "calories": 2200,
      "protein": 160,
      "carbs": null,
      "fat": null
    },
    "goalInherited": false,
    "createdAt": "2026-03-11T09:00:00Z",
    "updatedAt": "2026-03-11T10:30:00Z"
  }
}
```

Exemplo fetch para salvar meta:

```javascript
const res = await fetch(`${BASE_URL}/nutrition-goals`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-User-Id': userId
  },
  body: JSON.stringify({
    date: selectedDate,
    caloriesGoal: 2200,
    proteinGoal: 160
  })
});

const data = await res.json();
// data.nutritionGoals ja vem com a meta efetiva do dia
```

---

### 2. Buscar meta nutricional do dia

```
GET /nutrition-goals?date=2026-03-11
X-User-Id: <user-id>
```

Importante:
- Se nao existir linha explicita naquele dia, mas existir meta anterior, o backend devolve a ultima meta efetiva herdada.
- Esse endpoint nao calcula consumo, restante ou percentual.
- O front deve cruzar essa resposta com o endpoint de refeicoes/pratos para mostrar se a meta foi batida ou nao.

Resposta quando existe meta cadastrada:

```json
{
  "nutritionGoals": {
    "id": "uuid",
    "date": "2026-03-11",
    "goals": {
      "calories": 2200,
      "protein": 160,
      "carbs": 240,
      "fat": 70
    },
    "goalInherited": false,
    "createdAt": "2026-03-11T09:00:00Z",
    "updatedAt": "2026-03-11T10:30:00Z"
  }
}
```

Resposta quando ainda nao existe linha naquele dia, mas ja existe meta anterior:

```json
{
  "nutritionGoals": {
    "id": null,
    "date": "2026-03-12",
    "goals": {
      "calories": 2200,
      "protein": 160,
      "carbs": null,
      "fat": 70
    },
    "goalInherited": true,
    "createdAt": null,
    "updatedAt": null
  }
}
```

Resposta quando o usuario ainda nao cadastrou nenhuma meta:

```json
{
  "nutritionGoals": null
}
```

Exemplo fetch:

```javascript
const res = await fetch(`${BASE_URL}/nutrition-goals?date=${selectedDate}`, {
  headers: { 'X-User-Id': userId }
});
const data = await res.json();

if (!data.nutritionGoals) {
  // usuario ainda nao configurou nenhuma meta nutricional
} else {
  // renderizar apenas os campos/metas que vierem != null
}
```

---

### Fluxo recomendado da tela de metas nutricionais

1. Ao abrir a tela, chamar `GET /nutrition-goals?date=<hoje>`.
2. Se vier `null`, mostrar formulario de metas (caloria/proteina/carbo/gordura).
3. Se `goalInherited == true`, usar normalmente a meta herdada no panorama do dia.
4. Salvar metas com `POST /nutrition-goals`, enviando apenas os campos alterados.
5. Para corrigir um dia especifico, reenviar `POST /nutrition-goals` com a `date` daquele dia.
6. Buscar as refeicoes/pratos do dia em outro endpoint e comparar no front com `nutritionGoals.goals`.
7. O front decide se a meta foi batida ou nao com base nessa comparacao.

Observacoes:
- O front pode renderizar so os indicadores cujas metas vierem preenchidas em `goals`.
- Enquanto a pessoa nao alterar a meta novamente, os dias futuros usam a ultima meta efetiva.
- Alteracoes em uma data nao retroagem nem alteram automaticamente datas anteriores.

---

## Peso corporal

### Header obrigatorio

Todas as chamadas precisam enviar:

```http
X-User-Id: <uuid do usuario>
```

---

### 1. Salvar novo peso

```
POST /weight
Content-Type: application/json
X-User-Id: <user-id>
```

Body:

```json
{
  "weightKg": 120.5
}
```

Regras:
- O backend aceita apenas o peso atual.
- Nao existe envio de delta como `-1` ou `+2`.
- O horario e a data sao gerados automaticamente no servidor no momento do cadastro.
- `weightKg` deve ser maior que zero.

Resposta (200):

```json
{
  "weight": {
    "id": "uuid",
    "weightKg": 120.5,
    "measuredAt": "2026-03-15T12:34:56.000Z",
    "date": "2026-03-15",
    "time": "12:34:56"
  }
}
```

Exemplo fetch:

```javascript
const res = await fetch(`${BASE_URL}/weight`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-User-Id': userId
  },
  body: JSON.stringify({
    weightKg: 120.5
  })
});

const data = await res.json();
console.log(data.weight);
```

---

### 2. Buscar historico completo de peso

```
GET /weight
X-User-Id: <user-id>
```

Resposta (200):

```json
{
  "weights": [
    {
      "id": "uuid-1",
      "weightKg": 120.5,
      "measuredAt": "2026-03-15T12:34:56.000Z",
      "date": "2026-03-15",
      "time": "12:34:56"
    },
    {
      "id": "uuid-2",
      "weightKg": 119.4,
      "measuredAt": "2026-03-16T08:10:00.000Z",
      "date": "2026-03-16",
      "time": "08:10:00"
    }
  ]
}
```

Observacoes:
- O retorno vem em ordem crescente de `measuredAt`.
- Cada item representa uma pesagem real cadastrada pelo usuario.
- O front pode usar essa lista para grafico, tabela ou linha do tempo.

Exemplo fetch:

```javascript
const res = await fetch(`${BASE_URL}/weight`, {
  headers: {
    'X-User-Id': userId
  }
});

const data = await res.json();
const weights = data.weights;
```

---

### Fluxo recomendado da tela de peso

1. Quando o usuario informar o peso atual, chamar `POST /weight`.
2. Atualizar a UI usando a resposta do proprio `POST /weight`.
3. Para listar historico, chamar `GET /weight`.
4. Usar `date`, `time` e `weightKg` para montar tabela, cards ou grafico no front.

---

## Feedback para desenvolvedores

### Header obrigatorio

Todas as chamadas precisam enviar:

```http
X-User-Id: <uuid do usuario>
```

---

### 1. Enviar feedback

```
POST /feedback
Content-Type: application/json
X-User-Id: <user-id>
```

Body:

```json
{
  "userName": "Joao Silva",
  "message": "Seria legal melhorar a tela inicial e corrigir o bug do botao salvar.",
  "imageUrl": "https://meu-bucket.s3.amazonaws.com/debugs/print-123.png"
}
```

Regras:
- `message` e obrigatoria.
- `userName` e obrigatorio.
- `imageUrl` e opcional e pode ser `null`.
- O backend salva automaticamente data e horario do envio.
- O backend nao valida profundamente a URL da imagem; se vier preenchida, ele guarda o texto enviado.

Resposta (200):

```json
{
  "feedback": {
    "id": "uuid",
    "userId": "uuid-do-usuario",
    "userName": "Joao Silva",
    "message": "Seria legal melhorar a tela inicial e corrigir o bug do botao salvar.",
    "imageUrl": "https://meu-bucket.s3.amazonaws.com/debugs/print-123.png",
    "status": "OPEN",
    "developerResponse": null,
    "respondedAt": null,
    "respondedBy": null,
    "responseSeenAt": null,
    "createdAt": "2026-03-15T14:22:10.000Z",
    "updatedAt": "2026-03-15T14:22:10.000Z",
    "date": "2026-03-15",
    "time": "14:22:10"
  }
}
```

Exemplo fetch:

```javascript
const res = await fetch(`${BASE_URL}/feedback`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-User-Id': userId
  },
  body: JSON.stringify({
    userName: profileName,
    message: feedbackText,
    imageUrl: screenshotUrl || null
  })
});

const data = await res.json();
console.log(data.feedback);
```

---

### 2. Buscar todos os feedbacks para o painel admin

Esse endpoint foi pensado para a area interna de desenvolvedor/admin.

```
GET /feedback
X-User-Id: <seu-user-id-admin>
```

Importante:
- O retorno ja vem com campos preparados para resposta futura do desenvolvedor.

Resposta (200):

```json
{
  "feedbacks": [
    {
      "id": "uuid-1",
      "userId": "uuid-user-1",
      "userName": "Joao Silva",
      "message": "Seria legal melhorar a tela inicial.",
      "imageUrl": null,
      "status": "OPEN",
      "developerResponse": null,
      "respondedAt": null,
      "respondedBy": null,
      "responseSeenAt": null,
      "createdAt": "2026-03-15T14:22:10.000Z",
      "updatedAt": "2026-03-15T14:22:10.000Z",
      "date": "2026-03-15",
      "time": "14:22:10"
    },
    {
      "id": "uuid-2",
      "userId": "uuid-user-2",
      "userName": "Maria",
      "message": "No Android a foto ficou cortada.",
      "imageUrl": "https://cdn.exemplo.com/bug-android.png",
      "status": "ANSWERED",
      "developerResponse": "Obrigado, ajustamos isso na versao 1.0.2.",
      "respondedAt": "2026-03-16T09:00:00.000Z",
      "respondedBy": "admin@vidasync",
      "responseSeenAt": null,
      "createdAt": "2026-03-15T10:00:00.000Z",
      "updatedAt": "2026-03-16T09:00:00.000Z",
      "date": "2026-03-15",
      "time": "10:00:00"
    }
  ]
}
```

Exemplo fetch:

```javascript
const res = await fetch(`${BASE_URL}/feedback`, {
  headers: {
    'X-User-Id': adminUserId
  }
});

const data = await res.json();
const feedbacks = data.feedbacks;
```

---

### Fluxo recomendado da tela de feedback

1. O usuario preenche `userName`, `message` e opcionalmente `imageUrl`.
2. O front chama `POST /feedback`.
3. O backend salva a mensagem com `status = OPEN`.
4. O painel admin chama `GET /feedback` para listar tudo.
5. No futuro, voce pode adicionar um endpoint de resposta usando os campos que ja ficaram preparados no banco (`developerResponse`, `respondedAt`, `respondedBy`, `responseSeenAt`).

---

## Metricas do LLM judge

### Endpoint

Use esta rota no painel interno para mostrar cards, graficos e ultimas avaliacoes do judge.

```
GET /internal/admin/llm-judge/metrics?days=7
X-User-Id: <admin-user-id>
```

### Query params

| Campo | Obrigatorio | Observacao |
| --- | --- | --- |
| `days` | nao | janela em dias. Default `7`. Ignorado quando `startDate` e `endDate` vierem preenchidos. |
| `startDate` | nao | formato `YYYY-MM-DD`. |
| `endDate` | nao | formato `YYYY-MM-DD`. |
| `feature` | nao | filtra por feature. |
| `pipeline` | nao | filtra por pipeline. |
| `handler` | nao | filtra por handler. |
| `idioma` | nao | filtra por idioma. |
| `sourceModel` | nao | filtra por modelo fonte. |
| `judgeStatus` | nao | `pending`, `completed` ou `failed`. |
| `judgeDecision` | nao | `approved` ou `rejected`. |

### Resposta (200)

```json
{
  "metrics": {
    "filters": {
      "startDate": "2026-03-20",
      "endDate": "2026-03-26",
      "days": 7,
      "feature": null,
      "pipeline": null,
      "handler": null,
      "idioma": null,
      "sourceModel": null,
      "judgeStatus": null,
      "judgeDecision": null
    },
    "summary": {
      "totalEvaluations": 42,
      "completedCount": 35,
      "pendingCount": 4,
      "failedCount": 3,
      "approvedCount": 28,
      "rejectedCount": 7,
      "completionRatePercent": 83.33,
      "failureRatePercent": 7.14,
      "approvalRatePercent": 80.0,
      "averageOverallScore": 0.88,
      "averageSourceDurationMs": 1340.52,
      "averageJudgeDurationMs": 220.11,
      "averageSourceTotalTokens": 512.4,
      "averageJudgeTotalTokens": 144.9,
      "latestEvaluationAt": "2026-03-26T18:00:00Z",
      "oldestEvaluationAt": "2026-03-20T09:00:00Z"
    },
    "byFeature": [
      {
        "key": "nutrition",
        "totalEvaluations": 22,
        "completedCount": 20,
        "pendingCount": 1,
        "failedCount": 1,
        "approvedCount": 16,
        "rejectedCount": 4,
        "completionRatePercent": 90.91,
        "failureRatePercent": 4.55,
        "approvalRatePercent": 80.0,
        "averageOverallScore": 0.9,
        "averageSourceDurationMs": 1200.5,
        "averageJudgeDurationMs": 180.2,
        "averageSourceTotalTokens": 480.0,
        "averageJudgeTotalTokens": 120.0
      }
    ],
    "byPipeline": [],
    "byHandler": [],
    "byIdioma": [],
    "bySourceModel": [],
    "daily": [
      {
        "date": "2026-03-26",
        "totalEvaluations": 6,
        "completedCount": 5,
        "pendingCount": 1,
        "failedCount": 0,
        "approvedCount": 4,
        "rejectedCount": 1,
        "completionRatePercent": 83.33,
        "failureRatePercent": 0.0,
        "approvalRatePercent": 80.0,
        "averageOverallScore": 0.91
      }
    ],
    "topRejectionReasons": [
      {
        "key": "falta contexto",
        "count": 3
      }
    ],
    "recentEvaluations": [
      {
        "evaluationId": "eval-123",
        "createdAt": "2026-03-26T18:00:00Z",
        "feature": "nutrition",
        "judgeStatus": "completed",
        "judgeDecision": "approved",
        "judgeOverallScore": 0.97,
        "idioma": "pt-BR",
        "pipeline": "image",
        "handler": "calories",
        "sourceModel": "gpt-4.1-mini",
        "sourceDurationMs": 1180.0,
        "judgeDurationMs": 175.0,
        "sourceTotalTokens": 470,
        "judgeTotalTokens": 118
      }
    ]
  }
}
```

### Como o front pode usar

- `summary` para cards principais
- `daily` para grafico de linha ou barras
- `byFeature`, `byPipeline`, `byHandler`, `byIdioma` e `bySourceModel` para rankings e filtros
- `topRejectionReasons` para heatmap, chips ou tabela de causas
- `recentEvaluations` para a tabela de ultimas execucoes

### Exemplo fetch

```javascript
const params = new URLSearchParams({
  days: '7',
  feature: selectedFeature || ''
});

const res = await fetch(`${BASE_URL}/internal/admin/llm-judge/metrics?${params}`, {
  headers: {
    'X-User-Id': adminUserId
  }
});

const data = await res.json();
const metrics = data.metrics;
```

---

## Metricas de telemetria do backend

### Endpoint de dashboard

Use esta rota para montar os cards e graficos de custo, tokens e latencia do painel interno.

```
GET /internal/admin/telemetry/metrics?days=7&agent=nutrition
X-User-Id: <admin-user-id>
```

### Query params

| Campo | Obrigatorio | Observacao |
| --- | --- | --- |
| `days` | nao | janela em dias. Default `7`. Ignorado quando `startDate` e `endDate` vierem preenchidos. |
| `startDate` | nao | formato `YYYY-MM-DD`. |
| `endDate` | nao | formato `YYYY-MM-DD`. |
| `agent` | nao | filtra por agente/feature do BFF, por exemplo `chat` ou `nutrition`. |
| `status` | nao | filtra por `success`, `error` ou `timeout` no endpoint de runs recentes. |

### Resposta de dashboard (200)

```json
{
  "metrics": {
    "filters": {
      "startDate": "2026-03-20",
      "endDate": "2026-03-26",
      "days": 7,
      "agent": "nutrition",
      "model": null,
      "status": null
    },
    "summary": {
      "totalRuns": 28,
      "successCount": 22,
      "errorCount": 4,
      "timeoutCount": 2,
      "totalCostUsd": 0.0241,
      "inputTokens": 8200,
      "outputTokens": 9100,
      "totalTokens": 17300,
      "averageDurationMs": 1180.4,
      "p95DurationMs": 2840.0,
      "latestRunAt": "2026-03-26T18:00:00Z",
      "oldestRunAt": "2026-03-20T09:00:00Z"
    },
    "daily": [
      {
        "dayUtc": "2026-03-26",
        "runCount": 5,
        "successCount": 4,
        "errorCount": 1,
        "timeoutCount": 0,
        "totalCostUsd": 0.0042,
        "inputTokens": 1300,
        "outputTokens": 1440,
        "totalTokens": 2740,
        "averageDurationMs": 990.0,
        "p95DurationMs": 1220.0
      }
    ],
    "byAgent": [
      {
        "agent": "nutrition",
        "runCount": 18,
        "successCount": 15,
        "errorCount": 2,
        "timeoutCount": 1,
        "totalCostUsd": 0.017,
        "totalTokens": 12200,
        "averageDurationMs": 1090.0,
        "p95DurationMs": 2410.0
      }
    ],
    "byModel": [
      {
        "model": "gpt-4.1-mini",
        "agent": "nutrition",
        "llmCallCount": 16,
        "totalCostUsd": 0.0154,
        "inputTokens": 5600,
        "outputTokens": 5900,
        "totalTokens": 11500,
        "averageDurationMs": 930.0,
        "p95DurationMs": 1180.0
      }
    ]
  }
}
```

### Endpoint de runs recentes

Use esta rota para preencher a tabela de execucoes recentes.

```
GET /internal/admin/telemetry/runs?days=7&status=timeout&limit=20
X-User-Id: <admin-user-id>
```

### Resposta de runs (200)

```json
{
  "runs": {
    "filters": {
      "startDate": "2026-03-20",
      "endDate": "2026-03-26",
      "days": 7,
      "agent": null,
      "model": null,
      "status": "timeout"
    },
    "limit": 20,
    "recentRuns": [
      {
        "runId": "run-123",
        "requestId": "req-123",
        "traceId": "trace-123",
        "agent": "nutrition",
        "endpoint": "/nutrition/calories",
        "httpMethod": "POST",
        "httpStatus": 504,
        "status": "timeout",
        "timeout": true,
        "durationMs": 3000.0,
        "totalCostUsd": 0.0,
        "inputTokens": 0,
        "outputTokens": 0,
        "totalTokens": 0,
        "llmCallCount": 0,
        "toolCallCount": 1,
        "stageEventCount": 4,
        "errorMessage": "HTTP 504",
        "startedAt": "2026-03-26T18:10:00Z",
        "finishedAt": "2026-03-26T18:10:03Z",
        "requestContext": {
          "path": "/nutrition/calories"
        }
      }
    ]
  }
}
```

### Como o front pode usar

- `metrics.summary` para cards principais
- `metrics.daily` para grafico de linha/barras
- `metrics.byAgent` e `metrics.byModel` para rankings
- `runs.recentRuns` para tabela de execucoes com filtro por status

### Exemplo fetch

```javascript
const metricsRes = await fetch(`${BASE_URL}/internal/admin/telemetry/metrics?days=7`, {
  headers: {
    'X-User-Id': adminUserId
  }
});

const metricsData = await metricsRes.json();
const telemetryMetrics = metricsData.metrics;

const runsRes = await fetch(`${BASE_URL}/internal/admin/telemetry/runs?days=7&limit=20`, {
  headers: {
    'X-User-Id': adminUserId
  }
});

const runsData = await runsRes.json();
const recentRuns = runsData.runs.recentRuns;
```

