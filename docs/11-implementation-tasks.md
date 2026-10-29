# 11 — Implementation Tasks

Ordered breakdown for implementation. Dependencies are noted. Tasks within the same phase can run in parallel.

---

## Phase 0: Project Scaffolding

| # | Task | Description | Dependencies |
|---|------|-------------|-------------|
| 0.1 | Create Maven multi-module project | Parent POM + `url-shortener-service` module + `agentic-orchestration-service` module | — |
| 0.2 | Configure `docker-compose.yml` | PostgreSQL 16, Redis 7, Kafka, Zookeeper, Prometheus, Grafana | — |
| 0.3 | Set up CI pipeline | GitHub Actions: build, test, lint, Docker image push | 0.1 |
| 0.4 | Configure base Spring Boot for each module | Spring Boot 3.x, Java 25, virtual threads (`spring.threads.virtual.enabled=true`) | 0.1 |
| 0.5 | Configure Flyway + base migrations | `V001__create_users.sql`, `V002__create_urls.sql`, `V003__create_click_events.sql` | 0.4 |

---

## Phase 1: URL Shortener Service — Core

| # | Task | Description | Dependencies |
|---|------|-------------|-------------|
| 1.1 | Domain model | `Url`, `User`, `ClickEvent` JPA entities | 0.5 |
| 1.2 | `ShortCodeGenerator` | Base62 encoding of Redis `INCR` counter | 0.4 |
| 1.3 | `UrlValidator` | HTTP/HTTPS check, max-length, encoded chars | — |
| 1.4 | JPA repositories | `UrlRepository`, `UserRepository`, `ClickEventRepository` | 1.1 |
| 1.5 | `UrlShorteningService` | Create, duplicate check, custom alias, expiry | 1.2, 1.3, 1.4 |
| 1.6 | `RedirectService` | Cache-aside resolve, expired URL check | 1.4, 1.5 |
| 1.7 | `UrlController` | `POST /api/v1/urls`, `GET /api/v1/urls`, `GET /api/v1/urls/{code}`, `DELETE` | 1.5 |
| 1.8 | `RedirectController` | `GET /{shortCode}` → 302/404/410 | 1.6 |
| 1.9 | Spring Security + JWT filter | Auth config, `JwtAuthFilter`, role-based access | 0.4 |
| 1.10 | Redis cache integration | `RedisTemplate` wrapper, TTL management | 1.6 |
| 1.11 | Rate limiting | Spring Cloud Gateway rate limit filter (Redis token bucket) | 1.10 |
| 1.12 | Resilience4j circuit breaker | On DB calls in `RedirectService` | 1.6 |
| 1.13 | Unit tests — URL core | `ShortCodeGeneratorTest`, `UrlValidatorTest`, `UrlShorteningServiceTest`, `RedirectServiceTest` | 1.5, 1.6 |
| 1.14 | Integration tests — URL core | `UrlShorteningIT`, `RedirectIT` (Testcontainers) | 1.7, 1.8 |

---

## Phase 2: URL Shortener Service — Analytics

| # | Task | Description | Dependencies |
|---|------|-------------|-------------|
| 2.1 | Kafka producer in `RedirectService` | Publish `ClickEvent` after redirect | 1.8 |
| 2.2 | Kafka consumer `AnalyticsConsumer` | Consume `url.click.events`, persist to `click_events` | 2.1 |
| 2.3 | `UserAgentParserService` | Parse UA string to device/browser/OS | — |
| 2.4 | `GeoIpService` | Stub: return null; interface ready for MaxMind | — |
| 2.5 | `AnalyticsService` | Time-series aggregation, top-N queries | 2.2 |
| 2.6 | `AnalyticsSummaryJob` | Spring Batch job: compute daily summary, persist to `analytics_daily_summary` | 2.5 |
| 2.7 | `AnalyticsController` | `GET /api/v1/urls/{code}/stats` | 2.5 |
| 2.8 | Analytics tests | `AnalyticsConsumerTest`, `AnalyticsControllerIT` | 2.7 |

---

## Phase 3: Agentic Orchestration Service — Infrastructure

| # | Task | Description | Dependencies |
|---|------|-------------|-------------|
| 3.1 | Domain model | `PipelineRun`, `AgentTask`, `GateDefinition`, `DecisionRecord` JPA entities | 0.5 |
| 3.2 | Flyway migrations — orchestration | `V101__create_pipeline_runs.sql`, `V102__create_agent_tasks.sql`, `V103__create_pipeline_context.sql`, `V104__create_decision_records.sql`, `V105__create_approval_requests.sql` | 3.1 |
| 3.3 | JPA repositories — orchestration | `PipelineRunRepository`, `AgentTaskRepository`, `DecisionRecordRepository`, `ApprovalRequestRepository` | 3.1 |
| 3.4 | `PipelineContextService` | CRUD for shared context store (Postgres + Redis cache) | 3.3 |
| 3.5 | Spring AI configuration | `ChatModel` bean (OpenAI/Anthropic via config), `VectorStore` (for RAG), `ChatMemory` | 0.4 |

---

## Phase 4: Agentic Orchestration Service — Agents

