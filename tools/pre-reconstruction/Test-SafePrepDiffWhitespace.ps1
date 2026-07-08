param(
    [switch] $SummaryOnly,
    [switch] $Json,
    [switch] $FailOnIssues
)

$ErrorActionPreference = "Stop"

function Invoke-JsonScript {
    param(
        [string] $Path,
        [string[]] $Arguments = @()
    )

    $output = & powershell -ExecutionPolicy Bypass -File $Path @Arguments -Json 2>&1
    $exitCode = $LASTEXITCODE

    try {
        $report = ($output | ConvertFrom-Json)
    } catch {
        return [ordered]@{
            status = "FAIL"
            failCount = 1
            message = ($output -join "`n")
        }
    }

    if ($exitCode -ne 0 -and $report.status -ne "FAIL") {
        $report | Add-Member -NotePropertyName status -NotePropertyValue "FAIL" -Force
        $report | Add-Member -NotePropertyName failCount -NotePropertyValue 1 -Force
    }

    return $report
}

function Get-GitDiffCheckLines {
    param([string[]] $Paths)

    if ($Paths.Count -eq 0) {
        return @()
    }

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & git diff --check -- @Paths 2>&1
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    return @(
        $output |
            Where-Object {
                ![string]::IsNullOrWhiteSpace([string] $_) -and
                "$_" -notmatch "^warning:"
            }
    )
}

function Test-TextFile {
    param([string] $Path)

    try {
        $bytes = [System.IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $Path).Path)
    } catch {
        return [ordered]@{
            path = $Path
            skipped = $true
            issueCount = 0
            issues = @()
            reason = "Unable to read file: $($_.Exception.Message)"
        }
    }

    if ($bytes -contains 0) {
        return [ordered]@{
            path = $Path
            skipped = $true
            issueCount = 0
            issues = @()
            reason = "Binary-looking file skipped."
        }
    }

    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    $lines = $text -split "`r?`n", -1
    $issues = [System.Collections.Generic.List[object]]::new()

    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "[ \t]+$") {
            [void] $issues.Add([ordered]@{
                path = $Path
                line = $i + 1
                message = "trailing whitespace"
            })
        }
    }

    if ($text.Length -gt 0 -and !$text.EndsWith("`n")) {
        [void] $issues.Add([ordered]@{
            path = $Path
            line = $lines.Count
            message = "no newline at end of file"
        })
    }

    return [ordered]@{
        path = $Path
        skipped = $false
        issueCount = $issues.Count
        issues = @($issues)
        reason = ""
    }
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$candidateReport = Invoke-JsonScript "tools/pre-reconstruction/Get-SafePrepCommitCandidates.ps1"
$safeCandidates = @($candidateReport.safeCandidates)
$trackedSafePaths = @(
    $safeCandidates |
        Where-Object { $_.status -ne "??" } |
        ForEach-Object { $_.path } |
        Sort-Object -Unique
)
$untrackedSafePaths = @(
    $safeCandidates |
        Where-Object { $_.status -eq "??" -and (Test-Path -LiteralPath $_.path -PathType Leaf) } |
        ForEach-Object { $_.path } |
        Sort-Object -Unique
)

$trackedIssueLines = @(Get-GitDiffCheckLines $trackedSafePaths)
$untrackedResults = @($untrackedSafePaths | ForEach-Object { Test-TextFile $_ })
$untrackedIssues = @($untrackedResults | ForEach-Object { @($_.issues) })
$skippedUntracked = @($untrackedResults | Where-Object { $_.skipped })

$issueCount = $trackedIssueLines.Count + $untrackedIssues.Count
$status = if ($issueCount -gt 0) { "FAIL" } else { "PASS" }

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    repoRoot = $repoRoot
    status = $status
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    safeCandidateCount = $safeCandidates.Count
    trackedSafePathCount = $trackedSafePaths.Count
    untrackedSafePathCount = $untrackedSafePaths.Count
    issueCount = $issueCount
    trackedIssueCount = $trackedIssueLines.Count
    untrackedIssueCount = $untrackedIssues.Count
    skippedUntrackedCount = $skippedUntracked.Count
    forbiddenExclusionCount = $candidateReport.forbiddenExclusionCount
    reviewRequiredCount = $candidateReport.reviewRequiredCount
    commitBlockerCount = $candidateReport.commitBlockerCount
    returnedTrackedIssueCount = if ($SummaryOnly) { 0 } else { $trackedIssueLines.Count }
    returnedUntrackedIssueCount = if ($SummaryOnly) { 0 } else { $untrackedIssues.Count }
    returnedSkippedUntrackedCount = if ($SummaryOnly) { 0 } else { $skippedUntracked.Count }
    trackedIssues = if ($SummaryOnly) { $null } else { @($trackedIssueLines) }
    untrackedIssues = if ($SummaryOnly) { $null } else { @($untrackedIssues) }
    skippedUntracked = if ($SummaryOnly) { $null } else { @($skippedUntracked) }
}

if ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    Write-Host "Safe-prep whitespace check: $($report.status)"
    Write-Host "Safe candidates: $($report.safeCandidateCount)"
    Write-Host "Tracked safe paths: $($report.trackedSafePathCount)"
    Write-Host "Untracked safe paths: $($report.untrackedSafePathCount)"
    Write-Host "Issues: $($report.issueCount)"
    Write-Host "Forbidden exclusions: $($report.forbiddenExclusionCount)"
    if (!$SummaryOnly) {
        foreach ($line in @($trackedIssueLines)) {
            Write-Host "[tracked] $line"
        }
        foreach ($issue in @($untrackedIssues)) {
            Write-Host ("[untracked] {0}:{1}: {2}" -f $issue.path, $issue.line, $issue.message)
        }
    }
}

if ($FailOnIssues -and $issueCount -gt 0) {
    exit 1
}
