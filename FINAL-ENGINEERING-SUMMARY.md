# Final Engineering Summary

> Assessment: *Build an Agentic Software Engineering System — URL Shortener*
> Date: 2026-10-29

## 1. What was built

Two Spring Boot services in one Maven reactor:

| Service | Port | Role |
|---|---|---|
| `url-shortener-service` | 8080 | The domain workload: URL CRUD, redirects, JWT auth, Redis caching, **click analytics**, **per-user rate limiting**, **database circuit breaker** |
| `agentic-orchestration-service` | 8081 | The agentic SDLC system: supervisor Orchestrator + 6 specialized agents executing an explicit dependency graph with governance |

## 2. Plan & rationale

The assessment's critical differentiator is **workflow orchestration** — not the URL
shortener itself. The design therefore centers on a *supervisor/worker* architecture:

- **Explicit DAG as data** ([pipeline-definition.yml](agentic-orchestration-service/src/main/resources/pipeline-definition.yml)): 7 stages, sequential + parallel paths (`design-docs` runs in parallel with `implementation → testing → review`), with `release-readiness` as the synchronization point. The graph is validated (acyclic, resolvable deps) at startup.
- **Entry/exit gates per stage**: `schema` (JSON Schema), `build`, `test` (coverage ≥ 80%), `openapi`, `policy`, `approval`. Gates are evaluated in order; failures roll the stage back.
- **Specialized agents** (Requirement, Design, Implementation, Test, Review, Documentation) that transform immutable context artifacts. Agents compute deterministic baselines; an LLM (Spring AI/OpenAI) can refine them in `openai` mode — graceful degradation keeps the pipeline runnable keylessly (`stub` mode, the default).
- **Controlled autonomy**: *conditional* human approval checkpoints — clarification (BLOCKER ambiguities), schema-migration (DB changes), major-findings (unresolved MAJOR review findings). No approval fatigue: gates pass silently when the condition is absent.
- **Reliability controls**: bounded retries (3, exponential backoff) for recoverable failures; per-agent degraded fallbacks; rollback hooks that delete generated outputs; safe-stop; pipeline fails closed when gates refuse degraded output (e.g. untested code never passes release readiness).
- **Dynamic re-planning**: replacing an upstream artifact (`POST /replan`) appends a new immutable version and invalidates all transitively dependent stages, which re-execute under the same governance.
- **Audit lineage**: every orchestrator/agent/gate/human decision is an append-only `decision_records` row; artifacts are versioned, never overwritten.

## 3. Artifacts

- Orchestration engine: [OrchestratorService](agentic-orchestration-service/src/main/java/com/shortener/orchestration/orchestrator/OrchestratorService.java), gates, policy guardrails, agents, REST API
- Persistence: Flyway `V101–V105` (isolated `orchestration` schema), JPA store behind a `PipelineStore` interface (in-memory implementation powers deterministic engine tests)
- URL service brownfield additions: `click_events` migration, async `ClickTrackingService`, `AnalyticsService` + `GET /api/v1/urls/{code}/stats`, Redis fixed-window rate limiter, Resilience4j circuit breaker with cache-only degradation
- Executable scenarios + failure-injection demos: [scenarios/](scenarios/README.md)
- Per-run outputs in `runs/{runId}/`: generated sources, tests, `DESIGN.md`, `RUN-SUMMARY.md` (auto-compiled final summary with reliability stats and full lineage)

## 4. Validation

- **43 tests, all green** (`mvn test`): 21 orchestration (11 end-to-end DAG scenarios incl. approvals, retry, fallback, rollback, re-plan, cancellation, lineage; DAG validation; policy/tools) + 22 URL service (shortening, redirect incl. circuit-breaker fallback, analytics, validation, code generation).
- **Live E2E verification** against real PostgreSQL + Redis: all three scenarios (greenfield / brownfield / ambiguous) executed through the running service; brownfield impact analysis correctly identified the real `ClickTrackingService`, `RedirectController`, `V002__create_click_events.sql`, etc.
- Policy guardrails verified: secret leak → stage rolled back and generated files deleted; banned dependency and non-allowlisted license → design blocked.

## 5. Risks & trade-offs

| Decision | Trade-off |
|---|---|
| Hand-rolled DAG engine instead of LangGraph | More code to own, but the orchestration model *is* the deliverable; single JVM stack, fully testable without network |
| Stub LLM mode as default | Deterministic and demoable without keys; heuristic outputs are simpler than real LLM output. `openai` mode is a config switch with graceful fallback |
| Keyword-relevance impact analysis instead of pgvector RAG | No new infrastructure; deterministic and auditable; may miss semantically related files — vector RAG is the documented upgrade path |
| Simulated build/test results when the Maven tool is disabled | Fast demos; `ORCHESTRATOR_MAVEN_ENABLED=true` switches gates to real `mvn compile`/`mvn test` |
| Generated code stays in a sandboxed workspace | Promotion to the live codebase is a human release decision — by design (controlled autonomy) |
| Orchestration API unsecured | Demo scope; production would reuse the URL service's JWT resource-server setup |

## 6. Assumptions

- A single orchestration node; state is persisted (Postgres) but execution is not clustered.
- The `users` table is owned by an upstream identity provider; JWT `sub` is a provisioned UUID.
- Approval gates are the last gates of a stage (enforced by DAG convention).

## 7. Limitations / explicitly out of scope (per instruction)

- **Observability stack** (Kafka pipeline events, Prometheus/Grafana dashboards, MTTR/latency metric exporters, audit hash chain) — excluded gap #4; stage timestamps, attempts, and lineage still provide per-run reliability data.
- **Infrastructure extensions** (Kafka, Prometheus, Grafana, pgvector in docker-compose) — excluded gap #5; analytics is therefore recorded asynchronously in-process rather than via Kafka.
- Coverage ratchet for the orchestration module relaxed to 50% (prototype scope); URL service retains the 80% bar.
