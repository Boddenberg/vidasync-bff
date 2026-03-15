# Tutorial: Como consumir o endpoint de peso

Este arquivo mostra como o front pode:

- salvar uma nova pesagem
- buscar o historico completo de peso do usuario

Base URL de exemplo:

```text
http://localhost:8080
```

Header obrigatorio:

```http
X-User-Id: <uuid-do-usuario>
```

---

## 1. Salvar novo peso

Endpoint:

```http
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

Importante:

- o backend aceita apenas o peso atual
- nao existe envio de delta como `+1` ou `-2`
- o horario e a data sao gerados automaticamente no servidor

Exemplo em JavaScript:

```javascript
const response = await fetch(`${BASE_URL}/weight`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-User-Id': userId
  },
  body: JSON.stringify({
    weightKg: 120.5
  })
});

const data = await response.json();
console.log(data.weight);
```

Resposta esperada:

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

---

## 2. Buscar historico completo

Endpoint:

```http
GET /weight
X-User-Id: <user-id>
```

Exemplo:

```javascript
const response = await fetch(`${BASE_URL}/weight`, {
  headers: {
    'X-User-Id': userId
  }
});

const data = await response.json();
const weights = data.weights;
console.log(weights);
```

Resposta esperada:

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

- o retorno vem em ordem crescente de horario
- cada item representa uma pesagem real cadastrada pelo usuario
- o front pode usar isso para tabela, linha do tempo ou grafico

---

## 3. Fluxo recomendado no front

1. Quando o usuario informar o peso atual, chamar `POST /weight`.
2. Atualizar a UI com a resposta do proprio `POST /weight`.
3. Para mostrar o historico, chamar `GET /weight`.
4. Usar `weightKg`, `date` e `time` para montar tabela, cards ou grafico.
