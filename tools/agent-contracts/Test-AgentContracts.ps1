param(
    [string] $PlanPath = "docs/agents/plans/maple-island-mvp.plan.json",
    [string] $CatalogAcceptedGapSchemaPath = "docs/agents/catalog-platform/catalog-accepted-gap.schema.json",
    [string] $CatalogBundleManifestSchemaPath = "docs/agents/catalog-platform/catalog-bundle-manifest.schema.json",
    [string] $CatalogCompatibilityReportSchemaPath = "docs/agents/catalog-platform/catalog-compatibility-report.schema.json",
    [string] $CatalogIndexCoverageSchemaPath = "docs/agents/catalog-platform/catalog-index-coverage.schema.json",
    [string] $CatalogQueryRequestSchemaPath = "docs/agents/catalog-platform/catalog-query-request.schema.json",
    [string] $CatalogQueryResultSchemaPath = "docs/agents/catalog-platform/catalog-query-result.schema.json",
    [string] $CatalogSourceHashesSchemaPath = "docs/agents/catalog-platform/catalog-source-hashes.schema.json",
    [string] $CatalogValidationFindingSchemaPath = "docs/agents/catalog-platform/catalog-validation-finding.schema.json",
    [string] $CatalogValidationSummarySchemaPath = "docs/agents/catalog-platform/catalog-validation-summary.schema.json",
    [string] $PlanSchemaPath = "docs/agents/plan-runtime/plan-card.schema.json",
    [string] $PlanBundleManifestSchemaPath = "docs/agents/plan-runtime/plan-bundle-manifest.schema.json",
    [string] $PlanEventSchemaPath = "docs/agents/plan-runtime/plan-event.schema.json",
    [string] $PlanProgressSchemaPath = "docs/agents/plan-runtime/plan-progress.schema.json",
    [string] $ObjectiveProgressSchemaPath = "docs/agents/plan-runtime/objective-progress.schema.json",
    [string] $ObjectiveResultSchemaPath = "docs/agents/plan-runtime/objective-result.schema.json",
    [string] $AgentEventSchemaPath = "docs/agents/event-bus/agent-event.schema.json",
    [string] $AgentEventSubscriptionSchemaPath = "docs/agents/event-bus/agent-event-subscription.schema.json",
    [string] $AgentEventReplayQuerySchemaPath = "docs/agents/event-bus/agent-event-replay-query.schema.json",
    [string] $CapabilityCommandSchemaPath = "docs/agents/capability-runtime/capability-command.schema.json",
    [string] $CapabilityResultSchemaPath = "docs/agents/capability-runtime/capability-result.schema.json",
    [string] $LlmCommandSchemaPath = "docs/agents/llm-autonomy/llm-control-command.schema.json",
    [string] $LlmResultSchemaPath = "docs/agents/llm-autonomy/llm-control-result.schema.json",
    [string] $AgentProfileSchemaPath = "docs/agents/profile-platform/agent-profile.schema.json",
    [string] $AgentProfileSummarySchemaPath = "docs/agents/profile-platform/agent-profile-summary.schema.json",
    [string] $ProfileDecisionRequestSchemaPath = "docs/agents/profile-platform/profile-decision-request.schema.json",
    [string] $ProfileDecisionResultSchemaPath = "docs/agents/profile-platform/profile-decision-result.schema.json",
    [string] $DecisionJournalSchemaPath = "docs/agents/profile-platform/decision-journal-entry.schema.json",
    [string] $RelationshipMemorySchemaPath = "docs/agents/profile-platform/relationship-memory.schema.json",
    [string] $AgentExperienceEventSchemaPath = "docs/agents/profile-platform/agent-experience-event.schema.json",
    [string] $ProfilePatchSchemaPath = "docs/agents/profile-platform/profile-patch.schema.json",
    [string] $WorldPopulationPlanSchemaPath = "docs/agents/population-director/world-population-plan.schema.json",
    [string] $PopulationTargetSchemaPath = "docs/agents/population-director/population-target.schema.json",
    [string] $MapCapacityPolicySchemaPath = "docs/agents/population-director/map-capacity-policy.schema.json",
    [string] $PopulationSnapshotSchemaPath = "docs/agents/population-director/population-snapshot.schema.json",
    [string] $PopulationAssignmentSchemaPath = "docs/agents/population-director/population-assignment.schema.json",
    [string] $PopulationRebalanceProposalSchemaPath = "docs/agents/population-director/population-rebalance-proposal.schema.json",
    [string] $EconomicDemandSignalSchemaPath = "docs/agents/population-director/economic-demand-signal.schema.json",
    [string] $EconomyMarketObservationSchemaPath = "docs/agents/llm-autonomy/economy-market-observation.schema.json",
    [string] $EconomyMarketItemStateSchemaPath = "docs/agents/llm-autonomy/economy-market-item-state.schema.json",
    [string] $EconomyDecisionSchemaPath = "docs/agents/llm-autonomy/economy-decision.schema.json",
    [string] $LiveAgentSnapshotSchemaPath = "docs/agents/server-adapter/live-agent-snapshot.schema.json",
    [string] $ServerActionRequestSchemaPath = "docs/agents/server-adapter/server-action-request.schema.json",
    [string] $ServerActionResultSchemaPath = "docs/agents/server-adapter/server-action-result.schema.json",
    [string] $PortableInstallManifestSchemaPath = "docs/agents/server-adapter/portable-install-manifest.schema.json",
    [string] $PortableInstallPlanSchemaPath = "docs/agents/server-adapter/portable-install-plan.schema.json",
    [string] $PortablePatchOperationSchemaPath = "docs/agents/server-adapter/portable-patch-operation.schema.json",
    [string] $PortableInstallVerifyReportSchemaPath = "docs/agents/server-adapter/portable-install-verify-report.schema.json",
    [string] $SimulationTierDecisionSchemaPath = "docs/agents/simulation-tier-runtime/simulation-tier-decision.schema.json",
    [string] $MaterializationPlanSchemaPath = "docs/agents/simulation-tier-runtime/materialization-plan.schema.json",
    [string] $BackgroundActionRequestSchemaPath = "docs/agents/background-action-runtime/background-action-request.schema.json",
    [string] $BackgroundActionResultSchemaPath = "docs/agents/background-action-runtime/background-action-result.schema.json",
    [string] $VirtualAgentStateSchemaPath = "docs/agents/background-action-runtime/virtual-agent-state.schema.json",
    [string] $SoakScenarioManifestSchemaPath = "docs/agents/soak-test-harness/agent-soak-scenario-manifest.schema.json",
    [string] $SoakSummarySchemaPath = "docs/agents/soak-test-harness/agent-soak-summary.schema.json",
    [string] $SoakPopulationPresetSchemaPath = "docs/agents/soak-test-harness/agent-soak-population-preset.schema.json",
    [string] $VictoriaPopulationPresetPath = "docs/agents/soak-test-harness/presets/victoria_lt30_living_world_v1.population-preset.json",
    [string] $PlanSafetyVerifierPath = "tools/agent-contracts/Test-PlanCardSafety.ps1",
    [string] $ProfilePatchSafetyVerifierPath = "tools/agent-contracts/Test-ProfilePatchSafety.ps1",
    [string] $PopulationDirectorVerifierPath = "tools/agent-contracts/Test-PopulationDirectorContracts.ps1",
    [string] $PortableInstallerVerifierPath = "tools/agent-contracts/Test-PortableInstallerContracts.ps1",
    [string] $AgentScalingVerifierPath = "tools/agent-contracts/Test-AgentScalingContracts.ps1",
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Add-Check {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [string] $Id,
        [string] $Status,
        [string] $Message
    )

    $Checks.Add([ordered]@{
        id = $Id
        status = $Status
        message = $Message
    }) | Out-Null
}

