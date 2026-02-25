-- ============================================
-- VidaSync — Migrations Supabase
-- Execute no SQL Editor do Supabase
-- ============================================

-- 1. Adicionar coluna 'time' na tabela meals
ALTER TABLE meals ADD COLUMN IF NOT EXISTS time text;

-- 2. Adicionar coluna 'image_url' na tabela favorite_meals
ALTER TABLE favorite_meals ADD COLUMN IF NOT EXISTS image_url text;

-- 3. Adicionar coluna 'user_id' nas tabelas
ALTER TABLE meals ADD COLUMN IF NOT EXISTS user_id UUID;
ALTER TABLE favorite_meals ADD COLUMN IF NOT EXISTS user_id UUID;

-- 4. Índices para performance de busca por user_id
CREATE INDEX IF NOT EXISTS idx_meals_user_id ON meals(user_id);
CREATE INDEX IF NOT EXISTS idx_favorite_meals_user_id ON favorite_meals(user_id);

-- 5. Tabela de perfis de usuário
CREATE TABLE IF NOT EXISTS user_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL,
    username TEXT UNIQUE NOT NULL,
    profile_image_url TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_profiles_username ON user_profiles(username);

-- 6. Desabilitar RLS (autenticação é simples, sem JWT/RLS)
ALTER TABLE meals DISABLE ROW LEVEL SECURITY;
ALTER TABLE favorite_meals DISABLE ROW LEVEL SECURITY;
ALTER TABLE user_profiles DISABLE ROW LEVEL SECURITY;
