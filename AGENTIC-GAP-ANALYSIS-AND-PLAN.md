# Agentic Gap Analysis & Implementation Plan

> Assessment source: `Assesment Agentic-Proficient Software Engineer.pdf`
> Date: 2026-10-29
> Status: **IMPLEMENTED** (gaps 1, 2, 3, 6, 7 — all phases except observability stack E1–E4/7.x
> and docker-compose infra extensions, which were explicitly excluded by request).
> See [FINAL-ENGINEERING-SUMMARY.md](FINAL-ENGINEERING-SUMMARY.md) for what was built,
> validation evidence (43 green tests + live E2E runs), and remaining limitations.
> Executable scenario walkthroughs: [scenarios/](scenarios/README.md).

---

## 1. Is this currently an Agentic application?

**No — not yet.** The repository today contains:

| Component | State |
|---|---|
| `url-shortener-service` | Working vertical slice: URL CRUD, redirects, JWT auth, Redis cache, Flyway, 2 unit test classes. **A plain CRUD microservice — nothing agentic about it.** |
| `agentic-orchestration-service` | **Only a `pom.xml`** (Spring AI, Kafka, pgvector deps declared). Zero source code, zero migrations, zero config. |
| `docs/01–11` | Excellent, detailed *design* of the agentic system (agents, DAG, gates, context store, observability, tasks). **Design only — nothing implemented.** |
| `docker-compose.yml` | Only Postgres + Redis. **Missing Kafka, Prometheus, Grafana** referenced by the design. |

The assessment's "Critical Differentiator" (Core Requirement #4 — Workflow Orchestration) is therefore **0% implemented**.

---

## 2. Where does the "agentic" feature come into the picture?

The assessment is **not** asking for the URL shortener itself to be an AI agent. The URL
shortener is the *subject/workload*. The agentic system is a **separate SDLC orchestration
layer** that, given a requirement (e.g., "add link expiry notifications"), autonomously:

1. **RequirementAgent** — interprets intent, flags ambiguities, emits a structured `RequirementSpec`
2. **DesignAgent** — produces `DesignSpec`, OpenAPI fragments, ADRs, impact analysis (brownfield reasoning)
3. **ImplementationAgent** — generates Java/Spring code + Flyway migrations (RAG over the existing codebase)
4. **TestAgent** — generates and *executes* JUnit/Testcontainers tests via real tool calls
5. **ReviewAgent** — automated security/style/perf review (reflection pattern)
6. **DocumentationAgent** — updates OpenAPI/README/changelog
7. **Orchestrator (supervisor)** — drives all of the above through an **explicit dependency graph (DAG)** with:
   - entry/exit gates per stage (schema/build/test/policy/approval gates)
   - sequential + parallel paths with synchronization
   - persistent cross-stage context & decision lineage (audit trail)
   - **human approval checkpoints** for high-impact actions (schema migrations, releases)
   - bounded retries, fallback, rollback, safe-stop
   - policy guardrails (secret scan, banned deps, licenses)
   - reliability metrics: success rate, retry/rollback frequency, MTTR, E2E latency
   - **dynamic re-planning** when upstream artifacts change

That supervisor-driven, stateful, governed execution is what makes it "agentic" (vs. a script
of chained LLM prompts).

---

## 3. Is adding MCP enough? Do we need LangChain/LangGraph?

**MCP alone is NOT enough.** MCP (Model Context Protocol) is just a *tool-exposure protocol* —
it standardizes how an LLM client discovers/calls tools (e.g., "run mvn test", "read file").
It gives you **tool use**, but none of what the assessment grades you on:

| Assessment requirement | Does MCP provide it? |
|---|---|
| Explicit dependency graph (DAG) with gates | ❌ |
| Stateful, non-linear execution; resume/re-plan | ❌ |
| Human-in-the-loop approval checkpoints | ❌ |
| Bounded retries / fallback / rollback / safe-stop | ❌ |
| Cross-stage context store + decision lineage | ❌ |
| Policy guardrails, audit observability, reliability metrics | ❌ |
| Tool invocation (compile, test, scan) | ✅ (this part only) |

So: **real orchestration code must be written.** Three viable implementation routes:

### Option A — Spring AI (what the docs/pom already plan) ✅ RECOMMENDED here
- Single Java/Maven stack; `agentic-orchestration-service` pom already has `spring-ai-starter-model-openai` + pgvector.
- You hand-build the DAG engine (`OrchestratorService`), gates, context store — which is actually a *plus* for this assessment because the orchestration design is the thing being evaluated.
- Tool calling via Spring AI `@Tool`/`FunctionCallback` (compile, test, static analysis, secret scan).

### Option B — LangGraph (Python sidecar service)
- LangGraph natively gives: `StateGraph` (explicit DAG), checkpointing (Postgres saver), `interrupt()` for human-in-the-loop, conditional edges (re-planning), retries.
- Fastest path to the governance features, but introduces a second language/runtime and you'd demo "I used a framework" rather than "I designed the orchestration".

### Option C — Hybrid
- Spring AI orchestrator (Option A) **plus** a thin MCP server exposing the build/test/scan tools, so agents consume tools via a standard protocol. Nice talking point, optional.

