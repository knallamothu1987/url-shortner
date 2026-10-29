CREATE TABLE context_artifacts (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES pipeline_runs(id),
    stage_name VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    version INT NOT NULL,
    content TEXT NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (run_id, name, version)
);

CREATE INDEX idx_context_artifacts_run_name ON context_artifacts(run_id, name);
