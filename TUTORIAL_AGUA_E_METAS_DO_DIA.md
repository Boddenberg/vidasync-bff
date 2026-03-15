# Tutorial: Como consumir os endpoints de agua e metas do dia

Este arquivo mostra, de forma pratica, como o front pode consumir:

- endpoints de agua
- endpoints de metas diarias de calorias e macros

Header obrigatorio para todos os endpoints abaixo:

```http
X-User-Id: <uuid-do-usuario>
```

---

## 1. Agua

### Objetivo do fluxo

Com os endpoints de agua, o front consegue:

- cadastrar ou alterar a meta de agua de um dia
- adicionar ou remover agua consumida no dia
- buscar o panorama de um dia especifico
- buscar o historico de varios dias

Importante:

- a meta de um dia nao altera automaticamente dias passados
- se um dia futuro ainda nao tiver meta propria, o backend herda a ultima meta configurada
- cada `deltaMl` fica salvo no historico daquele dia

---

### 1.1. Salvar meta de agua

Endpoint:

```http
POST /water
Content-Type: application/json
X-User-Id: <user-id>
```

Body:

```json
{
  "date": "2026-03-15",
  "goalMl": 3500
}
```

Exemplo em JavaScript:

```javascript
const response = await fetch(`${BASE_URL}/water`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-User-Id': userId
  },
  body: JSON.stringify({
    date: '2026-03-15',
    goalMl: 3500
  })
});

const data = await response.json();
console.log(data.water);
```

Resposta esperada:

```json
{
  "water": {
    "id": "uuid",
    "date": "2026-03-15",
    "goalMl": 3500,
    "consumedMl": 0,
    "remainingMl": 3500,
    "progressPercent": 0,
    "goalReached": false,
    "goalInherited": false,
    "createdAt": "2026-03-15T09:00:00Z",
    "updatedAt": "2026-03-15T09:00:00Z",
    "events": []
  }
}
```

---

### 1.2. Adicionar agua consumida

Body com `deltaMl` positivo:

```json
{
  "date": "2026-03-15",
  "deltaMl": 500
}
```

Exemplo:

```javascript
const response = await fetch(`${BASE_URL}/water`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-User-Id': userId
  },
  body: JSON.stringify({
    date: selectedDate,
    deltaMl: 500
  })
});

const data = await response.json();
console.log(data.water.events);
```

---

### 1.3. Remover agua consumida

Body com `deltaMl` negativo:

```json
{
  "date": "2026-03-15",
  "deltaMl": -300
}
```

Exemplo:

```javascript
await fetch(`${BASE_URL}/water`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-User-Id': userId
  },
  body: JSON.stringify({
    date: selectedDate,
    deltaMl: -300
  })
});
```

Observacao:

- se tentar remover mais do que existe no dia, o backend trava o saldo em `0`

---

### 1.4. Buscar o panorama de agua do dia

Endpoint:

```http
GET /water?date=2026-03-15
X-User-Id: <user-id>
```

Exemplo:

```javascript
const response = await fetch(`${BASE_URL}/water?date=${selectedDate}`, {
  headers: {
    'X-User-Id': userId
  }
});

const data = await response.json();

if (!data.water) {
  console.log('Usuario ainda nao configurou agua');
} else {
  console.log('Meta do dia:', data.water.goalMl);
  console.log('Consumido:', data.water.consumedMl);
  console.log('Historico de movimentos:', data.water.events);
}
```

Quando pode vir `goalInherited: true`:

```json
{
  "water": {
    "id": null,
    "date": "2026-03-16",
    "goalMl": 3500,
    "consumedMl": 0,
    "remainingMl": 3500,
    "progressPercent": 0,
    "goalReached": false,
    "goalInherited": true,
    "createdAt": null,
    "updatedAt": null,
    "events": []
  }
}
```

Isso significa:

- ainda nao existe linha propria naquele dia
- mas a ultima meta conhecida foi herdada

---

### 1.5. Buscar historico de agua

Endpoint:

```http
GET /water/history?startDate=2026-03-10&endDate=2026-03-15
X-User-Id: <user-id>
```

