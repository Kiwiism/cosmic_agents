param(
    [string] $BaselineRoot = "logs/soak/baseline",

    [switch] $SummaryOnly,
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

function Get-RecommendedCommands {
    param(
        [string] $RunPath,
        [object[]] $WarningChecks,
        [object[]] $FailureChecks
    )

    $commands = [System.Collections.Generic.List[string]]::new()
    if ([string]::IsNullOrWhiteSpace($RunPath)) {
        [void] $commands.Add("powershell -ExecutionPolicy Bypass -File .\tools\soak\New-BaselineSoakEvidencePackage.ps1 -DurationMinutes 60 -SampleIntervalMinutes 5")
        return @($commands)
    }

    $allIssueIds = @($WarningChecks + $FailureChecks | ForEach-Object { $_.id })
    if ($allIssueIds -contains "evidence:serverhealth" -or $allIssueIds -contains "evidence:serverhealth-sample-count") {
        [void] $commands.Add("powershell -ExecutionPolicy Bypass -File .\tools\soak\Add-BaselineSoakSample.ps1 -RunPath `"$RunPath`" -Target serverhealth -FromClipboard")
    }

    if ($allIssueIds -contains "evidence:checklist") {
        [void] $commands.Add("powershell -ExecutionPolicy Bypass -File .\tools\soak\Set-BaselineSoakChecklistItem.ps1 -RunPath `"$RunPath`" -List")
    }

    if ($allIssueIds -contains "evidence:startup") {
        [void] $commands.Add("powershell -ExecutionPolicy Bypass -File .\tools\soak\Add-BaselineSoakSample.ps1 -RunPath `"$RunPath`" -Target startup -FromClipboard")
    }

    if ($allIssueIds -contains "evidence:shutdown") {
        [void] $commands.Add("powershell -ExecutionPolicy Bypass -File .\tools\soak\Add-BaselineSoakSample.ps1 -RunPath `"$RunPath`" -Target shutdown -FromClipboard")
    }

    if ($commands.Count -eq 0 -and ($WarningChecks.Count -gt 0 -or $FailureChecks.Count -gt 0)) {
        [void] $commands.Add("powershell -ExecutionPolicy Bypass -File .\tools\soak\Test-BaselineSoakEvidencePackage.ps1 -RunPath `"$RunPath`"")
    }

    return @($commands)
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
$latestWarnings = if ($latestVerification) {
    @($latestVerification.checks | Where-Object { $_.status -eq "WARN" } | ForEach-Object {
        [ordered]@{
            id = $_.id
            message = $_.message
        }
    })
} else {
    @()
}
$latestFailures = if ($latestVerification) {
    @($latestVerification.checks | Where-Object { $_.status -eq "FAIL" } | ForEach-Object {
        [ordered]@{
            id = $_.id
            message = $_.message
        }
    })
} else {
    @()
}
$latestRunPathForCommands = if ($latestRun) { $latestRun.FullName } else { "" }
$recommendedCommands = Get-RecommendedCommands `
    -RunPath $latestRunPathForCommands `
    -WarningChecks $latestWarnings `
    -FailureChecks $latestFailures
$latestEvidenceSummary = if ($latestVerification -and $latestVerification.evidenceSummary) { $latestVerification.evidenceSummary } else { $null }
$summary = [ordered]@{
    ready = $latestStatus -eq "PASS"
    latestRunId = if ($latestRun) { $latestRun.Name } else { $null }
    latestVerificationStatus = $latestStatus
    latestWarningIds = @($latestWarnings | ForEach-Object { $_.id })
    latestFailureIds = @($latestFailures | ForEach-Object { $_.id })
    latestWarningCount = $latestWarnings.Count
    latestFailureCount = $latestFailures.Count
    latestEvidenceSummary = $latestEvidenceSummary
    recommendedCommandCount = $recommendedCommands.Count
    nextAction = Get-NextAction $latestStatus $runFolders.Count
}

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
    latestWarnings = if ($SummaryOnly) { $null } else { @($latestWarnings) }
    latestFailures = if ($SummaryOnly) { $null } else { @($latestFailures) }
    latestWarningIds = @($summary.latestWarningIds)
    latestFailureIds = @($summary.latestFailureIds)
    recommendedCommandCount = $summary.recommendedCommandCount
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    returnedRunCount = if ($SummaryOnly) { 0 } else { @($runSummaries).Count }
    returnedWarningCount = if ($SummaryOnly) { 0 } else { @($latestWarnings).Count }
    returnedFailureCount = if ($SummaryOnly) { 0 } else { @($latestFailures).Count }
    returnedRecommendedCommandCount = if ($SummaryOnly) { 0 } else { @($recommendedCommands).Count }
    serverHealthSampleCount = if ($latestEvidenceSummary) { $latestEvidenceSummary.serverHealthSampleCount } else { $null }
    expectedServerHealthSampleCount = if ($latestEvidenceSummary) { $latestEvidenceSummary.expectedServerHealthSampleCount } else { $null }
    checklistCheckedCount = if ($latestEvidenceSummary) { $latestEvidenceSummary.checklistCheckedCount } else { $null }
    checklistUncheckedCount = if ($latestEvidenceSummary) { $latestEvidenceSummary.checklistUncheckedCount } else { $null }
    checklistItemCount = if ($latestEvidenceSummary) { $latestEvidenceSummary.checklistItemCount } else { $null }
    latestEvidenceSummary = $latestEvidenceSummary
    nextAction = Get-NextAction $latestStatus $runFolders.Count
    recommendedCommands = if ($SummaryOnly) { $null } else { @($recommendedCommands) }
    summary = if ($SummaryOnly) { $null } else { $summary }
    runs = if ($SummaryOnly) { $null } else { $runSummaries }
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

    if ($SummaryOnly) {
        Write-Host "Warnings: $($report.latestWarningIds -join ', ')"
        Write-Host "Recommended command count: $($report.recommendedCommandCount)"
        Write-Host "Detailed warning, command, summary, and run rows omitted because -SummaryOnly was used."
    } elseif ($latestFailures.Count -gt 0) {
        Write-Host ""
        Write-Host "Latest failures:"
        foreach ($failure in $latestFailures) {
            Write-Host ("- {0}: {1}" -f $failure.id, $failure.message)
        }
    }

    if (!$SummaryOnly -and $latestWarnings.Count -gt 0) {
        Write-Host ""
        Write-Host "Latest warnings:"
        foreach ($warning in $latestWarnings) {
            Write-Host ("- {0}: {1}" -f $warning.id, $warning.message)
        }
    }

    if (!$SummaryOnly -and $recommendedCommands.Count -gt 0) {
        Write-Host ""
        Write-Host "Recommended commands:"
        foreach ($command in $recommendedCommands) {
            Write-Host "- $command"
        }
    }

    if (!$SummaryOnly -and $runSummaries.Count -gt 0) {
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
