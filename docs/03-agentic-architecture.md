# 03 — Agentic Architecture

## 1. Design Philosophy

The agentic system is built on the **Supervisor + Specialized Worker** pattern — one of the most proven patterns in multi-agent systems. Rather than a flat peer-to-peer mesh, a central Orchestrator holds the DAG, manages state, enforces gates, and delegates work to domain-specialized agents.

This avoids:
- Unbounded autonomy (agents acting without oversight)
- Context drift (each agent independently interpreting history)
- Cascading failures (no retry/rollback coordination)

---

## 2. Agent Roles

### 2.1 Orchestrator (Supervisor Agent)

**Responsibility:** Own and execute the SDLC pipeline DAG. Not a domain expert — delegates everything.

**Capabilities:**
- Parse and maintain the task DAG
- Resolve agent dependencies and sequencing
- Evaluate entry/exit gates at each node
- Trigger human approval checkpoints
- Handle retries, fallbacks, and rollbacks
- Persist pipeline state in the Context Store
- Emit pipeline events to Kafka for observability

**Spring AI Pattern:** `ChatClient` with a system prompt defining its orchestration role + `FunctionCallbackWrapper` for tool invocations (gate evaluation, state reads/writes).

---

### 2.2 RequirementAgent

**Responsibility:** Interpret raw requirement text, identify ambiguities, normalize into a structured engineering specification.

**Input:** Free-form requirement text (user story, ticket, email)  
**Output:** Structured `RequirementSpec` (JSON) with:
- Clarified intent
- Functional breakdown
- Identified ambiguities with proposed resolutions
- Suggested acceptance criteria
- Dependency hints for downstream agents

**Entry Gate:** Input text is non-empty and within token budget.  
**Exit Gate:** `RequirementSpec` validates against JSON schema; no unresolved ambiguities flagged as BLOCKER.

---

### 2.3 DesignAgent

**Responsibility:** Produce architecture and API design artifacts from the `RequirementSpec`.

**Input:** `RequirementSpec`  
**Output:**
- `DesignSpec` (component diagram description, tech decisions, API contracts)
- OpenAPI 3.1 YAML fragment
- Database schema changes (if any)
- ADR (Architecture Decision Record) for significant choices

**Entry Gate:** Valid `RequirementSpec` with no BLOCKER ambiguities.  
**Exit Gate:** OpenAPI fragment is syntactically valid; ADR is present for each non-trivial choice.

---

### 2.4 ImplementationAgent

**Responsibility:** Generate production-quality Java/Spring Boot code based on `DesignSpec`.

**Input:** `DesignSpec` + existing codebase context  
**Output:**
- Java source files (entities, repositories, services, controllers)
- Maven `pom.xml` dependency additions (if needed)
- Database migration scripts (Flyway)
- Configuration properties

**Entry Gate:** `DesignSpec` exit gate passed; human approval given (for schema migrations).  
**Exit Gate:** Code compiles; static analysis passes (PMD/SpotBugs/Checkstyle); no secrets in code.

**Spring AI Pattern:** `ChatClient` with RAG over existing codebase (using `VectorStore` for code context retrieval) to ensure generated code matches project conventions.

---

### 2.5 TestAgent

**Responsibility:** Generate and execute unit and integration tests for the generated code.

**Input:** Generated source files + `RequirementSpec` (for acceptance criteria)  
**Output:**
- JUnit 5 unit test files
- Testcontainers-based integration tests
- Test execution report (pass/fail, coverage %)

**Entry Gate:** `ImplementationAgent` exit gate passed; generated code compiles.  
**Exit Gate:** All tests pass; coverage ≥ 80%; no test skips without documented reason.

---

### 2.6 ReviewAgent

**Responsibility:** Perform automated code review — security, style, performance, maintainability.

**Input:** Generated source files + test files  
**Output:**
- `ReviewReport` (list of findings with severity: BLOCKER / MAJOR / MINOR / INFO)
- Suggested fixes for BLOCKER and MAJOR findings
- Security scan results (OWASP dependency check, code patterns)

**Entry Gate:** `TestAgent` exit gate passed.  
**Exit Gate:** No BLOCKER findings; MAJOR findings have accepted mitigations.

**Human Approval Gate:** If any MAJOR finding cannot be auto-resolved, pipeline pauses for human review.

---

### 2.7 DocumentationAgent

**Responsibility:** Generate and update documentation artifacts.

**Input:** `DesignSpec` + `ReviewReport` + source files  
**Output:**
- Updated OpenAPI spec
- README section updates
- ADR updates
- Changelog entry

**Entry Gate:** `ReviewAgent` exit gate passed.  
**Exit Gate:** OpenAPI spec validates against 3.1 schema; no broken internal links.

---

### 2.8 ObservabilityAgent

**Responsibility:** Monitor the agent pipeline itself — reliability metrics, anomaly detection, reporting.

**Input:** Pipeline execution events (from Kafka)  
**Output:**
- Pipeline health dashboard data
- Alerts on retry storms, stage failures, SLA breaches
- MTTR, success rate, end-to-end latency reports

**Pattern:** Event-driven; runs continuously alongside the pipeline, not sequentially in the DAG.

---

## 3. Orchestration Model

### 3.1 DAG Structure

