param(
    [string] $BaselineRoot = "logs/soak/baseline",

    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Invoke-EvidenceVerifier {
    param([string] $RunPath)

    $verifierPath = Join-Path $repoRoot "tools/soak/Test-BaselineSoakEvidencePackage.ps1"
    $output = & powershell -ExecutionPolicy Bypass -File $verifierPath -RunPath $RunPath -Json 2>&1
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        return [ordered]@{
            status = "FAIL"
            failCount = 1
            warnCount = 0
            error = ($output -join "`n")
        }
    }

    return ($output | ConvertFrom-Json)
}

function Get-NextAction {
    param(
        [string] $Status,
        [int] $RunCount
    )

    if ($RunCount -eq 0) {
        return "Create a baseline run folder with tools/soak/New-BaselineSoakEvidencePackage.ps1."
    }

    if ($Status -eq "PASS") {
        return "Generate an audit entry and record the run in docs/agents/PRE_RECONSTRUCTION_COMPLETION_AUDIT.md."
    }

    if ($Status -eq "FAIL") {
        return "Open the latest run verification details and fix the failed evidence files or summary fields."
    }

    return "Fill the latest run folder with real startup, serverhealth, scale-health, slow-operation, shutdown, and summary evidence."
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$resolvedBaselineRoot = if ([System.IO.Path]::IsPathRooted($BaselineRoot)) {
    $BaselineRoot
} else {
    Join-Path $repoRoot $BaselineRoot
}
$runSummaries = @()
$latestRun = $null
$latestVerification = $null

if (Test-Path -LiteralPath $resolvedBaselineRoot) {
    $runFolders = @(Get-ChildItem -LiteralPath $resolvedBaselineRoot -Directory -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTimeUtc -Descending)

    if ($runFolders.Count -gt 0) {
        $latestRun = $runFolders | Select-Object -First 1
        $latestVerification = Invoke-EvidenceVerifier $latestRun.FullName

        $runSummaries = @($runFolders | ForEach-Object {
            $summaryPath = Join-Path $_.FullName "summary.json"
            $summary = $null
            if (Test-Path -LiteralPath $summaryPath) {
                try {
                    $summary = Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
                } catch {
                    $summary = $null
                }
            }

            [ordered]@{
                runId = $_.Name
                path = $_.FullName
                lastWriteTimeUtc = $_.LastWriteTimeUtc.ToString("o")
                durationMinutes = if ($summary) { $summary.durationMinutes } else { $null }
                sampleIntervalMinutes = if ($summary) { $summary.sampleIntervalMinutes } else { $null }
                onlinePlayerPeak = if ($summary) { $summary.onlinePlayerPeak } else { $null }
                onlineAgentPeak = if ($summary) { $summary.onlineAgentPeak } else { $null }
                shutdownClean = if ($summary) { $summary.shutdownClean } else { $null }
                restartClean = if ($summary) { $summary.restartClean } else { $null }
            }
        })
    } else {
        $runFolders = @()
    }
} else {
    $runFolders = @()
}

$latestStatus = if ($latestVerification) { $latestVerification.status } else { "MISSING" }
$report = [ordered]@{
    status = if ($latestStatus -eq "PASS") { "READY" } else { "INCOMPLETE" }
    repoRoot = $repoRoot
    baselineRoot = $resolvedBaselineRoot
    runCount = $runFolders.Count
    latestRunId = if ($latestRun) { $latestRun.Name } else { $null }
    latestRunPath = if ($latestRun) { $latestRun.FullName } else { $null }
    latestVerificationStatus = $latestStatus
    latestFailCount = if ($latestVerification) { $latestVerification.failCount } else { $null }
    latestWarnCount = if ($latestVerification) { $latestVerification.warnCount } else { $null }
    nextAction = Get-NextAction $latestStatus $runFolders.Count
    runs = $runSummaries
}

if ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    Write-Host "Baseline soak status: $($report.status)"
    Write-Host "Repo root: $repoRoot"
    Write-Host "Baseline root: $resolvedBaselineRoot"
    Write-Host "Run folders: $($report.runCount)"

    if ($latestRun) {
        Write-Host "Latest run: $($report.latestRunId)"
        Write-Host "Latest verification: $($report.latestVerificationStatus) (failures: $($report.latestFailCount), warnings: $($report.latestWarnCount))"
    } else {
        Write-Host "Latest run: none"
    }

    Write-Host "Next action: $($report.nextAction)"

    if ($runSummaries.Count -gt 0) {
        Write-Host ""
        Write-Host "Runs:"
        foreach ($run in $runSummaries) {
            Write-Host ("- {0} | duration={1}m | players={2} | agents={3} | shutdown={4} | restart={5}" -f `
                $run.runId, `
                $run.durationMinutes, `
                $run.onlinePlayerPeak, `
                $run.onlineAgentPeak, `
                $run.shutdownClean, `
                $run.restartClean)
        }
    }
}
