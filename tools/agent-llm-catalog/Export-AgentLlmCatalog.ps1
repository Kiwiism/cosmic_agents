param(
    [string] $WzRoot = "wz",
    [string] $GameCatalogDir = "tmp/game-catalog",
    [string] $NpcCatalogDir = "tmp/npc-catalog",
    [string] $OutputDir = "tmp/agent-llm-catalog"
)

$ErrorActionPreference = "Stop"

function Read-JsonArray {
    param([string] $Path)

    if (!(Test-Path $Path)) {
        return @()
    }
    $value = Get-Content -Raw -Path $Path | ConvertFrom-Json
    if ($null -eq $value) {
        return @()
    }
    if ($value -is [System.Array]) {
        return @($value)
    }
    return @($value)
}

function Load-XmlDocument {
    param([string] $Path)

    $doc = New-Object System.Xml.XmlDocument
    $doc.PreserveWhitespace = $false
    $doc.Load((Resolve-Path $Path))
    return $doc
}

function Get-AttrValue {
    param(
        [System.Xml.XmlNode] $Node,
        [string] $Name
    )

    if ($null -eq $Node -or $null -eq $Node.Attributes -or $null -eq $Node.Attributes[$Name]) {
        return $null
    }
    return $Node.Attributes[$Name].Value
}

function Get-ChildByName {
    param(
        [System.Xml.XmlNode] $Node,
        [string] $Name
    )

    if ($null -eq $Node) {
        return $null
    }
    foreach ($child in $Node.ChildNodes) {
        if ((Get-AttrValue $child "name") -eq $Name) {
            return $child
        }
    }
    return $null
}

function Get-IntChildValue {
    param(
        [System.Xml.XmlNode] $Node,
        [string] $Name
    )

    $child = Get-ChildByName $Node $Name
    $value = Get-AttrValue $child "value"
    if ($null -eq $value -or $value -eq "") {
        return $null
    }
    return [int] $value
}

function New-ObjectList {
    $list = New-Object System.Collections.ArrayList
    return ,$list
}

function New-IntList {
    param([object[]] $Values)

    $list = New-Object System.Collections.Generic.List[int]
    foreach ($value in $Values) {
        if ($null -ne $value -and "$value" -match "^-?\d+$") {
            [void] $list.Add([int] $value)
        }
    }
    return ,$list
}

function New-StringList {
    param([object[]] $Values)

    $list = New-Object System.Collections.Generic.List[string]
    foreach ($value in $Values) {
        if ($null -ne $value -and "$value" -ne "") {
            [void] $list.Add([string] $value)
        }
    }
    return ,$list
}

function New-Index {
    param(
        [object[]] $Rows,
        [string] $PropertyName
    )

    $index = @{}
    foreach ($row in $Rows) {
        $value = $row.$PropertyName
        if ($null -ne $value) {
            $index[[int] $value] = $row
        }
    }
    return $index
}

function Add-ToLookupList {
    param(
        [hashtable] $Lookup,
        [int] $Key,
        [object] $Value
    )

    $lookupKey = [string] $Key
    if (!$Lookup.ContainsKey($lookupKey)) {
        $Lookup[$lookupKey] = New-Object System.Collections.ArrayList
    }
    [void] $Lookup[$lookupKey].Add($Value)
}

function Get-MapLabel {
    param([object] $Map)

    if ($null -eq $Map) {
        return $null
    }
    if ($Map.streetName -and $Map.mapName) {
        return "$($Map.streetName): $($Map.mapName)"
    }
    if ($Map.mapName) {
        return $Map.mapName
    }
    return [string] $Map.mapId
}

function Get-ItemKind {
    param([object] $Item)

    if ($null -eq $Item) {
        return "unknown"
    }
    if ($Item.subCategory) {
        return $Item.subCategory
    }
    return $Item.category
}

function Get-TopDropSources {
    param(
        [hashtable] $DropsByItem,
        [int] $ItemId,
        [int] $Limit = 8
    )

    $lookupKey = [string] $ItemId
    if (!$DropsByItem.ContainsKey($lookupKey)) {
        return @()
    }
    return @($DropsByItem[$lookupKey] |
        Sort-Object @{ Expression = "chance"; Descending = $true }, @{ Expression = "sourceId"; Ascending = $true } |
        Select-Object -First $Limit |
        ForEach-Object {
            [pscustomobject] @{
                sourceType = $_.sourceType
                sourceId = $_.sourceId
                sourceName = $_.sourceName
                chance = $_.chance
                questId = $_.questId
                mapIds = (New-IntList @($_.mapIds))
            }
        })
}

