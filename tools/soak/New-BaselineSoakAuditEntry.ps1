param(
    [Parameter(Mandatory = $true)]
    [string] $RunPath,

    [string] $OutputPath
)

$ErrorActionPreference = "Stop"

function Get-JsonPropertyValue {
    param(
        [object] $Object,
        [string] $Name,
        [object] $Default = ""
    )

    if ($null -eq $Object) {
        return $Default
    }

    $property = $Object.PSObject.Properties | Where-Object { $_.Name -eq $Name } | Select-Object -First 1
    if ($null -eq $property -or $null -eq $property.Value) {
        return $Default
    }

    return $property.Value
}

function Format-Value {
    param([object] $Value)

    if ($null -eq $Value) {
        return ""
    }

    if ($Value -is [bool]) {
        return $Value.ToString().ToLowerInvariant()
    }

    return [string]$Value
}

$resolvedRunPath = Resolve-Path -LiteralPath $RunPath -ErrorAction Stop
$resolvedRunPathText = $resolvedRunPath.Path
$summaryPath = Join-Path $resolvedRunPath "summary.json"

if (-not (Test-Path -LiteralPath $summaryPath)) {
    throw "summary.json was not found in $($resolvedRunPath.Path)."
}

$summary = Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json

$verificationPath = Join-Path $resolvedRunPath "verification.json"
$verification = $null

if (Test-Path -LiteralPath $verificationPath) {
    $verification = Get-Content -LiteralPath $verificationPath -Raw | ConvertFrom-Json
}

$runId = Format-Value (Get-JsonPropertyValue $summary "runId" "(unknown)")
$stage = Format-Value (Get-JsonPropertyValue $summary "stage" "(unknown)")
$durationMinutes = Format-Value (Get-JsonPropertyValue $summary "durationMinutes" "")
$sampleIntervalMinutes = Format-Value (Get-JsonPropertyValue $summary "sampleIntervalMinutes" "")
$onlinePlayerPeak = Format-Value (Get-JsonPropertyValue $summary "onlinePlayerPeak" "")
$onlineAgentPeak = Format-Value (Get-JsonPropertyValue $summary "onlineAgentPeak" "")
$heapStartMb = Format-Value (Get-JsonPropertyValue $summary "heapStartMb" "")
$heapEndMb = Format-Value (Get-JsonPropertyValue $summary "heapEndMb" "")
$loadedMapStart = Format-Value (Get-JsonPropertyValue $summary "loadedMapStart" "")
$loadedMapEnd = Format-Value (Get-JsonPropertyValue $summary "loadedMapEnd" "")
$dbWaitingMax = Format-Value (Get-JsonPropertyValue $summary "dbWaitingMax" "")
$threadRejectedDelta = Format-Value (Get-JsonPropertyValue $summary "threadRejectedDelta" "")
$timerQueueMax = Format-Value (Get-JsonPropertyValue $summary "timerQueueMax" "")
$slowSaveCount = Format-Value (Get-JsonPropertyValue $summary "slowSaveCount" "")
$slowBroadcastCount = Format-Value (Get-JsonPropertyValue $summary "slowBroadcastCount" "")
$stuckLoginCount = Format-Value (Get-JsonPropertyValue $summary "stuckLoginCount" "")
$shutdownClean = Format-Value (Get-JsonPropertyValue $summary "shutdownClean" "")
$restartClean = Format-Value (Get-JsonPropertyValue $summary "restartClean" "")

$verificationStatus = if ($verification) {
    Format-Value (Get-JsonPropertyValue $verification "status" "not-recorded")
} else {
    "not-recorded"
}

$verificationFailures = if ($verification) {
    Format-Value (Get-JsonPropertyValue $verification "failCount" "")
} else {
    ""
}

$verificationWarnings = if ($verification) {
    Format-Value (Get-JsonPropertyValue $verification "warnCount" "")
} else {
    ""
}

$notes = @(Get-JsonPropertyValue $summary "notes" @())
$noteLines = if ($notes.Count -gt 0) {
    ($notes | ForEach-Object { "- " + (Format-Value $_) }) -join "`n"
} else {
    "- none recorded"
}

$auditStatus = if ($verificationStatus -eq "PASS") {
    "complete"
} elseif ($verificationStatus -eq "FAIL") {
    "failed"
} else {
    "incomplete"
}

$markdown = @"
## Baseline Soak Evidence - $runId

Status: $auditStatus

| Field | Value |
| --- | --- |
| Run path | $resolvedRunPathText |
| Stage | $stage |
| Duration minutes | $durationMinutes |
| Sample interval minutes | $sampleIntervalMinutes |
| Peak online players | $onlinePlayerPeak |
| Peak online Agents | $onlineAgentPeak |
| Heap start/end MB | $heapStartMb / $heapEndMb |
| Loaded maps start/end | $loadedMapStart / $loadedMapEnd |
| Max DB waiting | $dbWaitingMax |
| Thread rejected delta | $threadRejectedDelta |
| Max timer queue | $timerQueueMax |
| Slow save count | $slowSaveCount |
| Slow broadcast count | $slowBroadcastCount |
| Stuck login count | $stuckLoginCount |
| Shutdown clean | $shutdownClean |
| Restart clean | $restartClean |
| Verification status | $verificationStatus |
| Verification failures/warnings | $verificationFailures / $verificationWarnings |

Notes:

$noteLines

Follow-up:

- If status is complete, keep this entry as baseline evidence before Agent
  soak stages.
- If status is incomplete or failed, fix the evidence gap or record a
  server-only follow-up before using the run as proof.
"@

if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }

    Set-Content -LiteralPath $OutputPath -Value $markdown -Encoding UTF8
} else {
    Write-Output $markdown
}
