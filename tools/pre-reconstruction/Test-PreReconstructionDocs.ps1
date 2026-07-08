param(
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Add-Check {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [Parameter(Mandatory = $true)] [string] $Id,
        [Parameter(Mandatory = $true)] [string] $Status,
        [Parameter(Mandatory = $true)] [string] $Message
    )

    $Checks.Add([ordered]@{
        id = $Id
        status = $Status
        message = $Message
    }) | Out-Null
}

function Get-TextFiles {
    param([string[]] $Roots)

    $files = [System.Collections.Generic.List[object]]::new()
    foreach ($root in $Roots) {
        if (!(Test-Path -LiteralPath $root)) {
            continue
        }

        foreach ($file in Get-ChildItem -LiteralPath $root -Recurse -File -Include "*.md", "*.ps1") {
            [void] $files.Add($file)
        }
    }

    return @($files)
}

function Test-NoPattern {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [object[]] $Files,
        [string] $Id,
        [string] $Pattern,
        [string] $Message
    )

    $matchLocations = [System.Collections.Generic.List[string]]::new()
    foreach ($file in $Files) {
        if ($file.FullName -like "*tools\pre-reconstruction\Test-PreReconstructionDocs.ps1") {
            continue
        }

        $lines = Get-Content -LiteralPath $file.FullName
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if ($lines[$i] -match $Pattern) {
                $relative = [string] (Resolve-Path -LiteralPath $file.FullName -Relative)
                $relative = $relative -replace '^[.][\\/]', ''
                [void] $matchLocations.Add(("{0}:{1}" -f $relative, ($i + 1)))
            }
        }
    }

    if ($matchLocations.Count -eq 0) {
        Add-Check $Checks $Id "PASS" $Message
    } else {
        Add-Check $Checks $Id "FAIL" "Found stale reference(s): $($matchLocations -join ', ')."
    }
}

function Test-FileMentions {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [string] $Path,
        [string] $Id,
        [string[]] $RequiredPatterns
    )

    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        Add-Check $Checks $Id "FAIL" "Missing required doc/tool file: $Path."
        return
    }

    $text = Get-Content -LiteralPath $Path -Raw
    $missing = @($RequiredPatterns | Where-Object { $text -notmatch [regex]::Escape($_) })
    if ($missing.Count -eq 0) {
        Add-Check $Checks $Id "PASS" "$Path mentions all required helper(s)."
    } else {
        Add-Check $Checks $Id "FAIL" "$Path is missing required mention(s): $($missing -join ', ')."
    }
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()
$textFiles = Get-TextFiles @("docs/agents", "tools")

Test-NoPattern `
    -Checks $checks `
    -Files $textFiles `
    -Id "docs:no-obsolete-stream-parameter" `
    -Pattern "(^|\s)-Stream(\s|$)" `
    -Message "No docs/tools references use the obsolete Add-BaselineSoakSample -Stream parameter."

Test-NoPattern `
    -Checks $checks `
    -Files $textFiles `
    -Id "docs:no-inactive-agent-prep-commit-exception" `
    -Pattern "inactive\s+Agent\s+capability\s+prep\s+is\s+included" `
    -Message "No docs/tools references allow inactive Agent capability prep inside the concurrent safe-prep commit lane."

Test-FileMentions `
    -Checks $checks `
    -Path "docs/agents/PRE_RECONSTRUCTION_CURRENT_GAP_REPORT.md" `
    -Id "docs:gap-report:soak-warning-helpers" `
    -RequiredPatterns @(
        "Get-BaselineSoakStatus.ps1",
        "Get-PreReconstructionHandoff.ps1",
        "Get-PreReconstructionRemainingWork.ps1",
        "Get-SafePrepCommitCandidates.ps1",
        "-FailOnReviewRequired",
        "evidence:serverhealth",
        "evidence:serverhealth-sample-count",
        "evidence:checklist",
        "MAPLE_ISLAND_AMHERST_SUBPHASE_MVP.md",
        "MAPLE_ISLAND_AMHERST_SUBPHASE_TEST_PLAN.md",
        "BLOCKED_FOR_SAFE_PREP_COMMIT",
        "do not trust fixed counts",
        "completionBlockerIds",
        "primaryRemainingExternalBlocker",
        "completionReadyExceptExternalEvidence",
        "completionNextRequiredCommand",
        "completionProgressEstimatePercent"
    )

