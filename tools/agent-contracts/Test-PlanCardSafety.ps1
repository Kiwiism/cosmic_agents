param(
    [string] $PlanPath = "docs/agents/plans/maple-island-mvp.plan.json",
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

function Test-HasProperty {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [object] $Object,
        [string] $Property,
        [string] $Id
    )

    if ($null -eq $Object) {
        Add-Check $Checks $Id "FAIL" "Cannot check $Property because object is missing."
        return $false
    }

    if ($Object.PSObject.Properties.Name -contains $Property) {
        Add-Check $Checks $Id "PASS" "Found required property $Property."
        return $true
    }

    Add-Check $Checks $Id "FAIL" "Missing required property $Property."
    return $false
}

function Test-UniqueIntList {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [object[]] $Values,
        [string] $Id
    )

    $numbers = @($Values | ForEach-Object { [int] $_ })
    $duplicates = @($numbers | Group-Object | Where-Object { $_.Count -gt 1 } | Select-Object -ExpandProperty Name)
    if ($duplicates.Count -eq 0) {
        Add-Check $Checks $Id "PASS" "$Id has no duplicate id(s)."
    } else {
        Add-Check $Checks $Id "FAIL" "$Id has duplicate id(s): $($duplicates -join ', ')."
    }
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()
$plan = $null

if (!(Test-Path -LiteralPath $PlanPath -PathType Leaf)) {
    Add-Check $checks "plan:file" "FAIL" "Plan card file is missing: $PlanPath."
} else {
    Add-Check $checks "plan:file" "PASS" "Found plan card file $PlanPath."
    try {
        $plan = Get-Content -LiteralPath $PlanPath -Raw | ConvertFrom-Json
        Add-Check $checks "plan:json" "PASS" "Plan card is valid JSON."
    } catch {
        Add-Check $checks "plan:json" "FAIL" "Plan card is not valid JSON: $($_.Exception.Message)"
    }
}

foreach ($property in @("planId", "entryCriteria", "exitCriteria", "questPolicy", "route")) {
    Test-HasProperty $checks $plan $property "plan.required:$property" | Out-Null
}

if ($null -ne $plan) {
    if ($plan.planId -eq "maple-island-mvp") {
        Add-Check $checks "plan:id" "PASS" "Plan id is maple-island-mvp."
    } else {
        Add-Check $checks "plan:id" "FAIL" "Plan id should be maple-island-mvp."
    }

    if ([int] $plan.entryCriteria.requiredStartMapId -eq 10000) {
        Add-Check $checks "entry:start-map" "PASS" "Entry criteria starts at map 10000."
    } else {
        Add-Check $checks "entry:start-map" "FAIL" "Entry criteria should require start map 10000."
    }

    if ([int] $plan.exitCriteria.finalMapId -eq 2000000) {
        Add-Check $checks "exit:final-map" "PASS" "Exit criteria final map is Southperry (2000000)."
    } else {
        Add-Check $checks "exit:final-map" "FAIL" "Exit criteria final map should be 2000000."
    }

    $allowedIncomplete = @($plan.exitCriteria.allowedIncompleteQuestIds | ForEach-Object { [int] $_ })
    if ($allowedIncomplete -contains 1046) {
        Add-Check $checks "exit:biggs-start-only" "PASS" "Exit criteria allows Biggs quest 1046 to remain incomplete."
    } else {
        Add-Check $checks "exit:biggs-start-only" "FAIL" "Exit criteria should allow quest 1046 to remain incomplete."
    }

    $blockedCompleted = @($plan.exitCriteria.blockedCompletedQuestIds | ForEach-Object { [int] $_ })
    if ($blockedCompleted -contains 1028) {
        Add-Check $checks "exit:blocked-completed-1028" "PASS" "Exit criteria blocks quest 1028 completion."
    } else {
        Add-Check $checks "exit:blocked-completed-1028" "FAIL" "Exit criteria should block quest 1028 completion."
    }

    $forbiddenActions = @($plan.exitCriteria.forbiddenActions)
    $shanksTravel = @($forbiddenActions | Where-Object {
        $_.type -eq "npc-travel" -and [int] $_.npcId -eq 22000
    })
    if ($shanksTravel.Count -gt 0) {
        Add-Check $checks "forbidden:shanks-travel" "PASS" "Plan forbids Shanks NPC travel."
    } else {
        Add-Check $checks "forbidden:shanks-travel" "FAIL" "Plan must forbid Shanks NPC travel."
    }

    $southperryStep = @($plan.route | Where-Object { [int] $_.mapId -eq 2000000 } | Select-Object -First 1)
    if ($southperryStep.Count -gt 0) {
        Add-Check $checks "route:southperry" "PASS" "Route includes Southperry final step."
        $objectives = @($southperryStep[0].objectives)
        $shanksQuestComplete = @($objectives | Where-Object {
            $_.kind -eq "quest-complete" -and [int] $_.questId -eq 1026 -and [int] $_.npcId -eq 22000
        })
        if ($shanksQuestComplete.Count -gt 0) {
            Add-Check $checks "route:shanks-quest-complete-allowed" "PASS" "Plan allows Shanks quest completion for quest 1026."
        } else {
            Add-Check $checks "route:shanks-quest-complete-allowed" "FAIL" "Plan should allow Shanks quest completion for quest 1026."
        }

        $travelObjectives = @($objectives | Where-Object {
            ($_.kind -match "travel" -or $_.kind -match "npc-travel") -and [int] $_.npcId -eq 22000
        })
        if ($travelObjectives.Count -eq 0) {
            Add-Check $checks "route:no-shanks-travel-objective" "PASS" "Route has no Shanks travel objective."
        } else {
            Add-Check $checks "route:no-shanks-travel-objective" "FAIL" "Route should not include Shanks travel objective."
        }
    } else {
        Add-Check $checks "route:southperry" "FAIL" "Route is missing Southperry final step."
    }

    if ($plan.questPolicy) {
        foreach ($field in @("requiredQuestIds", "startOnlyQuestIds", "excludedQuestIds", "optionalReviewQuestIds")) {
            if ($plan.questPolicy.PSObject.Properties.Name -contains $field) {
                Test-UniqueIntList $checks @($plan.questPolicy.$field) "questPolicy:$field"
            }
        }
    }
}

$failures = @($checks | Where-Object { $_.status -eq "FAIL" })
$warnings = @($checks | Where-Object { $_.status -eq "WARN" })
$failCount = $failures.Count
$warnCount = $warnings.Count
$passCount = @($checks | Where-Object { $_.status -eq "PASS" }).Count
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
    planPath = $PlanPath
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
    Write-Host "Plan card safety verification: $overall"
    Write-Host "Failures: $failCount  Warnings: $warnCount"
    Write-Host ""
    foreach ($check in $checks) {
        Write-Host ("[{0}] {1} - {2}" -f $check.status, $check.id, $check.message)
    }
}

if ($failCount -gt 0) {
    exit 1
}