**Recommendation:** Option A (stick to the documented Spring AI design), optionally adding an
MCP tool server as a stretch goal. LangChain/LangGraph is *not required* — frameworks are a
means; the evaluation criteria are about the orchestration model, gates, and governance you
can defend.

---

## 4. Gap Matrix vs. Assessment PDF

| # | PDF Requirement | Current State | Gap |
|---|---|---|---|
| 1 | Requirement Understanding (ambiguity → normalized spec) | Docs only | Implement `RequirementAgent` + `RequirementSpec` JSON schema |
| 2 | Task Decomposition w/ dependencies | `docs/11` is a manual decomposition | Agent-generated task graph persisted per pipeline run |
| 3 | Codebase Reasoning (brownfield) | None | RAG (pgvector) over `url-shortener-service` source; impact-analysis output in `DesignSpec` |
| 4 | **Workflow Orchestration (critical)** | **Nothing** | Entire orchestration service: DAG engine, gates, context store, approvals, retry/rollback, re-planning |
| 5 | Engineering Output Generation | URL service partially exists | Agents must *generate* a new feature end-to-end (code+tests+docs) as a demo |
| 6 | Validation & Risk Control | `docs/10` exists | Enforce via `PolicyGuardrailService` + gate evaluators at runtime |
| 7 | Controlled Autonomy (HITL) | None | `ApprovalController` + paused pipeline states + decision log |
| 8 | Final Engineering Summary | None | Auto-generated `RUN-SUMMARY.md` per pipeline run |
| D1 | Working prototype runnable E2E | URL svc runs; orchestration doesn't exist | Build orchestration svc; `docker compose up` must bring up everything |
| D2 | Architecture overview | ✅ `docs/03`, `docs/04` | Keep in sync with implementation |
| D3 | 3 scenarios (greenfield/brownfield/ambiguous) | `docs/07` describes them | Must be *executable* through the pipeline, with recorded runs |
| D4 | Setup instructions | README partial | Add orchestration setup, LLM API key config, demo script |
| D5 | Testing approach/limitations/trade-offs | `docs/08`, `docs/10` | Add orchestration-service tests (unit + Testcontainers IT) |
| — | URL service completeness | No analytics, no rate limiting, no circuit breaker, thin tests | Phase 2 items from `docs/11` (can be the *brownfield scenario* the agents implement!) |
| — | Infra | No Kafka/Prometheus/Grafana in compose | Extend `docker-compose.yml` |

---

## 5. Implementation Steps (ordered, to execute later)

### Phase A — Foundation (infra + skeleton) `[~0.5 day]`
- [ ] A1. Extend `docker-compose.yml`: Kafka (KRaft single node), Prometheus, Grafana; add pgvector-enabled Postgres image (`pgvector/pgvector:pg16`) or second DB for orchestration.
- [ ] A2. Scaffold `agentic-orchestration-service` source tree: `OrchestrationApplication.java`, `application.yml` (port 8081, OpenAI key via env, Flyway, Kafka).
- [ ] A3. Flyway migrations `V101–V105`: `pipeline_runs`, `agent_tasks`, `pipeline_context`, `decision_records`, `approval_requests` (per `docs/05`).
- [ ] A4. JPA entities + repositories for the above; `PipelineContextService` (Postgres + Redis cache, immutable artifact versions, decision log append).

### Phase B — DAG Orchestrator Core (the critical differentiator) `[~1 day]`
- [ ] B1. `PipelineDefinition` model: nodes (agent stages), edges (dependencies), gate bindings — loaded from a declarative YAML/JSON DAG definition (explicit graph, not hardcoded chaining).
- [ ] B2. `OrchestratorService`: topological execution, parallel branches via virtual-thread executor, synchronization joins, stage state machine (PENDING→RUNNING→COMPLETED/FAILED/ROLLED_BACK/AWAITING_APPROVAL).
- [ ] B3. `GateEvaluatorService` + gate types: `SchemaGate` (networknt JSON-schema), `BuildGate` (mvn compile), `TestGate` (surefire+jacoco parse), `PolicyGate`, `ApprovalGate`.
- [ ] B4. Retry/fallback/rollback: bounded retries (3, exp backoff) via Spring Retry; per-agent fallback strategies; rollback hooks (e.g., delete generated files, revert context version); safe-stop flag.
- [ ] B5. Re-planning: on context artifact change (new version of upstream output), invalidate downstream completed stages and re-enqueue — record re-plan decision in `decision_records`.
- [ ] B6. HITL: `ApprovalController` (`POST /api/v1/pipelines/{id}/approvals/{gateId}` approve/reject w/ notes); pipeline pauses at ApprovalGate; decision recorded.
- [ ] B7. `PipelineController`: create run (`POST /api/v1/pipelines` with requirement text + scenario type), get status, get context/lineage, cancel (safe-stop).

