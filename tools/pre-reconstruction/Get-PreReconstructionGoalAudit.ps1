param(
    [string] $OutputPath,
    [switch] $SkipPrepVerifier,
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Invoke-JsonTool {
    param(
        [string] $Path,
        [string[]] $Arguments = @()
    )

    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        return [ordered]@{
            status = "FAIL"
            message = "Missing tool at $Path."
        }
    }

    $output = & powershell -ExecutionPolicy Bypass -File $Path @Arguments -Json 2>&1
    $exitCode = $LASTEXITCODE

    try {
        $result = ($output | ConvertFrom-Json)
    } catch {
        return [ordered]@{
            status = "FAIL"
            message = ($output -join "`n")
        }
    }

    if ($exitCode -ne 0 -and $result.status -ne "FAIL" -and $result.status -ne "BLOCKED_FOR_SAFE_PREP_COMMIT") {
        $result | Add-Member -NotePropertyName status -NotePropertyValue "FAIL" -Force
    }

    return $result
}

function Get-GitLines {
    param([string[]] $Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & git @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        throw "git $($Arguments -join ' ') failed: $output"
    }

    return @(
        $output |
            Where-Object {
                ![string]::IsNullOrWhiteSpace([string] $_) -and
                "$_" -notmatch "^warning:"
            }
    )
}

function New-AuditItem {
    param(
        [string] $Id,
        [string] $Status,
        [string] $Requirement,
        [string] $Evidence,
        [string] $NextAction
    )

    return [ordered]@{
        id = $Id
        status = $Status
        requirement = $Requirement
        evidence = $Evidence
        nextAction = $NextAction
    }
}

function Get-BaselineSoakEvidenceSummary {
    param([object] $BaselineSoak)

    if ($null -ne $BaselineSoak.latestEvidenceSummary) {
        return $BaselineSoak.latestEvidenceSummary
    }

    if ($null -ne $BaselineSoak.summary -and $null -ne $BaselineSoak.summary.latestEvidenceSummary) {
        return $BaselineSoak.summary.latestEvidenceSummary
    }

    return $null
}

function Format-BaselineSoakEvidence {
    param([object] $BaselineSoak)

    $warningIds = @($BaselineSoak.summary.latestWarningIds) -join ", "
    if ([string]::IsNullOrWhiteSpace($warningIds)) {
        $warningIds = "none"
    }

    $summary = Get-BaselineSoakEvidenceSummary $BaselineSoak
    if ($null -eq $summary) {
        return "Baseline status: $($BaselineSoak.status); warning ids: $warningIds; evidence summary unavailable."
    }

    return "Baseline status: $($BaselineSoak.status); warning ids: $warningIds; serverhealth samples: $($summary.serverHealthSampleCount)/$($summary.expectedServerHealthSampleCount); startup/shutdown lines: $($summary.startupLineCount)/$($summary.shutdownLineCount); checklist checked/unchecked: $($summary.checklistCheckedCount)/$($summary.checklistUncheckedCount)."
}