Test-FileMentions `
    -Checks $checks `
    -Path "docs/agents/PRE_RECONSTRUCTION_SAFE_PREP_STATUS.md" `
    -Id "docs:safe-prep-status:handoff-helpers" `
    -RequiredPatterns @(
        "Get-PreReconstructionHandoff.ps1",
        "Get-PreReconstructionRemainingWork.ps1",
        "Get-SafePrepCommitCandidates.ps1",
        "Test-PreReconstructionDocs.ps1",
        "-FailOnBlockers",
        "commitBlockers",
        "remainingWork",
        "server-only diagnostics soak follow-up",
        "New-BaselineSoakEvidencePackage.ps1 -SummaryOnly -Json",
        "Add-BaselineSoakSample.ps1 -DryRun -Json",
        "Update-BaselineSoakSummary.ps1 -DryRun -SummaryOnly -Json",
        "New-BaselineSoakAuditEntry.ps1 -SummaryOnly -Json",
        "MAPLE_ISLAND_AMHERST_SUBPHASE_MVP.md",
        "MAPLE_ISLAND_AMHERST_SUBPHASE_TEST_PLAN.md",
        "completionProgressEstimatePercent",
        "completionReadyExceptExternalEvidence",
        "baseline-soak-evidence"
    )

Test-FileMentions `
    -Checks $checks `
    -Path "tools/pre-reconstruction/README.md" `
    -Id "docs:pre-reconstruction-readme:guardrails" `
    -RequiredPatterns @(
        "Get-PreReconstructionHandoff.ps1",
        "Get-PreReconstructionGoalAudit.ps1",
        "direct staged forbidden-path check",
        "Get-SafePrepCommitCandidates.ps1",
        "Test-DatabaseConsoleBridgeDefault.ps1",
        "Test-CatalogQuerySmoke.ps1",
        "Test-PreReconstructionDocs.ps1 -SummaryOnly -Json",
        "nonPassCheckIds",
        "remainingWork",
        "remainingWorkCount",
        "remainingWorkWaiting",
        "safePrepDirectoryCandidateCount",
        "directoryCandidates",
        "safeCandidateGroupCounts",
        "hasSafeStageCommand",
        "hasBlockerUnstageCommand",
        "-SummaryOnly -Json",
        "summaryOnly",
        "rowsOmitted",
        "checkCount",
        "returnedCheckCount",
        "returnedSafeCandidateCount",
        "returnedDirectoryCandidateCount",
        "remainingWorkCategoryCounts",
        "remainingWorkTrackCounts",
        "remainingWorkPackageCounts",
        "clearCount",
        "readyWithCautionCount",
        "planCardSummaryStatus",
        "planCardObjectiveCount",
        "planCardCapabilityDependencyCount",
        "catalogRuntimeReadinessStatus",
        "catalogReadyAreaIds",
        "catalogDeferredAreaIds",
        "catalogMissingAreaIds",
        "catalogBundlePrepStatus",
        "catalogBundleDeferredEntryKeys",
        "catalogBundleMissingRequiredKeys",
        "packageReadinessStatus",
        "packageReadinessReadyCount",
        "packageReadinessCount",
        "packageImplementationTrackStepIds",
        "packageScalingFirstStepIds",
        "packageGameplayStepIds",
        "packageTrackedInScalingDocsStepIds",
        "packageMissingReferenceStepIds",
        "baselineSoakNextStepIds",
        "baselineSoakNextStepCount",
        "baselineSoakNextSteps",
        "packageImplementationTrackStepCount",
        "packageImplementationTrackMissingReferenceCount",
        "baselineSoakLatestRunId",
        "baselineSoakStatus",
        "baselineSoakWarningIds",
        "baselineSoakFailureIds",
        "baselineSoakServerHealthSampleCount",
        "baselineSoakRequiredNextStepIds",
        "baselineSoakNextRequiredCommand",
        "baselineSoakExpectedServerHealthSampleCount",
        "baselineSoakChecklistCheckedCount",
        "baselineSoakChecklistItemCount",
        "root-level prep verifier fields",
        "passCount",
        "completionReadyExceptExternalEvidence",
        "completionProgressEstimatePercent",
        "safePrepForbiddenExclusions",
        "directForbiddenUnstagedCount",
        "remainingWorkStatus",
        "packageReadinessStatus",
        "implementation order coverage",
        "package readiness",
        "categoryCounts",
        "trackCounts",
        "packageCounts",
        "-ImplementationTrack gameplay",
        "-PackageId catalog-platform",
        "returnedItemCount",
        "rowsOmitted",
        "Get-AgentPackageReadiness.ps1",
        "returnedPackageCount",
        "returnedImplementationTrackCount",
        "implementationTrackStepIds",
        "scalingFirstStepIds",
        "gameplayStepIds",
        "trackedInScalingDocsStepIds",
        "missingPackageReferenceStepIds",
        "readyCount",
        "readyAfterReconstructionCount",
        "readyAfterReconstructionIds",
        "readyAfterReconstructionTrackIds",
        "readyAfterReconstructionPackageIds",
        "serverOnlyItemIds",
        "waitingForSoakEvidenceItemIds",
        "waitingForAgentRuntimeBoundaryItemIds",
        "agentGameplayItemIds",
        "agentScalingOptimisationItemIds",
        "waitingCount",
        "blockedCount",
        "-SkipPrepVerifier",
        "root-level goal audit fields",
        "passCount",
        "waitingCount",
        "blockedCount",
        "failCount",
        "nonPassItemIds",
        "completionBlockerIds",
        "primaryRemainingExternalBlocker",
        "completionReadyExceptExternalEvidence",
        "completionNextRequiredCommand",
        "completionProgressEstimatePercent",
        "baselineSoakStatus",
        "returnedBaselineSoakNextStepCount",
        "directForbiddenStagedCount",
        "itemCount",
        "returnedItemCount",
        "-Id baseline-soak-evidence",
        "-ExcludeStatus ready",
        "-SortBy category",
        "-FailOnBlocked",
        "-SummaryOnly",
        "safePrepCommitStatus",
        "safePrepCommitBlockers",
        "safePrepReviewRequired",
        "safePrepStageReady",
        "safePrepRecommendedVerificationCommandCount",
        "safePrepRecommendedVerificationCommands",
        "baselineSoakNextRequiredCommand",
        "safeCandidateCount",
        "forbiddenExclusionCount",
        "commitBlockerCount",
        "unstagedForbiddenCount",
        "safeStageReady",
        "recommendedVerificationCommands",
        "root-level handoff fields",
        "gitForbiddenStaged",
        "gitForbiddenUnstaged",
        "safePrepForbiddenExclusions",
        "safePrepCommitCandidateCount",
        "safePrepDirectoryCandidates",
        "baselineSoakWarningCount",
        "baselineSoakFailureCount",
        "completionReadyExceptExternalEvidence",
        "completionProgressEstimatePercent",
        "returnedBaselineSoakNextStepCount",
        "packageReadinessReadyCount",
        "returnedCategoryCount",
        "returnedRemainingWorkCount",
        "blockerUnstageCommand",
        "-OutputPath .\logs\pre-reconstruction\remaining-work.md",
        "-OutputPath .\logs\pre-reconstruction\remaining-work.json",
        "catalog fast lookup validation",
        "NPC/quest catalog",
        "portable Agent installer runtime",
        "server-only diagnostics soak follow-up",
        "console platform work",
        "blockerUnstageCommand",
        "staged/unstaged forbidden counts",
        "direct git forbidden counts",
        "git status --porcelain=v1 -uall",
        "-FailOnBlockers",
        "-FailOnReviewRequired",
        "zero staged blockers",
        "git restore --staged --"
    )

