CREATE TABLE IF NOT EXISTS public.telemetry_agent_runs (
    run_id TEXT PRIMARY KEY,
    request_id TEXT NOT NULL,
    trace_id TEXT NULL,
    agent TEXT NOT NULL,
    endpoint TEXT NOT NULL,
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
    endpoint,
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