function Read-JsonFile {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [string] $Path,
        [string] $Label
    )

    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        Add-Check $Checks "file:$Label" "FAIL" "Missing $Label at $Path."
        return $null
    }

    Add-Check $Checks "file:$Label" "PASS" "Found $Label at $Path."

    try {
        $json = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
        Add-Check $Checks "json:$Label" "PASS" "$Label is valid JSON."
        return $json
    } catch {
        Add-Check $Checks "json:$Label" "FAIL" "$Label is not valid JSON: $($_.Exception.Message)"
        return $null
    }
}

function Test-RequiredProperty {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [object] $Object,
        [string] $Property,
        [string] $Label
    )

    if ($null -eq $Object) {
        Add-Check $Checks ("required:{0}:{1}" -f $Label, $Property) "FAIL" "Cannot check $Property because $Label is missing."
        return
    }

    if ($Object.PSObject.Properties.Name -contains $Property) {
        Add-Check $Checks ("required:{0}:{1}" -f $Label, $Property) "PASS" "$Label has required property $Property."
    } else {
        Add-Check $Checks ("required:{0}:{1}" -f $Label, $Property) "FAIL" "$Label is missing required property $Property."
    }
}

function Test-UniqueIntegerArray {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [object[]] $Values,
        [string] $Id
    )

    $numbers = @($Values | ForEach-Object { [int] $_ })
    $duplicates = @($numbers | Group-Object | Where-Object { $_.Count -gt 1 } | Select-Object -ExpandProperty Name)
    if ($duplicates.Count -eq 0) {
        Add-Check $Checks $Id "PASS" "$Id has no duplicate ids."
    } else {
        Add-Check $Checks $Id "FAIL" "$Id has duplicate ids: $($duplicates -join ', ')."
    }
}

function Test-RatioSum {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [object[]] $Rows,
        [string] $Id,
        [double] $Expected = 1.0,
        [double] $Tolerance = 0.0001
    )

    $sum = 0.0
    foreach ($row in @($Rows)) {
        if ($null -ne $row.ratio) {
            $sum += [double] $row.ratio
        }
    }

    if ([Math]::Abs($sum - $Expected) -le $Tolerance) {
        Add-Check $Checks $Id "PASS" "$Id ratios total $sum."
    } else {
        Add-Check $Checks $Id "FAIL" "$Id ratios total $sum, expected $Expected within tolerance $Tolerance."
    }
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()

