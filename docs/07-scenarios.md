# 07 — Scenarios

The assessment requires three distinct scenarios that exercise different parts of the agentic system. Each scenario is a pipeline run with a defined input, execution path, and expected outputs.

---

## Scenario 1: Greenfield — Build the URL Shortener from Scratch

**Type:** GREENFIELD  
**Description:** Starting from zero, build the core URL shortener service end-to-end using the agentic pipeline.

### Input to Orchestrator
```
Requirement: "Build a URL shortener service. Users should be able to submit a long URL 
and get back a short URL. Short URLs should redirect to the original. Include basic 
click tracking. The service must be fast (sub-50ms redirect) and handle high traffic."
```

### Agent Execution Path
```
RequirementAgent
  → Interprets the requirement
  → Identifies ambiguities: "What is 'high traffic'? Is auth needed for creation?
    How long should short codes be? Is there an expiry?"
  → Resolves with sensible defaults + flags for human review
  → Output: RequirementSpec (F-01 through F-10 from requirements.md)

DesignAgent
  → Produces: Three-tier architecture (Gateway → Service → DB/Cache)
  → Selects: Spring Boot, PostgreSQL, Redis
  → Output: DesignSpec + OpenAPI stub + Flyway migrations V001-V003

[PARALLEL]
ImplementationAgent                    DocumentationAgent (design phase)
  → Generates:                           → Generates:
    - Url entity (JPA)                     - Architecture overview
    - UrlRepository                        - API docs skeleton
    - UrlShorteningService
    - RedirectService
    - UrlController, RedirectController
    - ShortCodeGenerator (Base62)
    - SecurityConfig, CacheConfig
    - Flyway migration scripts

[SYNC at TestAgent]
TestAgent
  → Generates + runs:
    - UrlShorteningServiceTest (unit)
    - RedirectServiceTest (unit)
    - UrlControllerIT (Testcontainers, PostgreSQL + Redis)
    - RedirectControllerIT
    - ShortCodeGeneratorTest (collision test, 1M iterations)
  → Coverage report: 85%

ReviewAgent
  → Findings:
    - INFO: Consider adding index on urls.created_at for pagination
    - MINOR: RedirectController should log correlation ID
    - MAJOR: Missing rate limiting on redirect endpoint
    → Auto-fix applied: Spring Cloud Gateway rate limit config added
  → Final: No BLOCKERs

DocumentationAgent (final)
  → Updates OpenAPI spec with all endpoints
  → Generates README.md
  → Writes ADR-001: Base62 short code generation chosen over UUID truncation

Pipeline completes: COMPLETED
```

### Key Demonstrations
- Full SDLC from raw requirement to deployable service
- Ambiguity resolution by `RequirementAgent`
- Parallel execution (Implementation + Docs in parallel)
- Auto-fix applied by `ReviewAgent` without human intervention (MAJOR but auto-resolvable)
- Entry/exit gates evaluated at each stage

---

## Scenario 2: Brownfield — Add Analytics Dashboard to Existing Service

**Type:** BROWNFIELD  
**Description:** The URL shortener exists. Add a richer analytics feature: daily/weekly summaries, geographic data, device breakdown, and a CSV export endpoint.

### Input to Orchestrator
```
Requirement: "Add analytics to the URL shortener. I want to see how many clicks 
each short URL gets, broken down by day, country, and device type. Also need to 
export this data to CSV."
```

### Agent Execution Path
```
RequirementAgent
  → Interprets requirement against existing codebase context (RAG over existing code)
  → Identifies: click_events table exists but has no geo/device fields
  → Maps to: A-01 through A-06 from requirements.md
  → Output: RequirementSpec with delta analysis (what exists vs. what needs adding)

DesignAgent
  → Analyzes impact:
    - click_events table needs new columns: country_code, device_type, browser, os
    - New table: analytics_daily_summary
    - New Kafka consumer for async analytics processing
    - New GeoIP + User-Agent parser dependencies
    - New AnalyticsController endpoint
  → [HUMAN APPROVAL GATE] Schema migration modifies existing table — requires operator sign-off
  → Output: DesignSpec with diff (before/after schema), impact assessment

[HUMAN APPROVAL received: "Approved. Ensure migration is backward-compatible."]

[PARALLEL]
ImplementationAgent                     DocumentationAgent (design phase)
  → Generates:                            → Generates:
    - Flyway V004: ALTER TABLE               - Updated API docs
      click_events ADD COLUMNS               - Migration runbook
    - Flyway V005: CREATE TABLE
      analytics_daily_summary
    - AnalyticsConsumer (Kafka)
    - GeoIpService (MaxMind stub)
    - UserAgentParserService
    - AnalyticsSummaryJob (Spring Batch)
    - AnalyticsController
    - CsvExportService

[SYNC]
TestAgent
  → Generates + runs:
    - AnalyticsConsumerTest (unit with embedded Kafka)
    - AnalyticsControllerIT (Testcontainers)
    - CsvExportServiceTest
    - Migration test: applies V004/V005 against test DB, verifies no data loss
  → Coverage: 82%

ReviewAgent
  → Findings:
    - MAJOR: GeoIpService has no fallback when MaxMind API is unavailable
    → Auto-fix: circuit breaker + null-safe fallback added
    - MINOR: CSV export does not stream, loads all rows into memory
    → Flagged for human review (deferred to v2)
  → [HUMAN APPROVAL GATE] MINOR finding deferred — operator acknowledges

DocumentationAgent (final)
  → Changelog entry: "v1.1 — Analytics Dashboard"
  → ADR-002: Async analytics via Kafka to decouple from redirect hot path

Pipeline completes: COMPLETED
```