Exemplo:

```javascript
const response = await fetch(
  `${BASE_URL}/water/history?startDate=2026-03-10&endDate=2026-03-15`,
  {
    headers: {
      'X-User-Id': userId
    }
  }
);

const data = await response.json();
console.log(data.waterHistory.days);
```

Use esse endpoint quando o front precisar:

- montar tabela por dia
- montar calendario
- mostrar historico completo da hidratacao

---

### 1.6. Fluxo recomendado para agua

1. Abrir a tela e chamar `GET /water?date=<hoje>`.
2. Se vier `null`, mostrar UI para cadastrar meta.
3. Se vier `goalInherited: true`, usar a meta herdada normalmente.
4. Ao clicar em botoes como `+200ml`, `+500ml` ou `-300ml`, chamar `POST /water`.
5. Atualizar a UI usando a resposta do proprio `POST /water`.
6. Para historico/calendario, chamar `GET /water/history`.

---

## 2. Metas do dia: calorias e macros

### Objetivo do fluxo

Com os endpoints de metas do dia, o front consegue:

- cadastrar metas de calorias
- cadastrar metas de proteina
- cadastrar metas de carboidrato
- cadastrar metas de gordura
- alterar qualquer uma dessas metas por data
- herdar automaticamente a ultima meta para dias futuros

Importante:

- esse endpoint salva e devolve apenas as metas
- ele nao calcula consumo do dia
- ele nao informa se a meta foi batida
- essa comparacao deve ser feita no front usando o endpoint de refeicoes/pratos

---

### 2.1. Salvar metas do dia

Endpoint:

```http
POST /nutrition-goals
Content-Type: application/json
X-User-Id: <user-id>
```

Voce pode enviar todos os campos:

```json
{
  "date": "2026-03-15",
  "caloriesGoal": 2400,
  "proteinGoal": 150,
  "carbsGoal": 220,
  "fatGoal": 80
}
```

Ou enviar apenas parte deles:

```json
{
  "date": "2026-03-15",
  "caloriesGoal": 2400,
  "proteinGoal": 150
}
```

Exemplo:

```javascript
const response = await fetch(`${BASE_URL}/nutrition-goals`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-User-Id': userId
  },
  body: JSON.stringify({
    date: selectedDate,
    caloriesGoal: 2400,
    proteinGoal: 150
  })
});

const data = await response.json();
console.log(data.nutritionGoals);
```

Resposta esperada:

```json
{
  "nutritionGoals": {
    "id": "uuid",
    "date": "2026-03-15",
    "goals": {
      "calories": 2400,
      "protein": 150,
      "carbs": null,
      "fat": null
    },
    "goalInherited": false,
    "createdAt": "2026-03-15T09:00:00Z",
    "updatedAt": "2026-03-15T09:00:00Z"
  }
}
```

Regras importantes:

- envie apenas os campos que quer alterar
- se um campo nao for enviado, o backend reaproveita a ultima meta efetiva daquele campo ate essa data
- se for o primeiro cadastro do usuario, os campos nao enviados continuam `null`
- alterar a meta de hoje nao altera automaticamente dias anteriores
- para corrigir um dia antigo, envie o `POST` com a `date` exata daquele dia

---

### 2.2. Buscar meta do dia

Endpoint:

```http
GET /nutrition-goals?date=2026-03-15
X-User-Id: <user-id>
```

Exemplo:

```javascript
const response = await fetch(`${BASE_URL}/nutrition-goals?date=${selectedDate}`, {
  headers: {
    'X-User-Id': userId
  }
});

const data = await response.json();

if (!data.nutritionGoals) {
  console.log('Usuario ainda nao cadastrou metas');
} else {
  console.log('Metas do dia:', data.nutritionGoals.goals);
}
```

Resposta quando existe linha propria no dia:

```json
{
  "nutritionGoals": {
    "id": "uuid",
    "date": "2026-03-15",
    "goals": {
      "calories": 2400,
      "protein": 150,
      "carbs": 220,
      "fat": 80
    },
    "goalInherited": false,
    "createdAt": "2026-03-15T09:00:00Z",
    "updatedAt": "2026-03-15T10:00:00Z"
  }
}
```