function Get-ShopSourcesForItem {
    param(
        [object[]] $Shops,
        [int] $ItemId,
        [int] $Limit = 10
    )

    $sources = New-ObjectList
    foreach ($shop in $Shops) {
        foreach ($item in @($shop.items)) {
            if ([int] $item.itemId -ne $ItemId) {
                continue
            }
            [void] $sources.Add([pscustomobject] @{
                shopId = $shop.shopId
                npcId = $shop.npcId
                npcName = $shop.npcName
                price = $item.price
                mapIds = (New-IntList @($shop.mapIds))
            })
            break
        }
        if ($sources.Count -ge $Limit) {
            break
        }
    }
    if ($sources.Count -eq 0) {
        return @()
    }
    return @($sources.ToArray())
}

function Export-PortalGraph {
    param(
        [object[]] $Maps,
        [hashtable] $MapById
    )

    $edges = New-ObjectList
    foreach ($map in $Maps) {
        foreach ($portal in @($map.portals)) {
            if ($null -eq $portal.targetMapId -or [int] $portal.targetMapId -eq 999999999) {
                continue
            }

            $target = $MapById[[int] $portal.targetMapId]
            $reverseHint = $false
            if ($target) {
                foreach ($targetPortal in @($target.portals)) {
                    if ($null -ne $targetPortal.targetMapId -and [int] $targetPortal.targetMapId -eq [int] $map.mapId) {
                        $reverseHint = $true
                        break
                    }
                }
            }

            $flagValues = New-Object System.Collections.Generic.List[string]
            if ($portal.script) { [void] $flagValues.Add("scripted") }
            if ($reverseHint) { [void] $flagValues.Add("has-reverse-edge") }
            if ($null -eq $target) { [void] $flagValues.Add("target-map-missing-from-catalog") }
            $flags = New-StringList @($flagValues)

            [void] $edges.Add([pscustomobject] @{
                schemaVersion = 1
                fromMapId = [int] $map.mapId
                fromMapName = (Get-MapLabel $map)
                portalName = $portal.name
                portalType = $portal.type
                x = $portal.x
                y = $portal.y
                toMapId = [int] $portal.targetMapId
                toMapName = (Get-MapLabel $target)
                targetPortalName = $portal.targetPortalName
                script = $portal.script
                flags = $flags
            })
        }
    }
    return ,$edges
}

function Export-MobSpawnCatalog {
    param(
        [string] $MapRoot,
        [hashtable] $MapById,
        [hashtable] $MobById
    )

    $rows = New-ObjectList
    $files = Get-ChildItem -Path $MapRoot -Recurse -Filter "*.img.xml" |
        Where-Object { $_.BaseName -match "^\d+\.img$" }

    foreach ($file in $files) {
        $mapId = [int] ($file.BaseName -replace "\.img$", "")
        $doc = Load-XmlDocument $file.FullName
        $life = Get-ChildByName $doc.DocumentElement "life"
        if ($null -eq $life) {
            continue
        }

        $counts = @{}
        foreach ($entry in $life.ChildNodes) {
            if ((Get-AttrValue (Get-ChildByName $entry "type") "value") -ne "m") {
                continue
            }
            $mobId = Get-IntChildValue $entry "id"
            if ($null -eq $mobId) {
                continue
            }
            if (!$counts.ContainsKey($mobId)) {
                $counts[$mobId] = 0
            }
            $counts[$mobId]++
        }

        if ($counts.Count -eq 0) {
            continue
        }

        $mobs = New-ObjectList
        $levels = New-Object System.Collections.Generic.List[int]
        $bossCount = 0
        $spawnCount = 0
        foreach ($mobId in @($counts.Keys | Sort-Object)) {
            $mob = $MobById[[int] $mobId]
            $count = [int] $counts[$mobId]
            $spawnCount += $count
            if ($mob -and $null -ne $mob.level) {
                [void] $levels.Add([int] $mob.level)
            }
            if ($mob -and $mob.boss) {
                $bossCount += $count
            }
            [void] $mobs.Add([pscustomobject] @{
                mobId = [int] $mobId
                mobName = if ($mob) { $mob.name } else { $null }
                level = if ($mob) { $mob.level } else { $null }
                maxHp = if ($mob) { $mob.maxHp } else { $null }
                exp = if ($mob) { $mob.exp } else { $null }
                spawnEntries = $count
            })
        }

        $map = $MapById[$mapId]
        $flagValues = New-Object System.Collections.Generic.List[string]
        foreach ($flag in @($quest.flags)) {
            if ($flag) { [void] $flagValues.Add($flag) }
        }
        if ($objectives.Count -eq 0) { [void] $flagValues.Add("no-derived-objectives") }
        if ($quest.startNpcId -and $null -eq $startNpc) { [void] $flagValues.Add("start-npc-missing-from-npc-catalog") }
        if ($quest.completeNpcId -and $null -eq $completeNpc) { [void] $flagValues.Add("complete-npc-missing-from-npc-catalog") }

        [void] $rows.Add([pscustomobject] @{
            schemaVersion = 1
            mapId = $mapId
            mapName = (Get-MapLabel $map)
            spawnEntryCount = $spawnCount
            uniqueMobCount = $counts.Count
            minMobLevel = if ($levels.Count -gt 0) { ($levels | Measure-Object -Minimum).Minimum } else { $null }
            maxMobLevel = if ($levels.Count -gt 0) { ($levels | Measure-Object -Maximum).Maximum } else { $null }
            bossSpawnEntries = $bossCount
            mobs = $mobs
        })
    }
    return ,$rows
}