function ConvertTo-MarkdownGoalAudit {
    param([object] $Report)

    $lines = [System.Collections.Generic.List[string]]::new()
    [void] $lines.Add("# Pre-Reconstruction Goal Audit")
    [void] $lines.Add("")
    [void] $lines.Add("Generated: $($Report.generatedAt)")
    [void] $lines.Add("")
    [void] $lines.Add("| Field | Value |")
    [void] $lines.Add("| --- | --- |")
    [void] $lines.Add("| Status | $($Report.status) |")
    [void] $lines.Add("| Passed | $($Report.counts.pass) |")
    [void] $lines.Add("| Waiting | $($Report.counts.waiting) |")
    [void] $lines.Add("| Blocked | $($Report.counts.blocked) |")
    [void] $lines.Add("| Failed | $($Report.counts.fail) |")
    [void] $lines.Add("")
    [void] $lines.Add("## Requirements")
    [void] $lines.Add("")
    [void] $lines.Add("| Status | Id | Requirement | Evidence | Next Action |")
    [void] $lines.Add("| --- | --- | --- | --- | --- |")
    foreach ($item in @($Report.items)) {
        [void] $lines.Add(("| {0} | `{1}` | {2} | {3} | {4} |" -f $item.status, $item.id, $item.requirement, $item.evidence, $item.nextAction))
    }
    [void] $lines.Add("")
    [void] $lines.Add("## Notes")
    [void] $lines.Add("")
    [void] $lines.Add("- This audit is read-only unless `-OutputPath` is used.")
    [void] $lines.Add("- It proves safe-prep artifact readiness only; it does not prove live Agent runtime behavior.")
    [void] $lines.Add("- A `WAITING` item means the prep is intentionally blocked on soak evidence or reconstructed Agent boundaries.")

    return ($lines -join "`n")
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$forbiddenPaths = @(
    "src/main/java/server/agents",
    "src/main/java/server/bots",
    "src/test/java/server/agents",
    "src/test/java/server/bots",
    "config.yaml",
    "src/main/resources/config.yaml"
)

function Test-InactiveAgentPrepPath {
    param([string] $Path)

    return $false
}

$directForbiddenStaged = [System.Collections.Generic.HashSet[string]]::new()
$directForbiddenUnstaged = [System.Collections.Generic.HashSet[string]]::new()
foreach ($path in $forbiddenPaths) {
    foreach ($match in @(Get-GitLines @("diff", "--cached", "--name-only", "--", $path))) {
        if (!(Test-InactiveAgentPrepPath $match)) {
            [void] $directForbiddenStaged.Add($match)
        }
    }
    foreach ($match in @(Get-GitLines @("diff", "--name-only", "--", $path))) {
        if (!(Test-InactiveAgentPrepPath $match)) {
            [void] $directForbiddenUnstaged.Add($match)
        }
    }
}

$handoff = Invoke-JsonTool "tools/pre-reconstruction/Get-PreReconstructionHandoff.ps1" @("-SkipPrepVerifier")
$baselineNextSteps = Invoke-JsonTool "tools/soak/Get-BaselineSoakNextSteps.ps1"
$prep = if ($SkipPrepVerifier) {
    [ordered]@{
        status = "SKIPPED"
        failCount = 0
        warnCount = 0
        summary = [ordered]@{
            nonPassCheckIds = @()
        }
        message = "Skipped because the caller is already running the full prep verifier."
    }
} else {
    Invoke-JsonTool "tools/pre-reconstruction/Test-PreReconstructionPrep.ps1"
}
$planSummary = Invoke-JsonTool "tools/plan-runtime/Get-PlanCardSummary.ps1"

$packageReadiness = $handoff.packageReadiness
$remainingWorkItems = @($handoff.remainingWork)
$remainingTracks = @($remainingWorkItems | ForEach-Object { $_.implementationTrack } | Where-Object { ![string]::IsNullOrWhiteSpace([string] $_) } | Select-Object -Unique)
$remainingPackages = @(
    $remainingWorkItems |
        ForEach-Object { @($_.packageIds) } |
        Where-Object { ![string]::IsNullOrWhiteSpace([string] $_) } |
        Select-Object -Unique
)
$catalogReadiness = $handoff.catalogRuntimeReadiness
$catalogVerification = $handoff.catalogVerification
$catalogBundlePrep = $handoff.catalogBundlePrep
$safeCandidates = $handoff.summary
$baselineSoak = $handoff.baselineSoak

$items = [System.Collections.Generic.List[object]]::new()

[void] $items.Add((New-AuditItem `
    "runtime-boundary" `
    $(if ($directForbiddenStaged.Count -eq 0 -and [int] $handoff.summary.gitForbiddenStaged -eq 0) { "PASS" } else { "BLOCKED" }) `
    "Safe prep is separated from staged Agent/bot/config runtime changes." `
    "Direct staged forbidden paths: $($directForbiddenStaged.Count); direct unstaged forbidden paths: $($directForbiddenUnstaged.Count); handoff staged forbidden paths: $($handoff.summary.gitForbiddenStaged); handoff unstaged forbidden paths: $($handoff.summary.gitForbiddenUnstaged); safe-prep blockers: $($handoff.summary.commitBlockers); direct staged paths: $(@($directForbiddenStaged) -join ', '); direct unstaged paths: $(@($directForbiddenUnstaged) -join ', ')." `
    "Keep Agent/bot/config paths excluded from safe-prep commits."))

[void] $items.Add((New-AuditItem `
    "package-readiness" `
    $(if ($packageReadiness.status -eq "READY_WITH_DEFERRED_RUNTIME" -and [int] $packageReadiness.readyPackageCount -ge 22) { "PASS" } else { "FAIL" }) `
    "All well-defined packages have primary docs and verifier hooks." `
    "$($packageReadiness.readyPackageCount)/$($packageReadiness.packageCount) packages ready; missing docs: $($packageReadiness.missingDocCount); missing verifiers: $($packageReadiness.missingVerifierCount)." `
    "Add missing package docs or verifier hooks before implementation."))

[void] $items.Add((New-AuditItem `
    "remaining-work-routing" `
    $(if ($remainingWorkItems.Count -ge 12 -and $remainingTracks.Count -gt 0 -and $remainingPackages.Count -gt 0) { "PASS" } else { "FAIL" }) `
    "Remaining work is categorized by status, category, implementation track, and package." `
    "Remaining items: $($remainingWorkItems.Count); tracks: $($remainingTracks.Count); packages: $($remainingPackages.Count)." `
    "Keep track/package routing on every remaining-work item."))

[void] $items.Add((New-AuditItem `
    "catalog-runtime-prep" `
    $(if (($catalogReadiness.status -eq "READY" -or $catalogReadiness.status -eq "READY_WITH_DEFERRED_ITEMS") -and $catalogVerification.status -eq "PASS" -and ($catalogBundlePrep.status -eq "READY" -or $catalogBundlePrep.status -eq "READY_WITH_DEFERRED_ITEMS")) { "PASS" } else { "FAIL" }) `
    "Catalog tooling and fast lookup prep can be consumed after reconstruction." `
    "Catalog readiness: $($catalogReadiness.status); catalog verification: $($catalogVerification.status); bundle prep: $($catalogBundlePrep.status)." `
    "Keep generated catalogs read-only until runtime loader implementation."))

[void] $items.Add((New-AuditItem `
    "maple-island-mvp-prep" `
    $(if ($planSummary.status -eq "PASS" -and [int] $planSummary.summary.objectiveCount -ge 20) { "PASS" } else { "FAIL" }) `
    "Maple Island MVP has an implementation-ready plan summary." `
    "Plan: $($planSummary.planId); route steps: $($planSummary.summary.routeStepCount); objectives: $($planSummary.summary.objectiveCount); unique quests: $($planSummary.summary.uniqueQuestCount)." `
    "Implement only after reconstructed Plan/Capability/NPC boundaries are stable."))

[void] $items.Add((New-AuditItem `
    "pre-reconstruction-verifier" `
    $(if ($prep.status -eq "SKIPPED") { "PASS" } elseif ($prep.status -eq "PASS") { "PASS" } elseif ($prep.status -eq "INCOMPLETE" -and [int] $prep.failCount -eq 0) { "WAITING" } else { "FAIL" }) `
    "The full pre-reconstruction verifier runs and reports no failures." `
    "Prep status: $($prep.status); failures: $($prep.failCount); warnings: $($prep.warnCount); non-pass: $(@($prep.summary.nonPassCheckIds) -join ', ')." `
    "Resolve warnings before claiming full goal completion."))

[void] $items.Add((New-AuditItem `
    "baseline-soak-evidence" `
    $(if ($baselineSoak.status -eq "READY") { "PASS" } else { "WAITING" }) `
    "Baseline soak evidence is collected and verified." `
    (Format-BaselineSoakEvidence $baselineSoak) `
    "Fill the baseline evidence run and complete the checklist."))

[void] $items.Add((New-AuditItem `
    "safe-commit-readiness" `
    $(if ($directForbiddenStaged.Count -eq 0 -and $safeCandidates.safeStageReady) { "PASS" } else { "BLOCKED" }) `
    "The safe-prep changes can be committed without forbidden Agent/config paths." `
    "Safe candidates: $($safeCandidates.safePrepCommitCandidates); stage-ready: $($safeCandidates.safeStageReady); blockers: $($safeCandidates.commitBlockers); review-required: $($safeCandidates.reviewRequired); direct staged forbidden paths: $($directForbiddenStaged.Count)." `
    "Stage only safe candidates when making a safe-prep-only commit."))

$counts = [ordered]@{
    pass = @($items | Where-Object { $_.status -eq "PASS" }).Count
    waiting = @($items | Where-Object { $_.status -eq "WAITING" }).Count
    blocked = @($items | Where-Object { $_.status -eq "BLOCKED" }).Count
    fail = @($items | Where-Object { $_.status -eq "FAIL" }).Count
}

$status = if ($counts.fail -gt 0 -or $counts.blocked -gt 0) {
    "BLOCKED"
} elseif ($counts.waiting -gt 0) {
    "INCOMPLETE"
} else {
    "PASS"
}

$summary = [ordered]@{
    passCount = $counts.pass
    waitingCount = $counts.waiting
    blockedCount = $counts.blocked
    failCount = $counts.fail
    nonPassItemIds = @($items | Where-Object { $_.status -ne "PASS" } | ForEach-Object { $_.id })
    baselineSoakStatus = $baselineSoak.status
    baselineSoakEvidenceSummary = Get-BaselineSoakEvidenceSummary $baselineSoak
    recommendedSoakCommands = @($baselineSoak.recommendedCommands)
    catalogRuntimeReadinessStatus = $handoff.summary.catalogRuntimeReadinessStatus
    catalogReadyAreaIds = @($handoff.summary.catalogReadyAreaIds)
    catalogDeferredAreaIds = @($handoff.summary.catalogDeferredAreaIds)
    catalogMissingAreaIds = @($handoff.summary.catalogMissingAreaIds)
    catalogBundlePrepStatus = $handoff.summary.catalogBundlePrepStatus
    catalogBundleDeferredEntryKeys = @($handoff.summary.catalogBundleDeferredEntryKeys)
    catalogBundleMissingRequiredKeys = @($handoff.summary.catalogBundleMissingRequiredKeys)
    baselineSoakNextStepIds = @($baselineNextSteps.nextSteps | ForEach-Object { $_.id })
    baselineSoakRequiredNextStepIds = @($baselineNextSteps.requiredNextStepIds)
    baselineSoakNextStepCount = @($baselineNextSteps.nextSteps).Count
    baselineSoakRequiredNextStepCount = $baselineNextSteps.requiredNextStepCount
    baselineSoakNextRequiredCommand = $baselineNextSteps.nextRequiredCommand
    packageImplementationTrackStepIds = @($handoff.summary.packageImplementationTrackStepIds)
    packageScalingFirstStepIds = @($handoff.summary.packageScalingFirstStepIds)
    packageGameplayStepIds = @($handoff.summary.packageGameplayStepIds)
    packageTrackedInScalingDocsStepIds = @($handoff.summary.packageTrackedInScalingDocsStepIds)
    packageMissingReferenceStepIds = @($handoff.summary.packageMissingReferenceStepIds)
    safePrepCommitStatus = $safeCandidates.safePrepCommitStatus
    safePrepStageReady = $safeCandidates.safeStageReady
    safePrepCommitBlockers = $safeCandidates.commitBlockers
    safePrepRecommendedVerificationCommands = @($safeCandidates.recommendedVerificationCommands)
    safePrepRecommendedVerificationCommandCount = $safeCandidates.recommendedVerificationCommandCount
    safePrepWhitespaceStatus = $prep.safePrepWhitespaceStatus
    safePrepWhitespaceIssueCount = $prep.safePrepWhitespaceIssueCount
    directForbiddenStagedCount = $directForbiddenStaged.Count
    directForbiddenUnstagedCount = $directForbiddenUnstaged.Count
    directForbiddenStagedPaths = @($directForbiddenStaged | Sort-Object)
    directForbiddenUnstagedPaths = @($directForbiddenUnstaged | Sort-Object)
    gitForbiddenStagedCount = $handoff.summary.gitForbiddenStaged
    gitForbiddenUnstagedCount = $handoff.summary.gitForbiddenUnstaged
    gitForbiddenStagedPaths = @($handoff.summary.gitForbiddenStagedPaths)
    gitForbiddenUnstagedPaths = @($handoff.summary.gitForbiddenUnstagedPaths)
}

$remainingExternalBlockers = @()
if (@($summary.nonPassItemIds) -contains "baseline-soak-evidence") {
    $remainingExternalBlockers += "baseline-soak-evidence"
}

$internalNonPassItemIds = @(
    $summary.nonPassItemIds |
        Where-Object {
            $_ -ne "baseline-soak-evidence" -and
            $_ -ne "pre-reconstruction-verifier"
        }
)

$summary.completionBlockerIds = @($summary.nonPassItemIds)
$summary.primaryRemainingExternalBlocker = if ($remainingExternalBlockers.Count -gt 0) { $remainingExternalBlockers[0] } else { $null }
$summary.completionReadyExceptExternalEvidence = (
    $counts.fail -eq 0 -and
    $counts.blocked -eq 0 -and
    $internalNonPassItemIds.Count -eq 0 -and
    $remainingExternalBlockers.Count -gt 0
)
$summary.completionNextRequiredCommand = $summary.baselineSoakNextRequiredCommand
$summary.completionProgressEstimatePercent = if ($status -eq "PASS") {
    100
} elseif ($summary.completionReadyExceptExternalEvidence) {
    95
} elseif (@($items).Count -gt 0) {
    [math]::Round((100.0 * $counts.pass) / @($items).Count, 1)
} else {
    0
}

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    repoRoot = $repoRoot
    status = $status
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    itemCount = @($items).Count
    returnedItemCount = if ($SummaryOnly) { 0 } else { @($items).Count }
    passCount = $summary.passCount
    waitingCount = $summary.waitingCount
    blockedCount = $summary.blockedCount
    failCount = $summary.failCount
    nonPassItemIds = @($summary.nonPassItemIds)
    completionBlockerIds = @($summary.completionBlockerIds)
    primaryRemainingExternalBlocker = $summary.primaryRemainingExternalBlocker
    completionReadyExceptExternalEvidence = $summary.completionReadyExceptExternalEvidence
    completionNextRequiredCommand = $summary.completionNextRequiredCommand
    completionProgressEstimatePercent = $summary.completionProgressEstimatePercent
    baselineSoakStatus = $summary.baselineSoakStatus
    catalogRuntimeReadinessStatus = $summary.catalogRuntimeReadinessStatus
    catalogReadyAreaIds = @($summary.catalogReadyAreaIds)
    catalogDeferredAreaIds = @($summary.catalogDeferredAreaIds)
    catalogMissingAreaIds = @($summary.catalogMissingAreaIds)
    catalogBundlePrepStatus = $summary.catalogBundlePrepStatus
    catalogBundleDeferredEntryKeys = @($summary.catalogBundleDeferredEntryKeys)
    catalogBundleMissingRequiredKeys = @($summary.catalogBundleMissingRequiredKeys)
    baselineSoakNextStepIds = @($summary.baselineSoakNextStepIds)
    baselineSoakRequiredNextStepIds = @($summary.baselineSoakRequiredNextStepIds)
    baselineSoakNextStepCount = $summary.baselineSoakNextStepCount
    baselineSoakRequiredNextStepCount = $summary.baselineSoakRequiredNextStepCount
    baselineSoakNextRequiredCommand = $summary.baselineSoakNextRequiredCommand
    returnedBaselineSoakNextStepCount = if ($SummaryOnly) { 0 } else { @($baselineNextSteps.nextSteps).Count }
    packageImplementationTrackStepIds = @($summary.packageImplementationTrackStepIds)
    packageScalingFirstStepIds = @($summary.packageScalingFirstStepIds)
    packageGameplayStepIds = @($summary.packageGameplayStepIds)
    packageTrackedInScalingDocsStepIds = @($summary.packageTrackedInScalingDocsStepIds)
    packageMissingReferenceStepIds = @($summary.packageMissingReferenceStepIds)
    safePrepCommitStatus = $summary.safePrepCommitStatus
    safePrepStageReady = $summary.safePrepStageReady
    safePrepCommitBlockers = $summary.safePrepCommitBlockers
    safePrepRecommendedVerificationCommands = @($summary.safePrepRecommendedVerificationCommands)
    safePrepRecommendedVerificationCommandCount = $summary.safePrepRecommendedVerificationCommandCount
    safePrepWhitespaceStatus = $summary.safePrepWhitespaceStatus
    safePrepWhitespaceIssueCount = $summary.safePrepWhitespaceIssueCount
    directForbiddenStagedCount = $summary.directForbiddenStagedCount
    directForbiddenUnstagedCount = $summary.directForbiddenUnstagedCount
    directForbiddenStagedPaths = @($summary.directForbiddenStagedPaths)
    directForbiddenUnstagedPaths = @($summary.directForbiddenUnstagedPaths)
    gitForbiddenStagedCount = $summary.gitForbiddenStagedCount
    gitForbiddenUnstagedCount = $summary.gitForbiddenUnstagedCount
    gitForbiddenStagedPaths = @($summary.gitForbiddenStagedPaths)
    gitForbiddenUnstagedPaths = @($summary.gitForbiddenUnstagedPaths)
    counts = $counts
    summary = $summary
    baselineSoakNextSteps = if ($SummaryOnly) { $null } else { $baselineNextSteps }
    directForbiddenStaged = @($directForbiddenStaged)
    directForbiddenUnstaged = @($directForbiddenUnstaged)
    items = if ($SummaryOnly) { @() } else { @($items) }
}

if ($OutputPath) {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and !(Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }

    if ($Json) {
        $report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    } else {
        ConvertTo-MarkdownGoalAudit ([pscustomobject] $report) | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    }

    Write-Host "Pre-reconstruction goal audit written:"
    Write-Host "  $OutputPath"
} elseif ($Json) {
    $report | ConvertTo-Json -Depth 10
} else {
    ConvertTo-MarkdownGoalAudit ([pscustomobject] $report)
}

if ($status -eq "BLOCKED") {
    exit 1
}
