# 🔐 VidaSync — Guia de Integração: Autenticação e Rotas

## Visão Geral

Todas as rotas que acessam dados do usuário agora exigem o header `X-User-Id`.
O fluxo é simples:

1. Usuário faz **signup** ou **login** → backend retorna o `userId` (UUID)
2. Frontend **salva o `userId`** (AsyncStorage, SecureStore, contexto, etc.)
3. **Toda request** de dados envia o header `X-User-Id: <userId>`

---

## 1. Signup (criar conta)

```
POST /auth/signup
Content-Type: application/json

{
  "username": "joao123",
  "password": "minhasenha123",
  "profileImage": "data:image/jpeg;base64,/9j/4AAQ..."  // opcional
}
```

Resposta `201`:
```json
{
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "username": "joao123",
  "profileImageUrl": "https://...storage.../profile_joao123_xxx.jpg"
}
```

> **Regras do username**: só letras e números, 3–30 caracteres.
> **profileImage** é opcional — pode não enviar o campo ou enviar `null`.

---

## 2. Login

```
POST /auth/login
Content-Type: application/json

{
  "username": "joao123",
  "password": "minhasenha123"
}
```

Resposta `200`:
```json
{
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "username": "joao123",
  "profileImageUrl": "https://...storage.../profile_joao123_xxx.jpg"
}
```

> ⚠️ **Salve o `userId` retornado!** Ele será usado em TODAS as próximas requests.

---

## 3. Header obrigatório em todas as rotas de dados

Após o login, **toda request** (exceto signup, login, health e calories) precisa do header:

```
X-User-Id: a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

### Exemplo no frontend (TypeScript/React Native):

```typescript
// Salvar após login/signup
const [userId, setUserId] = useState<string | null>(null);

