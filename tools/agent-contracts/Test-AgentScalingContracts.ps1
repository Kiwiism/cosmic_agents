param(
    [string] $SimulationTierDecisionSchemaPath = "docs/agents/simulation-tier-runtime/simulation-tier-decision.schema.json",
    [string] $MaterializationPlanSchemaPath = "docs/agents/simulation-tier-runtime/materialization-plan.schema.json",
    [string] $BackgroundActionRequestSchemaPath = "docs/agents/background-action-runtime/background-action-request.schema.json",
    [string] $BackgroundActionResultSchemaPath = "docs/agents/background-action-runtime/background-action-result.schema.json",
    [string] $VirtualAgentStateSchemaPath = "docs/agents/background-action-runtime/virtual-agent-state.schema.json",
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Add-Check {
    param([System.Collections.Generic.List[object]] $Checks, [string] $Id, [string] $Status, [string] $Message)
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

function Test-EnumContains {
    param([System.Collections.Generic.List[object]] $Checks, [object[]] $Enum, [string] $Label, [string[]] $Values)
    foreach ($value in $Values) {
        if (@($Enum) -contains $value) {
            Add-Check $Checks "$Label.enum:$value" "PASS" "$Label allows $value."
        } else {
            Add-Check $Checks "$Label.enum:$value" "FAIL" "$Label should allow $value."
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
$decision = Read-Json $checks $SimulationTierDecisionSchemaPath "simulation-tier-decision-schema"
$materialization = Read-Json $checks $MaterializationPlanSchemaPath "materialization-plan-schema"
$request = Read-Json $checks $BackgroundActionRequestSchemaPath "background-action-request-schema"
$result = Read-Json $checks $BackgroundActionResultSchemaPath "background-action-result-schema"
$virtualState = Read-Json $checks $VirtualAgentStateSchemaPath "virtual-agent-state-schema"

$modes = @("PRESENTATION", "BACKGROUND_ACTIVE", "BACKGROUND_ABSTRACT", "STRATEGIC_OFFLINE")

if ($null -ne $decision) {
    Test-Required $checks $decision "simulation-tier-decision" @(
        "schemaVersion", "agentId", "mapId", "mode", "allowedShortcuts",
        "costBudget", "materializationRequired", "reasons", "warnings", "decidedAtMs"
    )
    Test-EnumContains $checks $decision.properties.mode.enum "simulation-tier-decision.mode" $modes
}

if ($null -ne $materialization) {
    Test-Required $checks $materialization "materialization-plan" @(
        "schemaVersion", "agentId", "mapId", "fromMode", "toMode", "reason",
        "visiblePosition", "mutationsToCommit", "mutationsToDiscard",
        "warnings", "createdAtMs"
    )
    Test-EnumContains $checks $materialization.properties.fromMode.enum "materialization-plan.fromMode" $modes
    Test-EnumContains $checks $materialization.properties.toMode.enum "materialization-plan.toMode" $modes
}

if ($null -ne $request) {
    Test-Required $checks $request "background-action-request" @(
        "schemaVersion", "requestId", "agentId", "planId", "objectiveId",
        "capability", "actionKind", "simulationMode", "allowedShortcuts",
        "payload", "createdAtMs"
    )
    Test-EnumContains $checks $request.properties.actionKind.enum "background-action-request.actionKind" @(
        "NAVIGATE_ROUTE", "COMBAT_SLICE", "LOOT_PICKUP", "NPC_QUEST_COMPLETE",
        "SHOP_SELL", "INVENTORY_RECONCILE", "PLAN_SLICE"
    )
    Test-EnumContains $checks $request.properties.simulationMode.enum "background-action-request.simulationMode" $modes
}

if ($null -ne $result) {
    Test-Required $checks $result "background-action-result" @(
        "schemaVersion", "requestId", "success", "resultCode", "virtualState",
        "pendingMutations", "elapsedVirtualMs", "auditReasons", "warnings", "completedAtMs"
    )
    Test-EnumContains $checks $result.properties.resultCode.enum "background-action-result.resultCode" @(
        "COMPLETED", "PARTIAL_PROGRESS", "REQUIRES_PRESENTATION",
        "REQUIRES_RECONCILIATION", "BLOCKED_BY_VALIDATION",
        "BLOCKED_BY_FAIRNESS_BUDGET", "BLOCKED_BY_SENSITIVE_MAP", "FAILED_CLOSED"
    )
}

if ($null -ne $virtualState) {
    Test-Required $checks $virtualState "virtual-agent-state" @(
        "schemaVersion", "agentId", "mapId", "virtualPosition",
        "lootBuffer", "pendingMutations", "updatedAtMs", "flags"
    )
    if ($virtualState.'$defs' -and $virtualState.'$defs'.virtualMutation) {
        Test-EnumContains $checks $virtualState.'$defs'.virtualMutation.properties.mutationType.enum "virtual-mutation.mutationType" @(
            "POSITION", "KILL", "EXP", "MESO", "ITEM", "QUEST", "POTION", "DEATH", "RECOVERY"
        )
    } else {
        Add-Check $checks "virtual-agent-state.virtualMutation" "FAIL" "Virtual Agent State schema is missing virtualMutation definition."
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
    Write-Host "Agent scaling contract verification: $overall"
    Write-Host "Failures: $failCount  Warnings: $warnCount"
    Write-Host ""
    foreach ($check in $checks) {
        Write-Host ("[{0}] {1} - {2}" -f $check.status, $check.id, $check.message)
    }
}

if ($failCount -gt 0) {
    exit 1
}
