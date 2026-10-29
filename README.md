# Agentic URL Shortener

This repository contains two services:

- **[url-shortener-service](url-shortener-service/)** — the domain workload: URL creation, owner-scoped URL listing/details/deletion, public redirects, click analytics, rate limiting, and a database circuit breaker, backed by PostgreSQL and Redis.
- **[agentic-orchestration-service](agentic-orchestration-service/)** — the agentic SDLC system: a supervisor Orchestrator executing an explicit dependency graph of six specialized agents (Requirement → Design → Implementation → Test → Review → Documentation) with entry/exit gates, parallel branches with synchronization, bounded retries/fallback/rollback, human approval checkpoints, policy guardrails, immutable artifact versioning, audit-grade decision lineage, and dynamic re-planning.

## Requirements

- Java 21
- Maven 3.9+
- Docker Compose

## Run the URL service

1. Copy `.env.example` to `.env` and replace `JWT_SECRET` with a random value of at least 32 bytes. Set the values in your shell before starting the application; Spring Boot does not automatically load `.env`.
2. Start the local dependencies with `docker compose up -d postgres redis`.
3. From the repository root, start the service with `mvn -pl url-shortener-service spring-boot:run`. Flyway creates the schema on startup.
4. While the service is running, provision the UUID used as the JWT `sub` in PostgreSQL (the database's `users` table is owned by the upstream identity provider):

   ```sql
   INSERT INTO users (id, username, email)
   VALUES ('<JWT_SUBJECT_UUID>', 'local-user', 'local-user@example.invalid');
   ```

The service listens on `http://localhost:8080`. Flyway creates the initial schema on startup. Supply an HS256 bearer token whose `sub` is a UUID; an optional `roles` claim can contain `USER` or `ADMIN`. `GET /actuator/health` and the short-code redirect are public; URL management endpoints require a valid token.

On PowerShell, environment variables can be loaded for the current terminal with:

```powershell
Get-Content .env | Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    Set-Item -Path "Env:$name" -Value $value
}
```

## Current API slice

- `POST /api/v1/urls` — create a short URL (supports an optional alias and expiry; rate limited per user)
- `GET /api/v1/urls` — list the authenticated owner's URLs
- `GET /api/v1/urls/{shortCode}` — retrieve one of the owner's URLs
- `DELETE /api/v1/urls/{shortCode}` — delete one of the owner's URLs
- `GET /api/v1/urls/{shortCode}/stats` — click analytics: totals, daily counts, top referrers
- `GET /{shortCode}` — public HTTP 302 redirect; expired links return 410; clicks are recorded asynchronously

Redis provides atomic Base62 sequence allocation, redirect caching with expiry-aware TTLs, and the fixed-window rate limiter. A Resilience4j circuit breaker on the redirect database path degrades to cache-only (503 on cache miss) when the database is unhealthy.

## Run the Agentic Orchestration service

1. Start the local dependencies with `docker compose up -d postgres redis` (shares the PostgreSQL instance with the URL service; the orchestration schema is isolated under the `orchestration` schema).
2. From the repository root, start the service with `mvn -pl agentic-orchestration-service spring-boot:run`. Flyway creates the orchestration schema on startup. The service listens on `http://localhost:8081`.
3. By default the service runs in **stub LLM mode** — deterministic, keyless agent outputs so the full pipeline is runnable and testable without credentials. To use a real model, set `ORCHESTRATOR_LLM_MODE=openai` and `OPENAI_API_KEY=<key>`.
4. Optionally set `ORCHESTRATOR_MAVEN_ENABLED=true` to make the Build/Test gates execute real `mvn compile` / `mvn test` against the URL service codebase.

### Orchestration API

- `POST /api/v1/pipelines` — start a pipeline run (`{"requirementText": "...", "scenarioType": "GREENFIELD|BROWNFIELD|AMBIGUOUS"}`)
- `GET /api/v1/pipelines/{runId}` — run status, stage statuses, attempts, fallback usage
- `GET /api/v1/pipelines/{runId}/context` — immutable, versioned artifacts (requirement spec, design spec, manifests, reports)
- `GET /api/v1/pipelines/{runId}/decisions` — audit-grade decision lineage (orchestrator, agents, gates, humans)
- `GET /api/v1/approvals` / `POST /api/v1/approvals/{id}/decision` — human approval checkpoints
- `POST /api/v1/pipelines/{runId}/replan` — replace an upstream artifact; downstream stages re-execute
- `POST /api/v1/pipelines/{runId}/cancel` — safe-stop

Generated outputs (sources, tests, DESIGN.md, RUN-SUMMARY.md) land in a sandboxed `runs/{runId}/` workspace — promotion into the live codebase is an explicit human decision.

## Scenarios

Three executable scenario walkthroughs (greenfield, brownfield, ambiguous) plus failure-injection demos for retry/fallback/rollback/policy guardrails live in [scenarios/](scenarios/README.md).

## Architecture, gaps, and design docs

- [AGENTIC-GAP-ANALYSIS-AND-PLAN.md](AGENTIC-GAP-ANALYSIS-AND-PLAN.md) — assessment gap analysis and implementation plan
- [FINAL-ENGINEERING-SUMMARY.md](FINAL-ENGINEERING-SUMMARY.md) — plan/rationale, artifacts, risks, trade-offs, assumptions, limitations
- [docs/](docs/README.md) — full design documentation (architecture, data model, API spec, testing strategy, risks)

Kafka-based analytics streaming, Prometheus/Grafana observability, and the remaining governance metrics are documented follow-ups in [docs/11-implementation-tasks.md](docs/11-implementation-tasks.md).
