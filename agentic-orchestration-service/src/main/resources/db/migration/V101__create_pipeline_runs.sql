CREATE TABLE pipeline_runs (
    id UUID PRIMARY KEY,
    requirement_text TEXT NOT NULL,
    scenario_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    safe_stop_requested BOOLEAN NOT NULL DEFAULT FALSE,
    replan_count INT NOT NULL DEFAULT 0,
    failure_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_pipeline_runs_status ON pipeline_runs(status);