### Phase C — Agents (Spring AI) `[~1 day]`
- [ ] C1. Spring AI config: `ChatClient` bean, `BeanOutputConverter` for structured outputs, RetryTemplate around LLM calls, (optional) mock/`stub` ChatModel profile so the pipeline is demoable without an API key.
- [ ] C2. `RequirementAgent` → `RequirementSpec` (JSON schema in `src/main/resources/schemas/`); ambiguity list with severities; BLOCKER ambiguities route to human clarification (ambiguous-scenario support).
- [ ] C3. `DesignAgent` → `DesignSpec` + OpenAPI fragment + ADR + brownfield impact analysis (modules/APIs/data flows affected).
- [ ] C4. RAG: embed `url-shortener-service` sources into pgvector; `ImplementationAgent` uses `QuestionAnswerAdvisor` to generate convention-matching code into a sandboxed workspace dir.
- [ ] C5. `TestAgent`: generate JUnit 5 tests, execute via `TestRunnerTool`, parse results+coverage.
- [ ] C6. `ReviewAgent` (reflection): findings with BLOCKER/MAJOR/MINOR; MAJOR unresolved → ApprovalGate.
- [ ] C7. `DocumentationAgent`: README/OpenAPI/changelog updates + final `RUN-SUMMARY.md` (plan, rationale, artifacts, risks, assumptions, limitations → Core Req #8).

### Phase D — Tools (function calling) `[~0.5 day]`
- [ ] D1. `CompileTool`, `TestRunnerTool` (mvn invoker), `StaticAnalysisTool` (PMD/SpotBugs optional — at minimum compile+test), `SecretScannerTool` (regex), `OpenApiValidatorTool`, `ContextReadTool`/`ContextWriteTool` — registered as Spring AI tools.
- [ ] D2. `PolicyGuardrailService`: secret scan, banned-dependency blocklist, license allowlist — invoked by `PolicyGate` before every exit gate finalizes.
- [ ] D3. (Stretch) Expose the tools additionally via a small MCP server for standard-protocol tool access.

### Phase E — Observability & Governance Evidence `[~0.5 day]`
- [ ] E1. Kafka `pipeline.stage.events` topic; Orchestrator publishes stage transitions; (optional) `ObservabilityAgent` consumer.
- [ ] E2. Micrometer metrics: `pipeline_success_rate`, `stage_retry_total`, `rollback_total`, `pipeline_e2e_latency`, `mttr` → Prometheus endpoint; Grafana dashboard JSON.
- [ ] E3. Structured JSON logs with `pipelineRunId`/`stage` MDC; decision lineage query endpoint (`GET /api/v1/pipelines/{id}/lineage`).
- [ ] E4. Audit integrity: SHA-256 hash chain over `decision_records` (nightly job or on-write).

### Phase F — URL Shortener Completion (used as agent demo workloads) `[~0.5 day]`
- [ ] F1. **Brownfield scenario input:** "Add click analytics" — ideally *executed through the pipeline*: Kafka click-event producer in `RedirectService`, consumer, `click_events` table, `GET /api/v1/urls/{code}/stats`.
- [ ] F2. Rate limiting (Redis token bucket filter) + Resilience4j circuit breaker on DB reads in `RedirectService`.
- [ ] F3. Fill test gaps: `UrlShorteningServiceTest`, `RedirectServiceTest`, Testcontainers ITs.

### Phase G — Scenarios, Docs, Final Polish `[~0.5 day]`
- [ ] G1. Run and record the 3 scenarios (greenfield: new QR-code endpoint or similar; brownfield: F1 analytics; ambiguous: vague requirement → clarification loop). Persist run artifacts under `runs/` as evidence.
- [ ] G2. Update `README.md`: full setup (both services, env vars, LLM key, compose), demo script (curl sequence driving a pipeline incl. an approval).
- [ ] G3. Orchestration-service tests: unit tests for DAG engine/gates/retry logic (deterministic, stub ChatModel), 1–2 Testcontainers ITs.
- [ ] G4. Final engineering summary doc: assumptions, limitations, trade-offs (e.g., stubbed LLM mode, single-node Kafka, no real deploy stage).

---

## 6. Suggested execution order & effort

```
A (infra/skeleton) → B (orchestrator core) → C (agents) ⇄ D (tools)
                                   ↘ E (observability)
F (URL features — run through the pipeline as the brownfield demo)
G (scenarios + docs + tests)
```

Total ≈ 3–4 focused days. If time-boxed to the assessment's 2–3 days, cut: D3 (MCP), E4
(hash chain), parts of E2 (Grafana dashboards), F2 (circuit breaker) — and note them as
documented limitations (which itself earns "engineering judgment" credit).

---

## 7. Key defensible decisions (for the interview)

1. **Hand-rolled DAG engine over LangGraph** — the orchestration model *is* the deliverable; a framework would hide the design. Single JVM stack, simpler ops.
2. **MCP not required** — tool calling via Spring AI suffices; MCP is a protocol choice, not an orchestration capability. Offered as a stretch goal.
3. **Stub ChatModel profile** — pipeline is runnable/testable deterministically without API keys; real LLM is a config switch.
4. **Immutable artifact versions + decision log** — enables audit replay and the re-planning trigger (version change ⇒ downstream invalidation).
5. **Approval gates only on high-impact actions** (schema migrations, unresolved MAJOR review findings) — controlled autonomy, not approval fatigue.