### Key Demonstrations
- **Codebase reasoning**: `ImplementationAgent` uses RAG to understand existing code before generating new code
- **Impact analysis**: `DesignAgent` identifies all affected modules, not just the obvious ones
- **Schema migration with human gate**: Migration changes require approval
- **Backward-compatible migration**: ALTER TABLE adds nullable columns — no data loss
- **Partial auto-fix + human deferral**: Some findings auto-resolved, one deferred with documentation

---

## Scenario 3: Ambiguous — "Make Short URLs More Reliable"

**Type:** AMBIGUOUS  
**Description:** The requirement is intentionally vague. This scenario demonstrates how the `RequirementAgent` handles ambiguity, generates clarification questions, and proceeds with conservative defaults when answers aren't immediately available.

### Input to Orchestrator
```
Requirement: "Make the short URL service more reliable. Users are complaining that 
sometimes it doesn't work."
```

### Agent Execution Path

```
RequirementAgent
  → Detects HIGH ambiguity score: 0.87/1.0
  → Generates clarification questions:
    1. "What specific failures are users experiencing? (404s, slow redirects, 
        errors during creation?)"
    2. "Is this a new problem or has it always occurred? Any specific time patterns?"
    3. "What is the current uptime? What is the target uptime?"
    4. "Are there specific geographies or user segments affected?"
  → No human response received within timeout (5 minutes demo mode)
  → Proceeds with conservative interpretation:
    - "Reliability" → availability, error rate reduction, latency improvement
    - Applies known reliability patterns: circuit breaker, retry, health checks, 
      graceful degradation, rate limiting tightening
  → Output: RequirementSpec marked as INFERRED with confidence scores per item
  → Decision recorded: "Proceeded with inferred requirements; human should validate
    before production deployment"

DesignAgent
  → Maps inferred requirements to concrete changes:
    1. Add circuit breaker on PostgreSQL calls (Resilience4j)
    2. Redis fallback: serve cached URL even if DB is down
    3. Add liveness + readiness probes
    4. Tighten rate limits to prevent abuse causing degraded service
    5. Add structured error logging with correlation IDs
    6. Add retry on Kafka publish failures
  → Flags: "These are conservative reliability improvements. Actual root cause 
    analysis requires production logs."
  → [HUMAN APPROVAL GATE: "Design based on inferred requirements. Please review
    before proceeding."]

[HUMAN APPROVAL received: "Looks good. Proceed. Focus on circuit breaker first."]

ImplementationAgent
  → Applies ordered list (focus = circuit breaker first per human note):
    1. Resilience4j CircuitBreaker on RedirectService DB calls
    2. Redis fallback in RedirectService
    3. Actuator health probes (liveness, readiness)
    4. Structured logging with MDC correlation ID
    5. RetryTemplate on Kafka producer
    6. Rate limit adjustment in Gateway config

TestAgent
  → Generates chaos tests using Testcontainers:
    - DB outage test: PostgreSQL stopped mid-request → verifies Redis fallback
    - Circuit breaker test: DB returns errors → verifies circuit opens after threshold
    - Slow DB test: DB introduces 200ms delay → verifies circuit breaker timeout
    - Retry test: Kafka broker unavailable → verifies retry with backoff
  → Coverage: 83%

ReviewAgent
  → BLOCKER: CircuitBreaker fallback does not handle stale cache entries (expired URL
    served from cache after expiry)
  → Auto-fix: fallback checks `expires_at` field before serving from cache
  → Rerun tests: all pass

DocumentationAgent
  → ADR-003: Circuit Breaker pattern for DB calls
  → Runbook: "Investigating URL Shortener reliability issues"
  → Changelog: "v1.2 — Reliability hardening"

Pipeline completes: COMPLETED
```

### Key Demonstrations
- **Ambiguity detection and handling**: High ambiguity score triggers clarification flow
- **Conservative inference**: Proceeds with sensible defaults, marks decisions as INFERRED
- **Human influence on execution**: Approval note changes implementation priority
- **Chaos/resilience testing**: Tests specifically simulate failure conditions
- **BLOCKER auto-fix with re-run**: ReviewAgent finds critical bug, fixes it, tests re-run
- **Decision lineage**: Every inferred decision is recorded with rationale for future audit

---

## Scenario Summary Comparison

| Dimension | Greenfield | Brownfield | Ambiguous |
|-----------|-----------|-----------|-----------|
| Requirement clarity | High | Medium | Low |
| Ambiguity resolution | Minor | Moderate | Major |
| Codebase reasoning | None (new) | Deep | Deep |
| Human gates triggered | 0 | 2 | 2 |
| Auto-fixes applied | 1 (MAJOR) | 1 (MAJOR) | 1 (BLOCKER) |
| Parallel execution | Yes | Yes | No (sequential, focused) |
| Chaos tests | No | No | Yes |
| Key patterns shown | Full SDLC | Impact analysis, migrations | Inference, resilience |