Resposta quando o dia ainda nao tem linha propria, mas herdou a ultima meta:

```json
{
  "nutritionGoals": {
    "id": null,
    "date": "2026-03-16",
    "goals": {
      "calories": 2400,
      "protein": 150,
      "carbs": 220,
      "fat": 80
    },
    "goalInherited": true,
    "createdAt": null,
    "updatedAt": null
  }
}
```

Resposta quando ainda nao existe nenhuma meta:

```json
{
  "nutritionGoals": null
}
```

---

### 2.3. Como o front decide se a meta foi batida

Esse calculo deve ser feito no front.

Fluxo:

1. Buscar a meta com `GET /nutrition-goals?date=<dia>`.
2. Buscar as refeicoes/pratos do mesmo dia no endpoint de meals/pratos.
3. Somar no front as calorias e os macros consumidos.
4. Comparar com `nutritionGoals.goals`.

Exemplo simples:

```javascript
const goals = dataGoals.nutritionGoals?.goals;

const totalCalories = meals.reduce((sum, meal) => sum + (meal.caloriesNumber || 0), 0);
const totalProtein = meals.reduce((sum, meal) => sum + (meal.proteinNumber || 0), 0);
const totalCarbs = meals.reduce((sum, meal) => sum + (meal.carbsNumber || 0), 0);
const totalFat = meals.reduce((sum, meal) => sum + (meal.fatNumber || 0), 0);

const caloriesReached = goals?.calories != null ? totalCalories >= goals.calories : null;
const proteinReached = goals?.protein != null ? totalProtein >= goals.protein : null;
const carbsReached = goals?.carbs != null ? totalCarbs >= goals.carbs : null;
const fatReached = goals?.fat != null ? totalFat >= goals.fat : null;
```

Se uma meta vier como `null`, significa:

- o usuario nao configurou aquele objetivo
- o front pode simplesmente nao renderizar esse card/indicador

---

### 2.4. Fluxo recomendado para metas do dia

1. Abrir a tela e chamar `GET /nutrition-goals?date=<hoje>`.
2. Se vier `null`, mostrar formulario de metas.
3. Se vier `goalInherited: true`, usar a meta herdada normalmente.
4. Salvar com `POST /nutrition-goals`, enviando apenas os campos alterados.
5. Para corrigir um dia passado, reenviar o mesmo `POST` com a data daquele dia.
6. Cruzar essa resposta com o endpoint de refeicoes/pratos para montar progresso no front.

---

## 3. Resumo rapido

### Agua

- `POST /water`: salva meta e/ou movimentacao de agua
- `GET /water`: traz o panorama do dia
- `GET /water/history`: traz historico por periodo

### Metas de calorias e macros

- `POST /nutrition-goals`: salva metas do dia
- `GET /nutrition-goals`: traz apenas a meta efetiva do dia

---

## 4. Dica de implementacao no front

Uma estrutura simples de carregamento pode ser:

```javascript
async function loadDayDashboard(date) {
  const [waterRes, goalsRes, mealsRes] = await Promise.all([
    fetch(`${BASE_URL}/water?date=${date}`, {
      headers: { 'X-User-Id': userId }
    }),
    fetch(`${BASE_URL}/nutrition-goals?date=${date}`, {
      headers: { 'X-User-Id': userId }
    }),
    fetch(`${BASE_URL}/meals?date=${date}`, {
      headers: { 'X-User-Id': userId }
    })
  ]);

  const waterData = await waterRes.json();
  const goalsData = await goalsRes.json();
  const mealsData = await mealsRes.json();

  return {
    water: waterData.water,
    nutritionGoals: goalsData.nutritionGoals,
    meals: mealsData.meals
  };
}
```

Assim:

- a agua vem pronta do backend com historico e saldo
- a meta nutricional vem pronta do backend
- o comparativo de meta x consumo das refeicoes fica no front
