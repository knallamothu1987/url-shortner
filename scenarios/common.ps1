# Shared helpers for driving the agentic SDLC pipeline from PowerShell.
$script:BaseUrl = if ($env:ORCHESTRATOR_URL) { $env:ORCHESTRATOR_URL } else { 'http://localhost:8081' }

function Start-Pipeline {
    param(
        [Parameter(Mandatory)] [string] $RequirementText,
        [Parameter(Mandatory)] [ValidateSet('GREENFIELD', 'BROWNFIELD', 'AMBIGUOUS')] [string] $ScenarioType
    )
    $body = @{ requirementText = $RequirementText; scenarioType = $ScenarioType } | ConvertTo-Json
    $response = Invoke-RestMethod -Method Post -Uri "$script:BaseUrl/api/v1/pipelines" `
        -ContentType 'application/json' -Body $body
    Write-Host "Pipeline started: $($response.runId)" -ForegroundColor Cyan
    return $response.runId
}

function Wait-PipelineStatus {
    param(
        [Parameter(Mandatory)] [string] $RunId,
        [string[]] $Until = @('COMPLETED', 'FAILED', 'CANCELLED', 'PAUSED_FOR_APPROVAL'),
        [int] $TimeoutSeconds = 120
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        Start-Sleep -Seconds 3
        $run = Invoke-RestMethod -Uri "$script:BaseUrl/api/v1/pipelines/$RunId"
        Write-Host "  status=$($run.status)" -ForegroundColor DarkGray
    } until (($Until -contains $run.status) -or ((Get-Date) -gt $deadline))
    return $run
}

function Get-PendingApprovals {
    param([Parameter(Mandatory)] [string] $RunId)
    return Invoke-RestMethod -Uri "$script:BaseUrl/api/v1/approvals?runId=$RunId" |
        Where-Object { $_.status -eq 'PENDING' }
}

function Approve-Request {
    param(
        [Parameter(Mandatory)] [string] $ApprovalId,
        [bool] $Approved = $true,
        [string] $Notes = '',
        [string] $DecidedBy = 'operator'
    )
    $body = @{ approved = $Approved; notes = $Notes; decidedBy = $DecidedBy } | ConvertTo-Json
    return Invoke-RestMethod -Method Post -Uri "$script:BaseUrl/api/v1/approvals/$ApprovalId/decision" `
        -ContentType 'application/json' -Body $body
}

function Get-PipelineContext {
    param([Parameter(Mandatory)] [string] $RunId)
    return Invoke-RestMethod -Uri "$script:BaseUrl/api/v1/pipelines/$RunId/context"
}

function Show-PipelineResult {
    param([Parameter(Mandatory)] [string] $RunId)
    $run = Invoke-RestMethod -Uri "$script:BaseUrl/api/v1/pipelines/$RunId"
    Write-Host "`nFinal status: $($run.status)" -ForegroundColor Green
    $run.stages | Format-Table name, status, attempts, fallbackUsed -AutoSize
    Write-Host "Decision lineage:   GET $script:BaseUrl/api/v1/pipelines/$RunId/decisions"
    Write-Host "Context/artifacts:  GET $script:BaseUrl/api/v1/pipelines/$RunId/context"
    Write-Host "Run workspace:      runs/$RunId (RUN-SUMMARY.md under release-readiness/)"
}