$catalogAcceptedGapSchema = Read-JsonFile $checks $CatalogAcceptedGapSchemaPath "catalog-accepted-gap-schema"
$catalogBundleManifestSchema = Read-JsonFile $checks $CatalogBundleManifestSchemaPath "catalog-bundle-manifest-schema"
$catalogCompatibilityReportSchema = Read-JsonFile $checks $CatalogCompatibilityReportSchemaPath "catalog-compatibility-report-schema"
$catalogIndexCoverageSchema = Read-JsonFile $checks $CatalogIndexCoverageSchemaPath "catalog-index-coverage-schema"
$catalogQueryRequestSchema = Read-JsonFile $checks $CatalogQueryRequestSchemaPath "catalog-query-request-schema"
$catalogQueryResultSchema = Read-JsonFile $checks $CatalogQueryResultSchemaPath "catalog-query-result-schema"
$catalogSourceHashesSchema = Read-JsonFile $checks $CatalogSourceHashesSchemaPath "catalog-source-hashes-schema"
$catalogValidationFindingSchema = Read-JsonFile $checks $CatalogValidationFindingSchemaPath "catalog-validation-finding-schema"
$catalogValidationSummarySchema = Read-JsonFile $checks $CatalogValidationSummarySchemaPath "catalog-validation-summary-schema"
$planSchema = Read-JsonFile $checks $PlanSchemaPath "plan-card-schema"
$planBundleManifestSchema = Read-JsonFile $checks $PlanBundleManifestSchemaPath "plan-bundle-manifest-schema"
$planEventSchema = Read-JsonFile $checks $PlanEventSchemaPath "plan-event-schema"
$planProgressSchema = Read-JsonFile $checks $PlanProgressSchemaPath "plan-progress-schema"
$objectiveProgressSchema = Read-JsonFile $checks $ObjectiveProgressSchemaPath "objective-progress-schema"
$objectiveResultSchema = Read-JsonFile $checks $ObjectiveResultSchemaPath "objective-result-schema"
$agentEventSchema = Read-JsonFile $checks $AgentEventSchemaPath "agent-event-schema"
$agentEventSubscriptionSchema = Read-JsonFile $checks $AgentEventSubscriptionSchemaPath "agent-event-subscription-schema"
$agentEventReplayQuerySchema = Read-JsonFile $checks $AgentEventReplayQuerySchemaPath "agent-event-replay-query-schema"
$commandSchema = Read-JsonFile $checks $CapabilityCommandSchemaPath "capability-command-schema"
$resultSchema = Read-JsonFile $checks $CapabilityResultSchemaPath "capability-result-schema"
$llmCommandSchema = Read-JsonFile $checks $LlmCommandSchemaPath "llm-control-command-schema"
$llmResultSchema = Read-JsonFile $checks $LlmResultSchemaPath "llm-control-result-schema"
$agentProfileSchema = Read-JsonFile $checks $AgentProfileSchemaPath "agent-profile-schema"
$agentProfileSummarySchema = Read-JsonFile $checks $AgentProfileSummarySchemaPath "agent-profile-summary-schema"
$profileDecisionRequestSchema = Read-JsonFile $checks $ProfileDecisionRequestSchemaPath "profile-decision-request-schema"
$profileDecisionResultSchema = Read-JsonFile $checks $ProfileDecisionResultSchemaPath "profile-decision-result-schema"
$decisionJournalSchema = Read-JsonFile $checks $DecisionJournalSchemaPath "decision-journal-entry-schema"
$relationshipMemorySchema = Read-JsonFile $checks $RelationshipMemorySchemaPath "relationship-memory-schema"
$agentExperienceEventSchema = Read-JsonFile $checks $AgentExperienceEventSchemaPath "agent-experience-event-schema"
$profilePatchSchema = Read-JsonFile $checks $ProfilePatchSchemaPath "profile-patch-schema"
$worldPopulationPlanSchema = Read-JsonFile $checks $WorldPopulationPlanSchemaPath "world-population-plan-schema"
$populationTargetSchema = Read-JsonFile $checks $PopulationTargetSchemaPath "population-target-schema"
$mapCapacityPolicySchema = Read-JsonFile $checks $MapCapacityPolicySchemaPath "map-capacity-policy-schema"
$populationSnapshotSchema = Read-JsonFile $checks $PopulationSnapshotSchemaPath "population-snapshot-schema"
$populationAssignmentSchema = Read-JsonFile $checks $PopulationAssignmentSchemaPath "population-assignment-schema"
$populationRebalanceProposalSchema = Read-JsonFile $checks $PopulationRebalanceProposalSchemaPath "population-rebalance-proposal-schema"
$economicDemandSignalSchema = Read-JsonFile $checks $EconomicDemandSignalSchemaPath "economic-demand-signal-schema"
$economyMarketObservationSchema = Read-JsonFile $checks $EconomyMarketObservationSchemaPath "economy-market-observation-schema"
$economyMarketItemStateSchema = Read-JsonFile $checks $EconomyMarketItemStateSchemaPath "economy-market-item-state-schema"
$economyDecisionSchema = Read-JsonFile $checks $EconomyDecisionSchemaPath "economy-decision-schema"
$liveAgentSnapshotSchema = Read-JsonFile $checks $LiveAgentSnapshotSchemaPath "live-agent-snapshot-schema"
$serverActionRequestSchema = Read-JsonFile $checks $ServerActionRequestSchemaPath "server-action-request-schema"
$serverActionResultSchema = Read-JsonFile $checks $ServerActionResultSchemaPath "server-action-result-schema"
$portableInstallManifestSchema = Read-JsonFile $checks $PortableInstallManifestSchemaPath "portable-install-manifest-schema"
$portableInstallPlanSchema = Read-JsonFile $checks $PortableInstallPlanSchemaPath "portable-install-plan-schema"
$portablePatchOperationSchema = Read-JsonFile $checks $PortablePatchOperationSchemaPath "portable-patch-operation-schema"
$portableInstallVerifyReportSchema = Read-JsonFile $checks $PortableInstallVerifyReportSchemaPath "portable-install-verify-report-schema"
$simulationTierDecisionSchema = Read-JsonFile $checks $SimulationTierDecisionSchemaPath "simulation-tier-decision-schema"
$materializationPlanSchema = Read-JsonFile $checks $MaterializationPlanSchemaPath "materialization-plan-schema"
$backgroundActionRequestSchema = Read-JsonFile $checks $BackgroundActionRequestSchemaPath "background-action-request-schema"
$backgroundActionResultSchema = Read-JsonFile $checks $BackgroundActionResultSchemaPath "background-action-result-schema"
$virtualAgentStateSchema = Read-JsonFile $checks $VirtualAgentStateSchemaPath "virtual-agent-state-schema"
$soakScenarioManifestSchema = Read-JsonFile $checks $SoakScenarioManifestSchemaPath "agent-soak-scenario-manifest-schema"
$soakSummarySchema = Read-JsonFile $checks $SoakSummarySchemaPath "agent-soak-summary-schema"
$soakPopulationPresetSchema = Read-JsonFile $checks $SoakPopulationPresetSchemaPath "agent-soak-population-preset-schema"
$victoriaPopulationPreset = Read-JsonFile $checks $VictoriaPopulationPresetPath "victoria-lt30-population-preset"
$plan = Read-JsonFile $checks $PlanPath "maple-island-plan"

if (Test-Path -LiteralPath $PlanSafetyVerifierPath -PathType Leaf) {
    $planSafetyJson = & powershell -ExecutionPolicy Bypass -File $PlanSafetyVerifierPath -PlanPath $PlanPath -Json 2>&1
    $planSafetyExitCode = $LASTEXITCODE
    try {
        $planSafetyReport = ($planSafetyJson | ConvertFrom-Json)
        if ($planSafetyReport.status -eq "PASS" -and $planSafetyExitCode -eq 0) {
            Add-Check $checks "plan-safety:verification" "PASS" "Plan card safety verifier passes."
        } elseif ($planSafetyReport.status -eq "FAIL" -or $planSafetyExitCode -ne 0) {
            Add-Check $checks "plan-safety:verification" "FAIL" "Plan card safety verifier fails with $($planSafetyReport.failCount) failure(s)."
        } else {
            Add-Check $checks "plan-safety:verification" "WARN" "Plan card safety verifier is $($planSafetyReport.status) with $($planSafetyReport.warnCount) warning(s)."
        }
    } catch {
        Add-Check $checks "plan-safety:verification" "FAIL" "Plan card safety verifier returned invalid JSON: $($_.Exception.Message)"
    }
} else {
    Add-Check $checks "plan-safety:verification" "FAIL" "Missing plan card safety verifier at $PlanSafetyVerifierPath."
}

if (Test-Path -LiteralPath $ProfilePatchSafetyVerifierPath -PathType Leaf) {
    $profilePatchSafetyJson = & powershell -ExecutionPolicy Bypass -File $ProfilePatchSafetyVerifierPath -ProfilePatchSchemaPath $ProfilePatchSchemaPath -ExperienceEventSchemaPath $AgentExperienceEventSchemaPath -Json 2>&1
    $profilePatchSafetyExitCode = $LASTEXITCODE
    try {
        $profilePatchSafetyReport = ($profilePatchSafetyJson | ConvertFrom-Json)
        if ($profilePatchSafetyReport.status -eq "PASS" -and $profilePatchSafetyExitCode -eq 0) {
            Add-Check $checks "profile-patch-safety:verification" "PASS" "Profile patch safety verifier passes."
        } elseif ($profilePatchSafetyReport.status -eq "FAIL" -or $profilePatchSafetyExitCode -ne 0) {
            Add-Check $checks "profile-patch-safety:verification" "FAIL" "Profile patch safety verifier fails with $($profilePatchSafetyReport.failCount) failure(s)."
        } else {
            Add-Check $checks "profile-patch-safety:verification" "WARN" "Profile patch safety verifier is $($profilePatchSafetyReport.status) with $($profilePatchSafetyReport.warnCount) warning(s)."
        }
    } catch {
        Add-Check $checks "profile-patch-safety:verification" "FAIL" "Profile patch safety verifier returned invalid JSON: $($_.Exception.Message)"
    }
} else {
    Add-Check $checks "profile-patch-safety:verification" "FAIL" "Missing profile patch safety verifier at $ProfilePatchSafetyVerifierPath."
}