```
                     ┌─────────────────┐
                     │  RequirementAgent│
                     └────────┬────────┘
                              │ (exit gate: RequirementSpec valid)
                              ▼
                     ┌─────────────────┐
                     │   DesignAgent   │
                     └────────┬────────┘
                              │ (exit gate: DesignSpec valid)
                              │ (human gate: schema migration approval)
                              ▼
              ┌───────────────┴───────────────┐
              │                               │
    ┌─────────┴──────────┐         ┌──────────┴──────────┐
    │ ImplementationAgent│         │  DocumentationAgent  │ (partial, design docs)
    └─────────┬──────────┘         └──────────┬──────────┘
              │ (exit gate: compiles, lint pass)          │
              ▼                               │
     ┌────────────────┐                       │
     │   TestAgent    │                       │
     └────────┬───────┘                       │
              │ (exit gate: tests pass, cov≥80%)         │
              ▼                               │
     ┌────────────────┐                       │
     │  ReviewAgent   │                       │
     └────────┬───────┘                       │
              │ (exit gate: no BLOCKERs)                 │
              │ (human gate: MAJOR findings)             │
              └───────────────┬───────────────┘
                              ▼
                   ┌──────────────────┐
                   │DocumentationAgent│ (final docs)
                   └──────────────────┘
```

### 3.2 Execution Modes

**Sequential path:** Each stage waits for predecessor. Used when stages have hard data dependencies.

**Parallel paths:** Independent work runs concurrently. Example: `DocumentationAgent` (partial, design-level docs) and `ImplementationAgent` run in parallel after `DesignAgent` completes.

**Synchronization points:** Parallel branches merge at defined sync nodes before continuing.

---

### 3.3 Entry and Exit Gates

Gates are evaluated by the Orchestrator using a `GateEvaluator` service:

```java
public interface GateEvaluator {
    GateResult evaluate(GateDefinition gate, PipelineContext context);
}
```

Gate types:
- **SchemaGate** — validates a JSON artifact against a JSON Schema
- **BuildGate** — runs `mvn compile` and checks exit code
- **TestGate** — runs `mvn test` and checks coverage report
- **ApprovalGate** — polls the `ApprovalStore` for a human decision
- **PolicyGate** — checks policy rules (no secrets, no banned dependencies)

---

### 3.4 Retry, Fallback, and Rollback

```
Stage Execution
      │
      ├── Success → advance to next stage
      │
      ├── Recoverable Failure
      │       └── Retry (max 3, exponential backoff: 5s, 15s, 45s)
      │               └── Still failing → trigger FallbackStrategy
      │
      └── Unrecoverable Failure
              └── Rollback (undo side effects of current stage)
                      └── Notify human operator
                              └── Safe-stop or manual override
```

**Fallback strategies per agent:**
- `RequirementAgent` → return clarification questions to human instead of a spec
- `DesignAgent` → return partial design with gaps marked for human fill-in
- `ImplementationAgent` → return TODO-annotated stubs instead of full implementation
- `TestAgent` → return test skeletons with `@Disabled` and reason
- `ReviewAgent` → downgrade to rule-based static analysis only (no LLM review)

---

### 3.5 Context Store

The shared state is persisted in PostgreSQL (`pipeline_context` table) with Redis caching for active pipelines.

**Schema:**
```
pipeline_run_id  UUID (PK)
stage_name       VARCHAR
input_artifact   JSONB
output_artifact  JSONB
status           ENUM(PENDING, RUNNING, COMPLETED, FAILED, ROLLED_BACK)
decision_log     JSONB[]   -- array of agent decisions with rationale
created_at       TIMESTAMPTZ
updated_at       TIMESTAMPTZ
```

**Key design decisions:**
- Artifacts are stored as immutable snapshots — never overwritten, new version appended
- `decision_log` captures every significant agent choice with rationale for audit replay
- Context is accessible to all agents via `PipelineContextService`

---

### 3.6 Human Approval Checkpoints

Approval gates pause the pipeline and emit a notification (webhook / email / Slack). The operator responds via:
- A REST endpoint: `POST /api/v1/pipeline/{runId}/approve` or `/reject`
- With optional override notes

The pipeline resumes (or terminates) based on the decision, and the decision is recorded in the `decision_log`.

---

### 3.7 Policy Guardrails

Enforced by `PolicyGuardrailService` before any exit gate finalizes:

| Policy | Check |
|--------|-------|
| No hardcoded secrets | Regex scan on all generated files |
| No banned dependencies | Maven dependency checked against blocklist |
| No deprecated APIs | SpotBugs + custom detector |
| No SQL injection risk | SAST pattern scan |
| License compliance | All new deps have approved SPDX licenses |

---

## 4. Spring AI Integration Points

| Agent | Spring AI Feature Used |
|-------|----------------------|
| All agents | `ChatClient` with structured output (`BeanOutputConverter`) |
| ImplementationAgent | `VectorStore` + `QuestionAnswerAdvisor` for RAG over codebase |
| RequirementAgent | `MessageWindowChatMemory` for multi-turn clarification |
| ObservabilityAgent | `ToolCallback` for querying metrics APIs |
| All agents | `RetryTemplate` via Spring Retry (wrapping LLM calls) |
| Orchestrator | Tool calling to invoke agent-specific functions |

---

## 5. Agentic Design Patterns Applied

| Pattern | Where Used |
|---------|-----------|
| **Supervisor / Worker** | Orchestrator delegates to specialized agents |
| **ReAct (Reason + Act)** | Each agent reasons about its task before acting via tool calls |
| **RAG (Retrieval-Augmented Generation)** | ImplementationAgent retrieves code context |
| **Reflection** | ReviewAgent reviews and critiques ImplementationAgent output |
| **Plan-and-Execute** | Orchestrator plans full DAG before execution begins |
| **Human-in-the-Loop** | Approval gates at high-impact decision points |
| **Tool Use** | Agents invoke real tools (compile, test, scan) via Spring AI function calling |
| **Memory** | Context Store provides persistent cross-agent memory |