| # | Task | Description | Dependencies |
|---|------|-------------|-------------|
| 4.1 | `RequirementAgent` | Spring AI `ChatClient`, structured output to `RequirementSpec`, ambiguity detection | 3.5 |
| 4.2 | `DesignAgent` | `ChatClient`, produces `DesignSpec` + OpenAPI fragment + ADR, impact analysis | 3.5, 4.1 |
| 4.3 | `ImplementationAgent` | `ChatClient` + RAG via `VectorStore`, generates Java source files | 3.5, 4.2 |
| 4.4 | `TestAgent` | `ChatClient`, generates JUnit 5 tests, invokes `mvn test` tool | 3.5, 4.3 |
| 4.5 | `ReviewAgent` | `ChatClient` (reflection pattern), invokes static analysis tools, produces `ReviewReport` | 3.5, 4.4 |
| 4.6 | `DocumentationAgent` | `ChatClient`, updates OpenAPI spec, README, changelogs | 3.5, 4.5 |
| 4.7 | `ObservabilityAgent` | Event-driven (Kafka consumer), monitors pipeline events, updates metrics | 3.5, Kafka |

---

## Phase 5: Orchestrator

| # | Task | Description | Dependencies |
|---|------|-------------|-------------|
| 5.1 | `GateEvaluatorService` | `SchemaGate`, `BuildGate`, `TestGate`, `PolicyGate`, `ApprovalGate` implementations | 3.3 |
| 5.2 | `PolicyGuardrailService` | Secret scan, banned dep check, license check | — |
| 5.3 | `OrchestratorService` | DAG parsing, stage execution, parallel task launch (virtual threads), retry/fallback/rollback | 4.1–4.7, 5.1, 5.2 |
| 5.4 | `PipelineController` | `POST /api/v1/pipelines`, `GET /api/v1/pipelines/{id}`, `GET /api/v1/pipelines/{id}/context` | 5.3 |
| 5.5 | `ApprovalController` | `POST /api/v1/pipeline/{id}/approve`, `POST /{id}/cancel` | 5.3 |
| 5.6 | Kafka pipeline events | `OrchestratorService` publishes `pipeline.stage.events` | 5.3 |

---

## Phase 6: Tools for Agents (Function Calling)

| # | Task | Description | Dependencies |
|---|------|-------------|-------------|
| 6.1 | `CompileToolFunction` | Runs `mvn compile` on generated code, returns exit code + errors | — |
| 6.2 | `TestRunnerToolFunction` | Runs `mvn test`, parses Surefire report, returns pass/fail + coverage | — |
| 6.3 | `StaticAnalysisToolFunction` | Runs SpotBugs + PMD, returns findings list | — |
| 6.4 | `SecretScannerToolFunction` | Regex scan on files for credential patterns | — |
| 6.5 | `OpenApiValidatorToolFunction` | Validates YAML against OpenAPI 3.1 schema | — |
| 6.6 | `ContextReadToolFunction` | Reads values from `PipelineContextService` | 3.4 |
| 6.7 | `ContextWriteToolFunction` | Writes values to `PipelineContextService` | 3.4 |

All tool functions are Spring components annotated with `@Bean` and registered as `FunctionCallback` in Spring AI.

---

## Phase 7: Observability & Cross-Cutting

| # | Task | Description | Dependencies |
|---|------|-------------|-------------|
| 7.1 | Micrometer custom metrics | Register all metrics from `09-observability.md` | 1.x, 5.x |
| 7.2 | OpenTelemetry tracing | Configure OTEL exporter (Jaeger/Tempo) | 0.4 |
| 7.3 | Structured JSON logging | Logback config with MDC propagation | 0.4 |
| 7.4 | Prometheus + Grafana setup | `docker-compose` service + dashboard JSON provisioning | 7.1 |
| 7.5 | Alerting rules | `prometheus-alerts.yml` with all rules from `09-observability.md` | 7.4 |
| 7.6 | Audit hash chain job | Nightly job: compute SHA-256 chain over `decision_records` | 3.3 |

---

## Phase 8: Scenarios & Integration

| # | Task | Description | Dependencies |
|---|------|-------------|-------------|
| 8.1 | Scenario 1: Greenfield pipeline run | End-to-end test of full pipeline from blank slate | All phases |
| 8.2 | Scenario 2: Brownfield pipeline run | Analytics addition with schema migration and human approval | All phases |
| 8.3 | Scenario 3: Ambiguous pipeline run | Reliability improvement from vague requirement | All phases |
| 8.4 | Chaos / resilience tests | DB outage, Redis outage, circuit breaker, Kafka down | 1.12, 2.2 |
| 8.5 | Performance test | Gatling redirect throughput test (p99 < 50ms target) | Phase 1 complete |

---

## Phase 9: Documentation & Release Readiness

| # | Task | Description | Dependencies |
|---|------|-------------|-------------|
| 9.1 | `README.md` | Setup instructions, docker-compose quickstart, environment variables | All phases |
| 9.2 | `ARCHITECTURE.md` | Human-readable architecture overview with diagrams | docs/* |
| 9.3 | OpenAPI spec finalization | Complete `openapi.yaml` for both services | Phase 1, 5 |
| 9.4 | ADR collection | Compile all ADRs into `docs/adr/` | Throughout |
| 9.5 | Final engineering summary | Plan/rationale, artifacts inventory, risks/trade-offs, assumptions, limitations | All phases |
| 9.6 | `.env.example` | Template env file with all required variables (no secrets) | All phases |

---

## Estimated Implementation Order (2-3 days)

**Day 1:** Phase 0 → Phase 1 (core URL service + tests)  
**Day 2:** Phase 2 (analytics) + Phase 3-4 (agent infrastructure + agents)  
**Day 3:** Phase 5-6 (orchestrator + tools) + Phase 7 (observability) + Phase 8 (scenarios)  
**Day 3 wrap-up:** Phase 9 (docs + final summary)
