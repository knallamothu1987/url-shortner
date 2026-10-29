# 02 — Requirements

## 1. Functional Requirements

### 1.1 URL Shortener Core

| ID | Requirement | Priority |
|----|-------------|----------|
| F-01 | A user can submit a long URL and receive a unique short code | MUST |
| F-02 | A short URL redirects to the original URL via HTTP 302 | MUST |
| F-03 | Short codes are collision-free, URL-safe, and configurable in length (6–12 chars) | MUST |
| F-04 | A user can provide a custom alias instead of a generated short code | SHOULD |
| F-05 | URLs can have an optional expiry date/time (TTL) | MUST |
| F-06 | Expired URLs return HTTP 410 Gone | MUST |
| F-07 | An authenticated user can delete their own short URLs | MUST |
| F-08 | An admin can deactivate any URL (abuse, compliance) | MUST |
| F-09 | Duplicate long URLs for the same owner return the existing short code | SHOULD |
| F-10 | A user can list all their short URLs with pagination | MUST |

### 1.2 Analytics

| ID | Requirement | Priority |
|----|-------------|----------|
| A-01 | Each redirect event is recorded (timestamp, IP, user-agent, referrer) | MUST |
| A-02 | Click counts are queryable per short URL | MUST |
| A-03 | Aggregated stats available: clicks by hour, day, country, browser, device | SHOULD |
| A-04 | Analytics ingestion is asynchronous (does not slow redirect path) | MUST |
| A-05 | Raw event data is queryable for up to 90 days | SHOULD |
| A-06 | An owner can export analytics as CSV | COULD |

### 1.3 Reliability & Operations

| ID | Requirement | Priority |
|----|-------------|----------|
| R-01 | Redirect latency p99 < 50ms under normal load | MUST |
| R-02 | System supports 10,000 redirect requests/second at peak | SHOULD |
| R-03 | Cache-aside pattern: Redis caches active short code → URL mappings | MUST |
| R-04 | Cache TTL aligns with URL expiry | MUST |
| R-05 | Rate limiting: max 100 URL creations per user per hour | MUST |
| R-06 | Global rate limiting on redirect endpoint: 50 req/s per IP | MUST |
| R-07 | Circuit breaker on DB calls; fallback to cache on DB outage | SHOULD |
| R-08 | Health check endpoints for liveness and readiness probes | MUST |

### 1.4 Security

| ID | Requirement | Priority |
|----|-------------|----------|
| S-01 | URL creation requires authentication (JWT bearer token) | MUST |
| S-02 | Redirect endpoint is public (no auth required) | MUST |
| S-03 | Malicious URL detection: validate against known phishing/malware blocklists | SHOULD |
| S-04 | Input validation: reject non-HTTP/HTTPS URLs | MUST |
| S-05 | Admin operations require ROLE_ADMIN | MUST |
| S-06 | All API traffic served over HTTPS | MUST |
| S-07 | PII (IP addresses) are hashed/anonymized after 30 days | SHOULD |

### 1.5 Agentic SDLC Orchestration

| ID | Requirement | Priority |
|----|-------------|----------|
| AG-01 | A RequirementAgent interprets and normalizes raw requirement text | MUST |
| AG-02 | A DesignAgent produces architecture and API design artifacts | MUST |
| AG-03 | An ImplementationAgent generates production-quality code stubs and implementations | MUST |
| AG-04 | A TestAgent generates and runs unit and integration tests | MUST |
| AG-05 | A ReviewAgent performs code review and security scanning | MUST |
| AG-06 | A DocumentationAgent generates API docs and README updates | SHOULD |
| AG-07 | An ObservabilityAgent monitors agent pipeline health and reports reliability metrics | MUST |
| AG-08 | The Orchestrator manages a DAG (Directed Acyclic Graph) of agent tasks | MUST |
| AG-09 | Human approval gates are enforced before high-impact actions (deploy, schema migration) | MUST |
| AG-10 | Each agent stage supports bounded retries (max 3) with exponential backoff | MUST |
| AG-11 | Fallback and rollback logic is defined per stage | MUST |
| AG-12 | Cross-agent state (context, decision lineage) is persisted in a shared store | MUST |
| AG-13 | Dynamic re-planning when upstream outputs change | SHOULD |
| AG-14 | Safe-stop control: any agent can be paused/cancelled by a human operator | MUST |
| AG-15 | Policy guardrails: agents cannot commit code or deploy without passing all exit gates | MUST |

---

## 2. Non-Functional Requirements

### 2.1 Performance

| NFR | Target |
|-----|--------|
| Redirect latency p50 | < 10ms |
| Redirect latency p99 | < 50ms |
| URL creation latency p99 | < 200ms |
| Analytics query latency p99 | < 500ms |
| Throughput (redirect) | 10,000 RPS sustained |
| Agent pipeline end-to-end | < 5 minutes for a standard task |

### 2.2 Availability & Reliability

| NFR | Target |
|-----|--------|
| URL Shortener service uptime | 99.9% monthly |
| Recovery Time Objective (RTO) | < 5 minutes |
| Recovery Point Objective (RPO) | < 1 minute |
| Agent pipeline success rate | > 95% (measured over rolling 7 days) |

### 2.3 Scalability

- Horizontal scaling of application tier via stateless Spring Boot instances behind a load balancer
- Redis cluster mode for cache scalability
- Kafka partitioning for analytics event throughput
- PostgreSQL read replicas for analytics queries

### 2.4 Maintainability

- Code coverage ≥ 80% (unit + integration)
- All public APIs documented via OpenAPI 3.1
- All agent decisions logged with full context for audit replay
- Architecture Decision Records (ADRs) maintained in `/docs/adr/`

### 2.5 Security & Compliance

- OWASP Top 10 mitigations applied
- Secrets managed via environment variables / Vault (never in code)
- Dependency vulnerability scanning in CI (OWASP Dependency Check)
- Audit log is immutable and append-only

---

## 3. Constraints

- Java 25 (LTS) with Spring Boot 3.x minimum
- Spring AI for all LLM/agent interactions
- Must run locally via `docker-compose` for reviewers
- No proprietary cloud-vendor lock-in (AWS/GCP/Azure services optional, not required)
- All third-party LLM calls must be mockable for offline/test execution

---

## 4. Assumptions

1. The LLM API key is provided via environment variable (`OPENAI_API_KEY` or `ANTHROPIC_API_KEY`).
2. PostgreSQL and Redis are available as Docker services in local dev.
3. Kafka is optional for local dev; an in-memory event bus fallback is acceptable.
4. Custom domain support (BYOD) is deferred to v2.
5. The phishing/malware URL blocklist is sourced from a public feed (e.g., Google Safe Browsing API) — integration is stubbed in v1 with a configurable toggle.

---

## 5. Glossary

| Term | Definition |
|------|-----------|
| Short Code | The unique alphanumeric identifier appended to the base URL (e.g., `abc123`) |
| Short URL | The full shortened URL including base domain (e.g., `https://short.ly/abc123`) |
| Redirect Event | A single click/request on a short URL that resolves to the original URL |
| Agent | An AI-powered software component with a defined role and tool access |
| Orchestrator | The coordinator that manages agent task execution via a dependency graph |
| Entry Gate | Precondition that must pass before an agent stage begins |
| Exit Gate | Postcondition that must pass before an agent stage is marked complete |
| Human Approval Gate | A mandatory pause in pipeline execution requiring explicit human sign-off |
| DAG | Directed Acyclic Graph — the dependency structure governing agent task sequencing |
| Context Store | The shared persistent store holding cross-agent state and decision lineage |
