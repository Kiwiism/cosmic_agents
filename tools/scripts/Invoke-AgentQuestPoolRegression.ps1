[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [int]$AgentId,

    [Parameter(Mandatory = $true)]
    [string]$QuestIds,

    [Parameter(Mandatory = $true)]
    [string]$Token,

    [string]$BaseUrl = 'http://127.0.0.1:8792',
    [string]$OutputDirectory = 'reports/quest-regression',
    [int]$TimeoutMinutes = 18,
    [int]$PollSeconds = 5,
    [int]$World = 0,
    [int]$Channel = 1,
    [switch]$AdoptMatchingActivity
)

$ErrorActionPreference = 'Stop'
$headers = @{
    Authorization = "Bearer $Token"
    'Content-Type' = 'application/json'
}
$agentUrl = "$BaseUrl/internal/director/agents/$AgentId"
$runId = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
$runDirectory = Join-Path $OutputDirectory "$runId-agent-$AgentId"
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
$eventPath = Join-Path $runDirectory 'events.jsonl'
$summaryPath = Join-Path $runDirectory 'summary.csv'
$questIdList = @($QuestIds -split ',' | ForEach-Object {
    $parsed = 0
    if (-not [int]::TryParse($_.Trim(), [ref]$parsed) -or $parsed -le 0) {
        throw "QuestIds must be a comma-separated list of positive quest IDs"
    }
    $parsed
})

function Get-AgentView {
    Invoke-RestMethod -Method Get -Headers $headers -Uri $agentUrl
}

function Ensure-AgentOnline {
    $roster = Invoke-RestMethod -Method Get -Headers $headers `
        -Uri "$BaseUrl/internal/director/agents"
    $record = @($roster.agents) | Where-Object { $_.characterId -eq $AgentId } | Select-Object -First 1
    if ($null -eq $record) {
        throw "Agent $AgentId is absent from the Director roster"
    }
    if (-not $record.online -or -not $record.runtimeActive) {
        $body = @{ world = $World; channel = $Channel } | ConvertTo-Json
        Invoke-RestMethod -Method Post -Headers $headers -Uri "$agentUrl/spawn" -Body $body | Out-Null
    }

    $deadline = (Get-Date).AddSeconds(45)
    do {
        try {
            return Get-AgentView
        } catch {
            Start-Sleep -Seconds 1
        }
    } while ((Get-Date) -lt $deadline)
    throw "Agent $AgentId did not become available after Director spawn"
}

function Write-RegressionEvent {
    param(
        [string]$Type,
        [int]$QuestId,
        [hashtable]$Evidence = @{}
    )
    $event = [ordered]@{
        occurredAt = (Get-Date).ToUniversalTime().ToString('o')
        agentId = $AgentId
        questId = $QuestId
        type = $Type
        evidence = $Evidence
    }
    Add-Content -LiteralPath $eventPath -Value ($event | ConvertTo-Json -Depth 8 -Compress)
}

function Submit-Action {
    param(
        [pscustomobject]$View,
        [string]$ActionId,
        [string]$Reason,
        [bool]$ConfirmDestructive = $false
    )
    $body = @{
        actionId = $ActionId
        contextRevision = $View.contextRevision
        idempotencyKey = "quest-regression-$AgentId-$runId-$([guid]::NewGuid())"
        reason = $Reason
        confirmDestructive = $ConfirmDestructive
    } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Headers $headers -Uri "$agentUrl/actions" -Body $body
}

function Acknowledge-Outcomes {
    param([pscustomobject]$View, [string]$Reason)
    foreach ($outcome in @($View.outcomes)) {
        if ($null -eq $outcome -or [string]::IsNullOrWhiteSpace($outcome.outcomeId)) {
            continue
        }
        $body = @{ reason = $Reason } | ConvertTo-Json
        Invoke-RestMethod -Method Post -Headers $headers `
            -Uri "$agentUrl/outcomes/$($outcome.outcomeId)/acknowledge" -Body $body | Out-Null
    }
}

