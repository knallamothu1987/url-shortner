CREATE TABLE stage_executions (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES pipeline_runs(id),
    stage_name VARCHAR(100) NOT NULL,
    agent_name VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    last_error TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (run_id, stage_name)
);

CREATE INDEX idx_stage_executions_run ON stage_executions(run_id);
