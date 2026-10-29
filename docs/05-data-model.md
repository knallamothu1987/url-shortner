# 05 — Data Model

## 1. URL Shortener — PostgreSQL Schema

### 1.1 `users` table
```sql
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username    VARCHAR(100) UNIQUE NOT NULL,
    email       VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role        VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    active      BOOLEAN NOT NULL DEFAULT TRUE
);
```

### 1.2 `urls` table
```sql
CREATE TABLE urls (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    short_code      VARCHAR(20) UNIQUE NOT NULL,
    long_url        TEXT NOT NULL,
    owner_id        UUID NOT NULL REFERENCES users(id),
    title           VARCHAR(500),                    -- optional user-provided label
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,                     -- NULL = never expires
    active          BOOLEAN NOT NULL DEFAULT TRUE,   -- FALSE = deactivated by admin
    click_count     BIGINT NOT NULL DEFAULT 0,       -- denormalized for fast reads
    custom_alias    BOOLEAN NOT NULL DEFAULT FALSE,  -- TRUE if user provided alias

    CONSTRAINT short_code_format CHECK (short_code ~ '^[A-Za-z0-9_-]{4,20}$')
);

CREATE INDEX idx_urls_short_code ON urls(short_code);
CREATE INDEX idx_urls_owner_id   ON urls(owner_id);
CREATE INDEX idx_urls_expires_at ON urls(expires_at) WHERE expires_at IS NOT NULL;
```

### 1.3 `click_events` table (partitioned by month)
```sql
CREATE TABLE click_events (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    short_code      VARCHAR(20) NOT NULL,
    url_id          UUID NOT NULL REFERENCES urls(id),
    clicked_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_hash         VARCHAR(64),          -- SHA-256 of IP (PII protection)
    user_agent      TEXT,
    referer         TEXT,
    country_code    CHAR(2),
    city            VARCHAR(100),
    device_type     VARCHAR(20),          -- DESKTOP, MOBILE, TABLET, BOT
    browser         VARCHAR(50),
    os              VARCHAR(50)
) PARTITION BY RANGE (clicked_at);

-- Create monthly partitions via Flyway migrations
CREATE TABLE click_events_2026_01 PARTITION OF click_events
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
-- ... additional partitions managed by a partition maintenance job

CREATE INDEX idx_click_events_url_id    ON click_events(url_id, clicked_at);
CREATE INDEX idx_click_events_short_code ON click_events(short_code, clicked_at);
```

### 1.4 `analytics_daily_summary` table (materialized aggregate)
```sql
CREATE TABLE analytics_daily_summary (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url_id          UUID NOT NULL REFERENCES urls(id),
    summary_date    DATE NOT NULL,
    total_clicks    BIGINT NOT NULL DEFAULT 0,
    unique_ips      BIGINT NOT NULL DEFAULT 0,
    top_countries   JSONB,    -- {"US": 120, "GB": 45, ...}
    top_browsers    JSONB,
    top_devices     JSONB,
    computed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(url_id, summary_date)
);

CREATE INDEX idx_analytics_url_date ON analytics_daily_summary(url_id, summary_date DESC);
```

---

## 2. Agentic Orchestration — PostgreSQL Schema

### 2.1 `pipeline_runs` table
```sql
CREATE TABLE pipeline_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    scenario_type   VARCHAR(30) NOT NULL,   -- GREENFIELD, BROWNFIELD, AMBIGUOUS
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING | RUNNING | PAUSED | COMPLETED | FAILED | ROLLED_BACK | CANCELLED
    initiated_by    UUID NOT NULL REFERENCES users(id),
    input_payload   JSONB NOT NULL,         -- original user input
    output_summary  JSONB,                  -- final summary when completed
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    error_message   TEXT
);

CREATE INDEX idx_pipeline_runs_status    ON pipeline_runs(status);
CREATE INDEX idx_pipeline_runs_initiated ON pipeline_runs(initiated_by, created_at DESC);
```