// Função login
const login = async (username: string, password: string) => {
  const res = await fetch(`${API_URL}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  const data = await res.json();
  setUserId(data.userId); // salva o userId
  // salvar em AsyncStorage/SecureStore também, para manter o login
  await AsyncStorage.setItem("userId", data.userId);
  return data;
};

// Helper para requests autenticadas
const apiFetch = async (path: string, options: RequestInit = {}) => {
  const storedUserId = userId || (await AsyncStorage.getItem("userId"));
  if (!storedUserId) throw new Error("Não autenticado");

  return fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      "X-User-Id": storedUserId,
      ...options.headers,
    },
  });
};
```

---

## 4. Rotas completas

### 🔓 Rotas PÚBLICAS (sem X-User-Id)

| Método | Rota | Body | Resposta |
|--------|------|------|----------|
| `GET` | `/health` | — | `{ "status": "UP" }` |
| `POST` | `/auth/signup` | `{ username, password, profileImage? }` | `{ userId, username, profileImageUrl }` |
| `POST` | `/auth/login` | `{ username, password }` | `{ userId, username, profileImageUrl }` |
| `POST` | `/nutrition/calories` | `{ "foods": "uma paçoquinha" }` | `{ nutrition: { calories, protein, carbs, fat } }` |

### 🔒 Rotas AUTENTICADAS (exigem header `X-User-Id`)

#### Perfil

| Método | Rota | Body | Resposta |
|--------|------|------|----------|
| `GET` | `/auth/profile` | — | `{ userId, username, profileImageUrl }` |
| `PUT` | `/auth/profile` | `{ username?, password?, profileImage? }` | `{ userId, username, profileImageUrl }` |

#### Refeições

| Método | Rota | Body | Resposta |
|--------|------|------|----------|
| `POST` | `/meals` | `{ foods, mealType, date, time?, nutrition? }` | `{ meal: {...} }` |
| `GET` | `/meals?date=2026-02-24` | — | `{ meals: [...] }` |
| `GET` | `/meals/summary?date=2026-02-24` | — | `{ date, totalMeals, meals, totals }` |
| `GET` | `/meals/range?startDate=...&endDate=...` | — | `{ meals: [...] }` |
| `PUT` | `/meals/{id}` | `{ foods?, mealType?, date?, time?, nutrition? }` | `{ meal: {...} }` |
| `DELETE` | `/meals/{id}` | — | `{ success: true }` |
| `POST` | `/meals/{id}/duplicate` | — | `{ meal: {...} }` |

#### Favoritos

| Método | Rota | Body | Resposta |
|--------|------|------|----------|
| `POST` | `/favorites` | `{ foods, nutrition?, image? }` | `{ favorite: {...} }` |
| `GET` | `/favorites` | — | `{ favorites: [...] }` |
| `DELETE` | `/favorites/{id}` | — | `{ success: true }` |

---

## 5. Exemplos com `apiFetch`

```typescript
// Criar refeição
const createMeal = async (meal) => {
  const res = await apiFetch("/meals", {
    method: "POST",
    body: JSON.stringify(meal),
  });
  return res.json();
};

// Buscar refeições do dia
const getMealsByDate = async (date: string) => {
  const res = await apiFetch(`/meals?date=${date}`);
  return res.json();
};

// Resumo do dia (timeline + totais)
const getDaySummary = async (date: string) => {
  const res = await apiFetch(`/meals/summary?date=${date}`);
  return res.json();
};

// Editar refeição (parcial)
const updateMeal = async (id: string, updates) => {
  const res = await apiFetch(`/meals/${id}`, {
    method: "PUT",
    body: JSON.stringify(updates),
  });
  return res.json();
};

// Deletar refeição
const deleteMeal = async (id: string) => {
  const res = await apiFetch(`/meals/${id}`, { method: "DELETE" });
  return res.json();
};

// Buscar favoritos
const getFavorites = async () => {
  const res = await apiFetch("/favorites");
  return res.json();
};

// Criar favorito (com foto opcional)
const createFavorite = async (foods, nutrition, imageBase64?) => {
  const res = await apiFetch("/favorites", {
    method: "POST",
    body: JSON.stringify({ foods, nutrition, image: imageBase64 }),
  });
  return res.json();
};

// Ver perfil
const getProfile = async () => {
  const res = await apiFetch("/auth/profile");
  return res.json();
};

// Editar perfil (qualquer campo, todos opcionais)
const updateProfile = async (updates) => {
  const res = await apiFetch("/auth/profile", {
    method: "PUT",
    body: JSON.stringify(updates),
  });
  return res.json();
};

// Exemplos de edição de perfil:
updateProfile({ username: "novoNome" });
updateProfile({ password: "novaSenha123" });
updateProfile({ profileImage: "data:image/jpeg;base64,..." });
updateProfile({ username: "novoNome", password: "novaSenha", profileImage: "data:image/..." });
```

---

## 6. Tratamento de erros

Quando o `X-User-Id` não é enviado em rota autenticada, o Spring retorna `400`:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Required header 'X-User-Id' is not present"
}
```

Login com credenciais erradas retorna `401`:
```json
{ "error": "Usuário ou senha inválidos" }
```

Signup com username já existente retorna `400`:
```json
{ "error": "Usuário 'joao123' já existe" }
```

---

## 7. Checklist de migração no frontend

- [ ] Criar telas de **Login** e **Signup** chamando `/auth/login` e `/auth/signup`
- [ ] Salvar o `userId` retornado (AsyncStorage / SecureStore / contexto)
- [ ] Criar helper `apiFetch` que injeta `X-User-Id` em todas as requests
- [ ] Substituir todas as chamadas `fetch` / `apiGet` / `apiPost` pelo `apiFetch`
- [ ] Se `userId` não existe → redirecionar para tela de Login
- [ ] Tela de perfil: `GET /auth/profile` para exibir, `PUT /auth/profile` para editar
- [ ] Logout = limpar o `userId` do storage e redirecionar para Login
