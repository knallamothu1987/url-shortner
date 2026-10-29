# 04 — System Design

## 1. Component Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         External Clients                             │
│           (Browser, Mobile, API Consumer, Admin UI)                  │
└─────────────────────────┬───────────────────────────────────────────┘
                          │ HTTPS
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Spring Cloud Gateway                              │
│         (Rate Limiting, JWT Auth Filter, Circuit Breaker)           │
└────────┬──────────────────────────────────────┬─────────────────────┘
         │                                      │
         ▼                                      ▼
┌────────────────────┐               ┌──────────────────────────────┐
│  URL Shortener     │               │  Agentic Orchestration API   │
│  Service           │               │  (Pipeline management,       │
│  (Spring Boot)     │               │   approval endpoints)        │
│                    │               └──────────┬───────────────────┘
│  - UrlController   │                          │
│  - RedirectCtrl    │               ┌──────────▼───────────────────┐
│  - AnalyticsCtrl   │               │     Orchestrator Service     │
│  - AdminController │               │     (DAG + Gate Evaluator)   │
└────────┬───────────┘               └──────────┬───────────────────┘
         │                                      │
         ├─────────┐              ┌─────────────┼──────────────────────┐
         ▼         ▼              ▼             ▼                      ▼
    ┌─────────┐ ┌──────┐  ┌──────────┐  ┌────────────┐  ┌────────────────┐
    │Postgres │ │Redis │  │Requirement│  │DesignAgent │  │Implementation  │
    │  (main) │ │Cache │  │Agent      │  │            │  │Agent           │
    └─────────┘ └──────┘  └──────────┘  └────────────┘  └────────────────┘
         │                      │             │                  │
         ▼                      └─────────────┼──────────────────┘
    ┌─────────┐                               │ (via Spring AI / LLM)
    │ Kafka   │◄──────────────────────────────┘
    │(Events) │
    └────┬────┘
         ▼
    ┌─────────────────┐      ┌──────────────────┐
    │ Analytics       │      │ Observability    │
    │ Consumer        │      │ Agent            │
    │ (async writes)  │      │ (pipeline health)│
    └─────────────────┘      └──────────────────┘
```

---

## 2. URL Shortener Service — Internal Modules

### 2.1 Module Breakdown

```
url-shortener-service/
├── api/                    # REST controllers + DTOs
│   ├── UrlController       POST /api/v1/urls
│   ├── RedirectController  GET  /{shortCode}
│   ├── AnalyticsController GET  /api/v1/urls/{shortCode}/stats
│   └── AdminController     DELETE/PATCH admin endpoints
├── domain/                 # Core domain model (pure Java, no framework deps)
│   ├── Url                 Entity
│   ├── ClickEvent          Entity
│   ├── ShortCodeGenerator  Domain service
│   └── UrlValidator        Domain service
├── application/            # Use cases / application services
│   ├── UrlShorteningService
│   ├── RedirectService
│   └── AnalyticsService
├── infrastructure/         # DB, cache, messaging, external adapters
│   ├── persistence/        JPA repositories (PostgreSQL)
│   ├── cache/              RedisTemplate wrappers
│   ├── messaging/          Kafka producers/consumers
│   └── blocklist/          URL safety check adapter
└── config/                 # Spring configuration
    ├── SecurityConfig
    ├── CacheConfig
    └── KafkaConfig
```

### 2.2 Redirect Flow (Critical Path — must be < 50ms p99)

```
Client GET /{shortCode}
    │
    ▼ Spring Cloud Gateway (rate limit check, no auth)
    │
    ▼ RedirectController
    │
    ▼ RedirectService.resolve(shortCode)
    │
    ├─► Redis GET shortCode              ← Cache HIT → 302 Redirect (< 5ms)
    │
    └─► (Cache MISS)
         │
         ▼ UrlRepository.findByShortCode(shortCode)   ← DB query
         │
         ├── Not found / expired → 404 / 410
         │
         └── Found → Redis SET shortCode (with TTL)
                   → 302 Redirect
                   → Async: publish ClickEvent to Kafka
