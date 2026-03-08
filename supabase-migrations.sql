-- ============================================
-- VidaSync — Migrations Supabase
-- Execute no SQL Editor do Supabase
-- ============================================

-- 1. Adicionar coluna 'time' na tabela meals
ALTER TABLE meals ADD COLUMN IF NOT EXISTS time text;

-- 2. Adicionar coluna 'image_url' na tabela favorite_meals
ALTER TABLE favorite_meals ADD COLUMN IF NOT EXISTS image_url text;

-- 2b. Adicionar coluna 'image_url' na tabela meals
ALTER TABLE meals ADD COLUMN IF NOT EXISTS image_url text;

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

-- 7. Tabela de cache de ingredientes (nutrição por ingrediente individual)
CREATE TABLE IF NOT EXISTS ingredient_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ingredient_key TEXT UNIQUE NOT NULL,
    original_input TEXT NOT NULL,
    corrected_input TEXT,
    calories TEXT NOT NULL DEFAULT '0 kcal',
    protein TEXT NOT NULL DEFAULT '0g',
    carbs TEXT NOT NULL DEFAULT '0g',
    fat TEXT NOT NULL DEFAULT '0g',
    is_valid_food BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ingredient_cache_key ON ingredient_cache(ingredient_key);
ALTER TABLE ingredient_cache DISABLE ROW LEVEL SECURITY;

-- 8. Tabela de auditoria de clone interno de usuario
CREATE TABLE IF NOT EXISTS user_clone_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cloned_from UUID NOT NULL,
    cloned_to UUID NOT NULL,
    cloned_by TEXT NOT NULL,
    dry_run BOOLEAN NOT NULL DEFAULT false,
    when_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_user_clone_audit_cloned_from ON user_clone_audit(cloned_from);
CREATE INDEX IF NOT EXISTS idx_user_clone_audit_cloned_to ON user_clone_audit(cloned_to);
CREATE INDEX IF NOT EXISTS idx_user_clone_audit_cloned_by ON user_clone_audit(cloned_by);
CREATE INDEX IF NOT EXISTS idx_user_clone_audit_created_at ON user_clone_audit(created_at DESC);

ALTER TABLE user_clone_audit DISABLE ROW LEVEL SECURITY;

