param(
    [string] $WzRoot = "wz",
    [string] $SqlRoot = "sql",
    [string] $ScriptRoot = "scripts",
    [string] $OverrideRoot = "docs/agents/catalog-overrides",
    [string] $GameCatalogDir = "tmp/game-catalog",
    [string] $NpcCatalogDir = "tmp/npc-catalog",
    [string] $AgentLlmCatalogDir = "tmp/agent-llm-catalog",
    [string] $ReactorCatalogDir = "tmp/reactor-catalog",
    [string] $OutputPath,
    [string] $OutputManifestPath,
    [string] $OutputSourceHashesPath,
    [switch] $IncludeSourceHashes,
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Add-Entry {
    param(
        [System.Collections.Generic.List[object]] $Entries,
        [string] $Category,
        [string] $Key,
        [string] $Path,
        [bool] $Required,
        [string] $ReadyUse,
        [string] $DeferredReason = ""
    )

    $exists = Test-Path -LiteralPath $Path -PathType Leaf
    $length = 0
    $lastWriteTime = $null
    if ($exists) {
        $item = Get-Item -LiteralPath $Path
        $length = $item.Length
        $lastWriteTime = $item.LastWriteTime.ToString("o")
    }

    $status = if ($exists) {
        "ready"
    } elseif ($Required) {
        "missing"
    } else {
        "deferred"
    }

    $Entries.Add([ordered]@{
        category = $Category
        key = $Key
        path = $Path
        exists = $exists
        required = $Required
        status = $status
        length = $length
        lastWriteTime = $lastWriteTime
        readyUse = $ReadyUse
        deferredReason = $DeferredReason
    }) | Out-Null
}

function New-FileEntry {
    param([object] $Entry)

    $hash = $null
    if ($Entry.exists) {
        $hash = (Get-FileHash -LiteralPath $Entry.path -Algorithm SHA256).Hash.ToLowerInvariant()
    }

    return [ordered]@{
        path = $Entry.path
        schemaVersion = 1
        rowCount = $null
        hash = $hash
        optional = -not [bool] $Entry.required
    }
}

function Get-DirectoryHash {
    param(
        [string] $Path,
        [string] $Kind
    )

    if (!(Test-Path -LiteralPath $Path -PathType Container)) {
        return [ordered]@{
            kind = $Kind
            path = $Path
            hash = $null
            fileCount = 0
        }
    }

    $root = (Get-Item -LiteralPath $Path).FullName
    $rows = [System.Collections.Generic.List[string]]::new()
    foreach ($file in Get-ChildItem -LiteralPath $root -File -Recurse | Sort-Object FullName) {
        $relative = $file.FullName.Substring($root.Length).TrimStart('\', '/').Replace('\', '/')
        $fileHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        [void] $rows.Add(("{0}|{1}|{2}" -f $relative, $file.Length, $fileHash))
    }

    $text = $rows -join "`n"
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($text)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hashBytes = $sha.ComputeHash($bytes)
    } finally {
        $sha.Dispose()
    }

    $hash = -join ($hashBytes | ForEach-Object { $_.ToString("x2") })
    return [ordered]@{
        kind = $Kind
        path = $Path
        hash = $hash
        fileCount = $rows.Count
    }
}

function New-SourceHashReport {
    param(
        [string] $BundleId,
        [object] $Sources
    )

    return [ordered]@{
        schemaVersion = 1
        bundleId = $BundleId
        generatedAt = (Get-Date).ToString("o")
        hashAlgorithm = "SHA-256"
        sources = $Sources
        builder = [ordered]@{
            name = "catalog-bundle-prep"
            version = "0.1.0"
            configHash = $null
        }
    }
}

function ConvertTo-DraftManifest {
    param(
        [object[]] $Entries,
        [object] $SourceHashes
    )

    $catalogs = [ordered]@{}
    $indexes = [ordered]@{}
    $summaries = [ordered]@{}
    $reports = [ordered]@{}
    $overrides = [ordered]@{}

    foreach ($entry in @($Entries | Where-Object { $_.exists })) {
        $fileEntry = New-FileEntry $entry
        switch ($entry.category) {
            "catalog" { $catalogs[$entry.key] = $fileEntry }
            "index" { $indexes[$entry.key] = $fileEntry }
            "summary" { $summaries[$entry.key] = $fileEntry }
            "report" { $reports[$entry.key] = $fileEntry }
            "override" { $overrides[$entry.key] = $fileEntry }
        }
    }

    return [ordered]@{
        schemaVersion = 1
        bundleId = "local-prep-draft"
        builder = [ordered]@{
            name = "catalog-bundle-prep"
            version = "0.1.0"
        }
        game = [ordered]@{
            family = "maplestory"
            version = "v83"
            serverFamily = "cosmic"
            locale = "en"
        }
        generatedAt = (Get-Date).ToString("o")
        sources = [ordered]@{
            wzRootHash = if ($SourceHashes -and $SourceHashes["wz"]) { $SourceHashes["wz"].hash } else { $null }
            sqlRootHash = if ($SourceHashes -and $SourceHashes["sql"]) { $SourceHashes["sql"].hash } else { $null }
            scriptRootHash = if ($SourceHashes -and $SourceHashes["scripts"]) { $SourceHashes["scripts"].hash } else { $null }
            overrideHash = if ($SourceHashes -and $SourceHashes["overrides"]) { $SourceHashes["overrides"].hash } else { $null }
        }
        catalogs = $catalogs
        indexes = $indexes
        summaries = $summaries
        reports = $reports
        overrides = $overrides
        compatibility = [ordered]@{
            requiresServerAdapterVersion = ">=1.0.0"
            requiresCatalogRuntimeVersion = ">=1.0.0"
        }
    }
}

function ConvertTo-MarkdownReport {
    param([object] $Report)

    function Get-MapKeys {
        param([object] $Map)

        if ($Map -is [System.Collections.IDictionary]) {
            return @($Map.Keys)
        }

        return @($Map.PSObject.Properties.Name)
    }

    function Get-MapValue {
        param(
            [object] $Map,
            [string] $Key
        )

        if ($Map -is [System.Collections.IDictionary]) {
            return $Map[$Key]
        }

        return $Map.$Key
    }

    $lines = [System.Collections.Generic.List[string]]::new()
    [void] $lines.Add("# Catalog Bundle Prep Report")
    [void] $lines.Add("")
    [void] $lines.Add("Generated: $($Report.generatedAt)")
    [void] $lines.Add("")
    [void] $lines.Add("| Field | Value |")
    [void] $lines.Add("| --- | --- |")
    [void] $lines.Add("| Status | $($Report.status) |")
    [void] $lines.Add("| Missing required | $($Report.counts.missingRequired) |")
    [void] $lines.Add("| Deferred optional | $($Report.counts.deferredOptional) |")
    [void] $lines.Add("| Ready entries | $($Report.counts.ready) |")
    [void] $lines.Add("| Required entries | $($Report.counts.required) |")
    [void] $lines.Add("| Optional entries | $($Report.counts.optional) |")
    [void] $lines.Add(("| Repo root | `{0}` |" -f $Report.repoRoot))
    [void] $lines.Add("")

    if ($Report.summaryOnly) {
        [void] $lines.Add("## Summary Counts")
        [void] $lines.Add("")
        [void] $lines.Add("| Category | Entries |")
        [void] $lines.Add("| --- | ---: |")
        foreach ($name in @(Get-MapKeys $Report.categoryCounts | Sort-Object)) {
            [void] $lines.Add(("| {0} | {1} |" -f $name, (Get-MapValue $Report.categoryCounts $name)))
        }
        [void] $lines.Add("")
        [void] $lines.Add("| Status | Entries |")
        [void] $lines.Add("| --- | ---: |")
        foreach ($name in @(Get-MapKeys $Report.statusCounts | Sort-Object)) {
            [void] $lines.Add(("| {0} | {1} |" -f $name, (Get-MapValue $Report.statusCounts $name)))
        }
        [void] $lines.Add("")
        [void] $lines.Add("Entry rows and the draft manifest are omitted because `-SummaryOnly` was used.")
        [void] $lines.Add("")
    } else {
        foreach ($category in @("catalog", "index", "summary", "report", "override")) {
        $items = @($Report.entries | Where-Object { $_.category -eq $category })
        if ($items.Count -eq 0) {
            continue
        }

        [void] $lines.Add("## $category")
        [void] $lines.Add("")
        [void] $lines.Add("| Key | Status | Required | Size | Ready Use | Deferred Reason | Path |")
        [void] $lines.Add("| --- | --- | --- | ---: | --- | --- | --- |")
        foreach ($entry in $items) {
            $readyUse = ([string] $entry.readyUse).Replace("|", "\|")
            $deferredReason = ([string] $entry.deferredReason).Replace("|", "\|")
            [void] $lines.Add(("| {0} | {1} | {2} | {3} | {4} | {5} | `{6}` |" -f $entry.key, $entry.status, $entry.required, $entry.length, $readyUse, $deferredReason, $entry.path))
        }
        [void] $lines.Add("")
        }
    }

    [void] $lines.Add("## Notes")
    [void] $lines.Add("")
    [void] $lines.Add("- This report is read-only and does not refresh catalogs.")
    [void] $lines.Add("- Generated file hashes are included in the draft manifest.")
    [void] $lines.Add("- Source-root hashes are generated only when `-IncludeSourceHashes` is passed, because WZ trees can be large.")
    [void] $lines.Add("- Row counts are intentionally left to the future bundle builder.")
    [void] $lines.Add("- Optional deferred entries document future catalog expansion without blocking current safe prep.")

    return ($lines -join "`n")
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$entries = [System.Collections.Generic.List[object]]::new()

Add-Entry $entries "catalog" "maps" (Join-Path $GameCatalogDir "generated_map_catalog.json") $true "Map metadata and id/name lookup source."
Add-Entry $entries "catalog" "mobs" (Join-Path $GameCatalogDir "generated_mob_catalog.json") $true "Mob stats and id/name lookup source."
Add-Entry $entries "catalog" "items" (Join-Path $GameCatalogDir "generated_item_catalog.json") $true "Item metadata and id/name lookup source."
Add-Entry $entries "catalog" "drops" (Join-Path $GameCatalogDir "generated_drop_catalog.json") $true "Mob/item drop source lookup."
Add-Entry $entries "catalog" "quests" (Join-Path $GameCatalogDir "generated_quest_catalog.json") $true "Quest requirement and reward source lookup."
Add-Entry $entries "catalog" "shops" (Join-Path $GameCatalogDir "generated_shop_catalog.json") $true "Shop inventory source lookup."
Add-Entry $entries "catalog" "skills" (Join-Path $GameCatalogDir "generated_skill_catalog.json") $true "Skill metadata lookup."
Add-Entry $entries "catalog" "npcs" (Join-Path $NpcCatalogDir "generated_npc_catalog.json") $true "NPC metadata lookup."
Add-Entry $entries "catalog" "npcPlacements" (Join-Path $NpcCatalogDir "generated_npc_placements.json") $true "NPC map placement lookup."
Add-Entry $entries "catalog" "npcActions" (Join-Path $NpcCatalogDir "generated_npc_action_catalog.json") $true "NPC available action lookup."
Add-Entry $entries "catalog" "npcApproachPoints" (Join-Path $NpcCatalogDir "generated_npc_approach_points.json") $true "Randomized NPC interaction spots."
Add-Entry $entries "catalog" "npcDialogueOptions" (Join-Path $NpcCatalogDir "generated_npc_dialogue_options.json") $true "Dialogue option and timing source."
Add-Entry $entries "catalog" "questDialogueTiming" (Join-Path $NpcCatalogDir "generated_quest_dialogue_timing.json") $true "Dialogue-length delay hints."
Add-Entry $entries "catalog" "npcServices" (Join-Path $NpcCatalogDir "generated_npc_services.json") $true "NPC service/action classification."
Add-Entry $entries "catalog" "npcShopInventory" (Join-Path $NpcCatalogDir "generated_npc_shop_inventory.json") $true "NPC shop inventory reverse lookup source."
Add-Entry $entries "catalog" "npcRewardChoices" (Join-Path $NpcCatalogDir "generated_npc_reward_choices.json") $true "Reward choice lookup."
Add-Entry $entries "catalog" "npcInteractionSpots" (Join-Path $NpcCatalogDir "generated_npc_interaction_spot_catalog.json") $true "Joined NPC placement, service, quest, and approach-point facts."
Add-Entry $entries "catalog" "questNpcInteractions" (Join-Path $NpcCatalogDir "generated_quest_npc_interaction_catalog.json") $true "Quest start/complete NPC placement coverage and review status."
Add-Entry $entries "catalog" "reactors" (Join-Path $ReactorCatalogDir "generated_reactor_catalog.json") $false "Reactor, box, and field-object lookup source for future reactor capability." "Run tools/reactor-catalog/Export-ReactorCatalog.ps1 to generate optional reactor lookup data."

Add-Entry $entries "index" "npcFastIndexes" (Join-Path $NpcCatalogDir "generated_npc_fast_indexes.json") $true "High-frequency NPC reverse indexes."
Add-Entry $entries "index" "mapNpcSummary" (Join-Path $NpcCatalogDir "generated_map_npc_summary.json") $true "Map-to-NPC summary lookup."
Add-Entry $entries "index" "portalGraph" (Join-Path $AgentLlmCatalogDir "generated_portal_graph.json") $true "Map traversal and portal graph lookup."
Add-Entry $entries "index" "mapSummary" (Join-Path $AgentLlmCatalogDir "generated_map_summary_index.json") $true "LLM-safe map summary lookup."
Add-Entry $entries "index" "itemSources" (Join-Path $AgentLlmCatalogDir "generated_item_source_index.json") $true "Item acquisition source lookup."
Add-Entry $entries "index" "mobSpawn" (Join-Path $AgentLlmCatalogDir "generated_mob_spawn_catalog.json") $true "Mob spawn and target pressure lookup."
Add-Entry $entries "index" "questObjectives" (Join-Path $AgentLlmCatalogDir "generated_quest_objective_catalog.json") $true "Quest objective lookup."
Add-Entry $entries "index" "victoriaQuestHunting" (Join-Path $AgentLlmCatalogDir "generated_victoria_lt30_quest_hunting_catalog.json") $true "Ranked Victoria level-30-and-below quest hunting maps and region guards."
Add-Entry $entries "index" "mapleIslandMvp" (Join-Path $AgentLlmCatalogDir "generated_maple_island_mvp_catalog.json") $true "Maple Island MVP sequence lookup."
Add-Entry $entries "index" "mapleIslandMvpFastIndexes" (Join-Path $AgentLlmCatalogDir "generated_maple_island_mvp_fast_indexes.json") $true "Maple Island MVP fast objective indexes."
Add-Entry $entries "index" "resupply" (Join-Path $AgentLlmCatalogDir "generated_resupply_catalog.json") $true "Resupply fallback lookup."
Add-Entry $entries "index" "actionAffordances" (Join-Path $AgentLlmCatalogDir "generated_action_affordance_catalog.json") $true "LLM-safe action affordance lookup."
Add-Entry $entries "index" "navigationTopology" (Join-Path $AgentLlmCatalogDir "generated_navigation_topology_catalog.json") $true "Exact footholds, components, climbables, and runtime-validation movement hints."
Add-Entry $entries "index" "combatMapPolicy" (Join-Path $AgentLlmCatalogDir "generated_combat_map_policy_catalog.json") $true "Combat anchors, occupancy capacity, and party partitions."
Add-Entry $entries "index" "travelServices" (Join-Path $AgentLlmCatalogDir "generated_travel_service_catalog.json") $true "NPC travel destination, cost, placement, and safety facts."
Add-Entry $entries "index" "progressionItemPolicy" (Join-Path $AgentLlmCatalogDir "generated_progression_item_policy_catalog.json") $true "Equipment, recovery supply, scroll, and inventory policy facts."
Add-Entry $entries "index" "questChainPolicy" (Join-Path $AgentLlmCatalogDir "generated_quest_chain_policy_catalog.json") $true "Quest prerequisites, dependents, capabilities, and special-handler classification."
Add-Entry $entries "index" "agentDecisionManifest" (Join-Path $AgentLlmCatalogDir "generated_agent_decision_catalog_manifest.json") $true "Five-catalog decision slice counts and provenance."

Add-Entry $entries "summary" "gameSummary" (Join-Path $GameCatalogDir "GAME_CATALOG_SUMMARY.md") $true "Human review summary for game catalog."
Add-Entry $entries "summary" "npcSummary" (Join-Path $NpcCatalogDir "NPC_CATALOG_SUMMARY.md") $true "Human review summary for NPC catalog."
Add-Entry $entries "summary" "agentLlmSummary" (Join-Path $AgentLlmCatalogDir "AGENT_LLM_CATALOG_SUMMARY.md") $true "Human review summary for Agent/LLM catalog."
Add-Entry $entries "summary" "agentDecisionSummary" (Join-Path $AgentLlmCatalogDir "AGENT_DECISION_CATALOG_SUMMARY.md") $true "Human review summary for navigation, combat, travel, item, and quest-chain policies."

Add-Entry $entries "report" "npcValidation" (Join-Path $NpcCatalogDir "NPC_CATALOG_VALIDATION.md") $true "NPC catalog validation report."
Add-Entry $entries "report" "npcGaps" (Join-Path $NpcCatalogDir "NPC_CATALOG_GAPS.md") $true "NPC catalog gap report."

Add-Entry $entries "override" "dropSourceClassifications" "docs/agents/catalog-overrides/drop-source-classifications.catalog.json" $true "Reviewed non-mob drop source classifications."

$missingRequired = @($entries | Where-Object { $_.required -and -not $_.exists }).Count
$deferredOptional = @($entries | Where-Object { -not $_.required -and -not $_.exists }).Count
$ready = @($entries | Where-Object { $_.exists }).Count
$required = @($entries | Where-Object { $_.required }).Count
$optional = @($entries | Where-Object { -not $_.required }).Count
$status = if ($missingRequired -gt 0) {
    "FAIL"
} elseif ($deferredOptional -gt 0) {
    "READY_WITH_DEFERRED_ITEMS"
} else {
    "READY"
}

$sourceHashes = $null
if ($IncludeSourceHashes -or $OutputSourceHashesPath) {
    $sourceHashes = [ordered]@{
        wz = Get-DirectoryHash $WzRoot "wz"
        sql = Get-DirectoryHash $SqlRoot "sql"
        scripts = Get-DirectoryHash $ScriptRoot "scripts"
        overrides = Get-DirectoryHash $OverrideRoot "overrides"
    }
}

$manifest = ConvertTo-DraftManifest -Entries @($entries) -SourceHashes $sourceHashes
$categoryCounts = [ordered]@{}
foreach ($category in @("catalog", "index", "summary", "report", "override")) {
    $categoryCounts[$category] = @($entries | Where-Object { $_.category -eq $category }).Count
}

$statusCounts = [ordered]@{}
foreach ($entryStatus in @("ready", "missing", "deferred")) {
    $statusCounts[$entryStatus] = @($entries | Where-Object { $_.status -eq $entryStatus }).Count
}

$requiredEntryKeys = @($entries | Where-Object { $_.required } | ForEach-Object { $_.key })
$deferredEntryKeys = @($entries | Where-Object { $_.status -eq "deferred" } | ForEach-Object { $_.key })
$missingRequiredKeys = @($entries | Where-Object { $_.required -and -not $_.exists } | ForEach-Object { $_.key })
$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    repoRoot = $repoRoot
    status = $status
    checkCount = $entries.Count
    passCount = $ready
    failCount = $missingRequired
    warnCount = $deferredOptional
    warningIds = @($deferredEntryKeys)
    failureIds = @($missingRequiredKeys)
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    returnedEntryCount = if ($SummaryOnly) { 0 } else { $entries.Count }
    manifestOmitted = [bool] $SummaryOnly
    counts = [ordered]@{
        ready = $ready
        missingRequired = $missingRequired
        deferredOptional = $deferredOptional
        required = $required
        optional = $optional
        total = $entries.Count
    }
    categoryCounts = $categoryCounts
    statusCounts = $statusCounts
    requiredEntryKeys = $requiredEntryKeys
    deferredEntryKeys = $deferredEntryKeys
    missingRequiredKeys = $missingRequiredKeys
    entries = if ($SummaryOnly) { $null } else { @($entries) }
    draftManifest = if ($SummaryOnly) { $null } else { $manifest }
    sourceHashes = $sourceHashes
}

if ($OutputManifestPath) {
    $manifestParent = Split-Path -Parent $OutputManifestPath
    if ($manifestParent -and !(Test-Path -LiteralPath $manifestParent)) {
        New-Item -ItemType Directory -Force -Path $manifestParent | Out-Null
    }
    $manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutputManifestPath -Encoding UTF8
}

if ($OutputSourceHashesPath) {
    $sourceParent = Split-Path -Parent $OutputSourceHashesPath
    if ($sourceParent -and !(Test-Path -LiteralPath $sourceParent)) {
        New-Item -ItemType Directory -Force -Path $sourceParent | Out-Null
    }
    $sourceHashReport = New-SourceHashReport $manifest.bundleId $sourceHashes
    $sourceHashReport | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutputSourceHashesPath -Encoding UTF8
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

    Write-Host "Catalog bundle prep report written:"
    Write-Host "  $OutputPath"
} elseif ($Json) {
    $report | ConvertTo-Json -Depth 12
} else {
    ConvertTo-MarkdownReport ([pscustomobject] $report)
}

if ($missingRequired -gt 0) {
    exit 1
}
