# Executable Scenarios

Three scripted demonstrations of the agentic SDLC pipeline, each showing requirement
decomposition, DAG orchestration, gates, and validation. Prerequisite: the orchestration
service is running on `http://localhost:8081` (see the repository [README](../README.md)).

| Script | Scenario | What it demonstrates |
|---|---|---|
| `run-greenfield.ps1` | New feature, well-defined | Full 7-stage DAG, parallel design-docs branch, sync point, no human checkpoint needed |
| `run-brownfield.ps1` | Enhancement with schema change | Codebase impact analysis + **schema-migration approval checkpoint** (human-in-the-loop) |
| `run-ambiguous.ps1` | Vague requirement | BLOCKER ambiguity detection → **clarification checkpoint**; human answer preserved in lineage |

```powershell
cd scenarios
./run-greenfield.ps1
./run-brownfield.ps1
./run-ambiguous.ps1
```

## Reliability-control demonstrations (failure injection)

The agents honor `[inject:*]` markers in the requirement text so that retry, fallback,
rollback, and policy guardrails can be demonstrated deterministically:

| Marker | Effect |
|---|---|
| `[inject:retry]` | DesignAgent fails transiently once → bounded retry with backoff succeeds on attempt 2 |
| `[inject:fallback-testing]` | TestAgent fails irrecoverably → fallback emits `@Disabled` skeletons → TestGate safe-stops the pipeline (untested code never ships) |
| `[inject:secret]` | ImplementationAgent writes a credential → PolicyGate catches it → stage **rolled back**, generated files deleted, pipeline fails safely |
| `[inject:banned-dep]` | DesignAgent adds `log4j:log4j:1.2.17` → PolicyGate blocks the design |
| `[inject:major-finding]` | ReviewAgent raises an unresolved MAJOR finding → `major-findings` approval checkpoint |

Example:

```powershell
. ./common.ps1
$runId = Start-Pipeline -RequirementText '[inject:retry] Provide an export endpoint returning 200 for all 10 columns' -ScenarioType GREENFIELD
Wait-PipelineStatus -RunId $runId | Out-Null
Show-PipelineResult -RunId $runId   # design stage shows attempts = 2
```

## Dynamic re-planning

After a run completes, replace an upstream artifact; all downstream stages are invalidated
and re-executed, with the replacement recorded as a new immutable artifact version:

```powershell
$design = (Get-PipelineContext -RunId $runId | Where-Object name -eq 'design-spec' | Select-Object -Last 1).content | ConvertTo-Json -Depth 20
$body = @{ artifactName = 'design-spec'; content = $design; rationale = 'Architecture board revision' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/v1/pipelines/$runId/replan" -ContentType 'application/json' -Body $body
```

## Evidence to inspect after any run

- `GET /api/v1/pipelines/{runId}` — stage statuses, attempts, fallback usage, durations
- `GET /api/v1/pipelines/{runId}/decisions` — audit-grade decision lineage (ORCHESTRATOR / AGENT / GATE / HUMAN)
- `GET /api/v1/pipelines/{runId}/context` — immutable, versioned artifacts
- `runs/{runId}/` — generated sources, tests, DESIGN.md, and the final `RUN-SUMMARY.md`