Test-FileMentions `
    -Checks $checks `
    -Path "tools/catalog/README.md" `
    -Id "docs:catalog-readme:runtime-readiness-summary" `
    -RequiredPatterns @(
        "Test-AllCatalogs.ps1",
        "Test-CatalogQuerySmoke.ps1",
        "Get-CatalogRuntimeReadiness.ps1",
        "Get-CatalogStatus.ps1",
        "Test-CatalogBundlePrep.ps1",
        "Update-AllCatalogs.ps1 -SkipExport -SummaryOnly -Json",
        "-SummaryOnly -Json",
        "summaryOnly",
        "rowsOmitted",
        "passCount",
        "failCount",
        "warnCount",
        "failureIds",
        "warningIds",
        "verifierCount",
        "returnedVerifierCount",
        "checkCount",
        "returnedCheckCount",
        "nonPassingCheckCount",
        "verifierSummaries",
        "nonPassingChecks",
        "returnedEntryCount",
        "manifestOmitted",
        "catalogDirectoryCount",
        "existingCatalogDirectoryCount",
        "verifierCount",
        "latestRefreshExists",
        "returnedCatalogDirectoryCount",
        "returnedRunCount",
        "returnedVerifierCount",
        "returnedReportCount",
        "statusCounts",
        "categoryCounts",
        "areaCount",
        "returnedAreaCount",
        "fileFactCount",
        "returnedFileFactCount",
        "deferredAreaCount",
        "missingAreaCount",
        "nextActionCount",
        "returnedNextActionCount",
        "manifestOmitted",
        "requiredEntryKeys",
        "deferredEntryKeys",
        "missingRequiredKeys",
        "reportCount",
        "returnedReportCount",
        "hadAgentLlmSnapshot",
        "deferredAreas",
        "missingAreas",
        "nextActions",
        "without scraping Markdown"
    )

