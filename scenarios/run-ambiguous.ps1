# Scenario 3 — AMBIGUOUS: a vague requirement. The RequirementAgent flags BLOCKER
# ambiguities and the pipeline pauses for human clarification instead of guessing
# (controlled autonomy). The clarification becomes part of the audit lineage.
. "$PSScriptRoot\common.ps1"

$requirement = 'Make the service better and faster somehow'

$runId = Start-Pipeline -RequirementText $requirement -ScenarioType AMBIGUOUS
$run = Wait-PipelineStatus -RunId $runId

if ($run.status -eq 'PAUSED_FOR_APPROVAL') {
    $approval = Get-PendingApprovals -RunId $runId | Select-Object -First 1
    Write-Host "`nHuman checkpoint: [$($approval.gate)] $($approval.reason)" -ForegroundColor Yellow
    Approve-Request -ApprovalId $approval.id `
        -Notes 'Target: p95 redirect latency < 50ms at 1000 rps; scope limited to redirect path' `
        -DecidedBy 'product-owner' | Out-Null
    Write-Host 'Clarification supplied — pipeline resuming.' -ForegroundColor Yellow
    $run = Wait-PipelineStatus -RunId $runId -Until @('COMPLETED', 'FAILED', 'CANCELLED')
}

Show-PipelineResult -RunId $runId
Write-Host 'The clarification is preserved as the clarification-notes artifact in /context.'