if (Test-Path -LiteralPath $PopulationDirectorVerifierPath -PathType Leaf) {
    $populationJson = & powershell -ExecutionPolicy Bypass -File $PopulationDirectorVerifierPath -WorldPopulationPlanSchemaPath $WorldPopulationPlanSchemaPath -PopulationTargetSchemaPath $PopulationTargetSchemaPath -MapCapacityPolicySchemaPath $MapCapacityPolicySchemaPath -PopulationSnapshotSchemaPath $PopulationSnapshotSchemaPath -PopulationAssignmentSchemaPath $PopulationAssignmentSchemaPath -PopulationRebalanceProposalSchemaPath $PopulationRebalanceProposalSchemaPath -EconomicDemandSignalSchemaPath $EconomicDemandSignalSchemaPath -Json 2>&1
    $populationExitCode = $LASTEXITCODE
    try {
        $populationReport = ($populationJson | ConvertFrom-Json)
        if ($populationReport.status -eq "PASS" -and $populationExitCode -eq 0) {
            Add-Check $checks "population-director:verification" "PASS" "Population Director contract verifier passes."
        } elseif ($populationReport.status -eq "FAIL" -or $populationExitCode -ne 0) {
            Add-Check $checks "population-director:verification" "FAIL" "Population Director contract verifier fails with $($populationReport.failCount) failure(s)."
        } else {
            Add-Check $checks "population-director:verification" "WARN" "Population Director contract verifier is $($populationReport.status) with $($populationReport.warnCount) warning(s)."
        }
    } catch {
        Add-Check $checks "population-director:verification" "FAIL" "Population Director contract verifier returned invalid JSON: $($_.Exception.Message)"
    }
} else {
    Add-Check $checks "population-director:verification" "FAIL" "Missing Population Director contract verifier at $PopulationDirectorVerifierPath."
}

if (Test-Path -LiteralPath $PortableInstallerVerifierPath -PathType Leaf) {
    $installerJson = & powershell -ExecutionPolicy Bypass -File $PortableInstallerVerifierPath -ManifestSchemaPath $PortableInstallManifestSchemaPath -PlanSchemaPath $PortableInstallPlanSchemaPath -PatchSchemaPath $PortablePatchOperationSchemaPath -VerifyReportSchemaPath $PortableInstallVerifyReportSchemaPath -Json 2>&1
    $installerExitCode = $LASTEXITCODE
    try {
        $installerReport = ($installerJson | ConvertFrom-Json)
        if ($installerReport.status -eq "PASS" -and $installerExitCode -eq 0) {
            Add-Check $checks "portable-installer:verification" "PASS" "Portable installer contract verifier passes."
        } elseif ($installerReport.status -eq "FAIL" -or $installerExitCode -ne 0) {
            Add-Check $checks "portable-installer:verification" "FAIL" "Portable installer contract verifier fails with $($installerReport.failCount) failure(s)."
        } else {
            Add-Check $checks "portable-installer:verification" "WARN" "Portable installer contract verifier is $($installerReport.status) with $($installerReport.warnCount) warning(s)."
        }
    } catch {
        Add-Check $checks "portable-installer:verification" "FAIL" "Portable installer contract verifier returned invalid JSON: $($_.Exception.Message)"
    }
} else {
    Add-Check $checks "portable-installer:verification" "FAIL" "Missing Portable installer contract verifier at $PortableInstallerVerifierPath."
}

if (Test-Path -LiteralPath $AgentScalingVerifierPath -PathType Leaf) {
    $scalingJson = & powershell -ExecutionPolicy Bypass -File $AgentScalingVerifierPath -SimulationTierDecisionSchemaPath $SimulationTierDecisionSchemaPath -MaterializationPlanSchemaPath $MaterializationPlanSchemaPath -BackgroundActionRequestSchemaPath $BackgroundActionRequestSchemaPath -BackgroundActionResultSchemaPath $BackgroundActionResultSchemaPath -VirtualAgentStateSchemaPath $VirtualAgentStateSchemaPath -Json 2>&1
    $scalingExitCode = $LASTEXITCODE
    try {
        $scalingReport = ($scalingJson | ConvertFrom-Json)
        if ($scalingReport.status -eq "PASS" -and $scalingExitCode -eq 0) {
            Add-Check $checks "agent-scaling:verification" "PASS" "Agent scaling contract verifier passes."
        } elseif ($scalingReport.status -eq "FAIL" -or $scalingExitCode -ne 0) {
            Add-Check $checks "agent-scaling:verification" "FAIL" "Agent scaling contract verifier fails with $($scalingReport.failCount) failure(s)."
        } else {
            Add-Check $checks "agent-scaling:verification" "WARN" "Agent scaling contract verifier is $($scalingReport.status) with $($scalingReport.warnCount) warning(s)."
        }
    } catch {
        Add-Check $checks "agent-scaling:verification" "FAIL" "Agent scaling contract verifier returned invalid JSON: $($_.Exception.Message)"
    }
} else {
    Add-Check $checks "agent-scaling:verification" "FAIL" "Missing Agent scaling contract verifier at $AgentScalingVerifierPath."
}

