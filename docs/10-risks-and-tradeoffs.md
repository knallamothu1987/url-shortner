# 10 — Risks, Trade-offs, and Mitigations

## 1. Technical Risks

### R1 — LLM Non-Determinism

**Risk:** LLM outputs are probabilistic. The same agent prompt may produce structurally different outputs across runs, breaking schema validation gates.

**Likelihood:** High  
**Impact:** Medium (blocked pipeline, not data corruption)

**Mitigation:**
- Use `BeanOutputConverter` (Spring AI) with strict JSON Schema to constrain output format
- Retry with adjusted temperature (lower = more deterministic) on schema gate failure
- Maintain a test suite of representative inputs with expected output schemas
- Pin model version (e.g., `gpt-4o-2024-11-20`) to prevent silent behavior changes from model updates

---

### R2 — LLM Hallucination in Code Generation

**Risk:** `ImplementationAgent` generates code that looks correct but has subtle logic errors — incorrect SQL, wrong Redis key expiry, broken auth logic.

**Likelihood:** Medium  
**Impact:** High (security vulnerability, data loss)

**Mitigation:**
- **Review gate** as a mandatory stage — `ReviewAgent` performs second-pass critique
- **Compilation gate** — code that doesn't compile is immediately rejected
- **Test gate** — tests must pass; TestAgent is prompted to write adversarial tests, not just happy-path
- **Human approval gate** on schema migrations and security-sensitive code changes
- RAG context (existing code) reduces hallucination by grounding generation in proven patterns

---

### R3 — Context Window Exhaustion

**Risk:** For large codebases or complex requirements, the accumulated context (cross-agent state + codebase snippets) may exceed LLM context limits.

**Likelihood:** Medium  
**Impact:** Medium (agent fails mid-pipeline)

**Mitigation:**
- RAG-based context retrieval: only relevant code chunks are passed, not the whole codebase
- `MessageWindowChatMemory` (Spring AI) with configurable window size
- Summarization step: `OrchestratorService` summarizes earlier stage outputs before passing to later agents
- Token budget tracking per agent call — proactively summarize if approaching limit

---

### R4 — Pipeline State Corruption

**Risk:** Partial writes to `pipeline_context` or `agent_tasks` during crash/restart leave the pipeline in an inconsistent state.

**Likelihood:** Low  
**Impact:** High (pipeline stuck, requires manual intervention)

**Mitigation:**
- All context writes within database transactions
- Idempotent agent execution: re-running a stage with the same input produces the same output
- Heartbeat + watchdog: `OrchestratorService` detects stale RUNNING tasks and resets them to PENDING for retry
- Pipeline state machine has explicit recovery transitions (RUNNING → FAILED → RETRYING)

---

### R5 — Redis Cache Poisoning / Stale Data

**Risk:** A deleted or expired URL is still served from Redis cache after DB deletion.

**Likelihood:** Medium  
**Impact:** High (users reach deactivated/malicious URLs)

**Mitigation:**
- On URL delete/deactivate: immediate `Redis DEL shortCode` in the same transaction commit callback
- Circuit breaker fallback double-checks `expires_at` before serving cached entry
- TTL on all cache entries aligned to URL expiry — stale entries auto-expire
- Integration test covering "delete then redirect" flow verifies cache invalidation

---

### R6 — Short Code Collision

**Risk:** Two concurrent URL creation requests receive the same short code.

**Likelihood:** Low (with Base62 counter-based generation)  
**Impact:** High (one URL silently overwrites the other)

**Mitigation:**
- Redis `INCR` for monotonic counter — atomic, collision-free by design
- Database UNIQUE constraint on `short_code` — acts as safety net
- For custom aliases: DB `INSERT ... ON CONFLICT` returns existing record, not a silent overwrite

---

### R7 — Agent Infinite Loop / Runaway Execution

**Risk:** A retry storm or agent feedback loop consumes excessive LLM tokens and costs.

