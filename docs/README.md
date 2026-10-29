# Agentic URL Shortener System — Documentation Index

> Classification: Internal  
> Technology Stack: Java 25, Spring Boot 3.x, Spring AI, PostgreSQL, Redis  
> Assessment: Agentic-Proficient Software Engineer

---

## Document Map

| # | Document | Purpose |
|---|----------|---------|
| 1 | [01-project-overview.md](01-project-overview.md) | High-level vision, goals, and scope |
| 2 | [02-requirements.md](02-requirements.md) | Functional & non-functional requirements |
| 3 | [03-agentic-architecture.md](03-agentic-architecture.md) | Agentic design patterns, orchestration model |
| 4 | [04-system-design.md](04-system-design.md) | Component architecture, data flows, APIs |
| 5 | [05-data-model.md](05-data-model.md) | Database schema, entities, relationships |
| 6 | [06-api-specification.md](06-api-specification.md) | REST API contracts (OpenAPI-style) |
| 7 | [07-scenarios.md](07-scenarios.md) | Greenfield, Brownfield, Ambiguous scenarios |
| 8 | [08-testing-strategy.md](08-testing-strategy.md) | Unit, integration, contract, chaos testing |
| 9 | [09-observability.md](09-observability.md) | Metrics, tracing, audit, alerting |
| 10 | [10-risks-and-tradeoffs.md](10-risks-and-tradeoffs.md) | Risks, mitigations, trade-offs |
| 11 | [11-implementation-tasks.md](11-implementation-tasks.md) | Ordered task breakdown for implementation |
| 12 | [12-orchestration-flow-diagram.md](12-orchestration-flow-diagram.md) | Mermaid flow diagrams of the implemented orchestration service |
| 13 | [13-quickstart-and-prompts.md](13-quickstart-and-prompts.md) | Quickstart: start/test the orchestration service, sample prompts |

---

## Quick Summary

The system is an **Agentic SDLC Orchestration Platform** built around a **URL Shortener** domain.  
The URL Shortener is both the target service to deliver *and* the vehicle to demonstrate:

- Multi-agent orchestration of the full software development lifecycle
- Stateful, non-linear execution with dependency graphs
- Human-in-the-loop approval checkpoints
- Policy guardrails, audit traceability, and reliability controls
- Spring AI as the LLM integration backbone for agent reasoning

The project is structured to show **three execution scenarios** — greenfield, brownfield, and ambiguous — each exercising different parts of the agentic system.