foreach ($schema in @(
    @{ label = "catalog-accepted-gap-schema"; value = $catalogAcceptedGapSchema },
    @{ label = "catalog-bundle-manifest-schema"; value = $catalogBundleManifestSchema },
    @{ label = "catalog-compatibility-report-schema"; value = $catalogCompatibilityReportSchema },
    @{ label = "catalog-index-coverage-schema"; value = $catalogIndexCoverageSchema },
    @{ label = "catalog-query-request-schema"; value = $catalogQueryRequestSchema },
    @{ label = "catalog-query-result-schema"; value = $catalogQueryResultSchema },
    @{ label = "catalog-source-hashes-schema"; value = $catalogSourceHashesSchema },
    @{ label = "catalog-validation-finding-schema"; value = $catalogValidationFindingSchema },
    @{ label = "catalog-validation-summary-schema"; value = $catalogValidationSummarySchema },
    @{ label = "plan-card-schema"; value = $planSchema },
    @{ label = "plan-bundle-manifest-schema"; value = $planBundleManifestSchema },
    @{ label = "plan-event-schema"; value = $planEventSchema },
    @{ label = "plan-progress-schema"; value = $planProgressSchema },
    @{ label = "objective-progress-schema"; value = $objectiveProgressSchema },
    @{ label = "objective-result-schema"; value = $objectiveResultSchema },
    @{ label = "agent-event-schema"; value = $agentEventSchema },
    @{ label = "agent-event-subscription-schema"; value = $agentEventSubscriptionSchema },
    @{ label = "agent-event-replay-query-schema"; value = $agentEventReplayQuerySchema },
    @{ label = "capability-command-schema"; value = $commandSchema },
    @{ label = "capability-result-schema"; value = $resultSchema },
    @{ label = "llm-control-command-schema"; value = $llmCommandSchema },
    @{ label = "llm-control-result-schema"; value = $llmResultSchema },
    @{ label = "agent-profile-schema"; value = $agentProfileSchema },
    @{ label = "agent-profile-summary-schema"; value = $agentProfileSummarySchema },
    @{ label = "profile-decision-request-schema"; value = $profileDecisionRequestSchema },
    @{ label = "profile-decision-result-schema"; value = $profileDecisionResultSchema },
    @{ label = "decision-journal-entry-schema"; value = $decisionJournalSchema },
    @{ label = "relationship-memory-schema"; value = $relationshipMemorySchema },
    @{ label = "agent-experience-event-schema"; value = $agentExperienceEventSchema },
    @{ label = "profile-patch-schema"; value = $profilePatchSchema },
    @{ label = "world-population-plan-schema"; value = $worldPopulationPlanSchema },
    @{ label = "population-target-schema"; value = $populationTargetSchema },
    @{ label = "map-capacity-policy-schema"; value = $mapCapacityPolicySchema },
    @{ label = "population-snapshot-schema"; value = $populationSnapshotSchema },
    @{ label = "population-assignment-schema"; value = $populationAssignmentSchema },
    @{ label = "population-rebalance-proposal-schema"; value = $populationRebalanceProposalSchema },
    @{ label = "economic-demand-signal-schema"; value = $economicDemandSignalSchema },
    @{ label = "economy-market-observation-schema"; value = $economyMarketObservationSchema },
    @{ label = "economy-market-item-state-schema"; value = $economyMarketItemStateSchema },
    @{ label = "economy-decision-schema"; value = $economyDecisionSchema },
    @{ label = "live-agent-snapshot-schema"; value = $liveAgentSnapshotSchema },
    @{ label = "server-action-request-schema"; value = $serverActionRequestSchema },
    @{ label = "server-action-result-schema"; value = $serverActionResultSchema },
    @{ label = "portable-install-manifest-schema"; value = $portableInstallManifestSchema },
    @{ label = "portable-install-plan-schema"; value = $portableInstallPlanSchema },
    @{ label = "portable-patch-operation-schema"; value = $portablePatchOperationSchema },
    @{ label = "portable-install-verify-report-schema"; value = $portableInstallVerifyReportSchema },
    @{ label = "simulation-tier-decision-schema"; value = $simulationTierDecisionSchema },
    @{ label = "materialization-plan-schema"; value = $materializationPlanSchema },
    @{ label = "background-action-request-schema"; value = $backgroundActionRequestSchema },
    @{ label = "background-action-result-schema"; value = $backgroundActionResultSchema },
    @{ label = "virtual-agent-state-schema"; value = $virtualAgentStateSchema },
    @{ label = "agent-soak-scenario-manifest-schema"; value = $soakScenarioManifestSchema },
    @{ label = "agent-soak-summary-schema"; value = $soakSummarySchema },
    @{ label = "agent-soak-population-preset-schema"; value = $soakPopulationPresetSchema }
)) {
    Test-RequiredProperty $checks $schema.value '$schema' $schema.label
    Test-RequiredProperty $checks $schema.value '$id' $schema.label
    Test-RequiredProperty $checks $schema.value 'title' $schema.label
    Test-RequiredProperty $checks $schema.value 'type' $schema.label
}

function Test-SchemaRequires {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [object] $Schema,
        [string] $Label,
        [string[]] $Fields
    )

    if ($null -eq $Schema) {
        foreach ($field in $Fields) {
            Add-Check $Checks ("{0}-required:{1}" -f $Label, $field) "FAIL" "$Label is missing; cannot verify required $field."
        }
        return
    }

    $required = @($Schema.required)
    foreach ($field in $Fields) {
        if ($required -contains $field) {
            Add-Check $Checks ("{0}-required:{1}" -f $Label, $field) "PASS" "$Label requires $field."
        } else {
            Add-Check $Checks ("{0}-required:{1}" -f $Label, $field) "FAIL" "$Label should require $field."
        }
    }
}

if ($null -ne $llmCommandSchema) {
    $required = @($llmCommandSchema.required)
    foreach ($field in @("schemaVersion", "commandId", "issuedBy", "type", "priority", "payload", "safety")) {
        if ($required -contains $field) {
            Add-Check $checks "llm-command-required:$field" "PASS" "LLM command schema requires $field."
        } else {
            Add-Check $checks "llm-command-required:$field" "FAIL" "LLM command schema should require $field."
        }
    }

    $types = @($llmCommandSchema.properties.type.enum)
    foreach ($type in @("ASSIGN_GOAL", "ASSIGN_TASK", "BATCH_ASSIGN", "REQUEST_STATUS", "SUBMIT_CAPABILITY_COMMAND")) {
        if ($types -contains $type) {
            Add-Check $checks "llm-command-type:$type" "PASS" "LLM command schema allows $type."
        } else {
            Add-Check $checks "llm-command-type:$type" "FAIL" "LLM command schema should allow $type."
        }
    }
}

Test-SchemaRequires $checks $agentProfileSchema "agent-profile" @(
    "schemaVersion",
    "agentId",
    "identity",
    "traits",
    "policy",
    "planProfile"
)

Test-SchemaRequires $checks $agentProfileSummarySchema "agent-profile-summary" @(
    "schemaVersion",
    "summaryId",
    "agentId",
    "profileVersion",
    "generatedAtMs",
    "identitySummary",
    "hardConstraints",
    "planPreferences",
    "riskPreferences",
    "llmNotes"
)

Test-SchemaRequires $checks $profileDecisionRequestSchema "profile-decision-request" @(
    "schemaVersion",
    "requestId",
    "agentId",
    "decisionKind",
    "task",
    "context",
    "requestedAtMs"
)

Test-SchemaRequires $checks $profileDecisionResultSchema "profile-decision-result" @(
    "schemaVersion",
    "requestId",
    "decisionId",
    "agentId",
    "decisionKind",
    "status",
    "summary",
    "reasons",
    "influences",
    "confidence",
    "shouldRecordDecision",
    "decidedAtMs"
)

Test-SchemaRequires $checks $decisionJournalSchema "decision-journal" @(
    "schemaVersion",
    "decisionId",
    "agentId",
    "timestamp",
    "decisionKind",
    "overview",
    "details"
)

Test-SchemaRequires $checks $relationshipMemorySchema "relationship-memory" @(
    "schemaVersion",
    "agentId",
    "subject",
    "relationship",
    "updatedAt"
)

