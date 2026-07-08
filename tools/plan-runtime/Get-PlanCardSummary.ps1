param(
    [string] $PlanPath = "docs/agents/plans/maple-island-mvp.plan.json",
    [string] $OutputPath,
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Get-Array {
    param([object] $Value)

    if ($null -eq $Value) {
        return @()
    }
    if ($Value -is [System.Array]) {
        return @($Value)
    }
    return @($Value)
}

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

function Get-CapabilityDependency {
    param([string] $Kind)

    switch ($Kind) {
        { $_ -in @("quest-start", "quest-complete", "quest-chain", "quest-chain-if-available", "quest-if-available") } {
            return "npc-quest-interaction"
        }
        "navigate-through" {
            return "navigation"
        }
        "use-item" {
            return "inventory-use-item"
        }
        "reactor-box-items" {
            return "reactor-field-object"
        }
        "grant-scripted-item" {
            return "controlled-test-injection"
        }
        { $_ -in @("skip-optional-review", "stop-plan") } {
            return "plan-control"
        }
        default {
            return "unmapped"
        }
    }
}

function ConvertTo-MarkdownReport {
    param([object] $Report)

    $lines = [System.Collections.Generic.List[string]]::new()
    [void] $lines.Add("# Plan Card Summary")
    [void] $lines.Add("")
    [void] $lines.Add("Generated: $($Report.generatedAt)")
    [void] $lines.Add("")
    [void] $lines.Add("| Field | Value |")
    [void] $lines.Add("| --- | --- |")
    [void] $lines.Add("| Status | $($Report.status) |")
    [void] $lines.Add("| Failures | $($Report.failCount) |")
    [void] $lines.Add("| Warnings | $($Report.warnCount) |")
    [void] $lines.Add(("| Plan path | `{0}` |" -f $Report.planPath))
    [void] $lines.Add(("| Plan id | `{0}` |" -f $Report.planId))
    [void] $lines.Add(("| Title | {0} |" -f $Report.title))
    [void] $lines.Add(("| Category | `{0}` |" -f $Report.category))
    [void] $lines.Add(("| Priority | `{0}` |" -f $Report.priority))
    [void] $lines.Add(("| Plan status | `{0}` |" -f $Report.planStatus))
    [void] $lines.Add(("| Objective mode | `{0}` |" -f $Report.objectiveMode))
    [void] $lines.Add("| Route steps | $($Report.summary.routeStepCount) |")
    [void] $lines.Add("| Objectives | $($Report.summary.objectiveCount) |")
    [void] $lines.Add("| Unique maps | $($Report.summary.uniqueMapCount) |")
    [void] $lines.Add("| Unique quests | $($Report.summary.uniqueQuestCount) |")
    [void] $lines.Add("| Forbidden actions | $($Report.summary.forbiddenActionCount) |")
    [void] $lines.Add("")

    [void] $lines.Add("## Checks")
    [void] $lines.Add("")
    [void] $lines.Add("| Status | ID | Message |")
    [void] $lines.Add("| --- | --- | --- |")
    foreach ($check in @($Report.checks)) {
        $message = ([string] $check.message).Replace("|", "\|")
        [void] $lines.Add(("| {0} | `{1}` | {2} |" -f $check.status, $check.id, $message))
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Objective Kinds")
    [void] $lines.Add("")
    [void] $lines.Add("| Kind | Count |")
    [void] $lines.Add("| --- | ---: |")
    foreach ($kind in @($Report.objectiveKinds)) {
        [void] $lines.Add(("| `{0}` | {1} |" -f $kind.kind, $kind.count))
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Capability Dependencies")
    [void] $lines.Add("")
    [void] $lines.Add("| Dependency | Count | Objective Kinds |")
    [void] $lines.Add("| --- | ---: | --- |")
    foreach ($dependency in @($Report.capabilityDependencies)) {
        [void] $lines.Add(("| `{0}` | {1} | {2} |" -f $dependency.dependency, $dependency.count, (@($dependency.objectiveKinds) -join ", ")))
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Route")
    [void] $lines.Add("")
    [void] $lines.Add("| Step | Map | Label | Objectives |")
    [void] $lines.Add("| ---: | ---: | --- | ---: |")
    foreach ($step in @($Report.routeSummary)) {
        $label = ([string] $step.label).Replace("|", "\|")
        [void] $lines.Add(("| {0} | {1} | {2} | {3} |" -f $step.sequence, $step.mapId, $label, $step.objectiveCount))
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Notes")
    [void] $lines.Add("")
    [void] $lines.Add("- This is a read-only prep loader for plan-card files.")
    [void] $lines.Add("- It does not execute objectives, assign Agents, or call server runtime APIs.")
    [void] $lines.Add("- Runtime Plan Loader implementation still waits for reconstructed Agent boundaries.")

    return ($lines -join "`n")
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()

if (!(Test-Path -LiteralPath $PlanPath -PathType Leaf)) {
    Add-Check $checks "file:plan" "FAIL" "Missing plan card at $PlanPath."
    $plan = $null
} else {
    Add-Check $checks "file:plan" "PASS" "Found plan card at $PlanPath."
    try {
        $plan = Get-Content -LiteralPath $PlanPath -Raw | ConvertFrom-Json
        Add-Check $checks "json:plan" "PASS" "Plan card is valid JSON."
    } catch {
        Add-Check $checks "json:plan" "FAIL" "Plan card is invalid JSON: $($_.Exception.Message)"
        $plan = $null
    }
}

$requiredFields = @(
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
)

if ($null -ne $plan) {
    foreach ($field in $requiredFields) {
        if ($plan.PSObject.Properties.Name -contains $field) {
            Add-Check $checks "required:$field" "PASS" "Plan has required field $field."
        } else {
            Add-Check $checks "required:$field" "FAIL" "Plan is missing required field $field."
        }
    }
}

$routeRows = [System.Collections.Generic.List[object]]::new()
$objectiveRows = [System.Collections.Generic.List[object]]::new()
$questIds = [System.Collections.Generic.HashSet[int]]::new()
$mapIds = [System.Collections.Generic.HashSet[int]]::new()

if ($null -ne $plan) {
    $route = @(Get-Array $plan.route)
    if ($route.Count -gt 0) {
        Add-Check $checks "route:not-empty" "PASS" "Plan route has $($route.Count) step(s)."
    } else {
        Add-Check $checks "route:not-empty" "FAIL" "Plan route is empty."
    }

    for ($i = 0; $i -lt $route.Count; $i++) {
        $step = $route[$i]
        $objectives = @(Get-Array $step.objectives)
        if ($null -ne $step.mapId) {
            [void] $mapIds.Add([int] $step.mapId)
        }

        $routeRows.Add([pscustomobject] [ordered]@{
            sequence = $i + 1
            mapId = $step.mapId
            label = $step.label
            objectiveCount = $objectives.Count
        }) | Out-Null

        for ($j = 0; $j -lt $objectives.Count; $j++) {
            $objective = $objectives[$j]
            $ids = @()
            if ($null -ne $objective.questId) {
                $ids += [int] $objective.questId
            }
            foreach ($questId in Get-Array $objective.questIds) {
                $ids += [int] $questId
            }
            foreach ($questId in $ids) {
                [void] $questIds.Add($questId)
            }

            $objectiveRows.Add([pscustomobject] [ordered]@{
                sequence = $objectiveRows.Count + 1
                routeStep = $i + 1
                mapId = $step.mapId
                kind = $objective.kind
                questIds = $ids
                npcId = $objective.npcId
                itemId = $objective.itemId
                reason = $objective.reason
            }) | Out-Null
        }
    }
}

$duplicateRequiredQuestIds = @()
if ($null -ne $plan -and $plan.questPolicy -and $plan.questPolicy.requiredQuestIds) {
    $requiredQuestIds = @(Get-Array $plan.questPolicy.requiredQuestIds | ForEach-Object { [int] $_ })
    $duplicateRequiredQuestIds = @($requiredQuestIds | Group-Object | Where-Object { $_.Count -gt 1 } | Select-Object -ExpandProperty Name)
    if ($duplicateRequiredQuestIds.Count -eq 0) {
        Add-Check $checks "quest-policy:required-unique" "PASS" "Required quest ids are unique."
    } else {
        Add-Check $checks "quest-policy:required-unique" "FAIL" "Required quest ids contain duplicates: $($duplicateRequiredQuestIds -join ', ')."
    }
}

if ($null -ne $plan -and $plan.exitCriteria -and $plan.exitCriteria.forbiddenActions) {
    Add-Check $checks "exit:forbidden-actions" "PASS" "Plan declares $(@(Get-Array $plan.exitCriteria.forbiddenActions).Count) forbidden action(s)."
} elseif ($null -ne $plan) {
    Add-Check $checks "exit:forbidden-actions" "WARN" "Plan declares no forbidden actions."
}

$objectiveKindRows = @(
    $objectiveRows |
        Group-Object kind |
        Sort-Object Name |
        ForEach-Object {
            [pscustomobject] [ordered]@{
                kind = $_.Name
                count = $_.Count
            }
        }
)
$capabilityDependencyRows = @(
    $objectiveRows |
        ForEach-Object {
            [pscustomobject] [ordered]@{
                dependency = Get-CapabilityDependency ([string] $_.kind)
                kind = $_.kind
            }
        } |
        Group-Object dependency |
        Sort-Object Name |
        ForEach-Object {
            [pscustomobject] [ordered]@{
                dependency = $_.Name
                count = $_.Count
                objectiveKinds = @($_.Group | ForEach-Object { $_.kind } | Sort-Object -Unique)
            }
        }
)

$unmappedObjectiveKinds = @(
    $capabilityDependencyRows |
        Where-Object { $_.dependency -eq "unmapped" } |
        ForEach-Object { @($_.objectiveKinds) } |
        Sort-Object -Unique
)
if ($unmappedObjectiveKinds.Count -gt 0) {
    Add-Check $checks "capability-dependencies:mapped" "FAIL" "Objective kind(s) have no capability dependency mapping: $($unmappedObjectiveKinds -join ', ')."
} elseif ($objectiveRows.Count -gt 0) {
    Add-Check $checks "capability-dependencies:mapped" "PASS" "All objective kinds map to capability dependencies."
}

$failures = @($checks | Where-Object { $_.status -eq "FAIL" })
$warnings = @($checks | Where-Object { $_.status -eq "WARN" })
$failCount = $failures.Count
$warnCount = $warnings.Count
$passCount = @($checks | Where-Object { $_.status -eq "PASS" }).Count
$status = if ($failCount -gt 0) {
    "FAIL"
} elseif ($warnCount -gt 0) {
    "INCOMPLETE"
} else {
    "PASS"
}

$forbiddenActionCount = 0
if ($null -ne $plan -and $null -ne $plan.exitCriteria -and $null -ne $plan.exitCriteria.forbiddenActions) {
    $forbiddenActionCount = @(Get-Array $plan.exitCriteria.forbiddenActions).Count
}

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    repoRoot = $repoRoot
    planPath = $PlanPath
    status = $status
    checkCount = $checks.Count
    passCount = $passCount
    failCount = $failCount
    warnCount = $warnCount
    failureIds = @($failures | ForEach-Object { $_.id })
    warningIds = @($warnings | ForEach-Object { $_.id })
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    returnedCheckCount = if ($SummaryOnly) { 0 } else { $checks.Count }
    returnedRouteStepCount = if ($SummaryOnly) { 0 } else { $routeRows.Count }
    returnedObjectiveCount = if ($SummaryOnly) { 0 } else { $objectiveRows.Count }
    returnedObjectiveKindCount = $objectiveKindRows.Count
    returnedCapabilityDependencyCount = $capabilityDependencyRows.Count
    routeStepCount = $routeRows.Count
    objectiveCount = $objectiveRows.Count
    uniqueMapCount = $mapIds.Count
    uniqueQuestCount = $questIds.Count
    forbiddenActionCount = $forbiddenActionCount
    capabilityDependencyCount = $capabilityDependencyRows.Count
    planId = if ($plan) { $plan.planId } else { $null }
    title = if ($plan) { $plan.title } else { $null }
    category = if ($plan) { $plan.category } else { $null }
    priority = if ($plan) { $plan.priority } else { $null }
    planStatus = if ($plan) { $plan.status } else { $null }
    objectiveMode = if ($plan) { $plan.objectiveMode } else { $null }
    summary = [ordered]@{
        routeStepCount = $routeRows.Count
        objectiveCount = $objectiveRows.Count
        uniqueMapCount = $mapIds.Count
        uniqueQuestCount = $questIds.Count
        forbiddenActionCount = $forbiddenActionCount
        capabilityDependencyCount = $capabilityDependencyRows.Count
    }
    checks = if ($SummaryOnly) { @() } else { @($checks) }
    routeSummary = if ($SummaryOnly) { @() } else { @($routeRows) }
    objectiveKinds = @($objectiveKindRows)
    capabilityDependencies = @($capabilityDependencyRows)
    objectives = if ($SummaryOnly) { @() } else { @($objectiveRows) }
}

if ($OutputPath) {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and !(Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }

    if ($Json) {
        $report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    } else {
        ConvertTo-MarkdownReport ([pscustomobject] $report) | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    }

    Write-Host "Plan card summary written:"
    Write-Host "  $OutputPath"
} elseif ($Json) {
    $report | ConvertTo-Json -Depth 12
} else {
    ConvertTo-MarkdownReport ([pscustomobject] $report)
}

if ($failCount -gt 0) {
    exit 1
}
