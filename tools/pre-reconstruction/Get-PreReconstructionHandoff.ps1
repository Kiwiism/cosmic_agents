param(
    [string] $OutputPath,
    [switch] $Json,
    [switch] $SummaryOnly,
    [switch] $SkipPrepVerifier
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
            failCount = 1
            warnCount = 0
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
            failCount = 1
            warnCount = 0
            message = ($output -join "`n")
        }
    }

    if ($exitCode -ne 0 -and ($null -eq $result.failCount -or [int] $result.failCount -eq 0)) {
        $result | Add-Member -NotePropertyName status -NotePropertyValue "FAIL" -Force
        $result | Add-Member -NotePropertyName failCount -NotePropertyValue 1 -Force
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

function New-Category {
    param(
        [string] $Name,
        [string] $Status,
        [string] $Evidence,
        [string] $NextAction
    )

    return [ordered]@{
        name = $Name
        status = $Status
        evidence = $Evidence
        nextAction = $NextAction
    }
}

function New-RemainingWork {
    param(
        [string] $Id,
        [string] $Category,
        [string] $Status,
        [string] $Title,
        [string] $Evidence,
        [string] $NextAction,
        [string] $ImplementationTrack,
        [string[]] $PackageIds = @()
    )

    return [ordered]@{
        id = $Id
        category = $Category
        status = $Status
        title = $Title
        evidence = $Evidence
        nextAction = $NextAction
        implementationTrack = $ImplementationTrack
        packageIds = @($PackageIds)
    }
}

function Format-BaselineSoakEvidence {
    param([object] $BaselineSoak)

    $summary = $BaselineSoak.latestEvidenceSummary
    if ($null -eq $summary -and $null -ne $BaselineSoak.summary) {
        $summary = $BaselineSoak.summary.latestEvidenceSummary
    }

    $warningIds = @($BaselineSoak.summary.latestWarningIds) -join ", "
    if ([string]::IsNullOrWhiteSpace($warningIds)) {
        $warningIds = "none"
    }

    if ($null -eq $summary) {
        return "Current status: $($BaselineSoak.status); warning ids: $warningIds; evidence summary unavailable."
    }

    return "Current status: $($BaselineSoak.status); warning ids: $warningIds; serverhealth samples: $($summary.serverHealthSampleCount)/$($summary.expectedServerHealthSampleCount); startup/shutdown lines: $($summary.startupLineCount)/$($summary.shutdownLineCount); checklist checked/unchecked: $($summary.checklistCheckedCount)/$($summary.checklistUncheckedCount)."
}

function Format-BaselineSoakNextAction {
    param([object] $BaselineSoak)

    $commands = @($BaselineSoak.recommendedCommands) | Where-Object { ![string]::IsNullOrWhiteSpace([string] $_) }
    if ($commands.Count -eq 0) {
        return "Run the baseline soak status helper, add missing evidence, and complete the checklist for the latest run."
    }

    return "Run recommended command(s): $($commands -join ' ; ')"
}

function ConvertTo-MarkdownHandoff {
    param([object] $Report)

    $lines = [System.Collections.Generic.List[string]]::new()
    [void] $lines.Add("# Pre-Reconstruction Handoff Report")
    [void] $lines.Add("")
    [void] $lines.Add("Generated: $($Report.generatedAt)")
    [void] $lines.Add("")
    [void] $lines.Add("| Field | Value |")
    [void] $lines.Add("| --- | --- |")
    [void] $lines.Add("| Overall status | $($Report.status) |")
    [void] $lines.Add("| Prep verifier | $($Report.prepVerifier.status) |")
    [void] $lines.Add("| Docs consistency | $($Report.docsConsistency.status) |")
    [void] $lines.Add("| Catalog readiness | $($Report.catalogRuntimeReadiness.status) |")
    [void] $lines.Add("| Catalog verification | $($Report.catalogVerification.status) |")
    [void] $lines.Add("| Catalog bundle prep | $($Report.catalogBundlePrep.status) |")
    [void] $lines.Add("| Package readiness | $($Report.packageReadiness.status) |")
    [void] $lines.Add("| Ready packages | $($Report.packageReadiness.readyPackageCount)/$($Report.packageReadiness.packageCount) |")
    [void] $lines.Add("| Soak population presets | $($Report.soakPopulationPresets.status) |")
    [void] $lines.Add("| Baseline soak status | $($Report.baselineSoak.status) |")
    [void] $lines.Add("| Baseline soak warnings | $(@($Report.summary.baselineSoakWarnings).Count) |")
    [void] $lines.Add("| Baseline soak next steps | $($Report.summary.baselineSoakNextStepCount) |")
    [void] $lines.Add("| Completion ready except external evidence | $($Report.summary.completionReadyExceptExternalEvidence) |")
    [void] $lines.Add("| Completion progress estimate | $($Report.summary.completionProgressEstimatePercent)% |")
    [void] $lines.Add("| Safe-prep commit candidates | $($Report.summary.safePrepCommitCandidates) |")
    [void] $lines.Add("| Safe-prep directory candidates | $($Report.summary.safePrepDirectoryCandidates) |")
    [void] $lines.Add("| Commit blockers | $($Report.summary.commitBlockers) |")
    [void] $lines.Add("| Safe-prep staged forbidden | $($Report.summary.stagedForbidden) |")
    [void] $lines.Add("| Safe-prep unstaged forbidden | $($Report.summary.unstagedForbidden) |")
    [void] $lines.Add("| Git staged forbidden | $($Report.summary.gitForbiddenStaged) |")
    [void] $lines.Add("| Git unstaged forbidden | $($Report.summary.gitForbiddenUnstaged) |")
    [void] $lines.Add("| Forbidden unstaged paths | $(@($Report.git.forbiddenUnstaged).Count) |")
    [void] $lines.Add("| Forbidden staged paths | $(@($Report.git.forbiddenStaged).Count) |")
    [void] $lines.Add("")

    if (@($Report.summary.baselineSoakWarnings).Count -gt 0) {
        [void] $lines.Add("## Baseline Soak Warnings")
        [void] $lines.Add("")
        foreach ($warning in @($Report.summary.baselineSoakWarnings)) {
            [void] $lines.Add(("- `{0}`: {1}" -f $warning.id, $warning.message))
        }
        [void] $lines.Add("")
    }

    if (@($Report.baselineSoakNextSteps.nextSteps).Count -gt 0) {
        [void] $lines.Add("## Baseline Soak Next Steps")
        [void] $lines.Add("")
        foreach ($step in @($Report.baselineSoakNextSteps.nextSteps)) {
            [void] $lines.Add(("- `{0}` ({1}): {2} Evidence: {3}" -f $step.id, $step.status, $step.title, $step.evidence))
            if (![string]::IsNullOrWhiteSpace([string] $step.command)) {
                [void] $lines.Add(("  Command: ``{0}``" -f $step.command))
            }
        }
        [void] $lines.Add("")
    }

    if (@($Report.summary.recommendedSoakCommands).Count -gt 0) {
        [void] $lines.Add("## Recommended Soak Commands")
        [void] $lines.Add("")
        [void] $lines.Add('```powershell')
        foreach ($command in @($Report.summary.recommendedSoakCommands)) {
            [void] $lines.Add($command)
        }
        [void] $lines.Add('```')
        [void] $lines.Add("")
    }

    if (![string]::IsNullOrWhiteSpace([string] $Report.summary.blockerUnstageCommand)) {
        [void] $lines.Add("## Blocker Unstage Command")
        [void] $lines.Add("")
        [void] $lines.Add('```powershell')
        [void] $lines.Add($Report.summary.blockerUnstageCommand)
        [void] $lines.Add('```')
        [void] $lines.Add("")
    }

    [void] $lines.Add("## Categories")
    [void] $lines.Add("")
    [void] $lines.Add("| Category | Status | Evidence | Next Action |")
    [void] $lines.Add("| --- | --- | --- | --- |")
    foreach ($category in @($Report.categories)) {
        [void] $lines.Add(("| {0} | {1} | {2} | {3} |" -f $category.name, $category.status, $category.evidence, $category.nextAction))
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Remaining Work")
    [void] $lines.Add("")
    [void] $lines.Add("| Id | Category | Status | Track | Packages | Title | Evidence | Next Action |")
    [void] $lines.Add("| --- | --- | --- | --- | --- | --- | --- | --- |")
    foreach ($item in @($Report.remainingWork)) {
        [void] $lines.Add(("| `{0}` | {1} | {2} | `{3}` | {4} | {5} | {6} | {7} |" -f $item.id, $item.category, $item.status, $item.implementationTrack, (@($item.packageIds) -join ", "), $item.title, $item.evidence, $item.nextAction))
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Unsafe-To-Commit Paths")
    [void] $lines.Add("")
    if (@($Report.git.forbiddenStaged).Count -eq 0 -and @($Report.git.forbiddenUnstaged).Count -eq 0) {
        [void] $lines.Add("No Agent/bot/config paths are currently reported by git diff.")
    } else {
        [void] $lines.Add("Staged:")
        foreach ($path in @($Report.git.forbiddenStaged)) {
            [void] $lines.Add("- $path")
        }
        if (@($Report.git.forbiddenStaged).Count -eq 0) {
            [void] $lines.Add("- none")
        }
        [void] $lines.Add("")
        [void] $lines.Add("Unstaged:")
        foreach ($path in @($Report.git.forbiddenUnstaged)) {
            [void] $lines.Add("- $path")
        }
        if (@($Report.git.forbiddenUnstaged).Count -eq 0) {
            [void] $lines.Add("- none")
        }
    }
    [void] $lines.Add("")
    [void] $lines.Add("## Notes")
    [void] $lines.Add("")
    [void] $lines.Add('- This report is read-only unless `-OutputPath` is used.')
    [void] $lines.Add("- It does not start the server, refresh catalogs, modify config, or alter Agent runtime behavior.")
    [void] $lines.Add("- Treat any listed Agent/bot/config path as excluded from a safe-prep commit unless explicitly requested.")

    return ($lines -join "`n")
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$prepVerifier = if ($SkipPrepVerifier) {
    [ordered]@{
        status = "SKIPPED"
        failCount = 0
        warnCount = 0
        message = "Skipped to avoid recursive verifier invocation."
    }
} else {
    Invoke-JsonTool "tools/pre-reconstruction/Test-PreReconstructionPrep.ps1"
}
$docsConsistency = Invoke-JsonTool "tools/pre-reconstruction/Test-PreReconstructionDocs.ps1"
$catalogVerification = Invoke-JsonTool "tools/catalog/Test-AllCatalogs.ps1"
$catalogRuntimeReadiness = Invoke-JsonTool "tools/catalog/Get-CatalogRuntimeReadiness.ps1"
$catalogBundlePrep = Invoke-JsonTool "tools/catalog/Test-CatalogBundlePrep.ps1"
$packageReadiness = Invoke-JsonTool "tools/pre-reconstruction/Get-AgentPackageReadiness.ps1"
$soakPopulationPresets = Invoke-JsonTool "tools/soak/Test-SoakPopulationPreset.ps1"
$baselineSoak = Invoke-JsonTool "tools/soak/Get-BaselineSoakStatus.ps1"
$baselineSoakNextSteps = Invoke-JsonTool "tools/soak/Get-BaselineSoakNextSteps.ps1"
$safePrepCommitCandidates = Invoke-JsonTool "tools/pre-reconstruction/Get-SafePrepCommitCandidates.ps1"

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

$forbiddenStaged = [System.Collections.Generic.HashSet[string]]::new()
$forbiddenUnstaged = [System.Collections.Generic.HashSet[string]]::new()
foreach ($path in $forbiddenPaths) {
    foreach ($match in @(Get-GitLines @("diff", "--cached", "--name-only", "--", $path))) {
        if (![string]::IsNullOrWhiteSpace($match) -and !(Test-InactiveAgentPrepPath $match)) {
            [void] $forbiddenStaged.Add($match)
        }
    }
    foreach ($match in @(Get-GitLines @("diff", "--name-only", "--", $path))) {
        if (![string]::IsNullOrWhiteSpace($match) -and !(Test-InactiveAgentPrepPath $match)) {
            [void] $forbiddenUnstaged.Add($match)
        }
    }
}

$categories = @(
    (New-Category "ready to implement after reconstruction" "ready" "Contracts, package specs, package-readiness checks, catalog verifiers, catalog bundle-prep checks, Maple Island MVP plan artifacts, and Amherst sub-phase smoke docs are present." "Use package registry to pick one post-reconstruction slice at a time."),
    (New-Category "waiting for soak evidence" $(if ($baselineSoak.status -eq "READY") { "ready" } else { "waiting" }) "Latest baseline soak status: $($baselineSoak.status)." "Fill and verify the latest baseline evidence run."),
    (New-Category "waiting for Agent runtime boundary" "waiting" "Agent gameplay, scaling runtime, LLM command execution, profile adaptation, economy automation, and portable installer runtime hooks are intentionally deferred." "Resume after reconstructed Agent control/capability/server-adapter boundaries are stable."),
    (New-Category "server-only" "ready-with-caution" "Server-only plans and diagnostics are documented; behavior changes still need explicit approval and soak evidence." "Keep default-preserving unless a targeted server-only task is requested."),
    (New-Category "Agent gameplay" "waiting" "Amherst sub-phase, full Maple Island MVP, NPC/quest/shop, combat/loot/resupply behavior are implementation-ready specs only." "Implement Amherst smoke first after Plan/Capability runtime is stable."),
    (New-Category "Agent scaling/optimisation" "waiting" "Simulation tiers, background shortcuts, soak harness, and population presets are specified and validated as data/tooling." "Implement after scheduler/control hooks exist.")
)

$remainingWork = @(
    (New-RemainingWork "safe-prep-commit-candidates" "server-only" $(if ($safePrepCommitCandidates.counts.commitBlockers -eq 0 -and $safePrepCommitCandidates.counts.reviewRequired -eq 0) { "ready" } else { "blocked" }) "Review and commit safe-prep candidates without Agent/runtime/config paths." "Safe candidates: $($safePrepCommitCandidates.counts.safeCandidates); blockers: $($safePrepCommitCandidates.counts.commitBlockers); review-required: $($safePrepCommitCandidates.counts.reviewRequired)." "Use the generated stage command only after reviewing the candidate list." "safe-prep" @("pre-reconstruction-tools")),
    (New-RemainingWork "baseline-soak-evidence" "waiting for soak evidence" $(if ($baselineSoak.status -eq "READY") { "ready" } else { "waiting" }) "Collect and verify real baseline soak evidence." (Format-BaselineSoakEvidence $baselineSoak) (Format-BaselineSoakNextAction $baselineSoak) "soak-evidence" @("server-baseline-soak")),
    (New-RemainingWork "catalog-fast-lookup-validation" "ready to implement after reconstruction" "ready" "Use catalog smoke/readiness/bundle checks as the fast lookup acceptance gate." "Catalog verification, runtime readiness, query smoke, and bundle-prep tools are available." "Keep generated catalogs read-only until the runtime bundle loader is wired." "catalog-runtime" @("catalog-platform")),
    (New-RemainingWork "catalog-runtime-loader" "ready to implement after reconstruction" "ready" "Build the future portable catalog bundle loader and query API implementation." "Catalog docs, schemas, smoke checks, readiness checks, bundle prep checks, and package-readiness checks exist." "Start after the Agent/server adapter boundary chooses its catalog entry point." "catalog-runtime" @("catalog-platform", "server-adapter")),
    (New-RemainingWork "npc-quest-catalog-runtime-gap" "Agent gameplay" "waiting" "Connect NPC, quest, map, shop, drop, and action-affordance catalog lookups to future objectives." "NPC/game/LLM catalog tools and Maple Island validation reports exist as prep artifacts." "Wait for NPC interaction and Plan/Capability runtime APIs, then bind lookups behind a stable catalog service." "gameplay" @("npc-catalog", "npc-quest-capability", "catalog-platform", "maple-island-mvp")),
    (New-RemainingWork "maple-island-amherst-smoke" "Agent gameplay" "waiting" "Implement the Amherst sub-phase as the first Maple Island gameplay smoke slice." "MVP handoff, Amherst sub-phase spec/test plan, and plan card artifacts exist." "Wait for stable Plan Runtime, Capability Runtime, NPC interaction, navigation, combat, loot, and recovery boundaries." "gameplay" @("maple-island-mvp", "plan-runtime", "capability-runtime", "npc-quest-capability", "recovery-policy")),
    (New-RemainingWork "agent-scaling-runtime" "Agent scaling/optimisation" "waiting" "Implement simulation tiers, materialization, background actions, and soak runner." "Scaling contracts, presets, and verifier scripts exist as data/tooling." "Wait for reconstructed scheduler, visibility, persistence, and server-adapter hooks." "scaling" @("simulation-tier-runtime", "background-action-runtime", "agent-soak-test-harness", "observability")),
    (New-RemainingWork "profile-economy-llm-runtime" "waiting for Agent runtime boundary" "waiting" "Implement profile adaptation, relationship memory, decision journal, economy observation, and LLM control." "Schemas and technical specifications exist; live behavior is intentionally deferred." "Wait for event bus, profile store, economy observation source, and safe LLM command gateway." "autonomy" @("profile-platform", "social-relationship-runtime", "economy-engine", "llm-gateway", "event-bus")),
    (New-RemainingWork "portable-agent-installer-runtime" "waiting for Agent runtime boundary" "waiting" "Implement portable Agent package installer, patch planner, and verifier." "Portable installer/server-adapter schemas and specifications exist." "Wait for final reconstructed package layout and minimal Cosmic edit points." "portable-install" @("portable-installer", "server-adapter")),
    (New-RemainingWork "server-only-diagnostics-soak-followup" "server-only" "waiting" "Use baseline soak evidence to choose later server-only behavior changes." "Server diagnostics and hardening plans are documented; soak evidence is still incomplete." "Avoid behavior-changing optimization until baseline evidence identifies the next safe target." "server-only" @("server-baseline-soak")),
    (New-RemainingWork "database-server-console-platform" "server-only" "ready-with-caution" "Keep Database Console and Server Console work modular and separate from Agent runtime." "Console planning docs exist; isolated console work is allowed if it does not touch Agent reconstruction." "Use separate console boundaries and fallback-safe server overrides when implementation resumes." "console-platform" @("database-console", "server-console")),
    (New-RemainingWork "forbidden-agent-runtime-dirt" "waiting for Agent runtime boundary" $(if ($forbiddenUnstaged.Count -gt 0 -or $forbiddenStaged.Count -gt 0) { "waiting" } else { "clear" }) "Keep active Agent/bot/config paths excluded from this safe-prep lane." "Git forbidden staged: $($forbiddenStaged.Count); git forbidden unstaged: $($forbiddenUnstaged.Count)." "Do not stage these paths into a safe-prep commit unless explicitly requested." "reconstruction-boundary" @("agent-reconstruction"))
)

$hasForbiddenStaged = $forbiddenStaged.Count -gt 0
$hasForbiddenUnstaged = $forbiddenUnstaged.Count -gt 0
$hasToolFailure = @($prepVerifier, $docsConsistency, $catalogVerification, $catalogRuntimeReadiness, $catalogBundlePrep, $packageReadiness, $soakPopulationPresets, $baselineSoak | Where-Object {
    $_.status -eq "FAIL"
}).Count -gt 0

$overall = if ($hasToolFailure -or $hasForbiddenStaged) {
    "BLOCKED_FOR_SAFE_PREP_COMMIT"
} elseif ($hasForbiddenUnstaged -or $prepVerifier.status -ne "PASS" -or $baselineSoak.status -ne "READY" -or $catalogBundlePrep.status -eq "READY_WITH_DEFERRED_ITEMS") {
    "READY_WITH_WARNINGS"
} else {
    "READY"
}

$safeStageReady = if ($safePrepCommitCandidates.PSObject.Properties.Name -contains "safeStageReady") {
    [bool] $safePrepCommitCandidates.safeStageReady
} else {
    ($safePrepCommitCandidates.counts.commitBlockers -eq 0 -and
        $safePrepCommitCandidates.counts.reviewRequired -eq 0 -and
        $safePrepCommitCandidates.counts.directoryCandidates -eq 0)
}

$recommendedVerificationCommands = if ($safePrepCommitCandidates.PSObject.Properties.Name -contains "recommendedVerificationCommands") {
    @($safePrepCommitCandidates.recommendedVerificationCommands)
} else {
    @()
}

$summary = [ordered]@{
    baselineSoakWarnings = @($baselineSoak.latestWarnings)
    baselineSoakFailures = @($baselineSoak.latestFailures)
    recommendedSoakCommands = @($baselineSoak.recommendedCommands)
    catalogRuntimeReadinessStatus = $catalogRuntimeReadiness.status
    catalogReadyAreaIds = @($catalogRuntimeReadiness.readyAreaIds)
    catalogDeferredAreaIds = @($catalogRuntimeReadiness.deferredAreaIds)
    catalogMissingAreaIds = @($catalogRuntimeReadiness.missingAreaIds)
    catalogBundlePrepStatus = $catalogBundlePrep.status
    catalogBundleDeferredEntryKeys = @($catalogBundlePrep.deferredEntryKeys)
    catalogBundleMissingRequiredKeys = @($catalogBundlePrep.missingRequiredKeys)
    baselineSoakNextStepIds = @($baselineSoakNextSteps.nextSteps | ForEach-Object { $_.id })
    baselineSoakRequiredNextStepIds = @($baselineSoakNextSteps.requiredNextStepIds)
    baselineSoakNextStepCount = @($baselineSoakNextSteps.nextSteps).Count
    baselineSoakRequiredNextStepCount = $baselineSoakNextSteps.requiredNextStepCount
    baselineSoakNextRequiredCommand = $baselineSoakNextSteps.nextRequiredCommand
    packageReadinessStatus = $packageReadiness.status
    packageReadinessReadyCount = $packageReadiness.readyPackageCount
    packageReadinessCount = $packageReadiness.packageCount
    packageImplementationTrackStepIds = @($packageReadiness.implementationTrackStepIds)
    packageScalingFirstStepIds = @($packageReadiness.scalingFirstStepIds)
    packageGameplayStepIds = @($packageReadiness.gameplayStepIds)
    packageTrackedInScalingDocsStepIds = @($packageReadiness.trackedInScalingDocsStepIds)
    packageMissingReferenceStepIds = @($packageReadiness.missingPackageReferenceStepIds)
    safePrepCommitStatus = $safePrepCommitCandidates.status
    safePrepCommitCandidates = if ($safePrepCommitCandidates.counts) { $safePrepCommitCandidates.counts.safeCandidates } else { @($safePrepCommitCandidates.safeCandidates).Count }
    safePrepDirectoryCandidates = if ($safePrepCommitCandidates.counts -and $null -ne $safePrepCommitCandidates.counts.directoryCandidates) { $safePrepCommitCandidates.counts.directoryCandidates } else { @($safePrepCommitCandidates.safeCandidates | Where-Object { ([string] $_.path).EndsWith("/") }).Count }
    safeStageReady = $safeStageReady
    recommendedVerificationCommands = @($recommendedVerificationCommands)
    recommendedVerificationCommandCount = @($recommendedVerificationCommands).Count
    commitBlockers = if ($safePrepCommitCandidates.counts) { $safePrepCommitCandidates.counts.commitBlockers } else { @($safePrepCommitCandidates.commitBlockers).Count }
    forbiddenExclusions = if ($safePrepCommitCandidates.counts) { $safePrepCommitCandidates.counts.forbiddenExclusions } else { @($safePrepCommitCandidates.excludedForbidden).Count }
    reviewRequired = if ($safePrepCommitCandidates.counts) { $safePrepCommitCandidates.counts.reviewRequired } else { @($safePrepCommitCandidates.needsReview).Count }
    stagedForbidden = if ($safePrepCommitCandidates.counts) { $safePrepCommitCandidates.counts.stagedForbidden } else { @($safePrepCommitCandidates.excludedForbidden | Where-Object { $_.staged }).Count }
    unstagedForbidden = if ($safePrepCommitCandidates.counts) { $safePrepCommitCandidates.counts.unstagedForbidden } else { @($safePrepCommitCandidates.excludedForbidden | Where-Object { $_.unstaged }).Count }
    gitForbiddenStaged = @($forbiddenStaged).Count
    gitForbiddenUnstaged = @($forbiddenUnstaged).Count
    gitForbiddenStagedPaths = @($forbiddenStaged | Sort-Object)
    gitForbiddenUnstagedPaths = @($forbiddenUnstaged | Sort-Object)
    blockerUnstageCommand = $safePrepCommitCandidates.blockerUnstageCommand
}

$summary.completionReadyExceptExternalEvidence = (
    $overall -ne "PASS" -and
    $summary.commitBlockers -eq 0 -and
    $summary.reviewRequired -eq 0 -and
    ![string]::IsNullOrWhiteSpace([string] $summary.baselineSoakNextRequiredCommand)
)
$summary.completionProgressEstimatePercent = if ($overall -eq "PASS") {
    100
} elseif ($summary.completionReadyExceptExternalEvidence) {
    95
} else {
    0
}

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    repoRoot = $repoRoot
    status = $overall
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    baselineSoakStatus = $baselineSoak.status
    baselineSoakWarningIds = @($baselineSoak.latestWarnings | ForEach-Object { $_.id })
    baselineSoakFailureIds = @($baselineSoak.latestFailures | ForEach-Object { $_.id })
    baselineSoakWarningCount = @($baselineSoak.latestWarnings).Count
    baselineSoakFailureCount = @($baselineSoak.latestFailures).Count
    catalogRuntimeReadinessStatus = $summary.catalogRuntimeReadinessStatus
    catalogReadyAreaIds = @($summary.catalogReadyAreaIds)
    catalogDeferredAreaIds = @($summary.catalogDeferredAreaIds)
    catalogMissingAreaIds = @($summary.catalogMissingAreaIds)
    catalogBundlePrepStatus = $summary.catalogBundlePrepStatus
    catalogBundleDeferredEntryKeys = @($summary.catalogBundleDeferredEntryKeys)
    catalogBundleMissingRequiredKeys = @($summary.catalogBundleMissingRequiredKeys)
    safePrepCommitStatus = $summary.safePrepCommitStatus
    safePrepCommitBlockers = $summary.commitBlockers
    safePrepForbiddenExclusions = $summary.forbiddenExclusions
    safePrepReviewRequired = $summary.reviewRequired
    safePrepCommitCandidateCount = $summary.safePrepCommitCandidates
    safePrepDirectoryCandidates = $summary.safePrepDirectoryCandidates
    safePrepStageReady = $summary.safeStageReady
    safePrepRecommendedVerificationCommands = @($summary.recommendedVerificationCommands)
    safePrepRecommendedVerificationCommandCount = $summary.recommendedVerificationCommandCount
    packageReadinessStatus = $summary.packageReadinessStatus
    packageReadinessReadyCount = $summary.packageReadinessReadyCount
    packageReadinessCount = $summary.packageReadinessCount
    packageImplementationTrackStepIds = @($summary.packageImplementationTrackStepIds)
    packageScalingFirstStepIds = @($summary.packageScalingFirstStepIds)
    packageGameplayStepIds = @($summary.packageGameplayStepIds)
    packageTrackedInScalingDocsStepIds = @($summary.packageTrackedInScalingDocsStepIds)
    packageMissingReferenceStepIds = @($summary.packageMissingReferenceStepIds)
    baselineSoakNextStepIds = @($summary.baselineSoakNextStepIds)
    baselineSoakRequiredNextStepIds = @($summary.baselineSoakRequiredNextStepIds)
    baselineSoakNextStepCount = $summary.baselineSoakNextStepCount
    baselineSoakRequiredNextStepCount = $summary.baselineSoakRequiredNextStepCount
    baselineSoakNextRequiredCommand = $summary.baselineSoakNextRequiredCommand
    completionReadyExceptExternalEvidence = $summary.completionReadyExceptExternalEvidence
    completionProgressEstimatePercent = $summary.completionProgressEstimatePercent
    gitForbiddenStaged = $summary.gitForbiddenStaged
    gitForbiddenUnstaged = $summary.gitForbiddenUnstaged
    gitForbiddenStagedPaths = @($summary.gitForbiddenStagedPaths)
    gitForbiddenUnstagedPaths = @($summary.gitForbiddenUnstagedPaths)
    summary = $summary
    prepVerifier = if ($SummaryOnly) { $null } else { $prepVerifier }
    docsConsistency = if ($SummaryOnly) { $null } else { $docsConsistency }
    catalogVerification = if ($SummaryOnly) { $null } else { $catalogVerification }
    catalogRuntimeReadiness = if ($SummaryOnly) { $null } else { $catalogRuntimeReadiness }
    catalogBundlePrep = if ($SummaryOnly) { $null } else { $catalogBundlePrep }
    packageReadiness = if ($SummaryOnly) { $null } else { $packageReadiness }
    soakPopulationPresets = if ($SummaryOnly) { $null } else { $soakPopulationPresets }
    baselineSoak = if ($SummaryOnly) { $null } else { $baselineSoak }
    baselineSoakNextSteps = if ($SummaryOnly) { $null } else { $baselineSoakNextSteps }
    safePrepCommitCandidates = if ($SummaryOnly) { $null } else { $safePrepCommitCandidates }
    git = [ordered]@{
        forbiddenStaged = @($forbiddenStaged | Sort-Object)
        forbiddenUnstaged = @($forbiddenUnstaged | Sort-Object)
    }
    categoryCount = @($categories).Count
    remainingWorkCount = @($remainingWork).Count
    returnedCategoryCount = if ($SummaryOnly) { 0 } else { @($categories).Count }
    returnedRemainingWorkCount = if ($SummaryOnly) { 0 } else { @($remainingWork).Count }
    returnedBaselineSoakNextStepCount = if ($SummaryOnly) { 0 } else { @($baselineSoakNextSteps.nextSteps).Count }
    categories = if ($SummaryOnly) { $null } else { @($categories) }
    remainingWork = if ($SummaryOnly) { $null } else { @($remainingWork) }
}

if ($OutputPath) {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and !(Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }

    if ($Json) {
        $report | ConvertTo-Json -Depth 14 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    } else {
        ConvertTo-MarkdownHandoff ([pscustomobject] $report) | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    }

    Write-Host "Pre-reconstruction handoff report written:"
    Write-Host "  $OutputPath"
} elseif ($Json) {
    $report | ConvertTo-Json -Depth 14
} else {
    ConvertTo-MarkdownHandoff ([pscustomobject] $report)
}

if ($hasToolFailure -or $hasForbiddenStaged) {
    exit 1
}