Test-SchemaRequires $checks $agentExperienceEventSchema "agent-experience-event" @(
    "schemaVersion",
    "eventId",
    "agentId",
    "occurredAtMs",
    "eventType",
    "source",
    "subject",
    "outcome",
    "influences",
    "evidence"
)

Test-SchemaRequires $checks $profilePatchSchema "profile-patch" @(
    "schemaVersion",
    "patchId",
    "agentId",
    "createdAtMs",
    "source",
    "reason",
    "operations",
    "safety"
)

Test-SchemaRequires $checks $worldPopulationPlanSchema "world-population-plan" @(
    "schemaVersion",
    "planId",
    "version",
    "seedPolicy",
    "defaultSeed",
    "totalTarget",
    "modes",
    "targets",
    "cohorts",
    "mapCapacityPolicies",
    "rebalancePolicy",
    "demandSignalPolicy"
)

Test-SchemaRequires $checks $populationTargetSchema "population-target" @(
    "schemaVersion",
    "targetId",
    "ratio",
    "cohortIds",
    "regionIds",
    "levelRange",
    "jobGroups",
    "roleWeights"
)

Test-SchemaRequires $checks $mapCapacityPolicySchema "map-capacity-policy" @(
    "schemaVersion",
    "mapId",
    "softAgentCap",
    "hardAgentCap",
    "visibleFullSimulationCap",
    "backgroundSimulationCap",
    "roleCaps",
    "crowdingPenalty"
)

Test-SchemaRequires $checks $populationSnapshotSchema "population-snapshot" @(
    "schemaVersion",
    "worldId",
    "channelId",
    "timestampMs",
    "agentCount",
    "byRegion",
    "byMap",
    "byLevelBracket",
    "byJobGroup",
    "byArchetype",
    "byRole",
    "simulationTierCounts",
    "playerCountsByMap"
)

Test-SchemaRequires $checks $populationAssignmentSchema "population-assignment" @(
    "schemaVersion",
    "assignmentId",
    "targetId",
    "cohortId",
    "profileTemplateId",
    "startingMapId",
    "startingLevel",
    "jobId",
    "planSetIds",
    "reasonCodes"
)

Test-SchemaRequires $checks $populationRebalanceProposalSchema "population-rebalance-proposal" @(
    "schemaVersion",
    "proposalId",
    "agentId",
    "changeType",
    "from",
    "to",
    "priority",
    "cooldownUntilMs",
    "reasonCodes"
)

Test-SchemaRequires $checks $economicDemandSignalSchema "economic-demand-signal" @(
    "schemaVersion",
    "signalId",
    "signalType",
    "strength",
    "reasonCodes",
    "createdAtMs"
)

Test-SchemaRequires $checks $economyMarketObservationSchema "economy-observation" @(
    "schemaVersion",
    "observationId",
    "eventType",
    "worldId",
    "itemId",
    "itemValuationKey",
    "observedAtMs",
    "source"
)

Test-SchemaRequires $checks $economyMarketItemStateSchema "economy-item-state" @(
    "schemaVersion",
    "worldId",
    "itemValuationKey",
    "itemId",
    "window",
    "sampleCount",
    "confidence",
    "updatedAtMs"
)

Test-SchemaRequires $checks $economyDecisionSchema "economy-decision" @(
    "schemaVersion",
    "decisionId",
    "agentId",
    "worldId",
    "action",
    "itemId",
    "itemValuationKey",
    "quantity",
    "confidence",
    "summary",
    "createdAtMs"
)

Test-SchemaRequires $checks $catalogAcceptedGapSchema "catalog-accepted-gap" @(
    "schemaVersion",
    "acceptedGapId",
    "findingIdPattern",
    "severityAllowed",
    "reason",
    "expiresOnSourceHashChange",
    "source",
    "review"
)

Test-SchemaRequires $checks $catalogBundleManifestSchema "catalog-bundle-manifest" @(
    "schemaVersion",
    "bundleId",
    "builder",
    "game",
    "generatedAt",
    "sources",
    "catalogs",
    "indexes",
    "compatibility"
)

Test-SchemaRequires $checks $catalogCompatibilityReportSchema "catalog-compatibility-report" @(
    "schemaVersion",
    "bundleId",
    "generatedAt",
    "status",
    "requiresServerAdapterVersion",
    "requiresCatalogRuntimeVersion",
    "checks"
)

Test-SchemaRequires $checks $catalogIndexCoverageSchema "catalog-index-coverage" @(
    "schemaVersion",
    "bundleId",
    "generatedAt",
    "overallStatus",
    "indexes",
    "highFrequencyQueries"
)

Test-SchemaRequires $checks $catalogQueryRequestSchema "catalog-query-request" @(
    "schemaVersion",
    "requestId",
    "queryType",
    "target",
    "constraints",
    "responseMode",
    "createdAtMs"
)

Test-SchemaRequires $checks $catalogQueryResultSchema "catalog-query-result" @(
    "schemaVersion",
    "requestId",
    "status",
    "confidence",
    "requiresLiveValidation",
    "results",
    "diagnostics",
    "completedAtMs"
)

Test-SchemaRequires $checks $catalogSourceHashesSchema "catalog-source-hashes" @(
    "schemaVersion",
    "bundleId",
    "generatedAt",
    "hashAlgorithm",
    "sources",
    "builder"
)

Test-SchemaRequires $checks $catalogValidationFindingSchema "catalog-validation-finding" @(
    "schemaVersion",
    "findingId",
    "severity",
    "category",
    "catalog",
    "key",
    "message",
    "sources",
    "recommendedAction",
    "acceptedGapId"
)

Test-SchemaRequires $checks $catalogValidationSummarySchema "catalog-validation-summary" @(
    "schemaVersion",
    "bundleId",
    "generatedAt",
    "runtimeReady",
    "counts",
    "catalogRowCounts",
    "indexCoverage",
    "acceptedGapCount",
    "newUnacceptedFindingCount"
)

Test-SchemaRequires $checks $planBundleManifestSchema "plan-bundle-manifest" @(
    "schemaVersion",
    "bundleId",
    "title",
    "generatedAt",
    "plans",
    "compatibility",
    "sources"
)

Test-SchemaRequires $checks $planEventSchema "plan-event" @(
    "schemaVersion",
    "eventId",
    "eventType",
    "agentId",
    "planId",
    "timestampMs",
    "reasonCode",
    "payload",
    "evidence"
)

Test-SchemaRequires $checks $planProgressSchema "plan-progress" @(
    "schemaVersion",
    "agentId",
    "planId",
    "status",
    "currentObjectiveId",
    "completedObjectiveIds",
    "objectives",
    "sidetrackStack",
    "totalRetries",
    "deathCount",
    "assignedAtMs",
    "updatedAtMs",
    "planVersion"
)

