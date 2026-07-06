param(
    [Parameter(Mandatory = $true)]
    [string] $RunPath,

    [int] $DurationMinutes = -1,
    [int] $SampleIntervalMinutes = -1,
    [int] $OnlinePlayerPeak = -1,
    [int] $OnlineAgentPeak = -1,
    [int] $HeapStartMb = -1,
    [int] $HeapEndMb = -1,
    [int] $LoadedMapStart = -1,
    [int] $LoadedMapEnd = -1,
    [int] $DbWaitingMax = -1,
    [int] $ThreadRejectedDelta = -1,
    [int] $TimerQueueMax = -1,
    [int] $SlowSaveCount = -1,
    [int] $SlowBroadcastCount = -1,
    [int] $StuckLoginCount = -1,

    [ValidateSet("true", "false", "unchanged")]
    [string] $ShutdownClean = "unchanged",

    [ValidateSet("true", "false", "unchanged")]
    [string] $RestartClean = "unchanged",

    [string[]] $Note
)

$ErrorActionPreference = "Stop"

function Set-PropertyIfProvided {
    param(
        [object] $Object,
        [string] $Name,
        [int] $Value
    )

    if ($Value -ge 0) {
        $Object.$Name = $Value
    }
}

function Set-BoolIfProvided {
    param(
        [object] $Object,
        [string] $Name,
        [string] $Value
    )

    if ($Value -eq "true") {
        $Object.$Name = $true
    } elseif ($Value -eq "false") {
        $Object.$Name = $false
    }
}

$resolvedRunPath = Resolve-Path -LiteralPath $RunPath -ErrorAction Stop
$summaryPath = Join-Path $resolvedRunPath "summary.json"

if (-not (Test-Path -LiteralPath $summaryPath)) {
    throw "summary.json was not found in $($resolvedRunPath.Path)."
}

$summary = Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json

Set-PropertyIfProvided $summary "durationMinutes" $DurationMinutes
Set-PropertyIfProvided $summary "sampleIntervalMinutes" $SampleIntervalMinutes
Set-PropertyIfProvided $summary "onlinePlayerPeak" $OnlinePlayerPeak
Set-PropertyIfProvided $summary "onlineAgentPeak" $OnlineAgentPeak
Set-PropertyIfProvided $summary "heapStartMb" $HeapStartMb
Set-PropertyIfProvided $summary "heapEndMb" $HeapEndMb
Set-PropertyIfProvided $summary "loadedMapStart" $LoadedMapStart
Set-PropertyIfProvided $summary "loadedMapEnd" $LoadedMapEnd
Set-PropertyIfProvided $summary "dbWaitingMax" $DbWaitingMax
Set-PropertyIfProvided $summary "threadRejectedDelta" $ThreadRejectedDelta
Set-PropertyIfProvided $summary "timerQueueMax" $TimerQueueMax
Set-PropertyIfProvided $summary "slowSaveCount" $SlowSaveCount
Set-PropertyIfProvided $summary "slowBroadcastCount" $SlowBroadcastCount
Set-PropertyIfProvided $summary "stuckLoginCount" $StuckLoginCount

Set-BoolIfProvided $summary "shutdownClean" $ShutdownClean
Set-BoolIfProvided $summary "restartClean" $RestartClean

if ($Note -and $Note.Count -gt 0) {
    $existingNotes = @()
    if ($summary.PSObject.Properties.Name -contains "notes" -and $summary.notes) {
        $existingNotes = @($summary.notes)
    }

    $summary.notes = @($existingNotes + $Note)
}

$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding UTF8

Write-Host "Updated baseline summary:"
Write-Host "  $summaryPath"