function Export-MapSummaryIndex {
    param(
        [object[]] $Maps,
        [hashtable] $MobSpawnByMap,
        [hashtable] $NpcSummaryByMap,
        [hashtable] $ShopByMap
    )

    $rows = New-ObjectList
    foreach ($map in $Maps) {
        $mapId = [int] $map.mapId
        $mobSummary = $MobSpawnByMap[$mapId]
        $npcSummary = $NpcSummaryByMap[$mapId]
        $shopLookupKey = [string] $mapId
        $shops = if ($ShopByMap.ContainsKey($shopLookupKey)) { @($ShopByMap[$shopLookupKey]) } else { @() }
        $flags = New-StringList @($map.flags)
        if ($shops.Count -gt 0) { [void] $flags.Add("has-shop") }
        if ($npcSummary -and $npcSummary.questNpcIds.Count -gt 0) { [void] $flags.Add("has-quest-npc") }
        if ($mobSummary) { [void] $flags.Add("has-mobs") }

        [void] $rows.Add([pscustomobject] @{
            schemaVersion = 1
            mapId = $mapId
            label = (Get-MapLabel $map)
            region = $map.region
            returnMapId = $map.returnMapId
            forcedReturnMapId = $map.forcedReturnMapId
            flags = $flags
            navigation = @{
                portalCount = @($map.portals).Count
                footholdCount = $map.footholdCount
                hasScriptedPortal = [bool] (@($map.portals) | Where-Object { $_.script } | Select-Object -First 1)
            }
            mobs = @{
                uniqueMobCount = if ($mobSummary) { $mobSummary.uniqueMobCount } else { 0 }
                spawnEntryCount = if ($mobSummary) { $mobSummary.spawnEntryCount } else { 0 }
                minLevel = if ($mobSummary) { $mobSummary.minMobLevel } else { $null }
                maxLevel = if ($mobSummary) { $mobSummary.maxMobLevel } else { $null }
                bossSpawnEntries = if ($mobSummary) { $mobSummary.bossSpawnEntries } else { 0 }
            }
            npcs = @{
                uniqueNpcCount = if ($npcSummary) { $npcSummary.uniqueNpcCount } else { @($map.npcIds).Count }
                questNpcIds = if ($npcSummary) { (New-IntList @($npcSummary.questNpcIds)) } else { @() }
                shopNpcIds = if ($npcSummary) { (New-IntList @($npcSummary.shopNpcIds)) } else { @() }
            }
            shops = @($shops | ForEach-Object {
                [pscustomobject] @{
                    shopId = $_.shopId
                    npcId = $_.npcId
                    npcName = $_.npcName
                    itemCount = $_.itemCount
                }
            })
        })
    }
    return ,$rows
}

