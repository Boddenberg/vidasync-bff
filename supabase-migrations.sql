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

-- 9. Tabela de ingestao diaria de agua
CREATE TABLE IF NOT EXISTS water_daily_intake (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    date TEXT NOT NULL,
    goal_ml INTEGER,
    consumed_ml INTEGER NOT NULL DEFAULT 0 CHECK (consumed_ml >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_water_daily_intake_user_date UNIQUE (user_id, date),
    CONSTRAINT ck_water_daily_intake_goal_non_negative CHECK (goal_ml IS NULL OR goal_ml >= 0)
);

CREATE INDEX IF NOT EXISTS idx_water_daily_intake_user_id ON water_daily_intake(user_id);
CREATE INDEX IF NOT EXISTS idx_water_daily_intake_user_date ON water_daily_intake(user_id, date DESC);

CREATE OR REPLACE FUNCTION set_updated_at_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_water_daily_intake_updated_at ON water_daily_intake;
CREATE TRIGGER trg_water_daily_intake_updated_at
BEFORE UPDATE ON water_daily_intake
FOR EACH ROW
EXECUTE FUNCTION set_updated_at_timestamp();

ALTER TABLE water_daily_intake DISABLE ROW LEVEL SECURITY;

-- 10. Tabela de historico de movimentos de agua
CREATE TABLE IF NOT EXISTS water_intake_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    date TEXT NOT NULL,
    delta_ml INTEGER NOT NULL CHECK (delta_ml <> 0),
    event_type TEXT NOT NULL DEFAULT 'ADD',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_water_intake_events_event_type CHECK (event_type IN ('ADD', 'REMOVE', 'ADJUSTMENT'))
);

CREATE INDEX IF NOT EXISTS idx_water_intake_events_user_id ON water_intake_events(user_id);
CREATE INDEX IF NOT EXISTS idx_water_intake_events_user_date ON water_intake_events(user_id, date DESC, created_at ASC);

INSERT INTO water_intake_events (user_id, date, delta_ml, event_type, created_at)
SELECT w.user_id, w.date, w.consumed_ml, 'ADJUSTMENT', w.created_at
FROM water_daily_intake w
WHERE w.consumed_ml > 0
  AND NOT EXISTS (
      SELECT 1
      FROM water_intake_events e
      WHERE e.user_id = w.user_id
        AND e.date = w.date
  );

ALTER TABLE water_intake_events DISABLE ROW LEVEL SECURITY;

-- 11. Tabela de metas nutricionais diarias (calorias e macros)
CREATE TABLE IF NOT EXISTS daily_nutrition_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    date TEXT NOT NULL,
    calories_goal INTEGER,
    protein_goal INTEGER,
    carbs_goal INTEGER,
    fat_goal INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_daily_nutrition_goals_user_date UNIQUE (user_id, date),
    CONSTRAINT ck_daily_nutrition_goals_calories_non_negative CHECK (calories_goal IS NULL OR calories_goal >= 0),
    CONSTRAINT ck_daily_nutrition_goals_protein_non_negative CHECK (protein_goal IS NULL OR protein_goal >= 0),
    CONSTRAINT ck_daily_nutrition_goals_carbs_non_negative CHECK (carbs_goal IS NULL OR carbs_goal >= 0),
    CONSTRAINT ck_daily_nutrition_goals_fat_non_negative CHECK (fat_goal IS NULL OR fat_goal >= 0)
);

CREATE INDEX IF NOT EXISTS idx_daily_nutrition_goals_user_id ON daily_nutrition_goals(user_id);
CREATE INDEX IF NOT EXISTS idx_daily_nutrition_goals_user_date ON daily_nutrition_goals(user_id, date DESC);

DROP TRIGGER IF EXISTS trg_daily_nutrition_goals_updated_at ON daily_nutrition_goals;
CREATE TRIGGER trg_daily_nutrition_goals_updated_at
BEFORE UPDATE ON daily_nutrition_goals
FOR EACH ROW
EXECUTE FUNCTION set_updated_at_timestamp();

ALTER TABLE daily_nutrition_goals DISABLE ROW LEVEL SECURITY;

-- 12. Tabela de historico de peso corporal
CREATE TABLE IF NOT EXISTS weight_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    weight_kg NUMERIC(6,2) NOT NULL CHECK (weight_kg > 0),
    measured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_weight_entries_user_id ON weight_entries(user_id);
CREATE INDEX IF NOT EXISTS idx_weight_entries_user_measured_at ON weight_entries(user_id, measured_at DESC);

ALTER TABLE weight_entries DISABLE ROW LEVEL SECURITY;

