# 09 — Observability

## 1. Principles

Observability is not optional — it is a first-class concern. Every agent action, pipeline stage transition, gate evaluation, retry, fallback, and human decision is visible, queryable, and alertable.

The three pillars:
- **Metrics** — quantitative measurements over time (Micrometer → Prometheus → Grafana)
- **Traces** — end-to-end request correlation (OpenTelemetry → Jaeger or Tempo)
- **Logs** — structured, contextual event records (SLF4J/Logback → JSON → Loki or ELK)

---

## 2. URL Shortener Service Metrics

### 2.1 Business Metrics

| Metric Name | Type | Description |
|-------------|------|-------------|
| `url.created.total` | Counter | Total short URLs created |
| `url.redirects.total` | Counter | Total redirect requests |
| `url.redirects.cache_hit` | Counter | Redirects served from Redis cache |
| `url.redirects.cache_miss` | Counter | Redirects that hit the database |
| `url.expired.total` | Counter | Requests hitting expired URLs (410) |
| `url.blocked.total` | Counter | URLs rejected by safety check |
| `url.click_events.published` | Counter | Analytics events published to Kafka |

### 2.2 Performance Metrics

| Metric Name | Type | Description |
|-------------|------|-------------|
| `http.server.requests` | Timer | Spring Boot auto-metric: latency by endpoint |
| `url.redirect.latency` | Timer | Redirect-specific latency (p50, p95, p99) |
| `url.creation.latency` | Timer | URL creation latency |
| `db.pool.active` | Gauge | Active DB connections |
| `redis.commands.latency` | Timer | Redis command latency |

### 2.3 Reliability Metrics

| Metric Name | Type | Description |
|-------------|------|-------------|
| `resilience4j.circuitbreaker.state` | Gauge | Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN) |
| `resilience4j.circuitbreaker.calls` | Counter | Circuit breaker call outcomes |
| `kafka.producer.record_error_total` | Counter | Failed Kafka publishes |

---

## 3. Agent Pipeline Metrics

### 3.1 Pipeline Reliability (Critical — per assessment requirements)

| Metric Name | Type | Description |
|-------------|------|-------------|
| `pipeline.runs.started.total` | Counter | Total pipeline runs started |
| `pipeline.runs.completed.total` | Counter | Completed (success) |
| `pipeline.runs.failed.total` | Counter | Failed (after max retries) |
| `pipeline.success.rate` | Gauge | Completed / (Completed + Failed) rolling 7-day |
| `pipeline.e2e.duration` | Timer | End-to-end pipeline duration |
| `pipeline.stage.duration` | Timer | Per-stage duration (tagged by agent_type) |
| `pipeline.retries.total` | Counter | Total agent retries (tagged by agent_type) |
| `pipeline.rollbacks.total` | Counter | Total rollbacks triggered |
| `pipeline.mttr.seconds` | Gauge | Mean Time to Recovery (avg time from FAILED to restarted COMPLETED) |

### 3.2 LLM / Spring AI Metrics

| Metric Name | Type | Description |
|-------------|------|-------------|
| `llm.requests.total` | Counter | Total LLM API calls |
| `llm.tokens.input` | Counter | Input tokens consumed |
| `llm.tokens.output` | Counter | Output tokens generated |
| `llm.latency` | Timer | LLM response latency |
| `llm.errors.total` | Counter | LLM API errors (tagged by error type) |
| `llm.cost.estimate` | Gauge | Estimated cost in USD (computed from token counts) |

### 3.3 Gate Evaluation Metrics

| Metric Name | Type | Description |
|-------------|------|-------------|
| `gate.evaluations.total` | Counter | Total gate evaluations (tagged by gate_type, result) |
| `gate.failures.total` | Counter | Gate evaluation failures that blocked pipeline progress |
| `approval.pending.count` | Gauge | Currently pending human approvals |
| `approval.response.duration` | Timer | Time for human to respond to approval request |

---

## 4. Structured Logging

All logs are JSON-structured with consistent fields:

```json
{
  "timestamp": "2026-10-28T12:00:42.123Z",
  "level": "INFO",
  "logger": "c.s.agent.RequirementAgent",
  "message": "Requirement spec produced successfully",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7",
  "pipelineRunId": "550e8400-e29b-41d4-a716-446655440000",
  "agentType": "REQUIREMENT",
  "taskId": "abc-123",
  "durationMs": 2340,
  "ambiguityScore": 0.12,
  "outputTokens": 847
}
```

**MDC (Mapped Diagnostic Context) fields propagated on every log line:**
- `traceId` — OpenTelemetry trace ID
- `pipelineRunId` — current pipeline run (agents)
- `shortCode` — current short code being processed (URL service)
- `userId` — authenticated user

---

## 5. Distributed Tracing

**Framework:** Spring Boot Actuator + Micrometer Tracing + OpenTelemetry

Traces span across:
- API Gateway → URL Service → PostgreSQL (via JDBC instrumentation)
- API Gateway → URL Service → Redis (via Lettuce instrumentation)
- URL Service → Kafka (via Kafka instrumentation)
- Orchestrator Service → Agent → LLM API → Tool calls

Every redirect request has a full trace from client entry to DB/cache return.

---

## 6. Alerting Rules (Prometheus Alertmanager)

| Alert | Condition | Severity | Action |
|-------|-----------|----------|--------|
| High redirect error rate | `rate(http.server.requests{status=~"5..",uri="/{shortCode}"}[5m]) > 0.01` | CRITICAL | Page on-call |
| Redirect p99 > 100ms | `histogram_quantile(0.99, ...) > 0.1` | WARNING | Notify team |
| Circuit breaker open | `circuitbreaker.state == 1` | CRITICAL | Page on-call |
| Pipeline success rate < 90% | `pipeline.success.rate < 0.9` | WARNING | Notify agent-ops |
| Approval pending > 30min | `time() - approval.requested_at > 1800` | WARNING | Re-notify approver |
| LLM error rate > 10% | `rate(llm.errors.total[10m]) > 0.1` | WARNING | Notify LLM team |
| Agent retry storm | `rate(pipeline.retries.total[5m]) > 5` | WARNING | Investigate agent |

---

## 7. Grafana Dashboards

### Dashboard 1: URL Shortener Operational
- Redirect RPS (requests per second)
- Redirect latency (p50, p95, p99) — time series
- Cache hit ratio — gauge
- Error rate by status code — stacked bar
- Top 10 most clicked URLs — table
- Circuit breaker state — status panel

### Dashboard 2: Agent Pipeline Health
- Pipeline runs today: total / completed / failed — stat panels
- Pipeline success rate (7-day rolling) — gauge
- Active pipelines — table with stage breakdown
- Pending approvals — table with age
- Retries by agent type — bar chart
- End-to-end pipeline duration — histogram
- LLM cost estimate (daily) — stat panel

### Dashboard 3: Audit & Compliance
- Decision log volume by type — time series
- Human approval response times — histogram
- Policy guardrail violations by type — bar chart
- Rollbacks per day — time series

---

## 8. Audit Log Integrity

The `decision_records` table is append-only (no UPDATE/DELETE permissions for the application user). A periodic job computes a SHA-256 hash chain over sequential records per `pipeline_run_id`, storing the hash in a separate `audit_checksum` table. This makes tampering detectable.

For production, consider shipping audit logs to an immutable log service (AWS CloudTrail, Azure Audit Logs, Splunk) as a secondary store.