function Wait-NoDirective {
    # Offline Director spawn parks through the same ordinary navigation lifecycle as a live
    # Agent. Large maps can legitimately take longer than a UI-sized request timeout.
    $deadline = (Get-Date).AddSeconds(120)
    do {
        $view = Get-AgentView
        $inFlight = @($view.directives | Where-Object {
            $_.status -notin @('COMPLETED', 'REJECTED', 'CANCELLED', 'EXPIRED')
        })
        if ($inFlight.Count -eq 0) {
            return $view
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "Agent $AgentId still has a Director directive in progress"
}

function Stop-TimedOutActivity {
    param([int]$QuestId)
    $view = Wait-NoDirective
    $abandon = $view.actions | Where-Object {
        $_.actionId -eq 'lifecycle:abandon' -and $_.availability -ne 'UNAVAILABLE'
    } | Select-Object -First 1
    if ($null -eq $abandon) {
        Write-RegressionEvent -Type 'timeout-cleanup-unavailable' -QuestId $QuestId `
            -Evidence @{ activity = $view.activity.now; mapId = $view.agent.mapId }
        return
    }
    Submit-Action -View $view -ActionId 'lifecycle:abandon' `
        -Reason "quest regression timeout for quest $QuestId" -ConfirmDestructive $true | Out-Null
    $cleanupDeadline = (Get-Date).AddSeconds(45)
    do {
        Start-Sleep -Seconds 2
        $view = Get-AgentView
        if ([string]::IsNullOrWhiteSpace($view.activity.kind)) {
            break
        }
    } while ((Get-Date) -lt $cleanupDeadline)
    Acknowledge-Outcomes -View $view -Reason "quest regression timeout cleanup for quest $QuestId"
}

function Get-QuestTimeoutEvidence {
    param([pscustomobject]$View, [int]$QuestId, [int]$MapTransitions)
    $questAction = $View.actions | Where-Object {
        $_.actionId -eq "individual-quest:$QuestId"
    } | Select-Object -First 1
    $phase = if ($null -eq $questAction) {
        'quest action no longer exposed'
    } elseif ($questAction.label -like 'Resume quest*') {
        'quest accepted; objective or return trip still in progress'
    } elseif ($questAction.label -like 'Start quest*') {
        'quest not yet accepted; travel to the start NPC still in progress'
    } elseif ($questAction.availability -eq 'UNAVAILABLE') {
        "quest unavailable: $($questAction.reason)"
    } else {
        "quest phase unresolved from action '$($questAction.label)'"
    }
    $classification = if ($null -eq $questAction) {
        'TIMEOUT_UNRESOLVED'
    } elseif ($questAction.label -like 'Resume quest*') {
        'TIMEOUT_ACTIVE'
    } elseif ($questAction.label -like 'Start quest*') {
        'TIMEOUT_TRAVEL'
    } elseif ($questAction.availability -eq 'UNAVAILABLE') {
        'TIMEOUT_UNAVAILABLE'
    } else {
        'TIMEOUT_UNRESOLVED'
    }
    return @{
        classification = $classification
        phase = $phase
        mapId = $View.agent.mapId
        activity = $View.activity.now
        waitingOn = $View.activity.waitingOn
        blockedBy = $View.activity.blockedBy
        mapTransitions = $MapTransitions
        hp = $View.agent.hp
        mp = $View.agent.mp
        meso = $View.agent.meso
    }
}

$summaries = [System.Collections.Generic.List[object]]::new()
$adopted = $false
Ensure-AgentOnline | Out-Null
$initialView = Wait-NoDirective
if (-not $AdoptMatchingActivity -and -not [string]::IsNullOrWhiteSpace($initialView.activity.kind)) {
    $abandon = $initialView.actions | Where-Object {
        $_.actionId -eq 'lifecycle:abandon' -and $_.availability -ne 'UNAVAILABLE'
    } | Select-Object -First 1
    if ($null -eq $abandon) {
        throw "Agent $AgentId retained '$($initialView.activity.now)' and cannot abandon it for regression"
    }
    Submit-Action -View $initialView -ActionId 'lifecycle:abandon' `
        -Reason 'replace retained pre-regression activity' -ConfirmDestructive $true | Out-Null
    Wait-NoDirective | Out-Null
}

foreach ($questId in $questIdList) {
    $startedAt = Get-Date
    $view = Wait-NoDirective
    $actionId = "individual-quest:$questId"
    $action = $view.actions | Where-Object { $_.actionId -eq $actionId } | Select-Object -First 1
    $questName = if ($null -eq $action) {
        ''
    } else {
        $action.label -replace '^(Start|Resume) quest — ', ''
    }
    $lastQuestDirective = $view.directives | Where-Object {
        $_.actionId -like 'individual-quest:*' -and $_.status -eq 'COMPLETED'
    } | Sort-Object createdAtMs -Descending | Select-Object -First 1
    $priorOutcome = @($view.outcomes) | Sort-Object publishedAtMs | Select-Object -First 1
    if (($AdoptMatchingActivity -and -not $adopted) -and
            ($null -ne $priorOutcome -and $null -ne $lastQuestDirective) -and
            ($lastQuestDirective.actionId -eq $actionId)) {
        $adopted = $true
        $summaries.Add([pscustomobject]@{
            agentId = $AgentId; agentName = $view.agent.name; questId = $questId
            questName = $questName; status = [string]$priorOutcome.status
            reason = [string]$priorOutcome.reason; elapsedSeconds = 0
            startMapId = $view.agent.mapId; endMapId = $view.agent.mapId
            mapTrail = [string]$view.agent.mapId
        })
        Write-RegressionEvent -Type 'adopted-terminal-outcome' -QuestId $questId `
            -Evidence @{ status = $priorOutcome.status; reason = $priorOutcome.reason; mapId = $view.agent.mapId }
        Acknowledge-Outcomes -View $view -Reason "adopted by quest regression for quest $questId"
        $summaries | Export-Csv -LiteralPath $summaryPath -NoTypeInformation
        continue
    }
    Acknowledge-Outcomes -View $view -Reason 'clearing prior quest regression outcome'
    $view = Get-AgentView
    $matchingActive = $false
    if ($AdoptMatchingActivity -and -not $adopted -and -not [string]::IsNullOrWhiteSpace($view.activity.kind)) {
        $matchingActive = $null -ne $lastQuestDirective -and $lastQuestDirective.actionId -eq $actionId
    }

    if (-not $matchingActive) {
        if (-not [string]::IsNullOrWhiteSpace($view.activity.kind)) {
            Write-RegressionEvent -Type 'blocked-by-active-activity' -QuestId $questId `
                -Evidence @{ activity = $view.activity.now; mapId = $view.agent.mapId }
            $summaries.Add([pscustomobject]@{
                agentId = $AgentId; agentName = $view.agent.name; questId = $questId
                questName = $questName
                status = 'BLOCKED'; reason = 'another activity remained active'
                elapsedSeconds = 0; startMapId = $view.agent.mapId
                endMapId = $view.agent.mapId; mapTrail = [string]$view.agent.mapId
            })
            continue
        }
        if ($null -eq $action -or $action.availability -eq 'UNAVAILABLE') {
            $reason = if ($null -eq $action) { 'quest action absent from catalog' } else { $action.reason }
            Write-RegressionEvent -Type 'not-available' -QuestId $questId -Evidence @{ reason = $reason }
            $summaries.Add([pscustomobject]@{
                agentId = $AgentId; agentName = $view.agent.name; questId = $questId
                questName = $questName
                status = 'NOT_AVAILABLE'; reason = $reason; elapsedSeconds = 0
                startMapId = $view.agent.mapId; endMapId = $view.agent.mapId
                mapTrail = [string]$view.agent.mapId
            })
            continue
        }
        Submit-Action -View $view -ActionId $actionId `
            -Reason "diverse quest-pool regression quest $questId" | Out-Null
        Write-RegressionEvent -Type 'submitted' -QuestId $questId `
            -Evidence @{ questName = $action.label; mapId = $view.agent.mapId }
    } else {
        $adopted = $true
        Write-RegressionEvent -Type 'adopted-existing' -QuestId $questId `
            -Evidence @{ activity = $view.activity.now; mapId = $view.agent.mapId }
    }

    $startMapId = $view.agent.mapId
    $mapTrail = [System.Collections.Generic.List[int]]::new()
    $mapTrail.Add($startMapId)
    $status = 'TIMEOUT'
    $reason = "no terminal outcome within $TimeoutMinutes minute(s)"
    $deadline = $startedAt.AddMinutes($TimeoutMinutes)

    do {
        Start-Sleep -Seconds $PollSeconds
        $view = Get-AgentView
        $mapId = [int]$view.agent.mapId
        if ($mapTrail[$mapTrail.Count - 1] -ne $mapId) {
            $mapTrail.Add($mapId)
            Write-RegressionEvent -Type 'map-changed' -QuestId $questId `
                -Evidence @{ mapId = $mapId; activity = $view.activity.now }
        }
        $outcome = @($view.outcomes) | Sort-Object publishedAtMs | Select-Object -First 1
        if ($null -ne $outcome) {
            $status = [string]$outcome.status
            $reason = [string]$outcome.reason
            Write-RegressionEvent -Type 'terminal' -QuestId $questId `
                -Evidence @{ status = $status; reason = $reason; mapId = $mapId }
            Acknowledge-Outcomes -View $view -Reason "recorded by quest regression for quest $questId"
            break
        }
    } while ((Get-Date) -lt $deadline)

    if ($status -eq 'TIMEOUT') {
        # Activity completion and Director outcome publication are separate scheduler slices.
        # A quest that completes on the deadline must not be mislabeled because the activity
        # disappeared one poll before its terminal outcome became visible.
        $publicationDeadline = (Get-Date).AddSeconds([math]::Max(10, $PollSeconds * 3))
        do {
            Start-Sleep -Seconds 1
            $view = Get-AgentView
            $outcome = @($view.outcomes) | Sort-Object publishedAtMs | Select-Object -First 1
            if ($null -ne $outcome) {
                $status = [string]$outcome.status
                $reason = [string]$outcome.reason
                Write-RegressionEvent -Type 'terminal-publication-grace' -QuestId $questId `
                    -Evidence @{ status = $status; reason = $reason; mapId = $view.agent.mapId }
                Acknowledge-Outcomes -View $view `
                    -Reason "recorded during publication grace for quest $questId"
                break
            }
        } while ((Get-Date) -lt $publicationDeadline)
    }

    if ($status -eq 'TIMEOUT') {
        $timeoutEvidence = Get-QuestTimeoutEvidence -View $view -QuestId $questId `
            -MapTransitions ([math]::Max(0, $mapTrail.Count - 1))
        $status = $timeoutEvidence.classification
        $reason = "time budget expired after $TimeoutMinutes minute(s); $($timeoutEvidence.phase)"
        Write-RegressionEvent -Type 'timeout' -QuestId $questId `
            -Evidence (@{ reason = $reason } + $timeoutEvidence)
        Stop-TimedOutActivity -QuestId $questId
    }

    $finishedAt = Get-Date
    $summaries.Add([pscustomobject]@{
        agentId = $AgentId
        agentName = $view.agent.name
        questId = $questId
        questName = $questName
        status = $status
        reason = $reason
        elapsedSeconds = [math]::Round(($finishedAt - $startedAt).TotalSeconds, 1)
        startMapId = $startMapId
        endMapId = $view.agent.mapId
        mapTrail = ($mapTrail -join '>')
    })
    $summaries | Export-Csv -LiteralPath $summaryPath -NoTypeInformation
}

$summaries | Export-Csv -LiteralPath $summaryPath -NoTypeInformation
$summaries | Format-Table -AutoSize