Test-SchemaRequires $checks $objectiveProgressSchema "objective-progress" @(
    "schemaVersion",
    "objectiveId",
    "status",
    "attempts",
    "activeCommandId",
    "lastReasonCode",
    "lastMessage",
    "startedAtMs",
    "updatedAtMs"
)

Test-SchemaRequires $checks $objectiveResultSchema "objective-result" @(
    "schemaVersion",
    "objectiveId",
    "status",
    "reasonCode",
    "message",
    "liveStateChanged",
    "retryable",
    "requiresRecovery",
    "requiresReplan",
    "evidence",
    "completedAtMs"
)

Test-SchemaRequires $checks $agentEventSchema "agent-event" @(
    "schemaVersion",
    "eventId",
    "occurredAtMs",
    "priority",
    "eventType",
    "source",
    "context",
    "payload",
    "deliveryMode"
)

Test-SchemaRequires $checks $agentEventSubscriptionSchema "agent-event-subscription" @(
    "schemaVersion",
    "subscriberName",
    "eventTypes",
    "priorities",
    "budget",
    "dropPolicy",
    "durableReplayRequired"
)

Test-SchemaRequires $checks $agentEventReplayQuerySchema "agent-event-replay-query" @(
    "schemaVersion",
    "fromMs",
    "toMs",
    "limit"
)

Test-SchemaRequires $checks $liveAgentSnapshotSchema "live-agent-snapshot" @(
    "schemaVersion",
    "agentId",
    "worldId",
    "channelId",
    "mapId",
    "position",
    "timestampMs"
)

Test-SchemaRequires $checks $serverActionRequestSchema "server-action-request" @(
    "schemaVersion",
    "actionId",
    "agentId",
    "source",
    "actionType",
    "target",
    "payload",
    "validation",
    "createdAtMs"
)

Test-SchemaRequires $checks $serverActionResultSchema "server-action-result" @(
    "schemaVersion",
    "actionId",
    "status",
    "reasonCode",
    "message",
    "liveStateChanged",
    "completedAtMs"
)

Test-SchemaRequires $checks $portableInstallManifestSchema "portable-install-manifest" @(
    "schemaVersion",
    "installerVersion",
    "targetFamily",
    "agentPlatformVersion",
    "markerPrefix",
    "defaultEnabled",
    "installedFiles",
    "patchedFiles",
    "configKeys"
)

Test-SchemaRequires $checks $portableInstallPlanSchema "portable-install-plan" @(
    "schemaVersion",
    "planId",
    "mode",
    "targetRoot",
    "agentRoot",
    "status",
    "filesToCopy",
    "patchesToApply",
    "configChanges",
    "risks"
)

Test-SchemaRequires $checks $portablePatchOperationSchema "portable-patch-operation" @(
    "schemaVersion",
    "patchId",
    "targetFile",
    "markerId",
    "markerPrefix",
    "anchor",
    "position",
    "content",
    "required"
)

Test-SchemaRequires $checks $portableInstallVerifyReportSchema "portable-install-verify-report" @(
    "schemaVersion",
    "status",
    "targetRoot",
    "installedVersion",
    "checks",
    "warnings",
    "errors"
)

Test-SchemaRequires $checks $simulationTierDecisionSchema "simulation-tier-decision" @(
    "schemaVersion",
    "agentId",
    "mapId",
    "mode",
    "allowedShortcuts",
    "costBudget",
    "materializationRequired",
    "reasons",
    "warnings",
    "decidedAtMs"
)

Test-SchemaRequires $checks $materializationPlanSchema "materialization-plan" @(
    "schemaVersion",
    "agentId",
    "mapId",
    "fromMode",
    "toMode",
    "reason",
    "visiblePosition",
    "mutationsToCommit",
    "mutationsToDiscard",
    "warnings",
    "createdAtMs"
)

Test-SchemaRequires $checks $backgroundActionRequestSchema "background-action-request" @(
    "schemaVersion",
    "requestId",
    "agentId",
    "planId",
    "objectiveId",
    "capability",
    "actionKind",
    "simulationMode",
    "allowedShortcuts",
    "payload",
    "createdAtMs"
)

Test-SchemaRequires $checks $backgroundActionResultSchema "background-action-result" @(
    "schemaVersion",
    "requestId",
    "success",
    "resultCode",
    "virtualState",
    "pendingMutations",
    "elapsedVirtualMs",
    "auditReasons",
    "warnings",
    "completedAtMs"
)

Test-SchemaRequires $checks $virtualAgentStateSchema "virtual-agent-state" @(
    "schemaVersion",
    "agentId",
    "mapId",
    "virtualPosition",
    "lootBuffer",
    "pendingMutations",
    "updatedAtMs",
    "flags"
)

Test-SchemaRequires $checks $soakScenarioManifestSchema "agent-soak-scenario-manifest" @(
    "schemaVersion",
    "scenario",
    "description",
    "requires",
    "durationMs",
    "captureIntervalMs",
    "waves",
    "safetyLimits"
)

Test-SchemaRequires $checks $soakSummarySchema "agent-soak-summary" @(
    "schemaVersion",
    "runId",
    "status",
    "stage",
    "scenario",
    "targetAgentCount",
    "startedAtMs",
    "updatedAtMs",
    "durationMs",
    "latestMetrics",
    "modeCounts",
    "failureCounts",
    "failures",
    "evidence"
)

Test-SchemaRequires $checks $soakPopulationPresetSchema "agent-soak-population-preset" @(
    "schemaVersion",
    "preset",
    "description",
    "seedPolicy",
    "defaultSeed",
    "targetAgentCount",
    "levelBands",
    "regions",
    "jobs",
    "archetypes",
    "roles",
    "cohorts",
    "mapCapacityPolicy"
)

if ($null -ne $llmResultSchema) {
    $statuses = @($llmResultSchema.properties.status.enum)
    foreach ($status in @("accepted", "rejected", "blocked", "manual-review-required")) {
        if ($statuses -contains $status) {
            Add-Check $checks "llm-result-status:$status" "PASS" "LLM result schema allows $status."
        } else {
            Add-Check $checks "llm-result-status:$status" "FAIL" "LLM result schema should allow $status."
        }
    }
}

foreach ($property in @(
    "schemaVersion",
    "preset",
    "seedPolicy",
    "defaultSeed",
    "targetAgentCount",
    "levelBands",
    "regions",
    "jobs",
    "archetypes",
    "roles",
    "cohorts",
    "mapCapacityPolicy"
)) {
    Test-RequiredProperty $checks $victoriaPopulationPreset $property "victoria-lt30-population-preset"
}