### 2.2 `agent_tasks` table
```sql
CREATE TABLE agent_tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_run_id UUID NOT NULL REFERENCES pipeline_runs(id),
    agent_type      VARCHAR(50) NOT NULL,
    -- REQUIREMENT | DESIGN | IMPLEMENTATION | TEST | REVIEW | DOCUMENTATION | OBSERVABILITY
    task_name       VARCHAR(255) NOT NULL,
    sequence_order  INTEGER NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING | WAITING | RUNNING | COMPLETED | FAILED | SKIPPED | ROLLED_BACK
    depends_on      UUID[],              -- array of upstream task IDs
    input_artifact  JSONB,
    output_artifact JSONB,
    retry_count     INTEGER NOT NULL DEFAULT 0,
    max_retries     INTEGER NOT NULL DEFAULT 3,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    error_details   JSONB

);

CREATE INDEX idx_agent_tasks_pipeline ON agent_tasks(pipeline_run_id, sequence_order);
CREATE INDEX idx_agent_tasks_status   ON agent_tasks(pipeline_run_id, status);
```

### 2.3 `pipeline_context` table
```sql
CREATE TABLE pipeline_context (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_run_id UUID NOT NULL REFERENCES pipeline_runs(id),
    context_key     VARCHAR(255) NOT NULL,
    context_value   JSONB NOT NULL,
    version         INTEGER NOT NULL DEFAULT 1,  -- incremented on update
    set_by_agent    VARCHAR(50) NOT NULL,
    set_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(pipeline_run_id, context_key, version)
);
```

### 2.4 `decision_records` table (immutable audit log)
```sql
CREATE TABLE decision_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_run_id UUID NOT NULL REFERENCES pipeline_runs(id),
    agent_task_id   UUID REFERENCES agent_tasks(id),
    decision_type   VARCHAR(50) NOT NULL,
    -- AGENT_REASONING | GATE_EVALUATION | RETRY_TRIGGER | FALLBACK_TRIGGER
    -- ROLLBACK_TRIGGER | HUMAN_APPROVAL_REQUEST | HUMAN_APPROVAL_RESPONSE | SAFE_STOP
    summary         TEXT NOT NULL,
    details         JSONB NOT NULL,      -- full context of the decision
    decided_by      VARCHAR(100) NOT NULL, -- agent name or user ID
    decided_at      TIMESTAMPTZ NOT NULL DEFAULT now()

    -- No updates or deletes — this table is append-only
);

CREATE INDEX idx_decisions_pipeline ON decision_records(pipeline_run_id, decided_at);
```

### 2.5 `approval_requests` table
```sql
CREATE TABLE approval_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_run_id UUID NOT NULL REFERENCES pipeline_runs(id),
    agent_task_id   UUID NOT NULL REFERENCES agent_tasks(id),
    gate_name       VARCHAR(100) NOT NULL,
    description     TEXT NOT NULL,
    artifacts       JSONB,               -- artifacts requiring review
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING | APPROVED | REJECTED | EXPIRED
    reviewed_by     UUID REFERENCES users(id),
    review_notes    TEXT,
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    reviewed_at     TIMESTAMPTZ
);
```

---

## 3. Entity Relationships

```
users ─────────────────────── urls
  │  1                     M   │
  │                            │
  │                      M click_events
  │
  │ 1                    M
pipeline_runs ─────────── agent_tasks
  │    │                     │
  │    │    M pipeline_context│
  │    │                     │
  │    │ M decision_records ──┘
  │
  └── M approval_requests ─── agent_tasks
```

---

## 4. Redis Data Structures

| Key Pattern | Type | Value | TTL |
|-------------|------|-------|-----|
| `url:redirect:{shortCode}` | String | `longUrl` | URL expiry or 24h default |
| `url:ratelimit:create:{userId}` | String (counter) | integer | 1 hour (sliding window) |
| `url:ratelimit:redirect:{ip}` | String (counter) | integer | 1 second |
| `pipeline:context:{runId}` | Hash | key→JSON | 24 hours |
| `analytics:clicks:{urlId}:{date}` | String (counter) | integer | 7 days |

---

## 5. Database Migration Strategy

- **Tool:** Flyway (integrated with Spring Boot auto-migration on startup)
- **Naming:** `V{version}__{description}.sql` (e.g., `V001__create_users_table.sql`)
- **Schema changes** generated by `DesignAgent` and reviewed through the `ApprovalGate` before being applied
- **Rollback scripts:** Every migration has a corresponding `U{version}__description.sql` undo script