foreach ($catalogReadme in @(
    @{ path = "tools/game-catalog/README.md"; id = "docs:game-catalog-readme:summary-output"; verifier = "Test-GameKnowledgeCatalog.ps1" },
    @{ path = "tools/npc-catalog/README.md"; id = "docs:npc-catalog-readme:summary-output"; verifier = "Test-NpcCatalog.ps1" },
    @{ path = "tools/agent-llm-catalog/README.md"; id = "docs:agent-llm-catalog-readme:summary-output"; verifier = "Test-AgentLlmCatalog.ps1" }
)) {
    Test-FileMentions `
        -Checks $checks `
        -Path $catalogReadme.path `
        -Id $catalogReadme.id `
        -RequiredPatterns @(
            $catalogReadme.verifier,
            "-SummaryOnly -Json",
            "summaryOnly",
            "rowsOmitted",
            "checkCount",
            "passCount",
            "warningIds",
            "failureIds",
            "returnedCheckCount"
        )
}

Test-FileMentions `
    -Checks $checks `
    -Path "tools/game-catalog/README.md" `
    -Id "docs:game-catalog-readme:export-summary" `
    -RequiredPatterns @(
        "Export-GameKnowledgeCatalog.ps1 -SummaryOnly -Json",
        "outputFileCount",
        "returnedOutputFileCount",
        "compact `counts` object",
        "while still writing the same generated catalog artifacts"
    )

Test-FileMentions `
    -Checks $checks `
    -Path "tools/game-catalog/README.md" `
    -Id "docs:game-catalog-readme:drop-source-gap-summary" `
    -RequiredPatterns @(
        "New-DropSourceGapReport.ps1",
        "sourceCount",
        "returnedSourceCount",
        "classCount",
        "returnedClassCount",
        "omits detailed missing source rows"
    )

Test-FileMentions `
    -Checks $checks `
    -Path "tools/npc-catalog/README.md" `
    -Id "docs:npc-catalog-readme:export-summary" `
    -RequiredPatterns @(
        "Export-NpcCatalog.ps1 -SummaryOnly -Json",
        "outputFileCount",
        "returnedOutputFileCount",
        'compact NPC `counts`',
        'compact `reviewCounts`',
        "do-not-auto-use NPCs",
        "while still writing the same generated NPC catalog artifacts"
    )