function Export-QuestObjectiveCatalog {
    param(
        [object[]] $Quests,
        [hashtable] $NpcById,
        [hashtable] $MobById,
        [hashtable] $ItemById,
        [hashtable] $DropsByItem,
        [hashtable] $DialogueTimingByQuestPhase
    )

    $rows = New-ObjectList
    foreach ($quest in $Quests) {
        $questId = [int] $quest.questId
        $objectives = New-ObjectList
        $startNpc = if ($quest.startNpcId) { $NpcById[[int] $quest.startNpcId] } else { $null }
        $completeNpc = if ($quest.completeNpcId) { $NpcById[[int] $quest.completeNpcId] } else { $null }

        if ($quest.startNpcId) {
            [void] $objectives.Add([pscustomobject] @{
                objectiveId = "$($questId):start"
                type = "interact-npc-start-quest"
                npcId = [int] $quest.startNpcId
                npcName = if ($startNpc) { $startNpc.name } else { $null }
                candidateMapIds = if ($startNpc) { (New-IntList @($startNpc.placements | ForEach-Object { $_.mapId })) } else { @() }
                timing = $DialogueTimingByQuestPhase["$questId|start"]
                preconditions = $quest.requirements.start
            })
        }

        foreach ($mobReq in @($quest.requirements.complete.mobs)) {
            $mob = $MobById[[int] $mobReq.mobId]
            [void] $objectives.Add([pscustomobject] @{
                objectiveId = "$($questId):kill:$($mobReq.mobId)"
                type = "kill-mob"
                mobId = [int] $mobReq.mobId
                mobName = if ($mob) { $mob.name } else { $null }
                count = $mobReq.count
                candidateMapIds = if ($mob) { (New-IntList @($mob.mapIds)) } else { @() }
            })
        }

        foreach ($itemReq in @($quest.requirements.complete.items)) {
            $item = $ItemById[[int] $itemReq.itemId]
            [void] $objectives.Add([pscustomobject] @{
                objectiveId = "$($questId):collect:$($itemReq.itemId)"
                type = "collect-item"
                itemId = [int] $itemReq.itemId
                itemName = if ($item) { $item.name } else { $null }
                count = $itemReq.count
                candidateDropSources = @(Get-TopDropSources $DropsByItem ([int] $itemReq.itemId) 8)
            })
        }

        if ($quest.completeNpcId) {
            [void] $objectives.Add([pscustomobject] @{
                objectiveId = "$($questId):complete"
                type = "interact-npc-complete-quest"
                npcId = [int] $quest.completeNpcId
                npcName = if ($completeNpc) { $completeNpc.name } else { $null }
                candidateMapIds = if ($completeNpc) { (New-IntList @($completeNpc.placements | ForEach-Object { $_.mapId })) } else { @() }
                timing = $DialogueTimingByQuestPhase["$questId|complete"]
                preconditions = $quest.requirements.complete
            })
        }

        [void] $rows.Add([pscustomobject] @{
            schemaVersion = 1
            questId = $questId
            startNpcId = $quest.startNpcId
            completeNpcId = $quest.completeNpcId
            prerequisiteQuests = (New-IntList @(
                @($quest.requirements.start.prerequisiteQuests) | ForEach-Object { $_.questId }
            ))
            levelRange = @{
                min = $quest.requirements.start.minLevel
                max = $quest.requirements.start.maxLevel
            }
            jobs = (New-IntList @($quest.requirements.start.jobs))
            objectives = $objectives
            rewards = $quest.rewards
            flags = (New-StringList @($flagValues))
        })
    }
    return ,$rows
}

function Export-ItemSourceIndex {
    param(
        [object[]] $Items,
        [object[]] $Shops,
        [hashtable] $DropsByItem
    )

    $rows = New-ObjectList
    foreach ($item in $Items) {
        $itemId = [int] $item.itemId
        $dropSources = @(Get-TopDropSources $DropsByItem $itemId 12 | Where-Object { $null -ne $_ })
        $shopSources = @(Get-ShopSourcesForItem $Shops $itemId 12 | Where-Object { $null -ne $_ })
        if (@($dropSources).Count -eq 0 -and @($shopSources).Count -eq 0) {
            continue
        }
        [void] $rows.Add([pscustomobject] @{
            schemaVersion = 1
            itemId = $itemId
            itemName = $item.name
            kind = (Get-ItemKind $item)
            dropSources = $dropSources
            shopSources = $shopSources
            planning = @{
                obtainableByDrop = @($dropSources).Count -gt 0
                buyableFromNpc = @($shopSources).Count -gt 0
                likelyQuestItem = $item.subCategory -eq "quest-item"
            }
        })
    }
    return ,$rows
}

