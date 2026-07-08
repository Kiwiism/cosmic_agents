param(
    [string] $OutputPath,
    [switch] $Json,
    [switch] $SummaryOnly,
    [switch] $FailOnBlockers,
    [switch] $FailOnReviewRequired
)

$ErrorActionPreference = "Stop"

function Get-GitPorcelain {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & git status --porcelain=v1 -uall 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        throw "git status --porcelain=v1 failed: $output"
    }

    return @($output | Where-Object { ![string]::IsNullOrWhiteSpace([string] $_) })
}

function Test-ForbiddenPath {
    param([string] $Path)

    return (
        ($Path -like "src/main/java/server/agents/*" -and !(Test-InactiveAgentPrepPath $Path)) -or
        $Path -like "src/main/java/server/bots/*" -or
        ($Path -like "src/test/java/server/agents/*" -and !(Test-InactiveAgentPrepPath $Path)) -or
        $Path -like "src/test/java/server/bots/*" -or
        $Path -eq "config.yaml" -or
        $Path -eq "src/main/resources/config.yaml"
    )
}

function Test-InactiveAgentPrepPath {
    param([string] $Path)

    return $false
}

function Test-SafePrepPath {
    param([string] $Path)

    return (
        $Path -like "docs/agents/*" -or
        $Path -like "docs/consoles/*" -or
        $Path -like "docs/SERVER_*" -or
        $Path -like "docs/NUTNNUT_*" -or
        $Path -like "docs/COSMIC_*" -or
        $Path -like "database-console/*" -or
        $Path -eq ".gitignore" -or
        $Path -eq "pom.xml" -or
        $Path -eq "src/main/java/net/server/Server.java" -or
        $Path -like "src/main/java/net/server/admin/*" -or
        $Path -like "tools/*" -or
        $false
    )
}

function Get-PathGroup {
    param([string] $Path)

    if ($Path -like "docs/agents/*") { return "docs-agents" }
    if ($Path -like "docs/consoles/*") { return "docs-consoles" }
    if ($Path -like "docs/SERVER_*") { return "docs-server" }
    if ($Path -like "docs/NUTNNUT_*" -or $Path -like "docs/COSMIC_*") { return "docs-review" }
    if ($Path -like "database-console/*") { return "database-console" }
    if ($Path -eq "pom.xml" -or $Path -eq "src/main/java/net/server/Server.java" -or $Path -like "src/main/java/net/server/admin/*") { return "database-console-bridge" }
    if ($Path -eq ".gitignore") { return "repo-hygiene" }
    if ($Path -like "tools/*") { return "tools" }
    return "other"
}

function ConvertTo-QuotedPath {
    param([string] $Path)

    return "'" + $Path.Replace("'", "''") + "'"
}

function New-StageCommand {
    param([object[]] $Entries)

    $paths = @($Entries | Sort-Object { $_.path } | ForEach-Object { ConvertTo-QuotedPath $_.path })
    if ($paths.Count -eq 0) {
        return $null
    }

    return "git add -- " + ($paths -join " ")
}

function New-UnstageCommand {
    param([object[]] $Entries)

    $paths = @($Entries | Sort-Object { $_.path } | ForEach-Object { ConvertTo-QuotedPath $_.path })
    if ($paths.Count -eq 0) {
        return $null
    }

    return "git restore --staged -- " + ($paths -join " ")
}