if ($null -ne $victoriaPopulationPreset) {
    if ($victoriaPopulationPreset.preset -eq "victoria_lt30_living_world_v1") {
        Add-Check $checks "population-preset:id" "PASS" "Population preset id is victoria_lt30_living_world_v1."
    } else {
        Add-Check $checks "population-preset:id" "FAIL" "Population preset id should be victoria_lt30_living_world_v1."
    }

    if ([int] $victoriaPopulationPreset.targetAgentCount -eq 2000) {
        Add-Check $checks "population-preset:targetAgentCount" "PASS" "Population preset targets 2000 Agents."
    } else {
        Add-Check $checks "population-preset:targetAgentCount" "FAIL" "Population preset should target 2000 Agents."
    }

    $ratioTolerance = 0.0001
    if ($victoriaPopulationPreset.validation -and $null -ne $victoriaPopulationPreset.validation.ratioTolerance) {
        $ratioTolerance = [double] $victoriaPopulationPreset.validation.ratioTolerance
    }

    Test-RatioSum $checks @($victoriaPopulationPreset.levelBands) "population-preset:levelBands-ratio" 1.0 $ratioTolerance
    Test-RatioSum $checks @($victoriaPopulationPreset.regions) "population-preset:regions-ratio" 1.0 $ratioTolerance
    Test-RatioSum $checks @($victoriaPopulationPreset.jobs) "population-preset:jobs-ratio" 1.0 $ratioTolerance
    Test-RatioSum $checks @($victoriaPopulationPreset.archetypes) "population-preset:archetypes-ratio" 1.0 $ratioTolerance
    Test-RatioSum $checks @($victoriaPopulationPreset.roles) "population-preset:roles-ratio" 1.0 $ratioTolerance
    Test-RatioSum $checks @($victoriaPopulationPreset.cohorts) "population-preset:cohorts-ratio" 1.0 $ratioTolerance
}

foreach ($property in @(
    "schemaVersion",
    "planId",
    "title",
    "category",
    "status",
    "objectiveMode",
    "focusPolicy",
    "entryCriteria",
    "exitCriteria",
    "route"
)) {
    Test-RequiredProperty $checks $plan $property "maple-island-plan"
}

if ($null -ne $plan) {
    if ($plan.schemaVersion -ge 1) {
        Add-Check $checks "plan:schemaVersion" "PASS" "Plan schemaVersion is $($plan.schemaVersion)."
    } else {
        Add-Check $checks "plan:schemaVersion" "FAIL" "Plan schemaVersion must be at least 1."
    }

    if ($plan.planId -eq "maple-island-mvp") {
        Add-Check $checks "plan:planId" "PASS" "Plan id is maple-island-mvp."
    } else {
        Add-Check $checks "plan:planId" "FAIL" "Plan id should be maple-island-mvp."
    }

    if (@($plan.route).Count -gt 0) {
        Add-Check $checks "plan:route" "PASS" "Plan has $(@($plan.route).Count) route step(s)."
    } else {
        Add-Check $checks "plan:route" "FAIL" "Plan route must contain at least one step."
    }

    $objectiveCount = 0
    $routeIndex = 0
    foreach ($step in @($plan.route)) {
        $routeIndex++
        if ($null -ne $step.mapId -and [int] $step.mapId -ge 0) {
            Add-Check $checks ("route:{0}:mapId" -f $routeIndex) "PASS" "Route step $routeIndex has mapId $($step.mapId)."
        } else {
            Add-Check $checks ("route:{0}:mapId" -f $routeIndex) "FAIL" "Route step $routeIndex is missing a valid mapId."
        }

        $objectives = @($step.objectives)
        if ($objectives.Count -gt 0) {
            Add-Check $checks ("route:{0}:objectives" -f $routeIndex) "PASS" "Route step $routeIndex has $($objectives.Count) objective(s)."
        } else {
            Add-Check $checks ("route:{0}:objectives" -f $routeIndex) "FAIL" "Route step $routeIndex must have objectives."
        }

        $objectiveIndex = 0
        foreach ($objective in $objectives) {
            $objectiveIndex++
            $objectiveCount++
            if ($objective.PSObject.Properties.Name -contains "kind" -and ![string]::IsNullOrWhiteSpace([string] $objective.kind)) {
                Add-Check $checks ("objective:{0}.{1}:kind" -f $routeIndex, $objectiveIndex) "PASS" "Objective $routeIndex.$objectiveIndex kind is $($objective.kind)."
            } else {
                Add-Check $checks ("objective:{0}.{1}:kind" -f $routeIndex, $objectiveIndex) "FAIL" "Objective $routeIndex.$objectiveIndex is missing kind."
            }
        }
    }
    Add-Check $checks "plan:objective-count" "PASS" "Plan has $objectiveCount objective(s)."

    if ($plan.questPolicy) {
        foreach ($field in @("requiredQuestIds", "startOnlyQuestIds", "excludedQuestIds", "optionalReviewQuestIds")) {
            if ($plan.questPolicy.PSObject.Properties.Name -contains $field) {
                Test-UniqueIntegerArray $checks @($plan.questPolicy.$field) "questPolicy:$field"
            }
        }
    }

    if (@($plan.exitCriteria.forbiddenActions).Count -gt 0) {
        Add-Check $checks "plan:forbidden-actions" "PASS" "Plan has $(@($plan.exitCriteria.forbiddenActions).Count) forbidden action rule(s)."
    } else {
        Add-Check $checks "plan:forbidden-actions" "WARN" "Plan has no forbidden action rules."
    }
}

$failCount = @($checks | Where-Object { $_.status -eq "FAIL" }).Count
$warnCount = @($checks | Where-Object { $_.status -eq "WARN" }).Count
$passCount = @($checks | Where-Object { $_.status -eq "PASS" }).Count
$nonPassingChecks = @($checks | Where-Object { $_.status -ne "PASS" })
$nonPassingCheckIds = @($nonPassingChecks | ForEach-Object { $_.id })
$overall = if ($failCount -gt 0) {
    "FAIL"
} elseif ($warnCount -gt 0) {
    "INCOMPLETE"
} else {
    "PASS"
}

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    repoRoot = $repoRoot
    status = $overall
    failCount = $failCount
    warnCount = $warnCount
    passCount = $passCount
    checkCount = @($checks).Count
    nonPassingCheckCount = @($nonPassingChecks).Count
    nonPassingCheckIds = $nonPassingCheckIds
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    returnedCheckCount = if ($SummaryOnly) { 0 } else { @($checks).Count }
    checks = if ($SummaryOnly) { $null } else { @($checks) }
}

if ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    Write-Host "Agent contract verification: $overall"
    Write-Host "Failures: $failCount  Warnings: $warnCount"
    Write-Host ""
    foreach ($check in $checks) {
        Write-Host ("[{0}] {1} - {2}" -f $check.status, $check.id, $check.message)
    }
}

if ($failCount -gt 0) {
    exit 1
}
