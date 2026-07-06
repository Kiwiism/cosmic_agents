param(
    [string] $RunId,
    [string] $OutputRoot = "logs/soak/baseline",
    [int] $DurationMinutes = 60,
    [int] $SampleIntervalMinutes = 5
)

$ErrorActionPreference = "Stop"

function Resolve-RepoRoot {
    $root = git rev-parse --show-toplevel 2>$null
    if (-not $root) {
        throw "This script must be run from inside the Cosmic Agents git repository."
    }
    return $root.Trim()
}

function New-TextFileIfMissing {
    param(
        [Parameter(Mandatory = $true)] [string] $Path,
        [Parameter(Mandatory = $true)] [string] $Content
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        $parent = Split-Path -Parent $Path
        if ($parent -and -not (Test-Path -LiteralPath $parent)) {
            New-Item -ItemType Directory -Force -Path $parent | Out-Null
        }
        Set-Content -LiteralPath $Path -Value $Content -Encoding UTF8
    }
}

function Get-FileSha256 {
    param([string] $Path)
    if (Test-Path -LiteralPath $Path) {
        return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    }
    return $null
}

$repoRoot = Resolve-RepoRoot
Set-Location -LiteralPath $repoRoot

if ([string]::IsNullOrWhiteSpace($RunId)) {
    $RunId = "baseline-" + (Get-Date -Format "yyyyMMdd-HHmm")
}

$runDir = Join-Path $OutputRoot $RunId
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$head = (git rev-parse HEAD).Trim()
$branch = (git branch --show-current).Trim()
$status = git status --short
$configHash = Get-FileSha256 "config.yaml"
$javaVersion = (& java -version 2>&1) -join "`n"
$createdAt = (Get-Date).ToString("o")

$summary = [ordered]@{
    runId = $RunId
    stage = "server-baseline"
    createdAt = $createdAt
    durationMinutes = $DurationMinutes
    sampleIntervalMinutes = $SampleIntervalMinutes
    onlinePlayerPeak = 0
    onlineAgentPeak = 0
    heapStartMb = 0
    heapEndMb = 0
    loadedMapStart = 0
    loadedMapEnd = 0
    dbWaitingMax = 0
    threadRejectedDelta = 0
    timerQueueMax = 0
    slowSaveCount = 0
    slowBroadcastCount = 0
    stuckLoginCount = 0
    shutdownClean = $false
    restartClean = $false
    git = [ordered]@{
        branch = $branch
        head = $head
        statusShort = @($status)
    }
    config = [ordered]@{
        configYamlSha256 = $configHash
    }
    environment = [ordered]@{
        computerName = $env:COMPUTERNAME
        userName = $env:USERNAME
        os = [System.Environment]::OSVersion.VersionString
        javaVersion = $javaVersion
    }
    notes = @(
        "Fill numeric fields after the smoke/soak run.",
        "Paste in-game !serverhealth samples into serverhealth-5min-samples.log.",
        "Copy startup, shutdown, slow-operation, and scale-health logs into this folder."
    )
}

$summaryPath = Join-Path $runDir "summary.json"
if (-not (Test-Path -LiteralPath $summaryPath)) {
    $summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding UTF8
}

$readme = @"
# Baseline Soak Evidence - $RunId

Purpose:

Capture server-only baseline evidence before Agent reconstruction-dependent
soak stages. This run should not require Agents.

Repository:

- Branch: $branch
- HEAD: $head
- Created: $createdAt

Expected capture:

1. Start the server with the normal configuration.
2. Record startup logs into ``startup.log``.
3. Every $SampleIntervalMinutes minutes, paste ``!serverhealth`` output into
   ``serverhealth-5min-samples.log``.
4. Copy scale-health lines into ``scale-health.log``.
5. Copy slow-operation warnings into ``slow-operations.log``.
6. Run normal baseline scenarios from ``docs/SOAK_TEST_CHECKLIST.md``.
7. Shut down cleanly and copy shutdown logs into ``shutdown.log``.
8. Update ``summary.json`` with final numbers and pass/fail notes.

Pass criteria are documented in:

- ``docs/agents/PRE_RECONSTRUCTION_COMPLETION_AUDIT.md``
- ``docs/SOAK_TEST_CHECKLIST.md``

Do not use this baseline as Agent-scale proof. Agent stages require the
reconstructed Agent runtime and dedicated Agent soak harness.
"@

New-TextFileIfMissing -Path (Join-Path $runDir "README.md") -Content $readme

$serverHealthTemplate = @"
# Paste !serverhealth samples here.
# Suggested cadence: every $SampleIntervalMinutes minutes.
# Format suggestion:
# [timestamp]
# <full !serverhealth output>

"@
New-TextFileIfMissing -Path (Join-Path $runDir "serverhealth-5min-samples.log") -Content $serverHealthTemplate

$scaleHealthTemplate = @"
# Copy scale-health log lines here.

"@
New-TextFileIfMissing -Path (Join-Path $runDir "scale-health.log") -Content $scaleHealthTemplate

$slowOpsTemplate = @"
# Copy slow-operation warnings here.

"@
New-TextFileIfMissing -Path (Join-Path $runDir "slow-operations.log") -Content $slowOpsTemplate

New-TextFileIfMissing -Path (Join-Path $runDir "startup.log") -Content "# Copy startup logs here.`n"
New-TextFileIfMissing -Path (Join-Path $runDir "shutdown.log") -Content "# Copy shutdown logs here.`n"

$checklist = @"
# Baseline Evidence Checklist

- [ ] Server started without manual config edits.
- [ ] Startup logs copied to ``startup.log``.
- [ ] ``!serverhealth`` samples captured every $SampleIntervalMinutes minutes.
- [ ] Scale-health logs copied to ``scale-health.log``.
- [ ] Slow-operation logs copied to ``slow-operations.log``.
- [ ] Login/logout cycle tested.
- [ ] Channel change tested.
- [ ] Map travel tested.
- [ ] Mob kill/drop/pickup tested.
- [ ] Save/autosave path observed.
- [ ] Shop open/buy/sell tested if available.
- [ ] Party create/join/leave tested if available.
- [ ] Shutdown logs copied to ``shutdown.log``.
- [ ] Restart checked after shutdown.
- [ ] ``summary.json`` updated with final values.
- [ ] Any failure has notes and root-cause status.
"@
New-TextFileIfMissing -Path (Join-Path $runDir "evidence-checklist.md") -Content $checklist

Write-Host "Created baseline evidence package:"
Write-Host "  $runDir"
Write-Host ""
Write-Host "Next:"
Write-Host "  1. Run the server baseline smoke/soak."
Write-Host "  2. Paste !serverhealth samples into serverhealth-5min-samples.log."
Write-Host "  3. Update summary.json with final numbers."
