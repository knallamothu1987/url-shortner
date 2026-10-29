CREATE TABLE decision_records (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES pipeline_runs(id),
    stage_name VARCHAR(100),
    actor VARCHAR(20) NOT NULL,
    decision_type VARCHAR(50) NOT NULL,
    rationale TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_decision_records_run ON decision_records(run_id, created_at);