Test-FileMentions `
    -Checks $checks `
    -Path "tools/agent-llm-catalog/README.md" `
    -Id "docs:agent-llm-catalog-readme:export-summary" `
    -RequiredPatterns @(
        "Export-AgentLlmCatalog.ps1 -SummaryOnly -Json",
        "outputFileCount",
        "returnedOutputFileCount",
        'compact derived catalog `counts`',
        "Maple Island MVP rows",
        "while still writing the same generated Agent/LLM catalog artifacts"
    )

Test-FileMentions `
    -Checks $checks `
    -Path "tools/agent-llm-catalog/README.md" `
    -Id "docs:agent-llm-catalog-readme:mvp-validation-summary" `
    -RequiredPatterns @(
        "New-MapleIslandMvpValidationReport.ps1",
        "returnedQuestRuleCount",
        "returnedSpecialRuleCount",
        "returnedForbiddenActionCount",
        "detailed check, quest-rule, special-rule, and forbidden-action rows"
    )

Test-FileMentions `
    -Checks $checks `
    -Path "tools/agent-llm-catalog/README.md" `
    -Id "docs:agent-llm-catalog-readme:diff-summary" `
    -RequiredPatterns @(
        "Compare-AgentLlmCatalog.ps1",
        "Compact machine-readable diff",
        "fileCount",
        "returnedFileCount",
        "manifestCountChangeCount",
        "returnedManifestCountChangeCount",
        "oldMapleIslandQuestCount",
        "newMapleIslandQuestCount",
        "returnedMapleIslandQuestListCount",
        "omitting detailed file rows, manifest-count rows, and full old/new quest-id lists"
    )

Test-FileMentions `
    -Checks $checks `
    -Path "tools/profile-platform/README.md" `
    -Id "docs:profile-platform-readme:summary-output" `
    -RequiredPatterns @(
        "Test-AgentProfileTemplates.ps1",
        "-SummaryOnly -Json",
        "summaryOnly",
        "rowsOmitted",
        "checkCount",
        "passCount",
        "warningIds",
        "failureIds",
        "returnedCheckCount",
        "New-AgentProfileSummary.ps1",
        "llmNoteCount",
        "returnedLlmNoteCount",
        "hardConstraintFieldCount",
        "returnedHardConstraintFieldCount",
        "planPreferenceFieldCount",
        "returnedPlanPreferenceFieldCount",
        "detailBlocksOmitted"
    )

Test-FileMentions `
    -Checks $checks `
    -Path "tools/agent-contracts/README.md" `
    -Id "docs:agent-contracts-readme:summary-output" `
    -RequiredPatterns @(
        "Test-AgentContracts.ps1",
        "-SummaryOnly -Json",
        "summaryOnly",
        "rowsOmitted",
        "checkCount",
        "passCount",
        "failCount",
        "warnCount",
        "nonPassingCheckCount",
        "nonPassingCheckIds",
        "returnedCheckCount",
        "pre-reconstruction",
        "Test-PlanCardSafety.ps1",
        "Test-ProfilePatchSafety.ps1",
        "Test-PopulationDirectorContracts.ps1",
        "Test-PortableInstallerContracts.ps1",
        "Test-AgentScalingContracts.ps1",
        "Each focused verifier",
        "Focused summary mode",
        "failureIds",
        "warningIds"
    )

Test-FileMentions `
    -Checks $checks `
    -Path "tools/plan-runtime/README.md" `
    -Id "docs:plan-runtime-readme:capability-dependencies" `
    -RequiredPatterns @(
        "Get-PlanCardSummary.ps1",
        "-SummaryOnly -Json",
        "summaryOnly",
        "rowsOmitted",
        "checkCount",
        "passCount",
        "failCount",
        "warnCount",
        "failureIds",
        "warningIds",
        "returnedCheckCount",
        "returnedRouteStepCount",
        "returnedObjectiveCount",
        "capability dependencies",
        "capabilityDependencyCount",
        "returnedCapabilityDependencyCount",
        "NPC quest interaction",
        "reactor/field-object interaction",
        "controlled test injection"
    )

