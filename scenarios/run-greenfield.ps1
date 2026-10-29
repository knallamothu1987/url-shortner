# Scenario 1 — GREENFIELD: a well-defined new feature flows through the full SDLC DAG
# with no human checkpoints required (controlled autonomy on the happy path).
. "$PSScriptRoot\common.ps1"

$requirement = 'Provide a QR code generation endpoint returning HTTP 200 within 100 ms ' +
               'for each short link, available to 99 percent of requests'

$runId = Start-Pipeline -RequirementText $requirement -ScenarioType GREENFIELD
$run = Wait-PipelineStatus -RunId $runId

if ($run.status -eq 'PAUSED_FOR_APPROVAL') {
    Write-Warning 'Unexpected approval checkpoint on the greenfield happy path.'
}

Show-PipelineResult -RunId $runId
