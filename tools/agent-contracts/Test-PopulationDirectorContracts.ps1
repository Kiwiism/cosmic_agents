param(
    [string] $WorldPopulationPlanSchemaPath = "docs/agents/population-director/world-population-plan.schema.json",
    [string] $PopulationTargetSchemaPath = "docs/agents/population-director/population-target.schema.json",
    [string] $MapCapacityPolicySchemaPath = "docs/agents/population-director/map-capacity-policy.schema.json",
    [string] $PopulationSnapshotSchemaPath = "docs/agents/population-director/population-snapshot.schema.json",
    [string] $PopulationAssignmentSchemaPath = "docs/agents/population-director/population-assignment.schema.json",
    [string] $PopulationRebalanceProposalSchemaPath = "docs/agents/population-director/population-rebalance-proposal.schema.json",
    [string] $EconomicDemandSignalSchemaPath = "docs/agents/population-director/economic-demand-signal.schema.json",
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
    $Checks.Add([ordered]@{ id = $Id; status = $Status; message = $Message }) | Out-Null
}

function Read-Json {
    param([System.Collections.Generic.List[object]] $Checks, [string] $Path, [string] $Label)
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        Add-Check $Checks "file:$Label" "FAIL" "Missing $Label at $Path."
        return $null
    }
    Add-Check $Checks "file:$Label" "PASS" "Found $Label."
    try {
        $value = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
        Add-Check $Checks "json:$Label" "PASS" "$Label is valid JSON."
        return $value
    } catch {
        Add-Check $Checks "json:$Label" "FAIL" "$Label is invalid JSON: $($_.Exception.Message)"
        return $null
    }
}