```

### 2.3 URL Creation Flow

```
Client POST /api/v1/urls
    │
    ▼ JWT validation (Spring Security)
    │
    ▼ UrlController.create(CreateUrlRequest)
    │
    ▼ Input validation (Bean Validation + UrlValidator)
    │
    ▼ UrlShorteningService.shorten(request, userId)
    │
    ├── Check duplicate (same owner + same long URL) → return existing
    │
    ├── Validate URL safety (blocklist check)
    │
    ├── Generate short code (ShortCodeGenerator)
    │   └── Base62 encoding of atomic counter from Redis INCR
    │
    ├── Persist Url entity (PostgreSQL)
    │
    ├── Cache shortCode → longUrl (Redis)
    │
    └── Return CreateUrlResponse (shortUrl, shortCode, expiresAt)
```

---

## 3. Agentic Orchestration Service — Internal Modules

```
agentic-orchestration-service/
├── api/
│   ├── PipelineController      POST /api/v1/pipelines
│   ├── ApprovalController      POST /api/v1/pipeline/{id}/approve
│   └── PipelineStatusController GET /api/v1/pipelines/{id}
├── domain/
│   ├── PipelineRun             Aggregate root
│   ├── AgentTask               Entity (node in DAG)
│   ├── GateDefinition          Value object
│   └── DecisionRecord          Value object
├── application/
│   ├── OrchestratorService     Core orchestration logic
│   ├── GateEvaluatorService    Entry/exit gate evaluation
│   └── ContextStoreService     Shared state read/write
├── agents/
│   ├── RequirementAgent        Spring AI ChatClient wrapper
│   ├── DesignAgent
│   ├── ImplementationAgent
│   ├── TestAgent
│   ├── ReviewAgent
│   ├── DocumentationAgent
│   └── ObservabilityAgent
├── infrastructure/
│   ├── persistence/            PipelineRun, AgentTask repositories
│   ├── llm/                    Spring AI configuration (ChatModel, VectorStore)
│   └── tools/                  Tool implementations (compile, test, scan)
└── config/
    ├── SpringAiConfig
    └── OrchestrationConfig
```

---

## 4. Cross-Cutting Concerns

### 4.1 Authentication & Authorization

- JWT issued by an Auth service (mock/stub in v1)
- Spring Security `OncePerRequestFilter` validates tokens
- Roles: `ROLE_USER`, `ROLE_ADMIN`, `ROLE_AGENT_OPERATOR`
- Agent pipeline management requires `ROLE_AGENT_OPERATOR` or `ROLE_ADMIN`

### 4.2 Caching Strategy

| Data | Cache | TTL | Invalidation |
|------|-------|-----|--------------|
| shortCode → longUrl | Redis String | Equals URL expiry | On URL delete/deactivate |
| Analytics aggregates | Redis Hash | 5 minutes | Time-based |
| Pipeline context | Redis Hash | 24 hours | On pipeline completion |

### 4.3 Event-Driven Design

**Kafka Topics:**

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `url.click.events` | RedirectService | AnalyticsConsumer | Async click tracking |
| `pipeline.stage.events` | OrchestratorService | ObservabilityAgent | Pipeline telemetry |
| `approval.requests` | OrchestratorService | NotificationService | Human gate notifications |

### 4.4 Database Sharding / Scaling Path

- v1: Single PostgreSQL primary + 1 read replica
- v2: Partition `click_events` table by month (PostgreSQL declarative partitioning)
- v3: Citus extension for horizontal sharding if needed

---

## 5. Deployment Architecture (Local Dev)

```yaml
# docker-compose services
services:
  postgres:    image: postgres:16-alpine
  redis:       image: redis:7-alpine
  kafka:       image: confluentinc/cp-kafka:7.6
  zookeeper:   image: confluentinc/cp-zookeeper:7.6
  url-service: build: ./url-shortener-service
  agent-svc:   build: ./agentic-orchestration-service
  prometheus:  image: prom/prometheus
  grafana:     image: grafana/grafana
```

All services communicate on a Docker bridge network. Environment variables are supplied via `.env` file (gitignored template provided as `.env.example`).
