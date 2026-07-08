param(
    [string] $CatalogDir = "tmp/agent-llm-catalog",
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

function Read-JsonFile {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [string] $Id,
        [string] $Path
    )

    if (!(Test-Path -LiteralPath $Path)) {
        Add-Check $Checks "file:$Id" "FAIL" "Missing $Id at $Path."
        return $null
    }

    Add-Check $Checks "file:$Id" "PASS" "Found $Id at $Path."

    try {
        $value = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
        Add-Check $Checks "json:$Id" "PASS" "$Id is valid JSON."
        return $value
    } catch {
        Add-Check $Checks "json:$Id" "FAIL" "$Id is not valid JSON: $($_.Exception.Message)"
        return $null
    }
}

function Get-ArrayCount {
    param([object] $Value)

    if ($null -eq $Value) {
        return 0
    }
    if ($Value -is [System.Array]) {
        return $Value.Count
    }
    return 1
}

function Test-NonEmptyArray {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [string] $Id,
        [object] $Value
    )

    $count = Get-ArrayCount $Value
    if ($count -gt 0) {
        Add-Check $Checks "content:$Id" "PASS" "$Id has $count row(s)."
    } else {
        Add-Check $Checks "content:$Id" "FAIL" "$Id is empty."
    }
}

function Test-ManifestFileEntry {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [object] $Manifest,
        [string] $Name
    )

    if ($null -eq $Manifest -or $null -eq $Manifest.files) {
        Add-Check $Checks "manifest:$Name" "FAIL" "Manifest is missing files list."
        return
    }

    $entry = @($Manifest.files | Where-Object { $_.name -eq $Name } | Select-Object -First 1)
    if ($entry.Count -eq 0) {
        Add-Check $Checks "manifest:$Name" "FAIL" "Manifest does not list $Name."
        return
    }

    $path = $entry[0].path
    if ([string]::IsNullOrWhiteSpace($path) -or !(Test-Path -LiteralPath $path)) {
        Add-Check $Checks "manifest:$Name" "FAIL" "Manifest lists $Name but path is missing: $path."
        return
    }

    Add-Check $Checks "manifest:$Name" "PASS" "Manifest lists existing $Name file."
}

function Test-MapleIslandMvp {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [object] $Catalog,
        [object] $Indexes
    )

    if ($null -eq $Catalog) {
        Add-Check $Checks "mvp:catalog" "FAIL" "Maple Island MVP catalog is missing."
        return
    }

    if ($Catalog.planId -eq "maple-island-mvp") {
        Add-Check $Checks "mvp:plan-id" "PASS" "Maple Island MVP plan id is present."
    } else {
        Add-Check $Checks "mvp:plan-id" "FAIL" "Maple Island MVP plan id should be maple-island-mvp."
    }

    if ([int] $Catalog.startMapId -eq 10000) {
        Add-Check $Checks "mvp:start-map" "PASS" "MVP start map is 10000."
    } else {
        Add-Check $Checks "mvp:start-map" "FAIL" "MVP start map should be 10000."
    }

    if ([int] $Catalog.finalMapId -eq 2000000) {
        Add-Check $Checks "mvp:final-map" "PASS" "MVP final map is Southperry (2000000)."
    } else {
        Add-Check $Checks "mvp:final-map" "FAIL" "MVP final map should be Southperry (2000000)."
    }

    $questIds = @($Catalog.quests | ForEach-Object { [int] $_.questId })
    foreach ($questId in @(1008, 1021, 1046, 8020)) {
        if ($questIds -contains $questId) {
            Add-Check $Checks "mvp:quest:$questId" "PASS" "MVP catalog contains quest $questId."
        } else {
            Add-Check $Checks "mvp:quest:$questId" "FAIL" "MVP catalog is missing quest $questId."
        }
    }

    $rules = @($Catalog.specialRules | ForEach-Object { $_.ruleId })
    foreach ($ruleId in @("pio-reactor-boxes", "roger-apple", "yoona-cash-shop-shopping-guide", "biggs-1046-start-only")) {
        if ($rules -contains $ruleId) {
            Add-Check $Checks "mvp:special-rule:$ruleId" "PASS" "MVP catalog contains special rule $ruleId."
        } else {
            Add-Check $Checks "mvp:special-rule:$ruleId" "FAIL" "MVP catalog is missing special rule $ruleId."
        }
    }

    $forbiddenNpcIds = @($Catalog.forbiddenActions | Where-Object { $_.type -eq "npc-travel" } | ForEach-Object { [int] $_.npcId })
    if ($forbiddenNpcIds -contains 22000) {
        Add-Check $Checks "mvp:forbidden-shanks" "PASS" "MVP catalog forbids Shanks travel."
    } else {
        Add-Check $Checks "mvp:forbidden-shanks" "FAIL" "MVP catalog should forbid Shanks travel."
    }

    if ($null -ne $Indexes -and $null -ne $Indexes.questId_to_mvpRule) {
        $indexKeys = @($Indexes.questId_to_mvpRule.PSObject.Properties.Name)
        if ($indexKeys -contains "1046") {
            Add-Check $Checks "mvp:index:1046" "PASS" "MVP fast index includes Biggs quest 1046."
        } else {
            Add-Check $Checks "mvp:index:1046" "FAIL" "MVP fast index is missing Biggs quest 1046."
        }
    } else {
        Add-Check $Checks "mvp:indexes" "FAIL" "MVP fast indexes are missing questId_to_mvpRule."
    }
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()