function Export-ResupplyCatalog {
    param([object[]] $Shops)

    $rows = New-ObjectList
    foreach ($shop in $Shops) {
        $usefulItems = @($shop.items | Where-Object {
            ($_.itemId -ge 2000000 -and $_.itemId -lt 2010000) -or
            ($_.itemId -ge 2020000 -and $_.itemId -lt 2030000) -or
            ($_.itemId -ge 2060000 -and $_.itemId -lt 2080000) -or
            ($_.itemId -ge 2330000 -and $_.itemId -lt 2340000)
        })
        if ($usefulItems.Count -eq 0) {
            continue
        }

        [void] $rows.Add([pscustomobject] @{
            schemaVersion = 1
            shopId = $shop.shopId
            npcId = $shop.npcId
            npcName = $shop.npcName
            mapIds = (New-IntList @($shop.mapIds))
            items = @($usefulItems | Sort-Object price, itemId | ForEach-Object {
                [pscustomobject] @{
                    itemId = $_.itemId
                    itemName = $_.itemName
                    price = $_.price
                    category = if ($_.itemId -ge 2060000 -and $_.itemId -lt 2080000) {
                        "projectile"
                    } elseif ($_.itemId -ge 2330000 -and $_.itemId -lt 2340000) {
                        "bullet"
                    } else {
                        "recovery-or-utility"
                    }
                }
            })
        })
    }
    return ,$rows
}

function Export-ActionAffordanceCatalog {
    $rows = @(
        [pscustomobject] @{
            action = "navigate.toMap"
            actor = "agent-engine"
            requiredCatalogs = @("generated_portal_graph.json", "generated_map_summary_index.json")
            liveValidation = @("current map", "portal availability", "field limit", "foothold reachability")
            result = "agent reaches target map or reports blocked route"
        },
        [pscustomobject] @{
            action = "navigate.toPoint"
            actor = "agent-engine"
            requiredCatalogs = @("generated_map_summary_index.json", "generated_npc_approach_points.json")
            liveValidation = @("same map", "foothold reachable", "point inside safe movement envelope")
            result = "agent reaches exact or nearest valid point"
        },
        [pscustomobject] @{
            action = "npc.startQuest"
            actor = "agent-engine"
            requiredCatalogs = @("generated_quest_objective_catalog.json", "generated_npc_catalog.json")
            liveValidation = @("quest not started", "level/job/prerequisite requirements", "npc present", "interaction range")
            result = "quest state becomes started"
        },
        [pscustomobject] @{
            action = "npc.completeQuest"
            actor = "agent-engine"
            requiredCatalogs = @("generated_quest_objective_catalog.json", "generated_npc_catalog.json")
            liveValidation = @("quest started", "completion requirements", "npc present", "interaction range")
            result = "quest state becomes completed and rewards are applied"
        },
        [pscustomobject] @{
            action = "combat.killMob"
            actor = "agent-engine"
            requiredCatalogs = @("generated_mob_spawn_catalog.json", "generated_skill_catalog.json")
            liveValidation = @("mob present", "agent alive", "skill usable", "combat safety")
            result = "mob kill progress or loot opportunity"
        },
        [pscustomobject] @{
            action = "loot.collectItem"
            actor = "agent-engine"
            requiredCatalogs = @("generated_item_source_index.json", "generated_drop_catalog.json")
            liveValidation = @("drop visible", "ownership/FFA state", "inventory space", "reachable drop point")
            result = "item enters inventory"
        },
        [pscustomobject] @{
            action = "shop.buy"
            actor = "agent-engine"
            requiredCatalogs = @("generated_resupply_catalog.json", "generated_shop_catalog.json")
            liveValidation = @("npc shop reachable", "mesos enough", "inventory space")
            result = "item bought"
        },
        [pscustomobject] @{
            action = "social.partyOrTrade"
            actor = "llm-or-agent-policy"
            requiredCatalogs = @("generated_action_affordance_catalog.json")
            liveValidation = @("target online", "range/channel/map constraints", "anti-abuse policy")
            result = "party/trade/social action attempted"
        },
        [pscustomobject] @{
            action = "plan.executeCard"
            actor = "agent-engine"
            requiredCatalogs = @("generated_quest_objective_catalog.json", "generated_action_affordance_catalog.json")
            liveValidation = @("profile focus policy", "current state", "plan exit criteria")
            result = "plan advances, pauses, sidetracks, or exits"
        }
    )

    return ,$rows
}

