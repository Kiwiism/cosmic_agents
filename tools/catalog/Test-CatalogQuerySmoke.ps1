param(
    [string] $AgentLlmCatalogDir = "tmp/agent-llm-catalog",
    [string] $NpcCatalogDir = "tmp/npc-catalog",
    [string] $ReactorCatalogDir = "tmp/reactor-catalog",
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
        [string] $Path,
        [string] $Label
    )

    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        Add-Check $Checks "catalog-query:${Label}:exists" "FAIL" "Missing $Label at $Path."
        return $null
    }

    try {
        $value = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
        Add-Check $Checks "catalog-query:${Label}:exists" "PASS" "Loaded $Label from $Path."
        return $value
    } catch {
        Add-Check $Checks "catalog-query:${Label}:parse" "FAIL" "Could not parse $Label at $Path`: $($_.Exception.Message)"
        return $null
    }
}

function As-Array {
    param([object] $Value)

    if ($null -eq $Value) {
        return @()
    }

    if ($Value -is [System.Array]) {
        return @($Value)
    }

    return @($Value)
}

function Contains-Value {
    param(
        [object] $Values,
        [object] $Expected
    )

    foreach ($value in @(As-Array $Values)) {
        if ([string] $value -eq [string] $Expected) {
            return $true
        }
    }

    return $false
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()

$manifestPath = Join-Path $AgentLlmCatalogDir "generated_catalog_manifest.json"
$mvpIndexPath = Join-Path $AgentLlmCatalogDir "generated_maple_island_mvp_fast_indexes.json"
$actionAffordancePath = Join-Path $AgentLlmCatalogDir "generated_action_affordance_catalog.json"
$itemSourcePath = Join-Path $AgentLlmCatalogDir "generated_item_source_index.json"
$npcCatalogPath = Join-Path $NpcCatalogDir "generated_npc_fast_indexes.json"
$reactorCatalogPath = Join-Path $ReactorCatalogDir "generated_reactor_catalog.json"

$manifest = Read-JsonFile $checks $manifestPath "agent-llm-manifest"
$mvpIndexes = Read-JsonFile $checks $mvpIndexPath "maple-island-mvp-fast-indexes"
$actionAffordances = Read-JsonFile $checks $actionAffordancePath "action-affordances"
$itemSources = Read-JsonFile $checks $itemSourcePath "item-source-index"
$npcCatalog = Read-JsonFile $checks $npcCatalogPath "npc-fast-indexes"
$reactorCatalog = Read-JsonFile $checks $reactorCatalogPath "reactor-catalog"

if ($manifest) {
    if ([int] $manifest.counts.mapleIslandMvpQuests -ge 40) {
        Add-Check $checks "catalog-query:manifest:maple-island-count" "PASS" "Manifest lists $($manifest.counts.mapleIslandMvpQuests) Maple Island MVP quests."
    } else {
        Add-Check $checks "catalog-query:manifest:maple-island-count" "FAIL" "Manifest Maple Island MVP quest count is too low."
    }

    if ([int] $manifest.counts.actionAffordances -ge 8) {
        Add-Check $checks "catalog-query:manifest:action-affordance-count" "PASS" "Manifest lists $($manifest.counts.actionAffordances) action affordances."
    } else {
        Add-Check $checks "catalog-query:manifest:action-affordance-count" "FAIL" "Manifest action affordance count is too low."
    }
}

if ($mvpIndexes) {
    $quest1029 = $mvpIndexes.questId_to_mvpRule."1029"
    if ($quest1029 -and $quest1029.required -eq $true -and [int] $quest1029.startNpcId -eq 2005) {
        Add-Check $checks "catalog-query:mvp:quest-1029" "PASS" "Quest 1029 resolves to required Maple Island rule with start NPC 2005."
    } else {
        Add-Check $checks "catalog-query:mvp:quest-1029" "FAIL" "Quest 1029 did not resolve to the expected Maple Island MVP rule."
    }

    $map10000 = $mvpIndexes.mapId_to_routeFacts."10000"
    if ($map10000 -and $map10000.reachableFromStartMap -eq $true -and (Contains-Value $map10000.npcIds 2007)) {
        Add-Check $checks "catalog-query:mvp:map-10000" "PASS" "Start map 10000 is reachable and includes NPC 2007."
    } else {
        Add-Check $checks "catalog-query:mvp:map-10000" "FAIL" "Start map 10000 did not expose the expected reachable route facts."
    }
}

if ($npcCatalog) {
    $npc2005 = $npcCatalog.npcId_to_catalogRow."2005"
    $startsQuest1029 = $npc2005 -and (Contains-Value $npc2005.interactions.quests.starts 1029)
    $hasPlacement = $npc2005 -and @(As-Array $npc2005.placements).Count -gt 0
    if ($startsQuest1029 -and $hasPlacement) {
        Add-Check $checks "catalog-query:npc:2005" "PASS" "NPC 2005 has placements and starts quest 1029."
    } else {
        Add-Check $checks "catalog-query:npc:2005" "FAIL" "NPC 2005 is missing quest 1029 or placement facts."
    }
}

if ($itemSources) {
    $blueSnailShell = @(As-Array $itemSources | Where-Object { [int] $_.itemId -eq 4000000 } | Select-Object -First 1)
    $mobSources = @($blueSnailShell.dropSources | Where-Object { $_.sourceType -eq "mob" })
    if ($blueSnailShell.Count -gt 0 -and $mobSources.Count -gt 0) {
        Add-Check $checks "catalog-query:item-source:4000000" "PASS" "Item 4000000 resolves to $($mobSources.Count) mob drop source(s)."
    } else {
        Add-Check $checks "catalog-query:item-source:4000000" "FAIL" "Item 4000000 did not resolve to mob drop sources."
    }
}

if ($actionAffordances) {
    $actions = @(As-Array $actionAffordances | ForEach-Object { $_.action })
    $requiredActions = @(
        "navigate.toMap",
        "navigate.toPoint",
        "npc.startQuest",
        "npc.completeQuest",
        "combat.killMob",
        "loot.collectItem",
        "shop.buy",
        "plan.executeCard"
    )

    $missingActions = @($requiredActions | Where-Object { $_ -notin $actions })
    if ($missingActions.Count -eq 0) {
        Add-Check $checks "catalog-query:action-affordances:required" "PASS" "All required MVP action affordances are present."
    } else {
        Add-Check $checks "catalog-query:action-affordances:required" "FAIL" "Missing action affordance(s): $($missingActions -join ', ')."
    }
}

if ($reactorCatalog) {
    $reactorEntries = @(As-Array $reactorCatalog.entries)
    $pioReactors = @($reactorEntries | Where-Object {
        (Contains-Value $_.inferredQuestIds 1008) -or
        (Contains-Value $_.inferredItemIds 4031161) -or
        (Contains-Value $_.inferredItemIds 4031162) -or
        (Contains-Value $_.flags "maple-island-pio-candidate")
    })
    if ($reactorEntries.Count -gt 0 -and $pioReactors.Count -gt 0) {
        Add-Check $checks "catalog-query:reactor:pio" "PASS" "Reactor catalog exposes $($pioReactors.Count) Pio/Maple Island candidate placement(s)."
    } else {
        Add-Check $checks "catalog-query:reactor:pio" "FAIL" "Reactor catalog did not expose Pio/Maple Island candidate placements."
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
    "WARN"
} else {
    "PASS"
}

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    status = $overall
    repoRoot = $repoRoot
    agentLlmCatalogDir = $AgentLlmCatalogDir
    npcCatalogDir = $NpcCatalogDir
    reactorCatalogDir = $ReactorCatalogDir
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
    Write-Host "Catalog query smoke verification: $overall"
    Write-Host "Repo root: $repoRoot"
    Write-Host "Failures: $failCount  Warnings: $warnCount"
    Write-Host ""

    foreach ($check in $checks) {
        Write-Host ("[{0}] {1} - {2}" -f $check.status, $check.id, $check.message)
    }
}

if ($failCount -gt 0) {
    exit 1
}