$requiredFiles = [ordered]@{
    portalGraph = "generated_portal_graph.json"
    mobSpawn = "generated_mob_spawn_catalog.json"
    mapSummary = "generated_map_summary_index.json"
    questObjectives = "generated_quest_objective_catalog.json"
    itemSources = "generated_item_source_index.json"
    resupply = "generated_resupply_catalog.json"
    affordances = "generated_action_affordance_catalog.json"
    mapleIslandMvp = "generated_maple_island_mvp_catalog.json"
    mapleIslandMvpIndexes = "generated_maple_island_mvp_fast_indexes.json"
    manifest = "generated_catalog_manifest.json"
}

$loaded = @{}
foreach ($key in $requiredFiles.Keys) {
    $path = Join-Path $CatalogDir $requiredFiles[$key]
    $loaded[$key] = Read-JsonFile $checks $key $path
}

foreach ($key in @("portalGraph", "mobSpawn", "mapSummary", "questObjectives", "itemSources", "resupply", "affordances")) {
    Test-NonEmptyArray $checks $key $loaded[$key]
}

$manifest = $loaded.manifest
foreach ($name in @($requiredFiles.Keys | Where-Object { $_ -ne "manifest" })) {
    Test-ManifestFileEntry $checks $manifest $name
}

if ($null -ne $manifest -and $null -ne $manifest.counts) {
    if ([int] $manifest.counts.actionAffordances -ge 8) {
        Add-Check $checks "manifest:action-affordance-count" "PASS" "Manifest action affordance count is $($manifest.counts.actionAffordances)."
    } else {
        Add-Check $checks "manifest:action-affordance-count" "WARN" "Manifest has fewer than 8 action affordances."
    }

    if ([int] $manifest.counts.mapleIslandMvpQuests -gt 0) {
        Add-Check $checks "manifest:maple-mvp-quests" "PASS" "Manifest records Maple Island MVP quest count $($manifest.counts.mapleIslandMvpQuests)."
    } else {
        Add-Check $checks "manifest:maple-mvp-quests" "FAIL" "Manifest records no Maple Island MVP quests."
    }
} else {
    Add-Check $checks "manifest:counts" "FAIL" "Manifest is missing counts."
}

Test-MapleIslandMvp $checks $loaded.mapleIslandMvp $loaded.mapleIslandMvpIndexes

$failCount = @($checks | Where-Object { $_.status -eq "FAIL" }).Count
$warnCount = @($checks | Where-Object { $_.status -eq "WARN" }).Count

$overall = if ($failCount -gt 0) {
    "FAIL"
} elseif ($warnCount -gt 0) {
    "INCOMPLETE"
} else {
    "PASS"
}

$report = [ordered]@{
    status = $overall
    repoRoot = $repoRoot
    catalogDir = $CatalogDir
    failCount = $failCount
    warnCount = $warnCount
    checkCount = @($checks).Count
    passCount = @($checks | Where-Object { $_.status -eq "PASS" }).Count
    warningIds = @($checks | Where-Object { $_.status -eq "WARN" } | ForEach-Object { $_.id })
    failureIds = @($checks | Where-Object { $_.status -eq "FAIL" } | ForEach-Object { $_.id })
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    returnedCheckCount = if ($SummaryOnly) { 0 } else { @($checks).Count }
    checks = if ($SummaryOnly) { $null } else { @($checks) }
}

if ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    Write-Host "Agent/LLM catalog verification: $overall"
    Write-Host "Repo root: $repoRoot"
    Write-Host "Catalog dir: $CatalogDir"
    Write-Host "Failures: $failCount  Warnings: $warnCount"
    Write-Host ""

    if ($SummaryOnly) {
        Write-Host "Detailed check rows omitted."
    } else {
        foreach ($check in $checks) {
            Write-Host ("[{0}] {1} - {2}" -f $check.status, $check.id, $check.message)
        }
    }
}

if ($failCount -gt 0) {
    exit 1
}