function Export-Manifest {
    param(
        [hashtable] $Files,
        [hashtable] $Counts
    )

    $fileRows = New-ObjectList
    foreach ($key in @($Files.Keys | Sort-Object)) {
        $path = $Files[$key]
        if (!(Test-Path $path)) {
            continue
        }
        $info = Get-Item $path
        [void] $fileRows.Add([pscustomobject] @{
            name = $key
            path = $path
            bytes = $info.Length
            lastWriteTime = $info.LastWriteTime.ToString("o")
        })
    }

    return [pscustomobject] @{
        schemaVersion = 1
        generatedAt = (Get-Date).ToString("o")
        purpose = "Portable decision-ready catalog layer for agent engine and LLM planning."
        sourceCatalogs = @{
            game = $GameCatalogDir
            npc = $NpcCatalogDir
            wz = $WzRoot
        }
        counts = $Counts
        files = $fileRows
    }
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$paths = @{
    maps = Join-Path $GameCatalogDir "generated_map_catalog.json"
    mobs = Join-Path $GameCatalogDir "generated_mob_catalog.json"
    drops = Join-Path $GameCatalogDir "generated_drop_catalog.json"
    items = Join-Path $GameCatalogDir "generated_item_catalog.json"
    shops = Join-Path $GameCatalogDir "generated_shop_catalog.json"
    quests = Join-Path $GameCatalogDir "generated_quest_catalog.json"
    skills = Join-Path $GameCatalogDir "generated_skill_catalog.json"
    npcs = Join-Path $NpcCatalogDir "generated_npc_catalog.json"
    npcMapSummary = Join-Path $NpcCatalogDir "generated_map_npc_summary.json"
    dialogueTiming = Join-Path $NpcCatalogDir "generated_quest_dialogue_timing.json"
}

foreach ($required in @("maps", "mobs", "drops", "items", "shops", "quests", "npcs")) {
    if (!(Test-Path $paths[$required])) {
        throw "Missing required source catalog '$required': $($paths[$required]). Run game and NPC catalog exporters first."
    }
}

$maps = Read-JsonArray $paths.maps
$mobs = Read-JsonArray $paths.mobs
$drops = Read-JsonArray $paths.drops
$items = Read-JsonArray $paths.items
$shops = Read-JsonArray $paths.shops
$quests = Read-JsonArray $paths.quests
$npcs = Read-JsonArray $paths.npcs
$npcMapSummaries = Read-JsonArray $paths.npcMapSummary
$dialogueTimings = Read-JsonArray $paths.dialogueTiming

$mapById = New-Index $maps "mapId"
$mobById = New-Index $mobs "mobId"
$itemById = New-Index $items "itemId"
$npcById = New-Index $npcs "npcId"

$dropsByItem = @{}
foreach ($drop in $drops) {
    if ($null -ne $drop.itemId) {
        Add-ToLookupList $dropsByItem ([int] $drop.itemId) $drop
    }
}

$npcSummaryByMap = @{}
foreach ($summary in $npcMapSummaries) {
    $npcSummaryByMap[[int] $summary.mapId] = $summary
}

$dialogueTimingByQuestPhase = @{}
foreach ($timing in $dialogueTimings) {
    $dialogueTimingByQuestPhase["$($timing.questId)|$($timing.phase)"] = $timing
}

$shopByMap = @{}
foreach ($shop in $shops) {
    foreach ($mapId in @($shop.mapIds)) {
        Add-ToLookupList $shopByMap ([int] $mapId) $shop
    }
}

$portalGraph = Export-PortalGraph $maps $mapById
$mobSpawnCatalog = Export-MobSpawnCatalog (Join-Path $WzRoot "Map.wz/Map") $mapById $mobById
$mobSpawnByMap = @{}
foreach ($row in $mobSpawnCatalog) {
    $mobSpawnByMap[[int] $row.mapId] = $row
}

$mapSummaryIndex = Export-MapSummaryIndex $maps $mobSpawnByMap $npcSummaryByMap $shopByMap
$questObjectiveCatalog = Export-QuestObjectiveCatalog $quests $npcById $mobById $itemById $dropsByItem $dialogueTimingByQuestPhase
$itemSourceIndex = Export-ItemSourceIndex $items $shops $dropsByItem
$resupplyCatalog = Export-ResupplyCatalog $shops
$actionAffordanceCatalog = Export-ActionAffordanceCatalog

$out = @{
    portalGraph = Join-Path $OutputDir "generated_portal_graph.json"
    mobSpawn = Join-Path $OutputDir "generated_mob_spawn_catalog.json"
    mapSummary = Join-Path $OutputDir "generated_map_summary_index.json"
    questObjectives = Join-Path $OutputDir "generated_quest_objective_catalog.json"
    itemSources = Join-Path $OutputDir "generated_item_source_index.json"
    resupply = Join-Path $OutputDir "generated_resupply_catalog.json"
    affordances = Join-Path $OutputDir "generated_action_affordance_catalog.json"
    manifest = Join-Path $OutputDir "generated_catalog_manifest.json"
    summary = Join-Path $OutputDir "AGENT_LLM_CATALOG_SUMMARY.md"
}

$portalGraph | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 $out.portalGraph
$mobSpawnCatalog | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 $out.mobSpawn
$mapSummaryIndex | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 $out.mapSummary
$questObjectiveCatalog | ConvertTo-Json -Depth 16 | Set-Content -Encoding UTF8 $out.questObjectives
$itemSourceIndex | ConvertTo-Json -Depth 14 | Set-Content -Encoding UTF8 $out.itemSources
$resupplyCatalog | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 $out.resupply
$actionAffordanceCatalog | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $out.affordances

$counts = @{
    maps = @($maps).Count
    portalEdges = @($portalGraph).Count
    mobSpawnMaps = @($mobSpawnCatalog).Count
    mapSummaries = @($mapSummaryIndex).Count
    questObjectivePlans = @($questObjectiveCatalog).Count
    itemSourceIndexes = @($itemSourceIndex).Count
    resupplyShops = @($resupplyCatalog).Count
    actionAffordances = @($actionAffordanceCatalog).Count
}

$manifest = Export-Manifest $out $counts
$manifest | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $out.manifest

$summary = @(
    "# Agent LLM Catalog Summary",
    "",
    "Generated: $((Get-Date).ToString('o'))",
    "",
    "This export is preparation-only. It does not modify or wire into server runtime.",
    "",
    "## Counts",
    "",
    "- Maps indexed: $($counts.maps)",
    "- Portal graph edges: $($counts.portalEdges)",
    "- Mob spawn maps: $($counts.mobSpawnMaps)",
    "- Map summaries: $($counts.mapSummaries)",
    "- Quest objective plans: $($counts.questObjectivePlans)",
    "- Item source indexes: $($counts.itemSourceIndexes)",
    "- Resupply shops: $($counts.resupplyShops)",
    "- Action affordances: $($counts.actionAffordances)",
    "",
    "## Outputs",
    "",
    "- `generated_portal_graph.json` - map-to-map portal edges, scripted flags, reverse-edge hints.",
    "- `generated_mob_spawn_catalog.json` - per-map mob spawn entries and level ranges.",
    "- `generated_map_summary_index.json` - compact map facts for planner/LLM context.",
    "- `generated_quest_objective_catalog.json` - quest plans decomposed into start, kill, collect, complete objectives.",
    "- `generated_item_source_index.json` - item-to-drop/shop availability index.",
    "- `generated_resupply_catalog.json` - NPC shops with potions, utility recovery items, projectiles, and bullets.",
    "- `generated_action_affordance_catalog.json` - capability/action contract hints for LLM command planning.",
    "- `generated_catalog_manifest.json` - file list and generation metadata.",
    "",
    "## Integration Notes",
    "",
    "- LLM should read map summaries, quest objectives, item sources, and action affordances.",
    "- Agent engine should use portal graph, mob spawns, NPC approach points, and live server validation.",
    "- Catalog facts are planning hints, not authority. Runtime must still validate current map, NPC, quest, inventory, party, and combat state."
)
Set-Content -Encoding UTF8 -Path $out.summary -Value $summary

Write-Host "Agent/LLM catalog export complete:"
Write-Host "  Output: $OutputDir"
Write-Host "  Portal edges: $($counts.portalEdges)"
Write-Host "  Quest objective plans: $($counts.questObjectivePlans)"
Write-Host "  Item source indexes: $($counts.itemSourceIndexes)"