Test-FileMentions `
    -Checks $checks `
    -Path "tools/soak/README.md" `
    -Id "docs:soak-readme:status-helper" `
    -RequiredPatterns @(
        "Get-BaselineSoakStatus.ps1",
        "Get-BaselineSoakNextSteps.ps1",
        "recommended follow-up commands",
        "recommendedCommands",
        "summary",
        "root-level soak status fields",
        "summaryOnly",
        "rowsOmitted",
        "returnedRunCount",
        "returnedWarningCount",
        "returnedFailureCount",
        "returnedRecommendedCommandCount",
        "latestWarningIds",
        "latestFailureIds",
        "recommendedCommandCount",
        "warning/failure counts",
        "latestEvidenceSummary",
        "serverhealth sample counts",
        "expectedServerHealthSampleCount",
        "runId",
        "packageFileCount",
        "missingPackageFileCount",
        "packageFiles",
        "returnedPackageFileCount",
        "sampleCount",
        "appendedCharacterCount",
        "changedFields",
        "changedFieldCount",
        "auditStatus",
        "summaryOmitted",
        "markdownCharacterCount",
        "markdownOmitted",
        "checklist checked/unchecked counts",
        "evidence:serverhealth",
        "evidence:checklist",
        "Test-BaselineSoakEvidencePackage.ps1",
        "Test-SoakPopulationPreset.ps1",
        "checkCount",
        "passCount",
        "warningIds",
        "failureIds",
        "returnedCheckCount",
        "Set-BaselineSoakChecklistItem.ps1",
        '-List `',
        "-SummaryOnly",
        "-Json",
        "-DryRun",
        "sampleCountBefore",
        "DRY_RUN",
        "sample appender already omits raw sample text",
        "updated=false",
        "checklistItemCount",
        "checked and unchecked counts",
        "returnedItemCount",
        "operator-facing next-step report",
        "nextStepIds",
        "requiredNextStepIds",
        "nextStepCount",
        "requiredNextStepCount",
        "nextRequiredCommand",
        "uncheckedChecklistItemCount",
        "uncheckedChecklistCommands",
        "returnedNextStepCount",
        "returnedUncheckedChecklistItemCount",
        "returnedUncheckedChecklistCommandCount",
        "root level"
    )

$failures = @($checks | Where-Object { $_.status -eq "FAIL" })
$warnings = @($checks | Where-Object { $_.status -eq "WARN" })
$failCount = $failures.Count
$warnCount = $warnings.Count
$passCount = @($checks | Where-Object { $_.status -eq "PASS" }).Count
$overall = if ($failCount -gt 0) {
    "FAIL"
} elseif ($warnCount -gt 0) {
    "WARN"
} else {
    "PASS"
}

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    status = $overall
    repoRoot = $repoRoot
    checkCount = $checks.Count
    passCount = $passCount
    failCount = $failCount
    warnCount = $warnCount
    failureIds = @($failures | ForEach-Object { $_.id })
    warningIds = @($warnings | ForEach-Object { $_.id })
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    returnedCheckCount = if ($SummaryOnly) { 0 } else { $checks.Count }
    checks = if ($SummaryOnly) { $null } else { @($checks) }
}

if ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    Write-Host "Pre-reconstruction docs consistency: $overall"
    Write-Host "Repo root: $repoRoot"
    Write-Host "Checks: $($checks.Count)  Pass: $passCount  Failures: $failCount  Warnings: $warnCount"
    Write-Host ""

    if ($SummaryOnly) {
        Write-Host "Detailed check rows omitted because -SummaryOnly was used."
    } else {
        foreach ($check in $checks) {
            Write-Host ("[{0}] {1} - {2}" -f $check.status, $check.id, $check.message)
        }
    }
}

if ($failCount -gt 0) {
    exit 1
}
