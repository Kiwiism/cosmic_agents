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

function Get-GitOutput {
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

function Test-InactiveAgentPrepPath {
    param([string] $Path)

    return $false
}

function Test-PathEntry {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [string] $Path,
        [string] $Description
    )

    if (Test-Path -LiteralPath $Path) {
        Add-Check $Checks "artifact:$Path" "PASS" "Found $Description at $Path."
    } else {
        Add-Check $Checks "artifact:$Path" "FAIL" "Missing $Description at $Path."
    }
}

function Invoke-BaselineEvidenceVerifier {
    param([string] $RunPath)

    $verifierPath = Join-Path $repoRoot "tools/soak/Test-BaselineSoakEvidencePackage.ps1"
    $json = & powershell -ExecutionPolicy Bypass -File $verifierPath -RunPath $RunPath -Json 2>&1
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        return [ordered]@{
            status = "FAIL"
            failCount = 1
            warnCount = 0
            message = ($json -join "`n")
        }
    }

    return ($json | ConvertFrom-Json)
}

function Invoke-BaselineNextSteps {
    param([string] $RunPath)

    $nextStepsPath = Join-Path $repoRoot "tools/soak/Get-BaselineSoakNextSteps.ps1"
    $json = & powershell -ExecutionPolicy Bypass -File $nextStepsPath -RunPath $RunPath -Json 2>&1
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        return [ordered]@{
            status = "FAIL"
            nextStepIds = @()
            requiredNextStepIds = @()
            nextStepCount = 0
            requiredNextStepCount = 0
            nextRequiredCommand = $null
            message = ($json -join "`n")
        }
    }

    return ($json | ConvertFrom-Json)
}

function Invoke-CatalogVerifier {
    $verifierPath = Join-Path $repoRoot "tools/catalog/Test-AllCatalogs.ps1"
    if (!(Test-Path -LiteralPath $verifierPath -PathType Leaf)) {
        return [ordered]@{
            status = "FAIL"
            failCount = 1
            warnCount = 0
            message = "Missing combined catalog verifier at $verifierPath."
        }
    }

    $json = & powershell -ExecutionPolicy Bypass -File $verifierPath -Json 2>&1
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        try {
            return ($json | ConvertFrom-Json)
        } catch {
            return [ordered]@{
                status = "FAIL"
                failCount = 1
                warnCount = 0
                message = ($json -join "`n")
            }
        }
    }

    return ($json | ConvertFrom-Json)
}

function Invoke-SoakPopulationPresetVerifier {
    $verifierPath = Join-Path $repoRoot "tools/soak/Test-SoakPopulationPreset.ps1"
    if (!(Test-Path -LiteralPath $verifierPath -PathType Leaf)) {
        return [ordered]@{
            status = "FAIL"
            failCount = 1
            warnCount = 0
            message = "Missing soak population preset verifier at $verifierPath."
        }
    }

    $json = & powershell -ExecutionPolicy Bypass -File $verifierPath -Json 2>&1
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        try {
            return ($json | ConvertFrom-Json)
        } catch {
            return [ordered]@{
                status = "FAIL"
                failCount = 1
                warnCount = 0
                message = ($json -join "`n")
            }
        }
    }

    return ($json | ConvertFrom-Json)
}

function Invoke-CatalogRuntimeReadiness {
    $verifierPath = Join-Path $repoRoot "tools/catalog/Get-CatalogRuntimeReadiness.ps1"
    if (!(Test-Path -LiteralPath $verifierPath -PathType Leaf)) {
        return [ordered]@{
            status = "FAIL"
            counts = [ordered]@{ missing = 1; deferred = 0; ready = 0 }
            message = "Missing catalog runtime readiness reporter at $verifierPath."
        }
    }

    $json = & powershell -ExecutionPolicy Bypass -File $verifierPath -Json 2>&1
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        try {
            return ($json | ConvertFrom-Json)
        } catch {
            return [ordered]@{
                status = "FAIL"
                counts = [ordered]@{ missing = 1; deferred = 0; ready = 0 }
                message = ($json -join "`n")
            }
        }
    }

    return ($json | ConvertFrom-Json)
}

function Invoke-CatalogBundlePrepVerifier {
    $verifierPath = Join-Path $repoRoot "tools/catalog/Test-CatalogBundlePrep.ps1"
    if (!(Test-Path -LiteralPath $verifierPath -PathType Leaf)) {
        return [ordered]@{
            status = "FAIL"
            counts = [ordered]@{ missingRequired = 1; deferredOptional = 0; ready = 0 }
            message = "Missing catalog bundle prep verifier at $verifierPath."
        }
    }

    $json = & powershell -ExecutionPolicy Bypass -File $verifierPath -Json 2>&1
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        try {
            return ($json | ConvertFrom-Json)
        } catch {
            return [ordered]@{
                status = "FAIL"
                counts = [ordered]@{ missingRequired = 1; deferredOptional = 0; ready = 0 }
                message = ($json -join "`n")
            }
        }
    }

    return ($json | ConvertFrom-Json)
}

function Invoke-SafePrepCommitCandidateReporter {
    $reporterPath = Join-Path $repoRoot "tools/pre-reconstruction/Get-SafePrepCommitCandidates.ps1"
    if (!(Test-Path -LiteralPath $reporterPath -PathType Leaf)) {
        return [ordered]@{
            status = "FAIL"
            counts = [ordered]@{ commitBlockers = 1; reviewRequired = 0; forbiddenExclusions = 0 }
            message = "Missing safe-prep commit candidate reporter at $reporterPath."
        }
    }

    $json = & powershell -ExecutionPolicy Bypass -File $reporterPath -Json 2>&1
    $exitCode = $LASTEXITCODE

    try {
        $report = ($json | ConvertFrom-Json)
    } catch {
        return [ordered]@{
            status = "FAIL"
            counts = [ordered]@{ commitBlockers = 1; reviewRequired = 0; forbiddenExclusions = 0 }
            message = ($json -join "`n")
        }
    }

    if ($exitCode -ne 0) {
        $report | Add-Member -NotePropertyName status -NotePropertyValue "FAIL" -Force
    }

    return $report
}

function Invoke-SafePrepWhitespaceVerifier {
    $reporterPath = Join-Path $repoRoot "tools/pre-reconstruction/Test-SafePrepDiffWhitespace.ps1"
    if (!(Test-Path -LiteralPath $reporterPath -PathType Leaf)) {
        return [ordered]@{
            status = "FAIL"
            issueCount = 1
            message = "Missing safe-prep whitespace verifier at $reporterPath."
        }
    }

    $json = & powershell -ExecutionPolicy Bypass -File $reporterPath -SummaryOnly -Json 2>&1
    $exitCode = $LASTEXITCODE
    try {
        $report = ($json | ConvertFrom-Json)
    } catch {
        return [ordered]@{
            status = "FAIL"
            issueCount = 1
            message = ($json -join "`n")
        }
    }

    if ($exitCode -ne 0 -and $report.status -ne "FAIL") {
        $report | Add-Member -NotePropertyName status -NotePropertyValue "FAIL" -Force
    }

    return $report
}

function Invoke-DatabaseConsoleBridgeDefaultVerifier {
    $verifierPath = Join-Path $repoRoot "tools/pre-reconstruction/Test-DatabaseConsoleBridgeDefault.ps1"
    if (!(Test-Path -LiteralPath $verifierPath -PathType Leaf)) {
        return [ordered]@{
            status = "FAIL"
            failCount = 1
            message = "Missing Database Console bridge default verifier at $verifierPath."
        }
    }

    $json = & powershell -ExecutionPolicy Bypass -File $verifierPath -Json 2>&1
    $exitCode = $LASTEXITCODE
    try {
        $report = ($json | ConvertFrom-Json)
    } catch {
        return [ordered]@{
            status = "FAIL"
            failCount = 1
            message = ($json -join "`n")
        }
    }

    if ($exitCode -ne 0 -and $report.status -ne "FAIL") {
        $report | Add-Member -NotePropertyName status -NotePropertyValue "FAIL" -Force
    }

    return $report
}