**Likelihood:** Low  
**Impact:** High (cost, availability)

**Mitigation:**
- Hard retry cap: max 3 retries per stage
- Pipeline-level timeout: max 30 minutes end-to-end before auto-cancel
- Cost budget per pipeline run: configurable token budget; agent calls that would exceed it trigger safe-stop
- `ObservabilityAgent` detects retry rate spikes and alerts within 60 seconds

---

### R8 — Kafka Consumer Lag

**Risk:** Analytics Kafka consumer falls behind during traffic spikes, leading to stale click count data.

**Likelihood:** Medium  
**Impact:** Low (analytics are eventually consistent; redirect path is unaffected)

**Mitigation:**
- Consumer group lag monitored via `kafka.consumer.lag` metric
- Alert when lag > 10,000 events
- Auto-scaling consumer instances (future: Kubernetes HPA on lag metric)
- Analytics are explicitly documented as eventually consistent — no user-facing SLA on freshness

---

## 2. Trade-offs

### T1 — Base62 Counter vs. Random Short Codes

**Choice:** Base62 counter-based (`Redis INCR` → encode)  
**Alternative:** Random Base62 with collision check

**Pros of counter-based:**
- Zero collision probability (monotonic)
- No DB round-trip for uniqueness check
- Predictable code length growth

**Cons of counter-based:**
- Codes are sequential — reveals creation volume to competitors
- Redis single point of failure for code generation (mitigated by Redis Sentinel/Cluster)

**Decision:** Counter-based for v1 due to simplicity and performance. Randomized generation can be added as a configurable option in v2.

---

### T2 — Synchronous Redirect vs. Async Analytics

**Choice:** Redirect is fully synchronous; analytics event published asynchronously to Kafka

**Pros:**
- Redirect p99 unaffected by analytics write latency
- Kafka provides durable event buffer during consumer downtime

**Cons:**
- Analytics are eventually consistent (seconds to minutes behind real time)
- Kafka is an operational dependency

**Decision:** Acceptable trade-off. Analytics dashboards are not real-time SLA-critical. Redirect is SLA-critical.

---

### T3 — Monolith vs. Microservices

**Choice:** Two deployable services (URL Shortener + Agentic Orchestration)  
**Alternative:** Single monolith or full microservices (5+ services)

**Pros of two-service split:**
- Clear separation of URL domain and SDLC automation domain
- Independent scaling (URL service needs high throughput; orchestration service needs less)
- Simpler than full microservices for a prototype

**Cons:**
- Inter-service communication adds latency for cases where both are involved
- More complex than a monolith for local dev (mitigated by docker-compose)

**Decision:** Two services strikes the right balance for a prototype demonstrating agentic orchestration without operational complexity of full microservices.

---

### T4 — Spring AI LLM Abstraction vs. Direct OpenAI SDK

**Choice:** Spring AI with model-agnostic abstraction

**Pros:**
- Swap LLM provider without code changes (env var change)
- Native Spring integration (auto-configuration, dependency injection)
- Built-in support for VectorStore, ChatMemory, tool calling, output conversion
- LLM calls mockable via standard Spring test tooling

**Cons:**
- Spring AI is newer; some advanced provider features may lag direct SDK support
- Abstraction adds a thin layer of indirection

**Decision:** Spring AI's benefits far outweigh the cons for a Spring Boot project.

---

## 3. Limitations (v1)

| Limitation | Notes |
|-----------|-------|
| No custom domain support | BYOD planned for v2 |
| Analytics are eventually consistent | By design — see T2 |
| LLM calls require network access | Fully mockable for offline tests |
| Single-region deployment | Multi-region is an infrastructure concern, not an application architecture blocker |
| Agent pipeline not yet self-healing | ObservabilityAgent detects issues but human intervention required for recovery (v2: automated re-planning) |
| Phishing blocklist is stubbed | Integration with Google Safe Browsing API deferred |
| No billing / usage metering | Out of scope for v1 |
