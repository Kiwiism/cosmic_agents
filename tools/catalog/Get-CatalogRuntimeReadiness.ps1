param(
    [string] $GameCatalogDir = "tmp/game-catalog",
    [string] $NpcCatalogDir = "tmp/npc-catalog",
    [string] $AgentLlmCatalogDir = "tmp/agent-llm-catalog",
    [string] $ReactorCatalogDir = "tmp/reactor-catalog",
    [string] $OutputPath,
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Get-FileFact {
    param([string] $Path)

    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        return [ordered]@{
            path = $Path
            exists = $false
            length = 0
            lastWriteTime = $null
        }
    }

    $item = Get-Item -LiteralPath $Path
    return [ordered]@{
        path = $item.FullName
        exists = $true
        length = $item.Length
        lastWriteTime = $item.LastWriteTime.ToString("o")
    }
}

function New-Area {
    param(
        [string] $Area,
        [string] $Status,
        [string] $Category,
        [string[]] $Files,
        [string] $ReadyUse,
        [string] $DeferredReason
    )

    return [ordered]@{
        area = $Area
        status = $Status
        category = $Category
        files = @($Files)
        readyUse = $ReadyUse
        deferredReason = $DeferredReason
    }
}

function New-NextAction {
    param(
        [string] $Id,
        [string] $Status,
        [string] $Title,
        [string] $Evidence,
        [string] $Command
    )

    return [ordered]@{
        id = $Id
        status = $Status
        title = $Title
        evidence = $Evidence
        command = $Command
    }
}

