# Tutorial: Como consumir o endpoint de feedback para desenvolvedores

Este arquivo mostra como o front pode:

- enviar feedback do usuario
- listar feedbacks no painel interno/admin

Base URL de exemplo:

```text
http://localhost:8080
```

Header obrigatorio:

```http
X-User-Id: <uuid-do-usuario>
```

Se o backend estiver com `INTERNAL_ADMIN_API_KEY` configurada, o GET admin tambem precisa enviar:

```http
X-Internal-Api-Key: <internal-admin-api-key>
```

---

## 1. Enviar feedback do usuario

Endpoint:

```http
POST /feedback
Content-Type: application/json
X-User-Id: <user-id>
```

Body:

```json
{
  "userName": "Joao Silva",
  "message": "O botao salvar travou quando eu tentei mandar a foto.",
  "imageUrl": "https://meu-bucket.s3.amazonaws.com/debugs/print-erro.png"
}
```

Campos:

- `userName`: obrigatorio
- `message`: obrigatoria
- `imageUrl`: opcional, pode ser `null`

Importante:

- o backend salva data e horario automaticamente
- o backend guarda a URL da imagem como texto, sem validacao pesada
- a estrutura ja fica preparada para resposta futura do desenvolvedor

Exemplo em JavaScript:

```javascript
const response = await fetch(`${BASE_URL}/feedback`, {
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

const data = await response.json();
console.log(data.feedback);
```

Resposta esperada:

```json
{
  "feedback": {
    "id": "uuid",
    "userId": "uuid-do-usuario",
    "userName": "Joao Silva",
    "message": "O botao salvar travou quando eu tentei mandar a foto.",
    "imageUrl": "https://meu-bucket.s3.amazonaws.com/debugs/print-erro.png",
    "status": "OPEN",
    "developerResponse": null,
    "respondedAt": null,
    "respondedBy": null,
    "responseSeenAt": null,
    "createdAt": "2026-03-15T15:10:00.000Z",
    "updatedAt": "2026-03-15T15:10:00.000Z",
    "date": "2026-03-15",
    "time": "15:10:00"
  }
}
```

---

## 2. Listar feedbacks no painel admin

Endpoint:

```http
GET /feedback
X-User-Id: <admin-user-id>
X-Internal-Api-Key: <internal-admin-api-key>
```

Exemplo:

```javascript
const response = await fetch(`${BASE_URL}/feedback`, {
  headers: {
    'X-User-Id': adminUserId,
    'X-Internal-Api-Key': internalApiKey
  }
});

const data = await response.json();
const feedbacks = data.feedbacks;
console.log(feedbacks);
```

Resposta esperada:

```json
{
  "feedbacks": [
    {
      "id": "uuid-1",
      "userId": "uuid-user-1",
      "userName": "Joao Silva",
      "message": "O botao salvar travou.",
      "imageUrl": null,
      "status": "OPEN",
      "developerResponse": null,
      "respondedAt": null,
      "respondedBy": null,
      "responseSeenAt": null,
      "createdAt": "2026-03-15T15:10:00.000Z",
      "updatedAt": "2026-03-15T15:10:00.000Z",
      "date": "2026-03-15",
      "time": "15:10:00"
    }
  ]
}
```

Observacoes:

- o retorno vem em ordem do mais recente para o mais antigo
- cada item traz o texto enviado pelo usuario
- se existir `imageUrl`, o painel admin pode carregar a imagem para debug
- os campos `developerResponse`, `respondedAt`, `respondedBy` e `responseSeenAt` ja existem para uma futura resposta ao usuario

---

## 3. Fluxo recomendado

1. O usuario abre a tela de sugestao/erro.
2. O front envia `POST /feedback` com nome, mensagem e imagem opcional.
3. O backend grava a entrada com `status = OPEN`.
4. O painel interno usa `GET /feedback` para listar tudo.
5. No futuro, voce pode adicionar um endpoint para responder esse feedback usando os campos ja preparados no banco.
