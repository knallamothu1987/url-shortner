# 01 — Project Overview

## 1. Purpose

This project delivers two things simultaneously:

1. **A production-grade URL Shortener service** — the domain application with core APIs, analytics, and reliability features.
2. **An Agentic SDLC Orchestration System** — the meta-system that *builds and evolves* the URL Shortener by coordinating AI agents through the full software development lifecycle.

The URL Shortener is not just the deliverable; it is the canvas on which agentic engineering is demonstrated.

---

## 2. Business Context

Short URLs are foundational infrastructure for marketing campaigns, analytics pipelines, QR code generation, and audit trails. The service must be:

- Highly available (multi-region ready)
- Analytically rich (click tracking, geo, device, referrer)
- Secure (abuse prevention, rate limiting, expiry)
- Developer-friendly (REST API, SDKs, webhooks)

---

## 3. Agentic Engineering Vision

The system embodies the **"Agents Execute, Humans Govern"** principle:

```
Human Intent → Requirement Agent → Design Agent → Implementation Agent
     ↑                                                       ↓
Human Approval Gates ←——— Orchestrator ———→ Test Agent → Review Agent
     ↑                                                       ↓
              ←——— Audit Log ←——— Observability Agent ←———
```

Each agent is a Spring AI–powered component with:
- A defined **role** (single responsibility)
- **Entry and exit gates** (preconditions and postconditions)
- **Bounded retries** with fallback and rollback paths
- **Context memory** shared via a cross-agent state store

---

## 4. Technology Choices

| Concern | Technology | Rationale |
|---------|-----------|-----------|
| Application Framework | Spring Boot 3.x (Java 25) | Industry standard, virtual threads (Project Loom), GraalVM-native ready |
| AI / Agent Framework | Spring AI | Native Spring integration, model-agnostic, tool calling, memory |
| LLM Backend | OpenAI GPT-4o / Anthropic Claude (configurable) | Spring AI abstraction means easy swap |
| Relational DB | PostgreSQL 16 | ACID, JSONB for flexible analytics, mature ecosystem |
| Cache / Rate Limit | Redis 7 | Sub-millisecond redirect, atomic counters, TTL support |
| Message Bus | Apache Kafka | Async analytics ingestion, event-driven agent triggers |
| Observability | Micrometer + Prometheus + Grafana | Spring Boot Actuator native, OpenTelemetry traces |
| API Gateway | Spring Cloud Gateway | Rate limiting, auth filter, circuit breaker |
| Security | Spring Security + JWT | Stateless auth, RBAC for admin APIs |
| Testing | JUnit 5, Testcontainers, WireMock, Awaitility | Full-stack test coverage without mocks |
| Build | Maven + Docker + GitHub Actions | Reproducible builds, containerized delivery |

---

## 5. High-Level Scope

### In Scope
- URL shortening core (create, resolve, delete, expire)
- Analytics (click tracking, aggregated stats, time-series)
- Agentic SDLC orchestration (requirement → design → code → test → release)
- Three scenario demonstrations (greenfield, brownfield, ambiguous)
- Human approval checkpoints and audit trail
- Observability dashboard

### Out of Scope (v1)
- Custom domain mapping (BYOD)
- Billing / monetization
- Mobile SDKs
- Multi-tenancy isolation at infrastructure level

---

## 6. Guiding Principles

1. **Agents execute; humans decide** — no agent takes a high-impact action without an approval gate.
2. **Observability first** — every agent action is traced, logged, and metered.
3. **Fail safely** — bounded retries, fallback paths, and safe-stop controls prevent runaway execution.
4. **Context continuity** — agents share a persistent state store so context is never lost between stages.
5. **Production parity** — test environments mirror production configuration via Testcontainers.
