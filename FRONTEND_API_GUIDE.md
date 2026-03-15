# 🍽️ VidaSync BFF — Guia de Integração Frontend

## Base URL

```
http://localhost:8080
```

## Contrato de dominio (obrigatorio)

- O app deve chamar somente rotas do BFF (`/nutrition`, `/meals`, `/favorites`, `/auth`).
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

