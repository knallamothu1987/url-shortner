CREATE TABLE approval_requests (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES pipeline_runs(id),
    stage_name VARCHAR(100) NOT NULL,
    gate_name VARCHAR(100) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    notes TEXT,
    decided_by VARCHAR(100),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    decided_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_approval_requests_run ON approval_requests(run_id, status);