function Invoke-RemainingWorkReporter {
    $reporterPath = Join-Path $repoRoot "tools/pre-reconstruction/Get-PreReconstructionRemainingWork.ps1"
    if (!(Test-Path -LiteralPath $reporterPath -PathType Leaf)) {
        return [ordered]@{
            status = "FAIL"
            count = 0
            message = "Missing remaining-work reporter at $reporterPath."
        }
    }

    $json = & powershell -ExecutionPolicy Bypass -File $reporterPath -Json 2>&1
    $exitCode = $LASTEXITCODE

    try {
        $report = ($json | ConvertFrom-Json)
    } catch {
        return [ordered]@{
            status = "FAIL"
            count = 0
            message = ($json -join "`n")
        }
    }

    if ($exitCode -ne 0) {
        $report | Add-Member -NotePropertyName status -NotePropertyValue "FAIL" -Force
    }

    return $report
}

function Invoke-PlanCardSummary {
    $reporterPath = Join-Path $repoRoot "tools/plan-runtime/Get-PlanCardSummary.ps1"
    if (!(Test-Path -LiteralPath $reporterPath -PathType Leaf)) {
        return [ordered]@{
            status = "FAIL"
            failCount = 1
            warnCount = 0
            summary = [ordered]@{ routeStepCount = 0; objectiveCount = 0; uniqueQuestCount = 0 }
            message = "Missing Plan Card summary reporter at $reporterPath."
        }
    }

    $json = & powershell -ExecutionPolicy Bypass -File $reporterPath -Json 2>&1
    $exitCode = $LASTEXITCODE

    try {
        $report = ($json | ConvertFrom-Json)
    } catch {
        return [ordered]@{
            status = "FAIL"
            failCount = 1
            warnCount = 0
            summary = [ordered]@{ routeStepCount = 0; objectiveCount = 0; uniqueQuestCount = 0 }
            message = ($json -join "`n")
        }
    }

    if ($exitCode -ne 0) {
        $report | Add-Member -NotePropertyName status -NotePropertyValue "FAIL" -Force
    }

    return $report
}