-- 13. Tabela de feedbacks para desenvolvedores
CREATE TABLE IF NOT EXISTS developer_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    user_name TEXT NOT NULL,
    message TEXT NOT NULL,
    image_url TEXT,
    status TEXT NOT NULL DEFAULT 'OPEN',
    developer_response TEXT,
    responded_at TIMESTAMPTZ,
    responded_by TEXT,
    response_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_developer_feedback_status CHECK (status IN ('OPEN', 'ANSWERED'))
);

CREATE INDEX IF NOT EXISTS idx_developer_feedback_user_id ON developer_feedback(user_id);
CREATE INDEX IF NOT EXISTS idx_developer_feedback_status ON developer_feedback(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_developer_feedback_created_at ON developer_feedback(created_at DESC);

DROP TRIGGER IF EXISTS trg_developer_feedback_updated_at ON developer_feedback;
CREATE TRIGGER trg_developer_feedback_updated_at
BEFORE UPDATE ON developer_feedback
FOR EACH ROW
EXECUTE FUNCTION set_updated_at_timestamp();

ALTER TABLE developer_feedback DISABLE ROW LEVEL SECURITY;

-- 14. Tabela de notificacoes do app
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    customer_id UUID,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    message TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT 'INFO',
    data JSONB NOT NULL DEFAULT '{}'::jsonb,
    channel TEXT NOT NULL DEFAULT 'push',
    priority TEXT NOT NULL DEFAULT 'normal',
    is_read BOOLEAN NOT NULL DEFAULT false,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    image_url TEXT,
    action_label TEXT,
    action_route TEXT,
    read_at TIMESTAMPTZ,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS user_id UUID;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS customer_id UUID;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS title TEXT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS body TEXT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS message TEXT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS type TEXT NOT NULL DEFAULT 'INFO';
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS data JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS channel TEXT NOT NULL DEFAULT 'push';
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS priority TEXT NOT NULL DEFAULT 'normal';
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS is_read BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS sent_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS image_url TEXT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS action_label TEXT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS action_route TEXT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

UPDATE notifications
SET body = COALESCE(body, message, '')
WHERE body IS NULL;

UPDATE notifications
SET message = COALESCE(message, body, '')
WHERE message IS NULL;

UPDATE notifications
SET data = '{}'::jsonb
WHERE data IS NULL;

UPDATE notifications
SET channel = 'push'
WHERE channel IS NULL;

UPDATE notifications
SET priority = 'normal'
WHERE priority IS NULL;

UPDATE notifications
SET is_read = false
WHERE is_read IS NULL;

UPDATE notifications
SET sent_at = COALESCE(sent_at, created_at, now())
WHERE sent_at IS NULL;

UPDATE notifications
SET is_deleted = false
WHERE is_deleted IS NULL;

UPDATE notifications
SET created_at = now()
WHERE created_at IS NULL;

UPDATE notifications
SET updated_at = COALESCE(updated_at, created_at, now())
WHERE updated_at IS NULL;

ALTER TABLE notifications ALTER COLUMN body SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN message SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN data SET DEFAULT '{}'::jsonb;
ALTER TABLE notifications ALTER COLUMN data SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN channel SET DEFAULT 'push';
ALTER TABLE notifications ALTER COLUMN channel SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN priority SET DEFAULT 'normal';
ALTER TABLE notifications ALTER COLUMN priority SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN is_read SET DEFAULT false;
ALTER TABLE notifications ALTER COLUMN is_read SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN sent_at SET DEFAULT now();
ALTER TABLE notifications ALTER COLUMN sent_at SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN is_deleted SET DEFAULT false;
ALTER TABLE notifications ALTER COLUMN is_deleted SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE notifications ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE notifications ALTER COLUMN updated_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_created_at ON notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_deleted_created_at ON notifications(user_id, is_deleted, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON notifications(user_id, is_deleted, read_at);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread_legacy ON notifications(user_id, is_deleted, is_read, read_at);

DROP TRIGGER IF EXISTS trg_notifications_updated_at ON notifications;
CREATE TRIGGER trg_notifications_updated_at
BEFORE UPDATE ON notifications
FOR EACH ROW
EXECUTE FUNCTION set_updated_at_timestamp();

ALTER TABLE notifications DISABLE ROW LEVEL SECURITY;

-- 15. Tabela de avaliacoes do llm judge
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS public.llm_judge_evaluations (
    evaluation_id TEXT PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now()),
    feature TEXT NOT NULL,
    judge_status TEXT NOT NULL CHECK (judge_status IN ('pending', 'completed', 'failed')),
    request_id TEXT NULL,
    conversation_id TEXT NULL,
    message_id TEXT NULL,
    user_id TEXT NULL,
    idioma TEXT NOT NULL DEFAULT 'pt-BR',
    intencao TEXT NULL,
    pipeline TEXT NULL,
    handler TEXT NULL,
    source_model TEXT NOT NULL,
    source_prompt TEXT NOT NULL,
    source_response TEXT NOT NULL,
    source_duration_ms DOUBLE PRECISION NULL,
    source_prompt_chars INTEGER NOT NULL DEFAULT 0,
    source_response_chars INTEGER NOT NULL DEFAULT 0,
    source_prompt_tokens INTEGER NULL,
    source_completion_tokens INTEGER NULL,
    source_total_tokens INTEGER NULL,
    source_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    judge_model TEXT NULL,
    judge_duration_ms DOUBLE PRECISION NULL,
    judge_prompt_tokens INTEGER NULL,
    judge_completion_tokens INTEGER NULL,
    judge_total_tokens INTEGER NULL,
    judge_overall_score DOUBLE PRECISION NULL,
    judge_decision TEXT NULL CHECK (judge_decision IN ('approved', 'rejected')),
    judge_summary TEXT NULL,
    judge_scores JSONB NOT NULL DEFAULT '{}'::jsonb,
    judge_improvements JSONB NOT NULL DEFAULT '[]'::jsonb,
    judge_rejection_reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    judge_result JSONB NULL,
    judge_error TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_llm_judge_evaluations_created_at
    ON public.llm_judge_evaluations (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_llm_judge_evaluations_feature_created_at
    ON public.llm_judge_evaluations (feature, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_llm_judge_evaluations_judge_status
    ON public.llm_judge_evaluations (judge_status);

CREATE INDEX IF NOT EXISTS idx_llm_judge_evaluations_request_id
    ON public.llm_judge_evaluations (request_id);

CREATE INDEX IF NOT EXISTS idx_llm_judge_evaluations_conversation_id
    ON public.llm_judge_evaluations (conversation_id);

CREATE INDEX IF NOT EXISTS idx_llm_judge_evaluations_message_id
    ON public.llm_judge_evaluations (message_id);

CREATE INDEX IF NOT EXISTS idx_llm_judge_evaluations_pipeline_decision
    ON public.llm_judge_evaluations (pipeline, judge_decision);

-- 16. Telemetria de execucao de agentes e chamadas LLM
CREATE TABLE IF NOT EXISTS public.telemetry_agent_runs (
    run_id TEXT PRIMARY KEY,
    request_id TEXT NOT NULL,
    trace_id TEXT NULL,
    agent TEXT NOT NULL,
    entrypoint TEXT NOT NULL,
    http_method TEXT NOT NULL,
    http_status INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('success', 'error', 'timeout')),
    timeout BOOLEAN NOT NULL DEFAULT false,
    duration_ms DOUBLE PRECISION NOT NULL,
    total_cost_usd DOUBLE PRECISION NOT NULL DEFAULT 0,
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    llm_call_count INTEGER NOT NULL DEFAULT 0,
    tool_call_count INTEGER NOT NULL DEFAULT 0,
    stage_event_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    request_context JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now()),
    finished_at TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_telemetry_agent_runs_started_at
    ON public.telemetry_agent_runs (started_at DESC);

CREATE INDEX IF NOT EXISTS idx_telemetry_agent_runs_agent_started_at
    ON public.telemetry_agent_runs (agent, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_telemetry_agent_runs_status_started_at
    ON public.telemetry_agent_runs (status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_telemetry_agent_runs_trace_id
    ON public.telemetry_agent_runs (trace_id);

CREATE INDEX IF NOT EXISTS idx_telemetry_agent_runs_request_id
    ON public.telemetry_agent_runs (request_id);

ALTER TABLE public.telemetry_agent_runs DISABLE ROW LEVEL SECURITY;

CREATE TABLE IF NOT EXISTS public.telemetry_llm_calls (
    call_id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES public.telemetry_agent_runs(run_id) ON DELETE CASCADE,
    request_id TEXT NOT NULL,
    trace_id TEXT NULL,
    agent TEXT NOT NULL,
    provider TEXT NOT NULL,
    operation TEXT NOT NULL,
    model TEXT NULL,
    status TEXT NOT NULL,
    input_tokens INTEGER NULL,
    output_tokens INTEGER NULL,
    total_tokens INTEGER NULL,
    duration_ms DOUBLE PRECISION NOT NULL,
    cost_usd DOUBLE PRECISION NULL,
    provider_response_id TEXT NULL,
    endpoint TEXT NULL,
    error_message TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now())
);

CREATE INDEX IF NOT EXISTS idx_telemetry_llm_calls_created_at
    ON public.telemetry_llm_calls (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_telemetry_llm_calls_run_id
    ON public.telemetry_llm_calls (run_id);

CREATE INDEX IF NOT EXISTS idx_telemetry_llm_calls_agent_model_created_at
    ON public.telemetry_llm_calls (agent, model, created_at DESC);

ALTER TABLE public.telemetry_llm_calls DISABLE ROW LEVEL SECURITY;

CREATE TABLE IF NOT EXISTS public.telemetry_tool_calls (
    tool_call_id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES public.telemetry_agent_runs(run_id) ON DELETE CASCADE,
    request_id TEXT NOT NULL,
    trace_id TEXT NULL,
    agent TEXT NOT NULL,
    tool_name TEXT NOT NULL,
    status TEXT NOT NULL,
    duration_ms DOUBLE PRECISION NULL,
    error_message TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now())
);

CREATE INDEX IF NOT EXISTS idx_telemetry_tool_calls_created_at
    ON public.telemetry_tool_calls (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_telemetry_tool_calls_run_id
    ON public.telemetry_tool_calls (run_id);

ALTER TABLE public.telemetry_tool_calls DISABLE ROW LEVEL SECURITY;

CREATE TABLE IF NOT EXISTS public.telemetry_stage_events (
    event_id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES public.telemetry_agent_runs(run_id) ON DELETE CASCADE,
    request_id TEXT NOT NULL,
    trace_id TEXT NULL,
    agent TEXT NOT NULL,
    stage TEXT NOT NULL,
    event_type TEXT NOT NULL,
    status TEXT NOT NULL,
    duration_ms DOUBLE PRECISION NULL,
    detail TEXT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now())
);

CREATE INDEX IF NOT EXISTS idx_telemetry_stage_events_created_at
    ON public.telemetry_stage_events (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_telemetry_stage_events_run_id
    ON public.telemetry_stage_events (run_id);

CREATE INDEX IF NOT EXISTS idx_telemetry_stage_events_agent_stage_created_at
    ON public.telemetry_stage_events (agent, stage, created_at DESC);

ALTER TABLE public.telemetry_stage_events DISABLE ROW LEVEL SECURITY;

CREATE OR REPLACE VIEW public.telemetry_agent_runs_daily AS
SELECT
    timezone('utc', started_at)::date AS day_utc,
    agent,
    entrypoint,
    COUNT(*)::INTEGER AS run_count,
    COUNT(*) FILTER (WHERE status = 'success')::INTEGER AS success_count,
    COUNT(*) FILTER (WHERE status = 'error')::INTEGER AS error_count,
    COUNT(*) FILTER (WHERE timeout = true OR status = 'timeout')::INTEGER AS timeout_count,
    ROUND(COALESCE(SUM(total_cost_usd), 0)::numeric, 6)::DOUBLE PRECISION AS total_cost_usd,
    COALESCE(SUM(total_tokens), 0)::INTEGER AS total_tokens,
    ROUND(COALESCE(AVG(duration_ms), 0)::numeric, 2)::DOUBLE PRECISION AS avg_duration_ms,
    ROUND(
        COALESCE(
            (percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) FILTER (WHERE duration_ms IS NOT NULL)),
            0
        )::numeric,
        2
    )::DOUBLE PRECISION AS p95_duration_ms
FROM public.telemetry_agent_runs
GROUP BY 1, 2, 3;

CREATE OR REPLACE VIEW public.telemetry_llm_models_daily AS
SELECT
    timezone('utc', created_at)::date AS day_utc,
    agent,
    COALESCE(NULLIF(model, ''), 'unknown') AS model,
    COUNT(*)::INTEGER AS llm_call_count,
    ROUND(COALESCE(SUM(cost_usd), 0)::numeric, 6)::DOUBLE PRECISION AS total_cost_usd,
    COALESCE(SUM(input_tokens), 0)::INTEGER AS input_tokens,
    COALESCE(SUM(output_tokens), 0)::INTEGER AS output_tokens,
    COALESCE(SUM(total_tokens), 0)::INTEGER AS total_tokens,
    ROUND(COALESCE(AVG(duration_ms), 0)::numeric, 2)::DOUBLE PRECISION AS avg_duration_ms,
    ROUND(
        COALESCE(
            (percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) FILTER (WHERE duration_ms IS NOT NULL)),
            0
        )::numeric,
        2
    )::DOUBLE PRECISION AS p95_duration_ms
FROM public.telemetry_llm_calls
GROUP BY 1, 2, 3;
