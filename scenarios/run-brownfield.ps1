# Scenario 2 — BROWNFIELD: an enhancement to the existing url-shortener-service that
# changes the database schema. Demonstrates codebase impact analysis and the
# schema-migration human approval checkpoint (high-impact change control).
. "$PSScriptRoot\common.ps1"

$requirement = 'Track analytics for every redirect and store click events in a table, ' +
               'exposing the top 5 referrers per short link'

$runId = Start-Pipeline -RequirementText $requirement -ScenarioType BROWNFIELD
$run = Wait-PipelineStatus -RunId $runId

if ($run.status -eq 'PAUSED_FOR_APPROVAL') {
    $approval = Get-PendingApprovals -RunId $runId | Select-Object -First 1
    Write-Host "`nHuman checkpoint: [$($approval.gate)] $($approval.reason)" -ForegroundColor Yellow
    Approve-Request -ApprovalId $approval.id -Notes 'Migration reviewed by DBA' -DecidedBy 'dba' | Out-Null
    Write-Host 'Approved — pipeline resuming.' -ForegroundColor Yellow
    $run = Wait-PipelineStatus -RunId $runId -Until @('COMPLETED', 'FAILED', 'CANCELLED')
}

Show-PipelineResult -RunId $runId

# Brownfield evidence: the DesignAgent's impact analysis over the real codebase.
$context = Get-PipelineContext -RunId $runId
$impact = ($context | Where-Object { $_.name -eq 'design-spec' } | Select-Object -Last 1).content.impactAnalysis
Write-Host "`nImpacted files identified in url-shortener-service:" -ForegroundColor Cyan
$impact.impactedFiles | Format-Table path, reason -AutoSize