function Test-Required {
    param([System.Collections.Generic.List[object]] $Checks, [object] $Schema, [string] $Label, [string[]] $Fields)
    $required = @($Schema.required)
    foreach ($field in $Fields) {
        if ($required -contains $field) {
            Add-Check $Checks "$Label.required:$field" "PASS" "$Label requires $field."
        } else {
            Add-Check $Checks "$Label.required:$field" "FAIL" "$Label should require $field."
        }
    }
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()
$world = Read-Json $checks $WorldPopulationPlanSchemaPath "world-population-plan-schema"
$target = Read-Json $checks $PopulationTargetSchemaPath "population-target-schema"
$capacity = Read-Json $checks $MapCapacityPolicySchemaPath "map-capacity-policy-schema"
$snapshot = Read-Json $checks $PopulationSnapshotSchemaPath "population-snapshot-schema"
$assignment = Read-Json $checks $PopulationAssignmentSchemaPath "population-assignment-schema"
$rebalance = Read-Json $checks $PopulationRebalanceProposalSchemaPath "population-rebalance-proposal-schema"
$demandSignal = Read-Json $checks $EconomicDemandSignalSchemaPath "economic-demand-signal-schema"

if ($null -ne $world) {
    Test-Required $checks $world "world-population-plan" @(
        "schemaVersion", "planId", "version", "seedPolicy", "defaultSeed",
        "totalTarget", "modes", "targets", "cohorts", "mapCapacityPolicies",
        "rebalancePolicy", "demandSignalPolicy"
    )

    $defaultModes = @($world.properties.modes.properties.defaultMode.enum)
    foreach ($mode in @("OFF", "OBSERVE_ONLY", "PLAN_ONLY", "ASSIGNMENT_ALLOWED")) {
        if ($defaultModes -contains $mode) {
            Add-Check $checks "world-population-plan.mode:$mode" "PASS" "World plan allows mode $mode."
        } else {
            Add-Check $checks "world-population-plan.mode:$mode" "FAIL" "World plan should allow mode $mode."
        }
    }
}

if ($null -ne $target) {
    Test-Required $checks $target "population-target" @(
        "schemaVersion", "targetId", "ratio", "cohortIds", "regionIds",
        "levelRange", "jobGroups", "roleWeights"
    )

    if ($target.'$defs' -and $target.'$defs'.cohort) {
        Test-Required $checks $target.'$defs'.cohort "population-cohort" @(
            "schemaVersion", "cohortId", "profileTemplateIds", "planSetIds",
            "preferredMapIds", "economicRoleTags"
        )
    } else {
        Add-Check $checks "population-cohort:def" "FAIL" "Population target schema is missing cohort definition."
    }
}

if ($null -ne $capacity) {
    Test-Required $checks $capacity "map-capacity-policy" @(
        "schemaVersion", "mapId", "softAgentCap", "hardAgentCap",
        "visibleFullSimulationCap", "backgroundSimulationCap", "roleCaps",
        "crowdingPenalty"
    )
}

if ($null -ne $snapshot) {
    Test-Required $checks $snapshot "population-snapshot" @(
        "schemaVersion", "worldId", "channelId", "timestampMs", "agentCount",
        "byRegion", "byMap", "byLevelBracket", "byJobGroup", "byArchetype",
        "byRole", "simulationTierCounts", "playerCountsByMap"
    )
}

if ($null -ne $assignment) {
    Test-Required $checks $assignment "population-assignment" @(
        "schemaVersion", "assignmentId", "targetId", "cohortId",
        "profileTemplateId", "startingMapId", "startingLevel", "jobId",
        "planSetIds", "reasonCodes"
    )
}

if ($null -ne $rebalance) {
    Test-Required $checks $rebalance "population-rebalance-proposal" @(
        "schemaVersion", "proposalId", "agentId", "changeType", "from", "to",
        "priority", "cooldownUntilMs", "reasonCodes"
    )

    $changeTypes = @($rebalance.properties.changeType.enum)
    foreach ($changeType in @("PLAN_SET_SHIFT", "ROLE_SHIFT", "PAUSE_ASSIGNMENT")) {
        if ($changeTypes -contains $changeType) {
            Add-Check $checks "population-rebalance-proposal.changeType:$changeType" "PASS" "Rebalance proposal allows $changeType."
        } else {
            Add-Check $checks "population-rebalance-proposal.changeType:$changeType" "FAIL" "Rebalance proposal should allow $changeType."
        }
    }
}

if ($null -ne $demandSignal) {
    Test-Required $checks $demandSignal "economic-demand-signal" @(
        "schemaVersion", "signalId", "signalType", "strength",
        "reasonCodes", "createdAtMs"
    )

    $signalTypes = @($demandSignal.properties.signalType.enum)
    foreach ($signalType in @("CLASS_POPULATION_DEMAND", "REGIONAL_DEMAND", "ROLE_DEMAND")) {
        if ($signalTypes -contains $signalType) {
            Add-Check $checks "economic-demand-signal.signalType:$signalType" "PASS" "Economic demand signal allows $signalType."
        } else {
            Add-Check $checks "economic-demand-signal.signalType:$signalType" "FAIL" "Economic demand signal should allow $signalType."
        }
    }
}

$failures = @($checks | Where-Object { $_.status -eq "FAIL" })
$warnings = @($checks | Where-Object { $_.status -eq "WARN" })
$failCount = $failures.Count
$warnCount = $warnings.Count
$passCount = @($checks | Where-Object { $_.status -eq "PASS" }).Count
$overall = if ($failCount -gt 0) { "FAIL" } elseif ($warnCount -gt 0) { "INCOMPLETE" } else { "PASS" }

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    repoRoot = $repoRoot
    status = $overall
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
    Write-Host "Population Director contract verification: $overall"
    Write-Host "Failures: $failCount  Warnings: $warnCount"
    Write-Host ""
    foreach ($check in $checks) {
        Write-Host ("[{0}] {1} - {2}" -f $check.status, $check.id, $check.message)
    }
}

if ($failCount -gt 0) {
    exit 1
}
