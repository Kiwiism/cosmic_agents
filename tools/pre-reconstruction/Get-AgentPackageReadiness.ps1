param(
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function New-Package {
    param(
        [string] $Id,
        [string] $Name,
        [string] $Category,
        [string[]] $RequiredDocs,
        [string[]] $VerifierPaths = @(),
        [string] $RuntimeStatus = "deferred-until-reconstruction"
    )

    return [ordered]@{
        id = $Id
        name = $Name
        category = $Category
        requiredDocs = @($RequiredDocs)
        verifierPaths = @($VerifierPaths)
        runtimeStatus = $RuntimeStatus
    }
}

function New-TrackStep {
    param(
        [string] $Track,
        [int] $Order,
        [string] $StepId,
        [string] $Label,
        [string[]] $PackageIds = @(),
        [string] $CoverageStatus = "registered-package",
        [string] $Note = ""
    )

    return [ordered]@{
        track = $Track
        order = $Order
        stepId = $StepId
        label = $Label
        packageIds = @($PackageIds)
        coverageStatus = $CoverageStatus
        note = $Note
    }
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$packages = @(
    New-Package "catalog-platform" "Catalog Platform" "catalog" @(
        "docs/agents/catalog-platform/CATALOG_PLATFORM_ARCHITECTURE.md",
        "docs/agents/catalog-platform/CATALOG_BUNDLE_SPEC.md",
        "docs/agents/catalog-platform/CATALOG_QUERY_API.md"
    ) @(
        "tools/catalog/Test-AllCatalogs.ps1",
        "tools/catalog/Get-CatalogRuntimeReadiness.ps1",
        "tools/catalog/Test-CatalogBundlePrep.ps1"
    )
    New-Package "npc-catalog" "NPC Catalog Package" "catalog" @(
        "docs/agents/NPC_CATALOG_SCHEMA.md",
        "docs/agents/NPC_CATALOG_INTEGRATION_CONTRACT.md",
        "tools/npc-catalog/README.md"
    ) @("tools/npc-catalog/Test-NpcCatalog.ps1")
    New-Package "profile-platform" "Profile Platform" "profile" @(
        "docs/agents/profile-platform/AGENT_PROFILE_SYSTEM_DESIGN_SPECIFICATION.md",
        "docs/agents/profile-platform/AGENT_PROFILE_SYSTEM_TECHNICAL_SPECIFICATION.md",
        "docs/agents/profile-platform/PROFILE_DECISION_API.md"
    ) @(
        "tools/profile-platform/Test-AgentProfileTemplates.ps1",
        "tools/agent-contracts/Test-ProfilePatchSafety.ps1"
    )
    New-Package "economy-engine" "Economy Engine" "economy" @(
        "docs/agents/llm-autonomy/ECONOMY_DESIGN_SPECIFICATION.md",
        "docs/agents/llm-autonomy/ECONOMY_TECHNICAL_IMPLEMENTATION_SPECIFICATION.md",
        "docs/agents/llm-autonomy/ECONOMY_SYSTEM_SCHEMA.md"
    )
    New-Package "server-adapter" "Server Adapter Package" "server-adapter" @(
        "docs/agents/server-adapter/SERVER_ADAPTER_CONTRACT.md",
        "docs/agents/server-adapter/MINIMAL_COSMIC_EDIT_INSTALL_TARGET.md"
    )
    New-Package "maple-island-mvp" "Maple Island MVP Package" "gameplay" @(
        "docs/agents/MAPLE_ISLAND_MVP_DESIGN_SPECIFICATION.md",
        "docs/agents/MAPLE_ISLAND_MVP_TECHNICAL_SPECIFICATION.md",
        "docs/agents/MAPLE_ISLAND_MVP_HANDOFF.md",
        "docs/agents/plans/maple-island-mvp.plan.json"
    ) @(
        "tools/agent-contracts/Test-PlanCardSafety.ps1",
        "tools/plan-runtime/Get-PlanCardSummary.ps1"
    )
    New-Package "plan-runtime" "Plan Runtime Package" "runtime-contract" @(
        "docs/agents/plan-runtime/PLAN_RUNTIME_DESIGN_SPECIFICATION.md",
        "docs/agents/plan-runtime/PLAN_RUNTIME_TECHNICAL_SPECIFICATION.md",
        "docs/agents/plan-runtime/plan-card.schema.json"
    ) @("tools/plan-runtime/Get-PlanCardSummary.ps1")
    New-Package "capability-runtime" "Capability Runtime Package" "runtime-contract" @(
        "docs/agents/capability-runtime/CAPABILITY_RUNTIME_DESIGN_SPECIFICATION.md",
        "docs/agents/capability-runtime/CAPABILITY_RUNTIME_TECHNICAL_SPECIFICATION.md",
        "docs/agents/capability-runtime/capability-command.schema.json"
    )
    New-Package "npc-quest-capability" "NPC / Quest Interaction Capability Package" "gameplay" @(
        "docs/agents/npc-quest-capability/NPC_QUEST_CAPABILITY_DESIGN_SPECIFICATION.md",
        "docs/agents/npc-quest-capability/NPC_QUEST_CAPABILITY_TECHNICAL_SPECIFICATION.md",
        "docs/agents/NPC_CAPABILITY_PLAN.md"
    )
    New-Package "event-bus" "Runtime Event Bus" "runtime-contract" @(
        "docs/agents/event-bus/AGENT_EVENT_BUS_DESIGN_SPECIFICATION.md",
        "docs/agents/event-bus/AGENT_EVENT_BUS_TECHNICAL_SPECIFICATION.md",
        "docs/agents/event-bus/agent-event.schema.json"
    )
    New-Package "recovery-policy" "Recovery / Survival Policy" "gameplay" @(
        "docs/agents/recovery-policy/AGENT_RECOVERY_POLICY_DESIGN_SPECIFICATION.md",
        "docs/agents/recovery-policy/AGENT_RECOVERY_POLICY_TECHNICAL_SPECIFICATION.md"
    )
    New-Package "observability" "Agent Observability / Diagnostics" "scaling" @(
        "docs/agents/observability/AGENT_OBSERVABILITY_DESIGN_SPECIFICATION.md",
        "docs/agents/observability/AGENT_OBSERVABILITY_TECHNICAL_SPECIFICATION.md"
    )
    New-Package "interaction-realism" "Interaction Realism Package" "gameplay" @(
        "docs/agents/interaction-realism/INTERACTION_REALISM_DESIGN_SPECIFICATION.md",
        "docs/agents/interaction-realism/INTERACTION_REALISM_TECHNICAL_SPECIFICATION.md"
    )
    New-Package "simulation-tier-runtime" "Agent Simulation Tier Runtime" "scaling" @(
        "docs/agents/simulation-tier-runtime/AGENT_SIMULATION_TIER_DESIGN_SPECIFICATION.md",
        "docs/agents/simulation-tier-runtime/AGENT_SIMULATION_TIER_TECHNICAL_SPECIFICATION.md",
        "docs/agents/simulation-tier-runtime/simulation-tier-decision.schema.json"
    ) @("tools/agent-contracts/Test-AgentScalingContracts.ps1")
    New-Package "perception-runtime" "Perception Runtime Package" "llm" @(
        "docs/agents/perception-runtime/PERCEPTION_RUNTIME_DESIGN_SPECIFICATION.md",
        "docs/agents/perception-runtime/PERCEPTION_RUNTIME_TECHNICAL_SPECIFICATION.md"
    )
    New-Package "background-action-runtime" "Background Action Runtime" "scaling" @(
        "docs/agents/background-action-runtime/BACKGROUND_ACTION_RUNTIME_DESIGN_SPECIFICATION.md",
        "docs/agents/background-action-runtime/BACKGROUND_ACTION_RUNTIME_TECHNICAL_SPECIFICATION.md",
        "docs/agents/background-action-runtime/background-action-request.schema.json"
    ) @("tools/agent-contracts/Test-AgentScalingContracts.ps1")
    New-Package "agent-soak-test-harness" "Agent Soak Test Harness" "scaling" @(
        "docs/agents/soak-test-harness/AGENT_SOAK_TEST_HARNESS_DESIGN_SPECIFICATION.md",
        "docs/agents/soak-test-harness/AGENT_SOAK_TEST_HARNESS_TECHNICAL_SPECIFICATION.md",
        "docs/agents/soak-test-harness/agent-soak-scenario-manifest.schema.json"
    ) @("tools/soak/Test-SoakPopulationPreset.ps1")
    New-Package "llm-gateway" "LLM Control Gateway Package" "llm" @(
        "docs/agents/llm-gateway/LLM_GATEWAY_DESIGN_SPECIFICATION.md",
        "docs/agents/llm-gateway/LLM_GATEWAY_TECHNICAL_SPECIFICATION.md",
        "docs/agents/llm-autonomy/LLM_CONTROL_CONTRACT.md"
    )
    New-Package "population-director" "Agent Population Director" "scaling" @(
        "docs/agents/population-director/AGENT_POPULATION_DIRECTOR_DESIGN_SPECIFICATION.md",
        "docs/agents/population-director/AGENT_POPULATION_DIRECTOR_TECHNICAL_SPECIFICATION.md",
        "docs/agents/population-director/world-population-plan.schema.json"
    ) @("tools/agent-contracts/Test-PopulationDirectorContracts.ps1")
    New-Package "portable-installer" "Portable Installer / Patcher" "server-adapter" @(
        "docs/agents/server-adapter/PORTABLE_INSTALLER_TECHNICAL_SPECIFICATION.md",
        "docs/agents/server-adapter/portable-install-manifest.schema.json",
        "docs/agents/server-adapter/portable-install-plan.schema.json"
    ) @("tools/agent-contracts/Test-PortableInstallerContracts.ps1")
    New-Package "quest-objective-policy" "Quest / Combat Focus Policy Package" "gameplay" @(
        "docs/agents/quest-objective-policy/QUEST_OBJECTIVE_POLICY_DESIGN_SPECIFICATION.md",
        "docs/agents/quest-objective-policy/QUEST_OBJECTIVE_POLICY_TECHNICAL_SPECIFICATION.md"
    )
    New-Package "social-relationship-runtime" "Social Relationship Runtime" "profile" @(
        "docs/agents/social-relationship-runtime/SOCIAL_RELATIONSHIP_RUNTIME_DESIGN_SPECIFICATION.md",
        "docs/agents/social-relationship-runtime/SOCIAL_RELATIONSHIP_RUNTIME_TECHNICAL_SPECIFICATION.md"
    )
)

$implementationTrackSteps = @(
    New-TrackStep "scaling-first" 1 "agent-observability" "Agent observability" @("observability")
    New-TrackStep "scaling-first" 2 "agent-event-bus" "Agent event bus" @("event-bus")
    New-TrackStep "scaling-first" 3 "agent-scheduler-runtime" "Agent scheduler runtime" @("observability", "event-bus") "tracked-in-scaling-docs" "Dedicated scheduler package is not split out yet; scheduling metrics/contracts are covered by observability, event bus, and scaling docs."
    New-TrackStep "scaling-first" 4 "agent-simulation-tier-runtime" "Agent simulation tier runtime" @("simulation-tier-runtime")
    New-TrackStep "scaling-first" 5 "agent-perception-runtime" "Agent perception runtime" @("perception-runtime")
    New-TrackStep "scaling-first" 6 "agent-route-eta-runtime" "Agent route ETA runtime" @("catalog-platform", "simulation-tier-runtime", "background-action-runtime") "tracked-in-scaling-docs" "Route ETA is specified as part of catalog lookup, simulation tier, and background navigation prep."
    New-TrackStep "scaling-first" 7 "agent-background-action-runtime" "Agent background action runtime" @("background-action-runtime")
    New-TrackStep "scaling-first" 8 "agent-load-shedding-policy" "Agent load shedding policy" @("simulation-tier-runtime", "background-action-runtime", "observability") "tracked-in-scaling-docs" "Load shedding remains a scaling policy across simulation tier, background actions, and observability."
    New-TrackStep "scaling-first" 9 "agent-memory-lifecycle-runtime" "Agent memory lifecycle runtime" @("profile-platform", "social-relationship-runtime", "observability") "tracked-in-scaling-docs" "Memory lifecycle is currently covered by profile, relationship, and observability specs."
    New-TrackStep "scaling-first" 10 "scale-soak-tests" "Scale soak tests" @("agent-soak-test-harness")

    New-TrackStep "gameplay" 1 "agent-event-bus" "Agent event bus" @("event-bus")
    New-TrackStep "gameplay" 2 "agent-catalog-platform" "Agent catalog platform" @("catalog-platform", "npc-catalog")
    New-TrackStep "gameplay" 3 "agent-profile-platform" "Agent profile platform read-only mode" @("profile-platform")
    New-TrackStep "gameplay" 4 "agent-plan-runtime" "Agent plan runtime loader and objective state" @("plan-runtime")
    New-TrackStep "gameplay" 5 "agent-capability-runtime" "Agent capability runtime command/result interfaces" @("capability-runtime")
    New-TrackStep "gameplay" 6 "agent-npc-quest-capability" "Agent NPC quest capability" @("npc-quest-capability")
    New-TrackStep "gameplay" 7 "agent-recovery-policy" "Agent recovery policy" @("recovery-policy")
    New-TrackStep "gameplay" 8 "maple-island-mvp" "Maple Island MVP" @("maple-island-mvp", "quest-objective-policy")
    New-TrackStep "gameplay" 9 "agent-observability" "Agent observability" @("observability")
    New-TrackStep "gameplay" 10 "agent-economy-engine" "Agent economy engine" @("economy-engine")
    New-TrackStep "gameplay" 11 "agent-interaction-realism" "Agent interaction realism" @("interaction-realism")
    New-TrackStep "gameplay" 12 "agent-simulation-tier-runtime" "Agent simulation tier runtime" @("simulation-tier-runtime")
    New-TrackStep "gameplay" 13 "agent-llm-gateway" "Agent LLM gateway" @("llm-gateway")
    New-TrackStep "gameplay" 14 "agent-population-director" "Agent population director" @("population-director")
)

$operationalPackageIds = @(
    "agent-reconstruction",
    "database-console",
    "pre-reconstruction-tools",
    "server-baseline-soak",
    "server-console"
)

$rows = [System.Collections.Generic.List[object]]::new()
$missingDocs = [System.Collections.Generic.List[string]]::new()
$missingVerifiers = [System.Collections.Generic.List[string]]::new()
$categoryCounts = [ordered]@{}
$runtimeStatusCounts = [ordered]@{}

foreach ($package in $packages) {
    $missingPackageDocs = @($package.requiredDocs | Where-Object { !(Test-Path -LiteralPath $_ -PathType Leaf) })
    $missingPackageVerifiers = @($package.verifierPaths | Where-Object { !(Test-Path -LiteralPath $_ -PathType Leaf) })

    foreach ($path in $missingPackageDocs) {
        [void] $missingDocs.Add("$($package.id):$path")
    }
    foreach ($path in $missingPackageVerifiers) {
        [void] $missingVerifiers.Add("$($package.id):$path")
    }

    if (!$categoryCounts.Contains($package.category)) {
        $categoryCounts[$package.category] = 0
    }
    $categoryCounts[$package.category] += 1

    if (!$runtimeStatusCounts.Contains($package.runtimeStatus)) {
        $runtimeStatusCounts[$package.runtimeStatus] = 0
    }
    $runtimeStatusCounts[$package.runtimeStatus] += 1

    [void] $rows.Add([pscustomobject] [ordered]@{
        id = $package.id
        name = $package.name
        category = $package.category
        runtimeStatus = $package.runtimeStatus
        requiredDocCount = $package.requiredDocs.Count
        verifierCount = $package.verifierPaths.Count
        missingDocs = @($missingPackageDocs)
        missingVerifiers = @($missingPackageVerifiers)
        ready = ($missingPackageDocs.Count -eq 0 -and $missingPackageVerifiers.Count -eq 0)
    })
}

$registeredPackageIds = [System.Collections.Generic.HashSet[string]]::new([string[]] @($packages | ForEach-Object { $_.id }))
$trackRows = [System.Collections.Generic.List[object]]::new()
foreach ($step in $implementationTrackSteps) {
    $missingPackageIds = @($step.packageIds | Where-Object { !$registeredPackageIds.Contains([string] $_) })
    $exactPackageRegistered = $registeredPackageIds.Contains($step.stepId)
    $coverageStatus = if ($missingPackageIds.Count -gt 0) {
        "missing-package-reference"
    } elseif ($exactPackageRegistered -and $step.coverageStatus -eq "registered-package") {
        "registered-package"
    } else {
        $step.coverageStatus
    }

    [void] $trackRows.Add([pscustomobject] [ordered]@{
        track = $step.track
        order = $step.order
        stepId = $step.stepId
        label = $step.label
        coverageStatus = $coverageStatus
        exactPackageRegistered = $exactPackageRegistered
        packageIds = @($step.packageIds)
        missingPackageIds = @($missingPackageIds)
        note = $step.note
    })
}

$implementationTrackCounts = [ordered]@{
    totalSteps = $trackRows.Count
    registeredPackageSteps = @($trackRows | Where-Object { $_.coverageStatus -eq "registered-package" }).Count
    trackedInScalingDocsSteps = @($trackRows | Where-Object { $_.coverageStatus -eq "tracked-in-scaling-docs" }).Count
    missingPackageReferenceSteps = @($trackRows | Where-Object { $_.coverageStatus -eq "missing-package-reference" }).Count
}

$implementationTrackStepIds = @($trackRows | Sort-Object track, order | ForEach-Object { $_.stepId })
$scalingFirstStepIds = @($trackRows | Where-Object { $_.track -eq "scaling-first" } | Sort-Object order | ForEach-Object { $_.stepId })
$gameplayStepIds = @($trackRows | Where-Object { $_.track -eq "gameplay" } | Sort-Object order | ForEach-Object { $_.stepId })
$trackedInScalingDocsStepIds = @($trackRows | Where-Object { $_.coverageStatus -eq "tracked-in-scaling-docs" } | Sort-Object track, order | ForEach-Object { $_.stepId })
$missingPackageReferenceStepIds = @($trackRows | Where-Object { $_.coverageStatus -eq "missing-package-reference" } | Sort-Object track, order | ForEach-Object { $_.stepId })

$status = if ($missingDocs.Count -gt 0 -or $missingVerifiers.Count -gt 0) {
    "FAIL"
} else {
    "READY_WITH_DEFERRED_RUNTIME"
}

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    repoRoot = $repoRoot
    status = $status
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    packageCount = $packages.Count
    readyPackageCount = @($rows | Where-Object { $_.ready }).Count
    readyCount = @($rows | Where-Object { $_.ready }).Count
    waitingCount = 0
    blockedCount = 0
    failCount = @($rows | Where-Object { -not $_.ready }).Count
    missingDocCount = $missingDocs.Count
    missingVerifierCount = $missingVerifiers.Count
    implementationTrackCount = $implementationTrackCounts.totalSteps
    implementationTrackRegisteredPackageCount = $implementationTrackCounts.registeredPackageSteps
    implementationTrackTrackedInScalingDocsCount = $implementationTrackCounts.trackedInScalingDocsSteps
    implementationTrackMissingReferenceCount = $implementationTrackCounts.missingPackageReferenceSteps
    implementationTrackStepIds = @($implementationTrackStepIds)
    scalingFirstStepIds = @($scalingFirstStepIds)
    gameplayStepIds = @($gameplayStepIds)
    trackedInScalingDocsStepIds = @($trackedInScalingDocsStepIds)
    missingPackageReferenceStepIds = @($missingPackageReferenceStepIds)
    operationalPackageIds = @($operationalPackageIds)
    operationalPackageIdCount = @($operationalPackageIds).Count
    returnedPackageCount = if ($SummaryOnly) { 0 } else { @($rows).Count }
    returnedImplementationTrackCount = if ($SummaryOnly) { 0 } else { @($trackRows).Count }
    categoryCounts = $categoryCounts
    runtimeStatusCounts = $runtimeStatusCounts
    implementationTrackCounts = $implementationTrackCounts
    implementationTracks = if ($SummaryOnly) { $null } else { @($trackRows) }
    packages = if ($SummaryOnly) { $null } else { @($rows) }
}

if ($Json) {
    $report | ConvertTo-Json -Depth 10
} else {
    Write-Host "Agent package readiness: $($report.status)"
    Write-Host "Packages: $($report.packageCount)"
    Write-Host "Ready packages: $($report.readyPackageCount)"
    Write-Host "Missing docs: $($report.missingDocCount)"
    Write-Host "Missing verifiers: $($report.missingVerifierCount)"
    Write-Host "Implementation track steps: $($report.implementationTrackCounts.totalSteps)"
    Write-Host "Tracked without dedicated package: $($report.implementationTrackCounts.trackedInScalingDocsSteps)"
    Write-Host ""
    foreach ($row in @($rows)) {
        $marker = if ($row.ready) { "READY" } else { "MISSING" }
        Write-Host ("[{0}] {1} ({2}) - {3}" -f $marker, $row.id, $row.category, $row.runtimeStatus)
        foreach ($path in @($row.missingDocs)) {
            Write-Host "  Missing doc: $path"
        }
        foreach ($path in @($row.missingVerifiers)) {
            Write-Host "  Missing verifier: $path"
        }
    }
}

if ($status -eq "FAIL") {
    exit 1
}
