# VidaSync BFF - Tutorial de Integracao de Notificacoes

## Objetivo

O app continua consumindo notificacoes somente pelo backend. O frontend:

- busca a inbox com `GET /notifications`
- marca uma, varias ou todas como lidas com `POST /notifications/read`
- marca uma, varias ou todas como deletadas com `POST /notifications/delete`

O delete e logico. Nada e removido fisicamente do banco. O historico fica preservado para analise interna.

## Headers

Todas as rotas abaixo exigem:

```http
X-User-Id: <user-id>
X-Access-Token: <token-opcional>
```

## 1. Carregar notificacoes

```http
GET /notifications
```

### Resposta

```json
{
  "unreadCount": 1,
  "notifications": [
    {
      "id": "uuid-1",
      "title": "Resposta da equipe",
      "message": "Respondemos seu feedback.",
      "type": "INFO",
      "imageUrl": "https://cdn.exemplo.com/notificacoes/feedback.jpg",
      "actionLabel": "Abrir feedback",
      "actionRoute": "/feedback",
      "readAt": null,
      "deleted": false,
      "deletedAt": null,
      "createdAt": "2026-03-15T16:20:00.000Z",
      "date": "2026-03-15",
      "time": "16:20:00"
    },
    {
      "id": "uuid-2",
      "title": "Mensagem antiga",
      "message": "Mantida so para historico.",
      "type": "INFO",
      "imageUrl": null,
      "actionLabel": null,
      "actionRoute": null,
      "readAt": "2026-03-15T16:21:00.000Z",
      "deleted": true,
      "deletedAt": "2026-03-15T16:22:00.000Z",
      "createdAt": "2026-03-15T16:19:00.000Z",
      "date": "2026-03-15",
      "time": "16:19:00"
    }
  ]
}
```

### Regras

- `unreadCount` considera apenas notificacoes com `readAt == null` e `deleted == false`
- a lista pode conter notificacoes deletadas para preservar historico no cliente
- o frontend deve esconder itens com `deleted == true`
- quando `imageUrl` vier preenchido, o frontend pode renderizar a imagem da notificacao

## 2. Marcar como lida

```http
POST /notifications/read
Content-Type: application/json
```

### Uma ou varias notificacoes

```json
{
  "notificationIds": ["uuid-1", "uuid-2"]
}
```

### Todas as notificacoes ativas

```json
{
  "markAll": true
}
```

### Resposta

```json
{
  "unreadCount": 0,
  "notifications": [
    {
      "id": "uuid-1",
      "readAt": "2026-03-15T16:25:00.000Z",
      "deleted": false,
      "deletedAt": null
    }
  ]
}
```

### Regras

- `notificationIds` e `markAll=true` sao mutuamente exclusivos
- `markAll=true` afeta apenas notificacoes nao deletadas
- ids inexistentes ou que pertencem a outro usuario sao ignorados

## 3. Marcar como deletada

```http
POST /notifications/delete
Content-Type: application/json
```

### Uma ou varias notificacoes

```json
{
  "notificationIds": ["uuid-1", "uuid-2"]
}
```

### Todas as notificacoes ativas

```json
{
  "markAll": true
}
```

### Resposta

```json
{
  "unreadCount": 0,
  "notifications": [
    {
      "id": "uuid-1",
      "readAt": null,
      "deleted": true,
      "deletedAt": "2026-03-15T16:30:00.000Z"
    }
  ]
}
```

### Regras

- o backend grava `is_deleted = true` e `deleted_at = now()`
- nenhuma linha e removida do banco
- `markAll=true` deleta apenas notificacoes ativas

## 4. Persistencia no Supabase

A tabela usada pelo backend e `notifications`, com os campos principais:

- `id`
- `user_id`
- `title`
- `message`
- `type`
- `image_url`
- `action_label`
- `action_route`
- `read_at`
- `is_deleted`
- `deleted_at`
- `created_at`
- `updated_at`

O SQL idempotente para criar ou evoluir a tabela foi adicionado em `supabase-migrations.sql`.

## 5. Publicar notificacao para um usuario

Essa rota e interna. Ela nao deve ser chamada pelo app cliente.

```http
POST /internal/admin/notifications
X-Internal-Api-Key: <internal-api-key>
Content-Type: application/json
```

### Body

```json
{
  "userId": "uuid-do-usuario",
  "title": "Resposta da equipe",
  "message": "Respondemos seu feedback.",
  "type": "INFO",
  "imageUrl": "https://cdn.exemplo.com/notificacoes/feedback.jpg",
  "actionLabel": "Abrir feedback",
  "actionRoute": "/feedback"
}
```

O `userId` do usuario destino deve ser enviado apenas no body.

### Resposta

```json
{
  "notification": {
    "id": "uuid-1",
    "title": "Resposta da equipe",
    "message": "Respondemos seu feedback.",
    "type": "INFO",
    "imageUrl": "https://cdn.exemplo.com/notificacoes/feedback.jpg",
    "actionLabel": "Abrir feedback",
    "actionRoute": "/feedback",
    "readAt": null,
    "deleted": false,
    "deletedAt": null,
    "createdAt": "2026-03-25T12:00:00.000Z",
    "date": "2026-03-25",
    "time": "12:00:00"
  }
}
```

## 6. Publicar notificacao para todos os usuarios

Essa rota e interna. Ela cria uma linha por usuario na tabela `notifications`.

```http
POST /internal/admin/notifications/broadcast
X-User-Id: <actor-user-id>
X-Internal-Api-Key: <internal-api-key>
Content-Type: application/json
```

### Body

```json
{
  "title": "Comunicado geral",
  "message": "Hoje teremos manutencao programada.",
  "type": "WARNING",
  "imageUrl": "https://cdn.exemplo.com/notificacoes/manutencao.jpg",
  "actionLabel": null,
  "actionRoute": null
}
```

### Resposta

```json
{
  "createdCount": 42
}
```

### Regras

- as rotas internas exigem `X-User-Id` para auditoria
- se `INTERNAL_ADMIN_API_KEY` estiver configurada, `X-Internal-Api-Key` precisa bater
- `imageUrl` e opcional, mas quando informado o backend persiste e devolve no `GET /notifications`
- `actionLabel` e `actionRoute` devem ser enviados juntos
- no broadcast, o backend busca todos os `user_id` em `user_profiles`