function ConvertTo-MarkdownReport {
    param([object] $Report)

    $lines = [System.Collections.Generic.List[string]]::new()
    [void] $lines.Add("# Safe-Prep Commit Candidates")
    [void] $lines.Add("")
    [void] $lines.Add("Generated: $($Report.generatedAt)")
    [void] $lines.Add("")
    [void] $lines.Add("| Field | Value |")
    [void] $lines.Add("| --- | --- |")
    [void] $lines.Add("| Status | $($Report.status) |")
    [void] $lines.Add("| Safe candidates | $(@($Report.safeCandidates).Count) |")
    [void] $lines.Add("| Excluded forbidden paths | $(@($Report.excludedForbidden).Count) |")
    [void] $lines.Add("| Needs review | $(@($Report.needsReview).Count) |")
    [void] $lines.Add("| Commit blockers | $(@($Report.commitBlockers).Count) |")
    [void] $lines.Add("| Directory candidates | $($Report.counts.directoryCandidates) |")
    [void] $lines.Add("| Safe stage ready | $($Report.safeStageReady) |")
    [void] $lines.Add("")

    [void] $lines.Add("## Safe Candidate Groups")
    [void] $lines.Add("")
    [void] $lines.Add("| Group | Count |")
    [void] $lines.Add("| --- | ---: |")
    foreach ($group in @($Report.safeCandidateGroups)) {
        [void] $lines.Add(("| {0} | {1} |" -f $group.group, $group.count))
    }
    if (@($Report.safeCandidateGroups).Count -eq 0) {
        [void] $lines.Add("| none | 0 |")
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Commit Blockers")
    [void] $lines.Add("")
    if (@($Report.commitBlockers).Count -eq 0) {
        [void] $lines.Add("- none")
    } else {
        foreach ($entry in @($Report.commitBlockers)) {
            [void] $lines.Add(("- `{0}` {1} - {2}" -f $entry.path, $entry.status, $entry.reason))
        }
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Blocker Unstage Command")
    [void] $lines.Add("")
    if ([string]::IsNullOrWhiteSpace([string] $Report.blockerUnstageCommand)) {
        [void] $lines.Add("- none")
    } else {
        [void] $lines.Add('```powershell')
        [void] $lines.Add($Report.blockerUnstageCommand)
        [void] $lines.Add('```')
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Safe Candidates")
    [void] $lines.Add("")
    if (@($Report.safeCandidates).Count -eq 0) {
        [void] $lines.Add("- none")
    } else {
        foreach ($entry in @($Report.safeCandidates)) {
            [void] $lines.Add(("- `{0}` {1} ({2})" -f $entry.path, $entry.status, $entry.group))
        }
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Safe Stage Command")
    [void] $lines.Add("")

    [void] $lines.Add("## Recommended Verification Commands")
    [void] $lines.Add("")
    foreach ($command in @($Report.recommendedVerificationCommands)) {
        [void] $lines.Add("- ``$command``")
    }
    if (@($Report.recommendedVerificationCommands).Count -eq 0) {
        [void] $lines.Add("- none")
    }
    [void] $lines.Add("")
    if ([string]::IsNullOrWhiteSpace([string] $Report.safeStageCommand)) {
        [void] $lines.Add("- none")
    } else {
        [void] $lines.Add('```powershell')
        [void] $lines.Add($Report.safeStageCommand)
        [void] $lines.Add('```')
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Excluded Forbidden Paths")
    [void] $lines.Add("")
    if (@($Report.excludedForbidden).Count -eq 0) {
        [void] $lines.Add("- none")
    } else {
        foreach ($entry in @($Report.excludedForbidden)) {
            [void] $lines.Add(("- `{0}` {1} - {2}" -f $entry.path, $entry.status, $entry.reason))
        }
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Needs Review")
    [void] $lines.Add("")
    if (@($Report.needsReview).Count -eq 0) {
        [void] $lines.Add("- none")
    } else {
        foreach ($entry in @($Report.needsReview)) {
            [void] $lines.Add(("- `{0}` {1} - {2}" -f $entry.path, $entry.status, $entry.reason))
        }
    }
    [void] $lines.Add("")
    [void] $lines.Add("## Notes")
    [void] $lines.Add("")
    [void] $lines.Add("- This report is read-only. It does not stage, unstage, or reset files.")
    [void] $lines.Add("- Safe candidates are docs/tools/server-planning paths only.")
    [void] $lines.Add("- The stage command is a review aid; inspect it before running it.")
    [void] $lines.Add("- The blocker unstage command only removes forbidden/review-required paths from the index; inspect it before running it.")
    [void] $lines.Add("- Excluded forbidden paths must not be included in a safe-prep-only commit unless explicitly requested.")

    return ($lines -join "`n")
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$safeCandidates = [System.Collections.Generic.List[object]]::new()
$excludedForbidden = [System.Collections.Generic.List[object]]::new()
$needsReview = [System.Collections.Generic.List[object]]::new()

foreach ($line in Get-GitPorcelain) {
    $status = $line.Substring(0, 2)
    $path = $line.Substring(3)
    if ($path -match " -> ") {
        $path = ($path -split " -> ", 2)[1]
    }

    $entry = [ordered]@{
        status = $status
        path = $path
        staged = ([string] $status[0] -ne " " -and [string] $status[0] -ne "?")
        unstaged = ([string] $status[1] -ne " " -or $status -eq "??")
        group = Get-PathGroup $path
        reason = ""
    }

    if (Test-ForbiddenPath $path) {
        $entry.reason = "Agent/bot/config path excluded by safe-prep boundary."
        [void] $excludedForbidden.Add($entry)
    } elseif (Test-SafePrepPath $path) {
        if ($entry.group -eq "database-console-bridge") {
            $entry.reason = "Database Console bridge integration path; must pass the local-only, token-protected default-state verifier."
        } else {
            $entry.reason = "Docs/tools/isolated-console/server-planning path allowed for safe-prep review."
        }
        [void] $safeCandidates.Add($entry)
    } else {
        $entry.reason = "Path is outside the known safe-prep allowlist and needs manual review."
        [void] $needsReview.Add($entry)
    }
}

$commitBlockers = [System.Collections.Generic.List[object]]::new()
foreach ($entry in @($excludedForbidden | Where-Object { $_.staged })) {
    [void] $commitBlockers.Add([ordered]@{
        status = $entry.status
        path = $entry.path
        reason = "Forbidden path is staged."
    })
}
foreach ($entry in @($needsReview | Where-Object { $_.staged })) {
    [void] $commitBlockers.Add([ordered]@{
        status = $entry.status
        path = $entry.path
        reason = "Review-required path is staged."
    })
}

$safeCandidateGroups = @(
    $safeCandidates |
        Group-Object { $_.group } |
        Sort-Object Name |
        ForEach-Object {
            [ordered]@{
                group = $_.Name
                count = $_.Count
            }
        }
)

$safeStageCommand = New-StageCommand @($safeCandidates)
$blockerUnstageCommand = New-UnstageCommand @($commitBlockers)
$directoryCandidates = @($safeCandidates | Where-Object { ([string] $_.path).EndsWith("/") })
$safeStageReady = ($commitBlockers.Count -eq 0 -and $needsReview.Count -eq 0 -and $directoryCandidates.Count -eq 0)
$recommendedVerificationCommands = @(
    "powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-SafePrepDiffWhitespace.ps1 -SummaryOnly -Json",
    "powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-DatabaseConsoleBridgeDefault.ps1 -SummaryOnly -Json",
    "powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-PreReconstructionDocs.ps1 -SummaryOnly -Json",
    "powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-PreReconstructionPrep.ps1 -SummaryOnly -Json"
)

$counts = [ordered]@{
    safeCandidates = @($safeCandidates).Count
    directoryCandidates = @($directoryCandidates).Count
    forbiddenExclusions = @($excludedForbidden).Count
    reviewRequired = @($needsReview).Count
    commitBlockers = @($commitBlockers).Count
    stagedSafeCandidates = @($safeCandidates | Where-Object { $_.staged }).Count
    unstagedSafeCandidates = @($safeCandidates | Where-Object { $_.unstaged }).Count
    stagedForbidden = @($excludedForbidden | Where-Object { $_.staged }).Count
    unstagedForbidden = @($excludedForbidden | Where-Object { $_.unstaged }).Count
}

$overall = if ($commitBlockers.Count -gt 0) {
    "BLOCKED_FOR_SAFE_PREP_COMMIT"
} elseif ($excludedForbidden.Count -gt 0) {
    "SAFE_CANDIDATES_WITH_FORBIDDEN_EXCLUSIONS"
} elseif ($needsReview.Count -gt 0) {
    "SAFE_CANDIDATES_WITH_REVIEW_ITEMS"
} else {
    "SAFE_CANDIDATES_ONLY"
}

$safeCandidateRows = if ($SummaryOnly) { $null } else { [object[]] @($safeCandidates) }
$excludedForbiddenRows = if ($SummaryOnly) { $null } else { [object[]] @($excludedForbidden) }
$needsReviewRows = if ($SummaryOnly) { $null } else { [object[]] @($needsReview) }
$commitBlockerRows = if ($SummaryOnly) { $null } else { [object[]] @($commitBlockers) }

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    repoRoot = $repoRoot
    status = $overall
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    safeCandidateCount = $counts.safeCandidates
    directoryCandidateCount = $counts.directoryCandidates
    forbiddenExclusionCount = $counts.forbiddenExclusions
    reviewRequiredCount = $counts.reviewRequired
    commitBlockerCount = $counts.commitBlockers
    stagedForbiddenCount = $counts.stagedForbidden
    unstagedForbiddenCount = $counts.unstagedForbidden
    returnedSafeCandidateCount = if ($SummaryOnly) { 0 } else { @($safeCandidates).Count }
    returnedDirectoryCandidateCount = if ($SummaryOnly) { 0 } else { @($directoryCandidates).Count }
    returnedForbiddenExclusionCount = if ($SummaryOnly) { 0 } else { @($excludedForbidden).Count }
    returnedReviewRequiredCount = if ($SummaryOnly) { 0 } else { @($needsReview).Count }
    returnedCommitBlockerCount = if ($SummaryOnly) { 0 } else { @($commitBlockers).Count }
    safeCandidateGroupCounts = @($safeCandidateGroups)
    hasSafeStageCommand = ![string]::IsNullOrWhiteSpace([string] $safeStageCommand)
    hasBlockerUnstageCommand = ![string]::IsNullOrWhiteSpace([string] $blockerUnstageCommand)
    safeStageReady = $safeStageReady
    recommendedVerificationCommands = @($recommendedVerificationCommands)
    recommendedVerificationCommandCount = @($recommendedVerificationCommands).Count
    counts = $counts
    safeCandidates = $safeCandidateRows
    safeCandidateGroups = @($safeCandidateGroups)
    safeStageCommand = $safeStageCommand
    blockerUnstageCommand = $blockerUnstageCommand
    excludedForbidden = $excludedForbiddenRows
    needsReview = $needsReviewRows
    commitBlockers = $commitBlockerRows
}

if ($OutputPath) {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and !(Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }

    if ($Json) {
        $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    } else {
        ConvertTo-MarkdownReport ([pscustomobject] $report) | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    }

    Write-Host "Safe-prep commit candidate report written:"
    Write-Host "  $OutputPath"
} elseif ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    ConvertTo-MarkdownReport ([pscustomobject] $report)
}

if ($FailOnBlockers -and $commitBlockers.Count -gt 0) {
    exit 1
}

if ($FailOnReviewRequired -and $needsReview.Count -gt 0) {
    exit 1
}
