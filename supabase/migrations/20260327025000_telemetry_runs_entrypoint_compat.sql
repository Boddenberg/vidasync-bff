DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'telemetry_agent_runs'
          AND column_name = 'endpoint'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'telemetry_agent_runs'
          AND column_name = 'entrypoint'
    ) THEN
        ALTER TABLE public.telemetry_agent_runs RENAME COLUMN endpoint TO entrypoint;
    END IF;
END $$;

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
