# 13 — Quickstart & Sample Prompts (agentic-orchestration-service)

How to start the orchestration service, drive it with a prompt, and which sample prompts
exercise which governance paths. Flow diagrams: [12-orchestration-flow-diagram.md](12-orchestration-flow-diagram.md).

---

## 1. Start the service

```powershell
# 1. Start infrastructure (Postgres + Redis)
docker compose up -d postgres redis

# 2. Start the orchestration service (from the repository root; requires JDK 21)
mvn -pl agentic-orchestration-service spring-boot:run

# 3. Verify it's up
curl http://localhost:8081/actuator/health   # → {"status":"UP"}
```

**No Docker?** Use the `local` profile, which runs on an embedded in-memory H2 database
(PostgreSQL compatibility mode) and disables the Redis health check — no infrastructure needed:

```powershell
mvn -pl agentic-orchestration-service spring-boot:run "-Dspring-boot.run.profiles=local"
```

If `JAVA_HOME` does not point to a JDK 21, set it first, e.g. on PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
```

**LLM mode:** no API key is needed — the service runs in deterministic **stub LLM mode** by
default, so the full pipeline is runnable and testable offline. For real LLM calls:

```powershell
$env:ORCHESTRATOR_LLM_MODE = 'openai'
$env:OPENAI_API_KEY = 'sk-...'
```

**Real build/test gates (optional):** by default the Build/Test gates validate structurally
and use the simulated test report. To make them execute real Maven against the URL service
codebase:

```powershell
$env:ORCHESTRATOR_MAVEN_ENABLED = 'true'
```

---

## 2. Test it with a prompt

### Option A — scripted scenarios (recommended)

The scripts poll for status and handle the human approval checkpoints automatically:

```powershell
cd scenarios
./run-greenfield.ps1    # happy path, no human checkpoint
./run-brownfield.ps1    # pauses for schema-migration approval, shows impact analysis
./run-ambiguous.ps1     # pauses for clarification of BLOCKER ambiguities
```

### Option B — manual, using the shared helpers

```powershell
cd scenarios; . ./common.ps1

$runId = Start-Pipeline -RequirementText 'YOUR PROMPT HERE' -ScenarioType GREENFIELD
Wait-PipelineStatus -RunId $runId

# If the run paused at a human checkpoint:
Get-PendingApprovals -RunId $runId
Approve-Request -ApprovalId <id> -Notes 'approved after review' -DecidedBy 'you'

Show-PipelineResult -RunId $runId
```

### Option C — raw REST (any HTTP client)

```http
POST http://localhost:8081/api/v1/pipelines
Content-Type: application/json

{"requirementText": "YOUR PROMPT HERE", "scenarioType": "GREENFIELD"}
```

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/pipelines/{runId}` | Run + stage statuses, attempts, fallback usage |
| `GET /api/v1/pipelines/{runId}/decisions` | Audit-grade decision lineage |
| `GET /api/v1/pipelines/{runId}/context` | Immutable, versioned artifacts |
| `GET /api/v1/approvals?runId=...` | Pending approval checkpoints |
| `POST /api/v1/approvals/{id}/decision` | Approve/reject (`{"approved":true,"notes":"...","decidedBy":"..."}`) |
| `POST /api/v1/pipelines/{runId}/replan` | Replace an upstream artifact → downstream stages re-execute |
| `POST /api/v1/pipelines/{runId}/cancel` | Safe-stop |

### Where to look afterwards

- `runs/{runId}/` — generated sources, tests, `DESIGN.md`
- `runs/{runId}/release-readiness/RUN-SUMMARY.md` — auto-compiled final engineering summary
  (plan, artifacts, human oversight, full decision lineage, reliability stats)

---

## 3. Sample prompts

The agents react to the *content* of the requirement text, so different phrasing exercises
different governance paths.

### Happy paths and human checkpoints

| Prompt | Scenario | What happens |
|---|---|---|
| `Provide a QR code generation endpoint returning HTTP 200 within 100 ms for each short link, available to 99 percent of requests` | `GREENFIELD` | Clean run through all 7 stages, no approvals (concrete metrics → no ambiguities) |
| `Track analytics for every redirect and store click events in a table, exposing the top 5 referrers per short link` | `BROWNFIELD` | "store/table" → schema change detected → **pauses for schema-migration approval**; impact analysis lists real codebase files |
| `Make the service better and faster somehow` | `AMBIGUOUS` | "better" + no metrics → BLOCKER ambiguity → **pauses for clarification**; the human answer is preserved in lineage |
| `Add bulk URL import so a user can upload 1000 links per batch and persist results` | `BROWNFIELD` | Persistence wording → schema-migration approval path |

### Reliability-control demonstrations (failure injection)

| Prompt | What happens |
|---|---|
| `[inject:retry] Provide an export endpoint returning 200 for all 10 columns` | DesignAgent fails once transiently → **bounded retry** with backoff succeeds (design stage shows `attempts = 2`) |
| `[inject:secret] Provide an endpoint returning 200 for all 3 fields` | Generated code contains a credential → PolicyGate catches it → stage **rolled back**, generated files deleted, run fails safely |
| `[inject:fallback-testing] Provide an endpoint returning 200 for all 3 fields` | TestAgent fails irrecoverably → **fallback** emits `@Disabled` skeletons → TestGate refuses untested code → **safe-stop** |
| `[inject:banned-dep] Provide an endpoint returning 200 for all 3 fields` | Design declares `log4j:log4j:1.2.17` → PolicyGate **blocks the design** (banned-dependency guardrail) |
| `[inject:major-finding] Provide a delete-all endpoint returning 204 in 50 ms` | ReviewAgent raises an unresolved MAJOR finding → **pauses for major-findings approval** |

### Rules of thumb

- **Numbers + concrete verbs** → autonomous happy path (no checkpoints).
- **Vague words** (`better`, `faster`, `scalable`, `robust`, `nice`, `improve`, ...) or a
  missing quantified target → clarification checkpoint.
- **Persistence words** (`store`, `table`, `persist`, `track`, `analytics`, `record`,
  `history`, ...) → schema change → schema-migration checkpoint.
- `[inject:*]` markers are honored by the agents to make retry/fallback/rollback/guardrail
  behavior deterministic for demos and tests (see [scenarios/README.md](../scenarios/README.md)).