function ConvertTo-MarkdownReadiness {
    param([object] $Report)

    $lines = [System.Collections.Generic.List[string]]::new()
    [void] $lines.Add("# Catalog Runtime Readiness")
    [void] $lines.Add("")
    [void] $lines.Add("Generated: $($Report.generatedAt)")
    [void] $lines.Add("")
    [void] $lines.Add("| Field | Value |")
    [void] $lines.Add("| --- | --- |")
    [void] $lines.Add("| Status | $($Report.status) |")
    [void] $lines.Add("| Ready areas | $($Report.counts.ready) |")
    [void] $lines.Add("| Deferred areas | $($Report.counts.deferred) |")
    [void] $lines.Add("| Missing areas | $($Report.counts.missing) |")
    [void] $lines.Add("")
    [void] $lines.Add("## Summary Counts")
    [void] $lines.Add("")
    [void] $lines.Add("Status counts:")
    foreach ($entry in $Report.statusCounts.GetEnumerator()) {
        [void] $lines.Add(("- `{0}`: {1}" -f $entry.Key, $entry.Value))
    }
    [void] $lines.Add("")
    [void] $lines.Add("Category counts:")
    foreach ($entry in $Report.categoryCounts.GetEnumerator()) {
        [void] $lines.Add(("- `{0}`: {1}" -f $entry.Key, $entry.Value))
    }
    [void] $lines.Add("")
    if (@($Report.deferredAreas).Count -gt 0) {
        [void] $lines.Add("Deferred areas:")
        foreach ($area in @($Report.deferredAreas)) {
            [void] $lines.Add(("- `{0}` ({1}): {2}" -f $area.area, $area.category, $area.deferredReason))
        }
        [void] $lines.Add("")
    }

    if (@($Report.nextActions).Count -gt 0) {
        [void] $lines.Add("Next actions:")
        foreach ($action in @($Report.nextActions)) {
            [void] $lines.Add(("- `{0}` ({1}): {2}" -f $action.id, $action.status, $action.title))
            [void] $lines.Add(("  Evidence: {0}" -f $action.evidence))
            if (![string]::IsNullOrWhiteSpace([string] $action.command)) {
                [void] $lines.Add(("  Command: ``{0}``" -f $action.command))
            }
        }
        [void] $lines.Add("")
    }

    [void] $lines.Add("")
    [void] $lines.Add("## Runtime Lookup Areas")
    [void] $lines.Add("")
    [void] $lines.Add("| Area | Status | Category | Ready Use | Deferred Reason |")
    [void] $lines.Add("| --- | --- | --- | --- | --- |")
    foreach ($area in @($Report.areas)) {
        [void] $lines.Add(("| {0} | {1} | {2} | {3} | {4} |" -f $area.area, $area.status, $area.category, $area.readyUse, $area.deferredReason))
    }
    [void] $lines.Add("")
    [void] $lines.Add("## File Facts")
    [void] $lines.Add("")
    [void] $lines.Add("| Key | Exists | Bytes | Last Write | Path |")
    [void] $lines.Add("| --- | --- | ---: | --- | --- |")
    foreach ($entry in $Report.files.GetEnumerator()) {
        $fact = $entry.Value
        [void] $lines.Add(("| {0} | {1} | {2} | {3} | `{4}` |" -f $entry.Key, $fact.exists, $fact.length, $fact.lastWriteTime, $fact.path))
    }
    [void] $lines.Add("")
    [void] $lines.Add("## Notes")
    [void] $lines.Add("")
    [void] $lines.Add("- `ready` means the offline file exists and can be consumed after a runtime adapter/loader is implemented.")
    [void] $lines.Add("- `deferred` means the data exists or is planned, but live validation or reconstructed Agent boundaries are still required.")
    [void] $lines.Add("- This report is read-only and does not refresh catalogs or modify runtime behavior.")

    return ($lines -join "`n")
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$files = [ordered]@{
    gameMaps = Get-FileFact (Join-Path $GameCatalogDir "generated_map_catalog.json")
    gameMobs = Get-FileFact (Join-Path $GameCatalogDir "generated_mob_catalog.json")
    gameItems = Get-FileFact (Join-Path $GameCatalogDir "generated_item_catalog.json")
    gameDrops = Get-FileFact (Join-Path $GameCatalogDir "generated_drop_catalog.json")
    gameQuests = Get-FileFact (Join-Path $GameCatalogDir "generated_quest_catalog.json")
    gameShops = Get-FileFact (Join-Path $GameCatalogDir "generated_shop_catalog.json")
    npcCatalog = Get-FileFact (Join-Path $NpcCatalogDir "generated_npc_catalog.json")
    npcActions = Get-FileFact (Join-Path $NpcCatalogDir "generated_npc_action_catalog.json")
    npcApproachPoints = Get-FileFact (Join-Path $NpcCatalogDir "generated_npc_approach_points.json")
    npcFastIndexes = Get-FileFact (Join-Path $NpcCatalogDir "generated_npc_fast_indexes.json")
    questDialogueTiming = Get-FileFact (Join-Path $NpcCatalogDir "generated_quest_dialogue_timing.json")
    portalGraph = Get-FileFact (Join-Path $AgentLlmCatalogDir "generated_portal_graph.json")
    mapSummary = Get-FileFact (Join-Path $AgentLlmCatalogDir "generated_map_summary_index.json")
    mobSpawn = Get-FileFact (Join-Path $AgentLlmCatalogDir "generated_mob_spawn_catalog.json")
    questObjectives = Get-FileFact (Join-Path $AgentLlmCatalogDir "generated_quest_objective_catalog.json")
    itemSources = Get-FileFact (Join-Path $AgentLlmCatalogDir "generated_item_source_index.json")
    resupply = Get-FileFact (Join-Path $AgentLlmCatalogDir "generated_resupply_catalog.json")
    affordances = Get-FileFact (Join-Path $AgentLlmCatalogDir "generated_action_affordance_catalog.json")
    mapleIslandMvp = Get-FileFact (Join-Path $AgentLlmCatalogDir "generated_maple_island_mvp_catalog.json")
    mapleIslandMvpIndexes = Get-FileFact (Join-Path $AgentLlmCatalogDir "generated_maple_island_mvp_fast_indexes.json")
    navigationTopology = Get-FileFact (Join-Path $AgentLlmCatalogDir "generated_navigation_topology_catalog.json")
    combatMapPolicy = Get-FileFact (Join-Path $AgentLlmCatalogDir "generated_combat_map_policy_catalog.json")
    travelServices = Get-FileFact (Join-Path $AgentLlmCatalogDir "generated_travel_service_catalog.json")
    progressionItemPolicy = Get-FileFact (Join-Path $AgentLlmCatalogDir "generated_progression_item_policy_catalog.json")
    questChainPolicy = Get-FileFact (Join-Path $AgentLlmCatalogDir "generated_quest_chain_policy_catalog.json")
    decisionManifest = Get-FileFact (Join-Path $AgentLlmCatalogDir "generated_agent_decision_catalog_manifest.json")
    reactorCatalog = Get-FileFact (Join-Path $ReactorCatalogDir "generated_reactor_catalog.json")
    manifest = Get-FileFact (Join-Path $AgentLlmCatalogDir "generated_catalog_manifest.json")
}

$manifest = $null
if ($files.manifest.exists) {
    try {
        $manifest = Get-Content -LiteralPath $files.manifest.path -Raw | ConvertFrom-Json
    } catch {
        $manifest = $null
    }
}

$areas = [System.Collections.Generic.List[object]]::new()

$allGameFacts = @($files.gameMaps, $files.gameMobs, $files.gameItems, $files.gameDrops, $files.gameQuests, $files.gameShops)
$allGameReady = @($allGameFacts | Where-Object { -not $_.exists }).Count -eq 0
[void] $areas.Add((New-Area "base-game-knowledge" $(if ($allGameReady) { "ready" } else { "missing" }) "catalog-runtime" @("gameMaps", "gameMobs", "gameItems", "gameDrops", "gameQuests", "gameShops") "Static map/mob/item/drop/quest/shop lookup." ""))

$npcReady = $files.npcCatalog.exists -and $files.npcActions.exists -and $files.npcFastIndexes.exists
[void] $areas.Add((New-Area "npc-actions-and-indexes" $(if ($npcReady) { "ready" } else { "missing" }) "npc-interaction" @("npcCatalog", "npcActions", "npcFastIndexes") "NPC placement/action reverse lookup." "Live NPC execution waits for reconstructed capability boundary."))

$approachReady = $files.npcApproachPoints.exists -and $files.questDialogueTiming.exists
[void] $areas.Add((New-Area "npc-realism-hints" $(if ($approachReady) { "ready" } else { "missing" }) "interaction-realism" @("npcApproachPoints", "questDialogueTiming") "Randomized interact spots and dialogue-length timing hints." "Runtime use waits for interaction realism package."))

$navReady = $files.portalGraph.exists -and $files.mapSummary.exists
[void] $areas.Add((New-Area "map-and-portal-lookup" $(if ($navReady) { "ready" } else { "missing" }) "catalog-runtime" @("portalGraph", "mapSummary") "Map summaries and portal graph lookup for planner/navigation inputs." "Live movement still waits for Agent navigation boundary."))

$combatReady = $files.mobSpawn.exists -and $files.itemSources.exists
[void] $areas.Add((New-Area "mob-drop-and-spawn-lookup" $(if ($combatReady) { "ready" } else { "missing" }) "agent-gameplay" @("mobSpawn", "itemSources") "Mob spawn, item source, and target selection lookup." "Live combat/loot behavior waits for reconstructed capability boundary."))

$questReady = $files.questObjectives.exists -and $files.mapleIslandMvp.exists -and $files.mapleIslandMvpIndexes.exists
[void] $areas.Add((New-Area "quest-objective-and-maple-island-mvp" $(if ($questReady) { "ready" } else { "missing" }) "maple-island-mvp" @("questObjectives", "mapleIslandMvp", "mapleIslandMvpIndexes") "Quest objective lookup and Maple Island MVP plan support." "Live quest interaction waits for NPC/Quest capability."))

$resupplyReady = $files.resupply.exists
[void] $areas.Add((New-Area "resupply-lookup" $(if ($resupplyReady) { "ready" } else { "missing" }) "agent-gameplay" @("resupply") "Potion/ammo/shop resupply lookup for future fallback behavior." "Live shop/buy actions wait for capability boundary."))

$affordanceReady = $files.affordances.exists
[void] $areas.Add((New-Area "llm-action-affordances" $(if ($affordanceReady) { "ready" } else { "missing" }) "llm-control" @("affordances") "LLM-safe action affordance summaries." "Command execution waits for LLM gateway and capability validators."))

$decisionReady = $files.navigationTopology.exists -and $files.combatMapPolicy.exists -and $files.travelServices.exists -and $files.progressionItemPolicy.exists -and $files.questChainPolicy.exists -and $files.decisionManifest.exists
[void] $areas.Add((New-Area "agent-decision-catalogs" $(if ($decisionReady) { "ready" } else { "missing" }) "catalog-runtime" @("navigationTopology", "combatMapPolicy", "travelServices", "progressionItemPolicy", "questChainPolicy", "decisionManifest") "Shared spatial, combat, travel, progression-item, and quest-chain facts/policy hints." "Every state-changing action remains subject to live capability and server validation."))

$reactorReady = $files.reactorCatalog.exists
[void] $areas.Add((New-Area "reactor-and-field-object-lookup" $(if ($reactorReady) { "ready" } else { "deferred" }) "catalog-runtime" @("reactorCatalog") "Reactor, box, and field-object lookup for future reactor capability." "Run tools/reactor-catalog/Export-ReactorCatalog.ps1 to generate optional reactor lookup data."))

$counts = [ordered]@{
    ready = @($areas | Where-Object { $_.status -eq "ready" }).Count
    deferred = @($areas | Where-Object { $_.status -eq "deferred" }).Count
    missing = @($areas | Where-Object { $_.status -eq "missing" }).Count
}
$categoryCounts = [ordered]@{}
foreach ($group in @($areas | Group-Object { $_.category } | Sort-Object Name)) {
    $categoryCounts[$group.Name] = $group.Count
}
$statusCounts = [ordered]@{}
foreach ($group in @($areas | Group-Object { $_.status } | Sort-Object Name)) {
    $statusCounts[$group.Name] = $group.Count
}
$deferredAreas = @($areas | Where-Object { $_.status -eq "deferred" } | ForEach-Object {
    [ordered]@{
        area = $_.area
        category = $_.category
        readyUse = $_.readyUse
        deferredReason = $_.deferredReason
        files = @($_.files)
    }
})
$missingAreas = @($areas | Where-Object { $_.status -eq "missing" } | ForEach-Object {
    [ordered]@{
        area = $_.area
        category = $_.category
        readyUse = $_.readyUse
        deferredReason = $_.deferredReason
        files = @($_.files)
    }
})

$nextActions = [System.Collections.Generic.List[object]]::new()
if (@($missingAreas).Count -gt 0) {
    [void] $nextActions.Add((New-NextAction `
        "refresh-missing-catalogs" `
        "required" `
        "Refresh or restore missing generated catalog files." `
        "Missing areas: $(@($missingAreas | ForEach-Object { $_.area }) -join ', ')." `
        "powershell -ExecutionPolicy Bypass -File .\tools\catalog\Update-AllCatalogs.ps1"))
}
if (@($deferredAreas).Count -gt 0) {
    [void] $nextActions.Add((New-NextAction `
        "review-deferred-catalog-areas" `
        "deferred" `
        "Review catalog areas intentionally waiting for later runtime boundaries." `
        "Deferred areas: $(@($deferredAreas | ForEach-Object { $_.area }) -join ', ')." `
        "powershell -ExecutionPolicy Bypass -File .\tools\catalog\Get-CatalogRuntimeReadiness.ps1"))
}

$status = if ($counts.missing -gt 0) {
    "INCOMPLETE"
} elseif ($counts.deferred -gt 0) {
    "READY_WITH_DEFERRED_ITEMS"
} else {
    "READY"
}
$warningIds = @($deferredAreas | ForEach-Object { $_.area })
$failureIds = @($missingAreas | ForEach-Object { $_.area })
$readyAreaIds = @($areas | Where-Object { $_.status -eq "ready" } | ForEach-Object { $_.area })
$deferredAreaIds = @($deferredAreas | ForEach-Object { $_.area })
$missingAreaIds = @($missingAreas | ForEach-Object { $_.area })

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    repoRoot = $repoRoot
    status = $status
    checkCount = $areas.Count
    passCount = $counts.ready
    failCount = $counts.missing
    warnCount = $counts.deferred
    warningIds = @($warningIds)
    failureIds = @($failureIds)
    readyAreaIds = @($readyAreaIds)
    deferredAreaIds = @($deferredAreaIds)
    missingAreaIds = @($missingAreaIds)
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    areaCount = $areas.Count
    returnedAreaCount = if ($SummaryOnly) { 0 } else { $areas.Count }
    fileFactCount = $files.Count
    returnedFileFactCount = if ($SummaryOnly) { 0 } else { $files.Count }
    deferredAreaCount = @($deferredAreas).Count
    missingAreaCount = @($missingAreas).Count
    nextActionCount = @($nextActions).Count
    returnedNextActionCount = @($nextActions).Count
    manifestOmitted = [bool] $SummaryOnly
    counts = $counts
    statusCounts = $statusCounts
    categoryCounts = $categoryCounts
    deferredAreas = @($deferredAreas)
    missingAreas = @($missingAreas)
    nextActions = @($nextActions)
    manifest = if ($SummaryOnly) { $null } else { $manifest }
    files = if ($SummaryOnly) { $null } else { $files }
    areas = if ($SummaryOnly) { $null } else { @($areas) }
}

if ($OutputPath) {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and !(Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }

    if ($Json) {
        $report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    } else {
        ConvertTo-MarkdownReadiness ([pscustomobject] $report) | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    }

    Write-Host "Catalog runtime readiness report written:"
    Write-Host "  $OutputPath"
} elseif ($Json) {
    $report | ConvertTo-Json -Depth 12
} else {
    ConvertTo-MarkdownReadiness ([pscustomobject] $report)
}

if ($counts.missing -gt 0) {
    exit 1
}