function Invoke-PackageReadinessReporter {
    $reporterPath = Join-Path $repoRoot "tools/pre-reconstruction/Get-AgentPackageReadiness.ps1"
    if (!(Test-Path -LiteralPath $reporterPath -PathType Leaf)) {
        return [ordered]@{
            status = "FAIL"
            packageCount = 0
            readyPackageCount = 0
            missingDocCount = 1
            missingVerifierCount = 0
            message = "Missing Agent package readiness reporter at $reporterPath."
        }
    }

    $json = & powershell -ExecutionPolicy Bypass -File $reporterPath -Json 2>&1
    $exitCode = $LASTEXITCODE

    try {
        $report = ($json | ConvertFrom-Json)
    } catch {
        return [ordered]@{
            status = "FAIL"
            packageCount = 0
            readyPackageCount = 0
            missingDocCount = 1
            missingVerifierCount = 0
            message = ($json -join "`n")
        }
    }

    if ($exitCode -ne 0) {
        $report | Add-Member -NotePropertyName status -NotePropertyValue "FAIL" -Force
    }

    return $report
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()

$requiredArtifacts = @(
    @{ path = "docs/agents/PRE_RECONSTRUCTION_SAFE_PREP_STATUS.md"; description = "safe-prep status map" },
    @{ path = "docs/agents/PRE_RECONSTRUCTION_COMPLETION_AUDIT.md"; description = "completion/evidence audit" },
    @{ path = "docs/agents/PRE_RECONSTRUCTION_BASELINE_SOAK_RUNBOOK.md"; description = "baseline soak runbook" },
    @{ path = "docs/agents/PRE_RECONSTRUCTION_CURRENT_GAP_REPORT.md"; description = "current pre-reconstruction proof-gap report" },
    @{ path = "docs/agents/PRE_RECONSTRUCTION_GOAL_PROMPT.md"; description = "reusable goal prompt" },
    @{ path = "docs/agents/PACKAGE_REGISTRY.md"; description = "Agent package registry" },
    @{ path = "docs/agents/POST_RECONSTRUCTION_AGENT_PLATFORM_SPECIFICATION.md"; description = "post-reconstruction platform specification" },
    @{ path = "docs/agents/MAPLE_ISLAND_MVP_HANDOFF.md"; description = "Maple Island MVP handoff" },
    @{ path = "docs/agents/MAPLE_ISLAND_MVP_SEQUENCE.md"; description = "Maple Island MVP quest sequence" },
    @{ path = "docs/agents/MAPLE_ISLAND_AMHERST_SUBPHASE_MVP.md"; description = "Maple Island Amherst sub-phase MVP scope" },
    @{ path = "docs/agents/MAPLE_ISLAND_AMHERST_SUBPHASE_TEST_PLAN.md"; description = "Maple Island Amherst sub-phase test plan" },
    @{ path = "docs/agents/plans/maple-island-amherst-subphase.plan.json"; description = "Maple Island Amherst sub-phase plan card" },
    @{ path = "docs/agents/plans/maple-island-mvp.plan.json"; description = "Maple Island MVP plan card" },
    @{ path = "docs/agents/catalog-platform/CATALOG_PLATFORM_ARCHITECTURE.md"; description = "catalog platform architecture" },
    @{ path = "docs/agents/catalog-platform/CATALOG_BUNDLE_SPEC.md"; description = "catalog bundle specification" },
    @{ path = "docs/agents/catalog-platform/CATALOG_QUERY_API.md"; description = "catalog query API" },
    @{ path = "docs/agents/catalog-platform/catalog-accepted-gap.schema.json"; description = "catalog accepted-gap JSON schema" },
    @{ path = "docs/agents/catalog-platform/catalog-bundle-manifest.schema.json"; description = "catalog bundle manifest JSON schema" },
    @{ path = "docs/agents/catalog-platform/catalog-compatibility-report.schema.json"; description = "catalog compatibility report JSON schema" },
    @{ path = "docs/agents/catalog-platform/catalog-index-coverage.schema.json"; description = "catalog index coverage JSON schema" },
    @{ path = "docs/agents/catalog-platform/catalog-query-request.schema.json"; description = "catalog query request JSON schema" },
    @{ path = "docs/agents/catalog-platform/catalog-query-result.schema.json"; description = "catalog query result JSON schema" },
    @{ path = "docs/agents/catalog-platform/catalog-source-hashes.schema.json"; description = "catalog source hashes JSON schema" },
    @{ path = "docs/agents/catalog-platform/catalog-validation-finding.schema.json"; description = "catalog validation finding JSON schema" },
    @{ path = "docs/agents/catalog-platform/catalog-validation-summary.schema.json"; description = "catalog validation summary JSON schema" },
    @{ path = "docs/agents/catalog-overrides/drop-source-classifications.catalog.json"; description = "reviewed drop source classification catalog" },
    @{ path = "docs/agents/plan-runtime/PLAN_RUNTIME_DESIGN_SPECIFICATION.md"; description = "plan runtime design specification" },
    @{ path = "docs/agents/plan-runtime/PLAN_RUNTIME_TECHNICAL_SPECIFICATION.md"; description = "plan runtime technical specification" },
    @{ path = "docs/agents/plan-runtime/plan-card.schema.json"; description = "plan card JSON schema" },
    @{ path = "docs/agents/plan-runtime/plan-bundle-manifest.schema.json"; description = "plan bundle manifest JSON schema" },
    @{ path = "docs/agents/plan-runtime/plan-event.schema.json"; description = "plan event JSON schema" },
    @{ path = "docs/agents/plan-runtime/plan-progress.schema.json"; description = "plan progress JSON schema" },
    @{ path = "docs/agents/plan-runtime/objective-progress.schema.json"; description = "objective progress JSON schema" },
    @{ path = "docs/agents/plan-runtime/objective-result.schema.json"; description = "objective result JSON schema" },
    @{ path = "docs/agents/capability-runtime/CAPABILITY_RUNTIME_DESIGN_SPECIFICATION.md"; description = "capability runtime design specification" },
    @{ path = "docs/agents/capability-runtime/CAPABILITY_RUNTIME_TECHNICAL_SPECIFICATION.md"; description = "capability runtime technical specification" },
    @{ path = "docs/agents/capability-runtime/capability-command.schema.json"; description = "capability command JSON schema" },
    @{ path = "docs/agents/capability-runtime/capability-result.schema.json"; description = "capability result JSON schema" },
    @{ path = "docs/agents/npc-quest-capability/NPC_QUEST_CAPABILITY_DESIGN_SPECIFICATION.md"; description = "NPC quest capability design specification" },
    @{ path = "docs/agents/npc-quest-capability/NPC_QUEST_CAPABILITY_TECHNICAL_SPECIFICATION.md"; description = "NPC quest capability technical specification" },
    @{ path = "docs/agents/profile-platform/AGENT_PROFILE_SYSTEM_DESIGN_SPECIFICATION.md"; description = "Agent profile design specification" },
    @{ path = "docs/agents/profile-platform/AGENT_PROFILE_SYSTEM_TECHNICAL_SPECIFICATION.md"; description = "Agent profile technical specification" },
    @{ path = "docs/agents/profile-platform/agent-profile.schema.json"; description = "Agent profile JSON schema" },
    @{ path = "docs/agents/profile-platform/agent-profile-summary.schema.json"; description = "Agent profile summary JSON schema" },
    @{ path = "docs/agents/profile-platform/profile-decision-request.schema.json"; description = "profile decision request JSON schema" },
    @{ path = "docs/agents/profile-platform/profile-decision-result.schema.json"; description = "profile decision result JSON schema" },
    @{ path = "docs/agents/profile-platform/decision-journal-entry.schema.json"; description = "decision journal entry JSON schema" },
    @{ path = "docs/agents/profile-platform/relationship-memory.schema.json"; description = "relationship memory JSON schema" },
    @{ path = "docs/agents/profile-platform/agent-experience-event.schema.json"; description = "Agent experience event JSON schema" },
    @{ path = "docs/agents/profile-platform/profile-patch.schema.json"; description = "profile patch JSON schema" },
    @{ path = "docs/agents/population-director/world-population-plan.schema.json"; description = "world population plan JSON schema" },
    @{ path = "docs/agents/population-director/population-target.schema.json"; description = "population target/cohort JSON schema" },
    @{ path = "docs/agents/population-director/map-capacity-policy.schema.json"; description = "map capacity policy JSON schema" },
    @{ path = "docs/agents/population-director/population-snapshot.schema.json"; description = "population snapshot JSON schema" },
    @{ path = "docs/agents/population-director/population-assignment.schema.json"; description = "population assignment JSON schema" },
    @{ path = "docs/agents/population-director/population-rebalance-proposal.schema.json"; description = "population rebalance proposal JSON schema" },
    @{ path = "docs/agents/population-director/economic-demand-signal.schema.json"; description = "economic demand signal JSON schema" },
    @{ path = "docs/agents/llm-autonomy/ECONOMY_DESIGN_SPECIFICATION.md"; description = "economy design specification" },
    @{ path = "docs/agents/llm-autonomy/ECONOMY_TECHNICAL_IMPLEMENTATION_SPECIFICATION.md"; description = "economy technical specification" },
    @{ path = "docs/agents/llm-autonomy/economy-market-observation.schema.json"; description = "economy market observation JSON schema" },
    @{ path = "docs/agents/llm-autonomy/economy-market-item-state.schema.json"; description = "economy market item state JSON schema" },
    @{ path = "docs/agents/llm-autonomy/economy-decision.schema.json"; description = "economy decision JSON schema" },
    @{ path = "docs/agents/llm-autonomy/LLM_CONTROL_CONTRACT.md"; description = "LLM control contract" },
    @{ path = "docs/agents/llm-autonomy/llm-control-command.schema.json"; description = "LLM control command JSON schema" },
    @{ path = "docs/agents/llm-autonomy/llm-control-result.schema.json"; description = "LLM control result JSON schema" },
    @{ path = "docs/agents/event-bus/agent-event.schema.json"; description = "Agent Event Bus envelope JSON schema" },
    @{ path = "docs/agents/event-bus/agent-event-subscription.schema.json"; description = "Agent Event Bus subscription JSON schema" },
    @{ path = "docs/agents/event-bus/agent-event-replay-query.schema.json"; description = "Agent Event Bus replay query JSON schema" },
    @{ path = "docs/agents/soak-test-harness/agent-soak-scenario-manifest.schema.json"; description = "Agent soak scenario manifest JSON schema" },
    @{ path = "docs/agents/soak-test-harness/agent-soak-summary.schema.json"; description = "Agent soak summary JSON schema" },
    @{ path = "docs/agents/soak-test-harness/agent-soak-population-preset.schema.json"; description = "Agent soak population preset JSON schema" },
    @{ path = "docs/agents/soak-test-harness/presets/victoria_lt30_living_world_v1.population-preset.json"; description = "Victoria level-30 soak population preset" },
    @{ path = "docs/agents/simulation-tier-runtime/AGENT_SIMULATION_TIER_DESIGN_SPECIFICATION.md"; description = "simulation tier design specification" },
    @{ path = "docs/agents/simulation-tier-runtime/AGENT_SIMULATION_TIER_TECHNICAL_SPECIFICATION.md"; description = "simulation tier technical specification" },
    @{ path = "docs/agents/simulation-tier-runtime/simulation-tier-decision.schema.json"; description = "simulation tier decision JSON schema" },
    @{ path = "docs/agents/simulation-tier-runtime/materialization-plan.schema.json"; description = "materialization plan JSON schema" },
    @{ path = "docs/agents/background-action-runtime/background-action-request.schema.json"; description = "background action request JSON schema" },
    @{ path = "docs/agents/background-action-runtime/background-action-result.schema.json"; description = "background action result JSON schema" },
    @{ path = "docs/agents/background-action-runtime/virtual-agent-state.schema.json"; description = "virtual agent state JSON schema" },
    @{ path = "docs/agents/server-adapter/SERVER_ADAPTER_CONTRACT.md"; description = "server adapter contract" },
    @{ path = "docs/agents/server-adapter/live-agent-snapshot.schema.json"; description = "server adapter live agent snapshot JSON schema" },
    @{ path = "docs/agents/server-adapter/server-action-request.schema.json"; description = "server adapter action request JSON schema" },
    @{ path = "docs/agents/server-adapter/server-action-result.schema.json"; description = "server adapter action result JSON schema" },
    @{ path = "docs/agents/server-adapter/portable-install-manifest.schema.json"; description = "portable installer manifest JSON schema" },
    @{ path = "docs/agents/server-adapter/portable-install-plan.schema.json"; description = "portable installer plan JSON schema" },
    @{ path = "docs/agents/server-adapter/portable-patch-operation.schema.json"; description = "portable installer patch operation JSON schema" },
    @{ path = "docs/agents/server-adapter/portable-install-verify-report.schema.json"; description = "portable installer verify report JSON schema" },
    @{ path = "docs/agents/server-adapter/MINIMAL_COSMIC_EDIT_INSTALL_TARGET.md"; description = "minimal Cosmic edit target" },
    @{ path = "docs/agents/server-adapter/PORTABLE_INSTALLER_TECHNICAL_SPECIFICATION.md"; description = "portable installer technical specification" },
    @{ path = "docs/consoles/DATABASE_CONSOLE_INFORMATION_ARCHITECTURE.md"; description = "Database Console information architecture" },
    @{ path = "docs/consoles/DATABASE_CONSOLE_UI_DESIGN.md"; description = "Database Console UI design" },
    @{ path = "docs/consoles/SERVER_CONSOLE_SCOPE.md"; description = "Server Console scope" },
    @{ path = "docs/NUTNNUT_OVER_COSMIC_REVIEW.md"; description = "NuTNNuT-over-Cosmic review" },
    @{ path = "docs/COSMIC_REVERT_REVIEW.md"; description = "Cosmic revert review" },
    @{ path = "docs/SERVER_SCALE_TODO.md"; description = "server scale TODO" },
    @{ path = "docs/SERVER_PLAYER_SCALE_IMPLEMENTATION_PLAN.md"; description = "server player-scale implementation plan" },
    @{ path = "tools/soak/New-BaselineSoakEvidencePackage.ps1"; description = "baseline soak evidence scaffold" },
    @{ path = "tools/soak/Test-BaselineSoakEvidencePackage.ps1"; description = "baseline soak evidence verifier" },
    @{ path = "tools/soak/Add-BaselineSoakSample.ps1"; description = "baseline soak sample appender" },
    @{ path = "tools/soak/Update-BaselineSoakSummary.ps1"; description = "baseline soak summary updater" },
    @{ path = "tools/soak/Get-BaselineSoakStatus.ps1"; description = "baseline soak status helper" },
    @{ path = "tools/soak/Get-BaselineSoakNextSteps.ps1"; description = "baseline soak next-step helper" },
    @{ path = "tools/soak/Set-BaselineSoakChecklistItem.ps1"; description = "baseline soak checklist updater" },
    @{ path = "tools/soak/New-BaselineSoakAuditEntry.ps1"; description = "baseline soak audit-entry generator" },
    @{ path = "tools/soak/Test-SoakPopulationPreset.ps1"; description = "Agent soak population preset verifier" },
    @{ path = "tools/catalog/README.md"; description = "catalog tooling README" },
    @{ path = "tools/catalog/Test-AllCatalogs.ps1"; description = "combined catalog verification runner" },
    @{ path = "tools/catalog/Test-CatalogBundlePrep.ps1"; description = "catalog bundle prep verifier" },
    @{ path = "tools/catalog/Update-AllCatalogs.ps1"; description = "combined catalog refresh runner" },
    @{ path = "tools/catalog/Get-CatalogStatus.ps1"; description = "combined catalog status reporter" },
    @{ path = "tools/catalog/Get-CatalogRuntimeReadiness.ps1"; description = "catalog runtime-readiness reporter" },
    @{ path = "tools/catalog/Test-CatalogQuerySmoke.ps1"; description = "catalog query smoke verifier" },
    @{ path = "tools/game-catalog/Test-GameKnowledgeCatalog.ps1"; description = "game knowledge catalog verifier" },
    @{ path = "tools/game-catalog/New-DropSourceGapReport.ps1"; description = "drop source gap reporter" },
    @{ path = "tools/npc-catalog/Test-NpcCatalog.ps1"; description = "NPC catalog verifier" },
    @{ path = "tools/agent-llm-catalog/Test-AgentLlmCatalog.ps1"; description = "Agent/LLM catalog verifier" },
    @{ path = "tools/agent-llm-catalog/New-MapleIslandMvpValidationReport.ps1"; description = "Maple Island MVP validation reporter" },
    @{ path = "tools/agent-llm-catalog/Compare-AgentLlmCatalog.ps1"; description = "Agent/LLM catalog diff reporter" },
    @{ path = "tools/plan-runtime/Get-PlanCardSummary.ps1"; description = "read-only Plan Card summary loader" },
    @{ path = "tools/pre-reconstruction/Get-AgentPackageReadiness.ps1"; description = "Agent package readiness reporter" },
    @{ path = "tools/pre-reconstruction/Test-PreReconstructionDocs.ps1"; description = "pre-reconstruction docs consistency verifier" },
    @{ path = "tools/agent-contracts/Test-AgentContracts.ps1"; description = "Agent platform contract verifier" },
    @{ path = "tools/agent-contracts/Test-PlanCardSafety.ps1"; description = "Plan Card safety verifier" },
    @{ path = "tools/agent-contracts/Test-ProfilePatchSafety.ps1"; description = "Profile patch safety verifier" },
    @{ path = "tools/agent-contracts/Test-PopulationDirectorContracts.ps1"; description = "Population Director contract verifier" },
    @{ path = "tools/agent-contracts/Test-PortableInstallerContracts.ps1"; description = "Portable installer contract verifier" },
    @{ path = "tools/agent-contracts/Test-AgentScalingContracts.ps1"; description = "Agent scaling contract verifier" },
    @{ path = "tools/profile-platform/Test-AgentProfileTemplates.ps1"; description = "profile template verifier" },
    @{ path = "tools/profile-platform/New-AgentProfileSummary.ps1"; description = "profile summary generator" },
    @{ path = "tools/pre-reconstruction/Get-PreReconstructionHandoff.ps1"; description = "pre-reconstruction handoff reporter" },
    @{ path = "tools/pre-reconstruction/Get-PreReconstructionRemainingWork.ps1"; description = "pre-reconstruction remaining-work reporter" },
    @{ path = "tools/pre-reconstruction/Get-SafePrepCommitCandidates.ps1"; description = "safe-prep commit candidate reporter" },
    @{ path = "tools/pre-reconstruction/Test-SafePrepDiffWhitespace.ps1"; description = "safe-prep whitespace verifier" },
    @{ path = "tools/pre-reconstruction/Test-DatabaseConsoleBridgeDefault.ps1"; description = "Database Console bridge default-state verifier" }
)

foreach ($artifact in $requiredArtifacts) {
    Test-PathEntry $checks $artifact.path $artifact.description
}

$stagedForbiddenPaths = @(
    "src/main/java/server/agents",
    "src/main/java/server/bots",
    "src/test/java/server/agents",
    "src/test/java/server/bots",
    "config.yaml",
    "src/main/resources/config.yaml"
)

$stagedForbiddenMatches = [System.Collections.Generic.HashSet[string]]::new()
foreach ($path in $stagedForbiddenPaths) {
    $matches = @(
        Get-GitOutput @("diff", "--cached", "--name-only", "--", $path) |
            Where-Object { !(Test-InactiveAgentPrepPath $_) }
    )
    if ($matches.Count -eq 0) {
        Add-Check $checks "staged-forbidden:$path" "PASS" "No staged changes under $path."
    } else {
        foreach ($match in $matches) {
            [void] $stagedForbiddenMatches.Add($match)
        }
        Add-Check $checks "staged-forbidden:$path" "FAIL" "Forbidden staged changes found under $path."
    }
}

$unstagedForbiddenPaths = @(
    "src/main/java/server/agents",
    "src/main/java/server/bots",
    "src/test/java/server/agents",
    "src/test/java/server/bots",
    "config.yaml",
    "src/main/resources/config.yaml"
)

$unstagedForbiddenMatches = [System.Collections.Generic.HashSet[string]]::new()
foreach ($path in $unstagedForbiddenPaths) {
    $matches = @(
        Get-GitOutput @("diff", "--name-only", "--", $path) |
            Where-Object { !(Test-InactiveAgentPrepPath $_) }
    )
    if ($matches.Count -eq 0) {
        Add-Check $checks "unstaged-forbidden:$path" "PASS" "No unstaged changes under $path."
    } else {
        foreach ($match in $matches) {
            [void] $unstagedForbiddenMatches.Add($match)
        }
        Add-Check $checks "unstaged-forbidden:$path" "WARN" "Unstaged changes found under $path; verify they are intentional."
    }
}

$catalogReport = Invoke-CatalogVerifier
if ($catalogReport.status -eq "PASS") {
    Add-Check $checks "catalog:combined-verification" "PASS" "Combined catalog verification passes."
} elseif ($catalogReport.status -eq "FAIL") {
    Add-Check $checks "catalog:combined-verification" "FAIL" "Combined catalog verification fails with $($catalogReport.failCount) failure(s)."
} else {
    Add-Check $checks "catalog:combined-verification" "WARN" "Combined catalog verification is $($catalogReport.status) with $($catalogReport.warnCount) warning(s)."
}

$catalogRuntimeReadiness = Invoke-CatalogRuntimeReadiness
if ($catalogRuntimeReadiness.status -eq "READY" -or $catalogRuntimeReadiness.status -eq "READY_WITH_DEFERRED_ITEMS") {
    Add-Check $checks "catalog:runtime-readiness" "PASS" "Catalog runtime readiness is $($catalogRuntimeReadiness.status) with $($catalogRuntimeReadiness.counts.deferred) deferred area(s)."
} elseif ($catalogRuntimeReadiness.status -eq "FAIL") {
    Add-Check $checks "catalog:runtime-readiness" "FAIL" "Catalog runtime readiness reporter failed."
} else {
    Add-Check $checks "catalog:runtime-readiness" "WARN" "Catalog runtime readiness is $($catalogRuntimeReadiness.status)."
}

$catalogBundlePrep = Invoke-CatalogBundlePrepVerifier
if ($catalogBundlePrep.status -eq "READY") {
    Add-Check $checks "catalog:bundle-prep" "PASS" "Catalog bundle prep is READY with $($catalogBundlePrep.counts.ready) ready entries."
} elseif ($catalogBundlePrep.status -eq "READY_WITH_DEFERRED_ITEMS") {
    Add-Check $checks "catalog:bundle-prep" "PASS" "Catalog bundle prep is READY_WITH_DEFERRED_ITEMS with $($catalogBundlePrep.counts.deferredOptional) deferred optional entries."
} elseif ($catalogBundlePrep.status -eq "FAIL") {
    Add-Check $checks "catalog:bundle-prep" "FAIL" "Catalog bundle prep has $($catalogBundlePrep.counts.missingRequired) missing required entries."
} else {
    Add-Check $checks "catalog:bundle-prep" "WARN" "Catalog bundle prep is $($catalogBundlePrep.status)."
}

$catalogQuerySmokePath = Join-Path $repoRoot "tools/catalog/Test-CatalogQuerySmoke.ps1"
if (Test-Path -LiteralPath $catalogQuerySmokePath -PathType Leaf) {
    $catalogQuerySmokeJson = & powershell -ExecutionPolicy Bypass -File $catalogQuerySmokePath -Json 2>&1
    $catalogQuerySmokeExitCode = $LASTEXITCODE
    if ($catalogQuerySmokeExitCode -ne 0) {
        Add-Check $checks "catalog:query-smoke" "FAIL" "Catalog query smoke verifier command failed."
    } else {
        $catalogQuerySmokeReport = ($catalogQuerySmokeJson | ConvertFrom-Json)
        if ($catalogQuerySmokeReport.status -eq "PASS") {
            Add-Check $checks "catalog:query-smoke" "PASS" "Catalog query smoke verification passes."
        } elseif ($catalogQuerySmokeReport.status -eq "FAIL") {
            Add-Check $checks "catalog:query-smoke" "FAIL" "Catalog query smoke verification fails with $($catalogQuerySmokeReport.failCount) failure(s)."
        } else {
            Add-Check $checks "catalog:query-smoke" "WARN" "Catalog query smoke verification is $($catalogQuerySmokeReport.status) with $($catalogQuerySmokeReport.warnCount) warning(s)."
        }
    }
} else {
    Add-Check $checks "catalog:query-smoke" "FAIL" "Missing catalog query smoke verifier."
}

$contractVerifierPath = Join-Path $repoRoot "tools/agent-contracts/Test-AgentContracts.ps1"
if (Test-Path -LiteralPath $contractVerifierPath -PathType Leaf) {
    $contractJson = & powershell -ExecutionPolicy Bypass -File $contractVerifierPath -SummaryOnly -Json 2>&1
    $contractExitCode = $LASTEXITCODE
    if ($contractExitCode -ne 0) {
        Add-Check $checks "contracts:verification" "FAIL" "Agent contract verification command failed."
    } else {
        $contractReport = ($contractJson | ConvertFrom-Json)
        if ($contractReport.status -eq "PASS") {
            Add-Check $checks "contracts:verification" "PASS" "Agent platform contract verification passes."
        } elseif ($contractReport.status -eq "FAIL") {
            Add-Check $checks "contracts:verification" "FAIL" "Agent platform contract verification fails with $($contractReport.failCount) failure(s)."
        } else {
            Add-Check $checks "contracts:verification" "WARN" "Agent platform contract verification is $($contractReport.status) with $($contractReport.warnCount) warning(s)."
        }
    }
} else {
    Add-Check $checks "contracts:verification" "FAIL" "Missing Agent platform contract verifier."
}

$docsVerifierPath = Join-Path $repoRoot "tools/pre-reconstruction/Test-PreReconstructionDocs.ps1"
if (Test-Path -LiteralPath $docsVerifierPath -PathType Leaf) {
    $docsVerifierJson = & powershell -ExecutionPolicy Bypass -File $docsVerifierPath -Json 2>&1
    $docsVerifierExitCode = $LASTEXITCODE
    if ($docsVerifierExitCode -ne 0) {
        Add-Check $checks "docs:consistency" "FAIL" "Pre-reconstruction docs consistency verifier command failed."
    } else {
        $docsVerifierReport = ($docsVerifierJson | ConvertFrom-Json)
        if ($docsVerifierReport.status -eq "PASS") {
            Add-Check $checks "docs:consistency" "PASS" "Pre-reconstruction docs consistency verification passes."
        } elseif ($docsVerifierReport.status -eq "FAIL") {
            Add-Check $checks "docs:consistency" "FAIL" "Pre-reconstruction docs consistency verification fails with $($docsVerifierReport.failCount) failure(s)."
        } else {
            Add-Check $checks "docs:consistency" "WARN" "Pre-reconstruction docs consistency verification is $($docsVerifierReport.status) with $($docsVerifierReport.warnCount) warning(s)."
        }
    }
} else {
    Add-Check $checks "docs:consistency" "FAIL" "Missing pre-reconstruction docs consistency verifier."
}

$safePrepCommitReport = Invoke-SafePrepCommitCandidateReporter
if ($safePrepCommitReport.status -eq "FAIL") {
    Add-Check $checks "git:safe-prep-commit-candidates" "FAIL" "Safe-prep commit candidate reporter failed."
} elseif ($safePrepCommitReport.counts.commitBlockers -gt 0) {
    Add-Check $checks "git:safe-prep-commit-candidates" "FAIL" "Safe-prep commit has $($safePrepCommitReport.counts.commitBlockers) staged blocker(s)."
} elseif ($safePrepCommitReport.counts.reviewRequired -gt 0) {
    Add-Check $checks "git:safe-prep-commit-candidates" "WARN" "Safe-prep commit report has $($safePrepCommitReport.counts.reviewRequired) review-required path(s)."
} else {
    Add-Check $checks "git:safe-prep-commit-candidates" "PASS" "Safe-prep commit report has 0 blocker(s), $($safePrepCommitReport.counts.safeCandidates) safe candidate(s), and $($safePrepCommitReport.counts.forbiddenExclusions) forbidden exclusion(s)."
}

$directorySafeCandidates = @($safePrepCommitReport.safeCandidates | Where-Object { ([string] $_.path).EndsWith("/") })
if ($directorySafeCandidates.Count -gt 0) {
    Add-Check $checks "git:safe-prep-leaf-candidates" "FAIL" "Safe-prep stage candidates include directory path(s): $(@($directorySafeCandidates | ForEach-Object { $_.path }) -join ', ')."
} else {
    Add-Check $checks "git:safe-prep-leaf-candidates" "PASS" "Safe-prep stage candidates are explicit leaf paths from git status -uall."
}

if ($null -ne $safePrepCommitReport.counts.directoryCandidates -and [int] $safePrepCommitReport.counts.directoryCandidates -ne $directorySafeCandidates.Count) {
    Add-Check $checks "git:safe-prep-directory-count" "FAIL" "Safe-prep reporter directory count $($safePrepCommitReport.counts.directoryCandidates) does not match path scan count $($directorySafeCandidates.Count)."
} else {
    Add-Check $checks "git:safe-prep-directory-count" "PASS" "Safe-prep reporter directory count matches the explicit path scan."
}

$safePrepWhitespaceReport = Invoke-SafePrepWhitespaceVerifier
if ($safePrepWhitespaceReport.status -eq "FAIL") {
    Add-Check $checks "git:safe-prep-whitespace" "FAIL" "Safe-prep whitespace verifier found $($safePrepWhitespaceReport.issueCount) issue(s)."
} else {
    Add-Check $checks "git:safe-prep-whitespace" "PASS" "Safe-prep whitespace verifier found no issues across $($safePrepWhitespaceReport.trackedSafePathCount) tracked and $($safePrepWhitespaceReport.untrackedSafePathCount) untracked safe-prep path(s)."
}

$bridgeDefaultReport = Invoke-DatabaseConsoleBridgeDefaultVerifier
if ($bridgeDefaultReport.status -eq "PASS") {
    Add-Check $checks "database-console:bridge-default" "PASS" "Database Console bridge default state is verified, loopback-bound, token-protected, and explicitly disableable."
} else {
    Add-Check $checks "database-console:bridge-default" "FAIL" "Database Console bridge default verifier failed."
}

$packageReadinessReport = Invoke-PackageReadinessReporter
if ($packageReadinessReport.status -eq "FAIL") {
    Add-Check $checks "packages:readiness" "FAIL" "Agent package readiness reporter found missing docs or verifier hooks."
} elseif ([int] $packageReadinessReport.packageCount -lt 22 -or [int] $packageReadinessReport.readyPackageCount -lt 22) {
    Add-Check $checks "packages:readiness" "FAIL" "Agent package readiness coverage is below the expected package registry count."
} elseif ([int] $packageReadinessReport.implementationTrackCounts.missingPackageReferenceSteps -gt 0) {
    Add-Check $checks "packages:readiness" "FAIL" "Agent implementation track coverage references missing package id(s)."
} else {
    Add-Check $checks "packages:readiness" "PASS" "Agent package readiness covers $($packageReadinessReport.readyPackageCount)/$($packageReadinessReport.packageCount) package(s), $($packageReadinessReport.implementationTrackCounts.totalSteps) implementation-track step(s), and $($packageReadinessReport.implementationTrackCounts.trackedInScalingDocsSteps) scaling step(s) tracked without dedicated packages."
}

$remainingWorkReport = Invoke-RemainingWorkReporter
if ($remainingWorkReport.status -eq "FAIL") {
    Add-Check $checks "handoff:remaining-work" "FAIL" "Remaining-work reporter failed."
} else {
    $remainingIds = @($remainingWorkReport.items | ForEach-Object { $_.id })
    $remainingCategories = @($remainingWorkReport.items | ForEach-Object { $_.category } | Select-Object -Unique)
    $missingTrackIds = @($remainingWorkReport.items | Where-Object { [string]::IsNullOrWhiteSpace([string] $_.implementationTrack) } | ForEach-Object { $_.id })
    $missingPackageIds = @($remainingWorkReport.items | Where-Object { @($_.packageIds).Count -eq 0 } | ForEach-Object { $_.id })
    $registeredPackageIds = @($packageReadinessReport.packages | ForEach-Object { $_.id })
    $allowedOperationalPackageIds = @($packageReadinessReport.operationalPackageIds)
    $unknownRoutedPackageIds = @(
        $remainingWorkReport.items |
            ForEach-Object { @($_.packageIds) } |
            Where-Object {
                ![string]::IsNullOrWhiteSpace([string] $_) -and
                $registeredPackageIds -notcontains $_ -and
                $allowedOperationalPackageIds -notcontains $_
            } |
            Sort-Object -Unique
    )
    $missingIds = @(
        "baseline-soak-evidence",
        "catalog-fast-lookup-validation",
        "npc-quest-catalog-runtime-gap",
        "maple-island-amherst-smoke",
        "agent-scaling-runtime",
        "profile-economy-llm-runtime",
        "portable-agent-installer-runtime",
        "server-only-diagnostics-soak-followup"
    ) | Where-Object { $remainingIds -notcontains $_ }
    $missingCategories = @(
        "ready to implement after reconstruction",
        "waiting for soak evidence",
        "waiting for Agent runtime boundary",
        "server-only",
        "Agent gameplay",
        "Agent scaling/optimisation"
    ) | Where-Object { $remainingCategories -notcontains $_ }

    if ($missingIds.Count -gt 0 -or $missingCategories.Count -gt 0 -or $missingTrackIds.Count -gt 0 -or $missingPackageIds.Count -gt 0 -or $unknownRoutedPackageIds.Count -gt 0 -or $remainingWorkReport.count -lt 12) {
        Add-Check $checks "handoff:remaining-work" "FAIL" "Remaining-work reporter is missing expected backlog coverage."
    } else {
        Add-Check $checks "handoff:remaining-work" "PASS" "Remaining-work reporter returns $($remainingWorkReport.count) item(s) across expected categories with implementation tracks and package routing; all routed package ids are registered or explicitly operational."
    }
}

$planSummaryReport = Invoke-PlanCardSummary
if ($planSummaryReport.status -eq "FAIL") {
    Add-Check $checks "plan-card:summary" "FAIL" "Plan Card summary reporter failed."
} elseif ($planSummaryReport.status -ne "PASS") {
    Add-Check $checks "plan-card:summary" "WARN" "Plan Card summary is $($planSummaryReport.status) with $($planSummaryReport.warnCount) warning(s)."
} elseif ($planSummaryReport.planId -ne "maple-island-mvp") {
    Add-Check $checks "plan-card:summary" "FAIL" "Plan Card summary did not load maple-island-mvp."
} elseif ([int] $planSummaryReport.summary.routeStepCount -lt 10 -or [int] $planSummaryReport.summary.objectiveCount -lt 20 -or [int] $planSummaryReport.summary.uniqueQuestCount -lt 40 -or [int] $planSummaryReport.summary.capabilityDependencyCount -lt 5) {
    Add-Check $checks "plan-card:summary" "FAIL" "Plan Card summary counts are below expected Maple Island MVP coverage."
} else {
    Add-Check $checks "plan-card:summary" "PASS" "Plan Card summary loads $($planSummaryReport.planId) with $($planSummaryReport.summary.routeStepCount) route step(s), $($planSummaryReport.summary.objectiveCount) objective(s), $($planSummaryReport.summary.uniqueQuestCount) unique quest reference(s), and $($planSummaryReport.summary.capabilityDependencyCount) capability dependency group(s)."
}

$soakPopulationPresetReport = Invoke-SoakPopulationPresetVerifier
if ($soakPopulationPresetReport.status -eq "PASS") {
    Add-Check $checks "soak:population-presets" "PASS" "Soak population preset verification passes."
} elseif ($soakPopulationPresetReport.status -eq "FAIL") {
    Add-Check $checks "soak:population-presets" "FAIL" "Soak population preset verification fails with $($soakPopulationPresetReport.failCount) failure(s)."
} else {
    Add-Check $checks "soak:population-presets" "WARN" "Soak population preset verification is $($soakPopulationPresetReport.status) with $($soakPopulationPresetReport.warnCount) warning(s)."
}

$baselineRoot = "logs/soak/baseline"
$latestBaselineRun = $null
$baselineReport = $null
$baselineNextSteps = $null
if (Test-Path -LiteralPath $baselineRoot) {
    $runFolders = @(Get-ChildItem -LiteralPath $baselineRoot -Directory -ErrorAction SilentlyContinue)
    if ($runFolders.Count -gt 0) {
        Add-Check $checks "soak:baseline-folder" "PASS" "Found $($runFolders.Count) baseline evidence folder(s)."

        $latestBaselineRun = $runFolders | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        $baselineReport = Invoke-BaselineEvidenceVerifier $latestBaselineRun.FullName
        $baselineNextSteps = Invoke-BaselineNextSteps $latestBaselineRun.FullName

        if ($baselineReport.status -eq "PASS") {
            Add-Check $checks "soak:latest-baseline-evidence" "PASS" "Latest baseline evidence run $($latestBaselineRun.Name) verifies as PASS."
        } elseif ($baselineReport.status -eq "FAIL") {
            Add-Check $checks "soak:latest-baseline-evidence" "FAIL" "Latest baseline evidence run $($latestBaselineRun.Name) verifies as FAIL."
        } else {
            Add-Check $checks "soak:latest-baseline-evidence" "WARN" "Latest baseline evidence run $($latestBaselineRun.Name) is $($baselineReport.status) with $($baselineReport.warnCount) warning(s)."
        }
    } else {
        Add-Check $checks "soak:baseline-folder" "WARN" "Baseline evidence root exists but has no run folders."
    }
} else {
    Add-Check $checks "soak:baseline-folder" "WARN" "No baseline evidence folder exists yet; collect runtime evidence when ready."
}

$failCount = @($checks | Where-Object { $_.status -eq "FAIL" }).Count
$warnCount = @($checks | Where-Object { $_.status -eq "WARN" }).Count
$passCount = @($checks | Where-Object { $_.status -eq "PASS" }).Count
$nonPassCheckIds = @($checks | Where-Object { $_.status -ne "PASS" } | ForEach-Object { $_.id })

$overall = if ($failCount -gt 0) {
    "FAIL"
} elseif ($warnCount -gt 0) {
    "INCOMPLETE"
} else {
    "PASS"
}

$stableNonPassCheckIds = @(
    $nonPassCheckIds |
        Where-Object {
            $_ -ne "soak:latest-baseline-evidence" -and
            $_ -notlike "unstaged-forbidden:*"
        }
)
$completionReadyExceptExternalEvidence = (
    $failCount -eq 0 -and
    $safePrepCommitReport.counts.commitBlockers -eq 0 -and
    $safePrepCommitReport.counts.reviewRequired -eq 0 -and
    @($nonPassCheckIds) -contains "soak:latest-baseline-evidence" -and
    $stableNonPassCheckIds.Count -eq 0
)
$completionProgressEstimatePercent = if ($overall -eq "PASS") {
    100
} elseif ($completionReadyExceptExternalEvidence) {
    95
} elseif (@($checks).Count -gt 0) {
    [math]::Round((100.0 * $passCount) / @($checks).Count, 1)
} else {
    0
}

$report = [ordered]@{
    status = $overall
    repoRoot = $repoRoot
    failCount = $failCount
    warnCount = $warnCount
    passCount = $passCount
    nonPassCheckIds = @($nonPassCheckIds)
    completionReadyExceptExternalEvidence = $completionReadyExceptExternalEvidence
    completionProgressEstimatePercent = $completionProgressEstimatePercent
    safePrepCommitStatus = $safePrepCommitReport.status
    safePrepStageReady = $safePrepCommitReport.safeStageReady
    safePrepCommitBlockers = $safePrepCommitReport.counts.commitBlockers
    safePrepForbiddenExclusions = $safePrepCommitReport.counts.forbiddenExclusions
    safePrepReviewRequired = $safePrepCommitReport.counts.reviewRequired
    safePrepDirectoryCandidateCount = $directorySafeCandidates.Count
    safePrepRecommendedVerificationCommands = @($safePrepCommitReport.recommendedVerificationCommands)
    safePrepRecommendedVerificationCommandCount = $safePrepCommitReport.recommendedVerificationCommandCount
    safePrepWhitespaceStatus = $safePrepWhitespaceReport.status
    safePrepWhitespaceIssueCount = $safePrepWhitespaceReport.issueCount
    directForbiddenStagedCount = $stagedForbiddenMatches.Count
    directForbiddenUnstagedCount = $unstagedForbiddenMatches.Count
    catalogRuntimeReadinessStatus = $catalogRuntimeReadiness.status
    catalogReadyAreaIds = @($catalogRuntimeReadiness.readyAreaIds)
    catalogDeferredAreaIds = @($catalogRuntimeReadiness.deferredAreaIds)
    catalogMissingAreaIds = @($catalogRuntimeReadiness.missingAreaIds)
    catalogBundlePrepStatus = $catalogBundlePrep.status
    catalogBundleDeferredEntryKeys = @($catalogBundlePrep.deferredEntryKeys)
    catalogBundleMissingRequiredKeys = @($catalogBundlePrep.missingRequiredKeys)
    remainingWorkStatus = $remainingWorkReport.status
    remainingWorkCount = $remainingWorkReport.count
    remainingWorkWaiting = $remainingWorkReport.statusCounts.waiting
    planCardSummaryStatus = $planSummaryReport.status
    packageReadinessStatus = $packageReadinessReport.status
    packageReadinessCount = $packageReadinessReport.packageCount
    packageReadinessReadyCount = $packageReadinessReport.readyPackageCount
    packageImplementationTrackStepIds = @($packageReadinessReport.implementationTrackStepIds)
    packageScalingFirstStepIds = @($packageReadinessReport.scalingFirstStepIds)
    packageGameplayStepIds = @($packageReadinessReport.gameplayStepIds)
    packageTrackedInScalingDocsStepIds = @($packageReadinessReport.trackedInScalingDocsStepIds)
    packageMissingReferenceStepIds = @($packageReadinessReport.missingPackageReferenceStepIds)
    baselineSoakLatestRunId = if ($latestBaselineRun) { $latestBaselineRun.Name } else { $null }
    baselineSoakStatus = if ($baselineReport) { $baselineReport.status } else { $null }
    baselineSoakWarnCount = if ($baselineReport) { $baselineReport.warnCount } else { $null }
    baselineSoakFailCount = if ($baselineReport) { $baselineReport.failCount } else { $null }
    baselineSoakWarningIds = if ($baselineReport) { @($baselineReport.checks | Where-Object { $_.status -eq "WARN" } | ForEach-Object { $_.id }) } else { @() }
    baselineSoakFailureIds = if ($baselineReport) { @($baselineReport.checks | Where-Object { $_.status -eq "FAIL" } | ForEach-Object { $_.id }) } else { @() }
    baselineSoakNextStepIds = if ($baselineNextSteps) { @($baselineNextSteps.nextStepIds) } else { @() }
    baselineSoakRequiredNextStepIds = if ($baselineNextSteps) { @($baselineNextSteps.requiredNextStepIds) } else { @() }
    baselineSoakNextStepCount = if ($baselineNextSteps) { $baselineNextSteps.nextStepCount } else { 0 }
    baselineSoakRequiredNextStepCount = if ($baselineNextSteps) { $baselineNextSteps.requiredNextStepCount } else { 0 }
    baselineSoakNextRequiredCommand = if ($baselineNextSteps) { $baselineNextSteps.nextRequiredCommand } else { $null }
    baselineSoakServerHealthSampleCount = if ($baselineReport -and $baselineReport.evidenceSummary) { $baselineReport.evidenceSummary.serverHealthSampleCount } else { $null }
    baselineSoakExpectedServerHealthSampleCount = if ($baselineReport -and $baselineReport.evidenceSummary) { $baselineReport.evidenceSummary.expectedServerHealthSampleCount } else { $null }
    baselineSoakChecklistCheckedCount = if ($baselineReport -and $baselineReport.evidenceSummary) { $baselineReport.evidenceSummary.checklistCheckedCount } else { $null }
    baselineSoakChecklistItemCount = if ($baselineReport -and $baselineReport.evidenceSummary) { $baselineReport.evidenceSummary.checklistItemCount } else { $null }
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    returnedCheckCount = if ($SummaryOnly) { 0 } else { @($checks).Count }
    summary = [ordered]@{
        passCount = $passCount
        failCount = $failCount
        warnCount = $warnCount
        nonPassCheckIds = @($nonPassCheckIds)
        completionReadyExceptExternalEvidence = $completionReadyExceptExternalEvidence
        completionProgressEstimatePercent = $completionProgressEstimatePercent
        safePrepCommitStatus = $safePrepCommitReport.status
        safePrepStageReady = $safePrepCommitReport.safeStageReady
        safePrepCommitBlockers = $safePrepCommitReport.counts.commitBlockers
        safePrepForbiddenExclusions = $safePrepCommitReport.counts.forbiddenExclusions
        safePrepReviewRequired = $safePrepCommitReport.counts.reviewRequired
        safePrepDirectoryCandidateCount = $directorySafeCandidates.Count
        safePrepRecommendedVerificationCommands = @($safePrepCommitReport.recommendedVerificationCommands)
        safePrepRecommendedVerificationCommandCount = $safePrepCommitReport.recommendedVerificationCommandCount
        safePrepWhitespaceStatus = $safePrepWhitespaceReport.status
        safePrepWhitespaceIssueCount = $safePrepWhitespaceReport.issueCount
        directForbiddenStagedCount = $stagedForbiddenMatches.Count
        directForbiddenUnstagedCount = $unstagedForbiddenMatches.Count
        directForbiddenStagedPaths = @($stagedForbiddenMatches | Sort-Object)
        directForbiddenUnstagedPaths = @($unstagedForbiddenMatches | Sort-Object)
        catalogRuntimeReadinessStatus = $catalogRuntimeReadiness.status
        catalogReadyAreaIds = @($catalogRuntimeReadiness.readyAreaIds)
        catalogDeferredAreaIds = @($catalogRuntimeReadiness.deferredAreaIds)
        catalogMissingAreaIds = @($catalogRuntimeReadiness.missingAreaIds)
        catalogBundlePrepStatus = $catalogBundlePrep.status
        catalogBundleDeferredEntryKeys = @($catalogBundlePrep.deferredEntryKeys)
        catalogBundleMissingRequiredKeys = @($catalogBundlePrep.missingRequiredKeys)
        remainingWorkStatus = $remainingWorkReport.status
        remainingWorkCount = $remainingWorkReport.count
        remainingWorkWaiting = $remainingWorkReport.statusCounts.waiting
        remainingWorkCategoryCounts = $remainingWorkReport.categoryCounts
        remainingWorkTrackCounts = $remainingWorkReport.trackCounts
        remainingWorkPackageCounts = $remainingWorkReport.packageCounts
        planCardSummaryStatus = $planSummaryReport.status
        planCardRouteStepCount = $planSummaryReport.summary.routeStepCount
        planCardObjectiveCount = $planSummaryReport.summary.objectiveCount
        planCardUniqueQuestCount = $planSummaryReport.summary.uniqueQuestCount
        planCardCapabilityDependencyCount = $planSummaryReport.summary.capabilityDependencyCount
        packageReadinessStatus = $packageReadinessReport.status
        packageReadinessCount = $packageReadinessReport.packageCount
        packageReadinessReadyCount = $packageReadinessReport.readyPackageCount
        packageImplementationTrackStepCount = $packageReadinessReport.implementationTrackCounts.totalSteps
        packageImplementationTrackMissingReferenceCount = $packageReadinessReport.implementationTrackCounts.missingPackageReferenceSteps
        packageImplementationTrackStepIds = @($packageReadinessReport.implementationTrackStepIds)
        packageScalingFirstStepIds = @($packageReadinessReport.scalingFirstStepIds)
        packageGameplayStepIds = @($packageReadinessReport.gameplayStepIds)
        packageTrackedInScalingDocsStepIds = @($packageReadinessReport.trackedInScalingDocsStepIds)
        packageMissingReferenceStepIds = @($packageReadinessReport.missingPackageReferenceStepIds)
        baselineSoakLatestRunId = if ($latestBaselineRun) { $latestBaselineRun.Name } else { $null }
        baselineSoakStatus = if ($baselineReport) { $baselineReport.status } else { $null }
        baselineSoakWarnCount = if ($baselineReport) { $baselineReport.warnCount } else { $null }
        baselineSoakFailCount = if ($baselineReport) { $baselineReport.failCount } else { $null }
        baselineSoakWarningIds = if ($baselineReport) { @($baselineReport.checks | Where-Object { $_.status -eq "WARN" } | ForEach-Object { $_.id }) } else { @() }
        baselineSoakFailureIds = if ($baselineReport) { @($baselineReport.checks | Where-Object { $_.status -eq "FAIL" } | ForEach-Object { $_.id }) } else { @() }
        baselineSoakNextStepIds = if ($baselineNextSteps) { @($baselineNextSteps.nextStepIds) } else { @() }
        baselineSoakRequiredNextStepIds = if ($baselineNextSteps) { @($baselineNextSteps.requiredNextStepIds) } else { @() }
        baselineSoakNextStepCount = if ($baselineNextSteps) { $baselineNextSteps.nextStepCount } else { 0 }
        baselineSoakRequiredNextStepCount = if ($baselineNextSteps) { $baselineNextSteps.requiredNextStepCount } else { 0 }
        baselineSoakNextRequiredCommand = if ($baselineNextSteps) { $baselineNextSteps.nextRequiredCommand } else { $null }
        baselineSoakServerHealthSampleCount = if ($baselineReport -and $baselineReport.evidenceSummary) { $baselineReport.evidenceSummary.serverHealthSampleCount } else { $null }
        baselineSoakExpectedServerHealthSampleCount = if ($baselineReport -and $baselineReport.evidenceSummary) { $baselineReport.evidenceSummary.expectedServerHealthSampleCount } else { $null }
        baselineSoakChecklistCheckedCount = if ($baselineReport -and $baselineReport.evidenceSummary) { $baselineReport.evidenceSummary.checklistCheckedCount } else { $null }
        baselineSoakChecklistItemCount = if ($baselineReport -and $baselineReport.evidenceSummary) { $baselineReport.evidenceSummary.checklistItemCount } else { $null }
    }
    checks = if ($SummaryOnly) { $null } else { @($checks) }
}

if ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    Write-Host "Pre-reconstruction prep verification: $overall"
    Write-Host "Repo root: $repoRoot"
    Write-Host "Failures: $failCount  Warnings: $warnCount"
    Write-Host ""

    if ($SummaryOnly) {
        Write-Host "Passes: $passCount"
        Write-Host "Non-pass checks: $($nonPassCheckIds -join ', ')"
        Write-Host "Safe-prep commit status: $($safePrepCommitReport.status)"
        Write-Host "Baseline soak status: $(if ($baselineReport) { $baselineReport.status } else { 'none' })"
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
