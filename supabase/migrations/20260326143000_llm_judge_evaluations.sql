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
