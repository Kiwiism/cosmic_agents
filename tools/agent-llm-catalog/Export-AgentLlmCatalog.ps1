param(
    [string] $WzRoot = "wz",
    [string] $GameCatalogDir = "tmp/game-catalog",
    [string] $NpcCatalogDir = "tmp/npc-catalog",
    [string] $OutputDir = "tmp/agent-llm-catalog",
    [switch] $SummaryOnly,
    [switch] $Json
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
        $flagValues = New-Object System.Collections.Generic.List[string]

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

        foreach ($flag in @($quest.flags)) {
            if ($flag) { [void] $flagValues.Add($flag) }
        }
        if ($objectives.Count -eq 0) { [void] $flagValues.Add("no-derived-objectives") }
        if ($quest.startNpcId -and $null -eq $startNpc) { [void] $flagValues.Add("start-npc-missing-from-npc-catalog") }
        if ($quest.completeNpcId -and $null -eq $completeNpc) { [void] $flagValues.Add("complete-npc-missing-from-npc-catalog") }

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

function Get-ReachableMapIds {
    param(
        [object[]] $PortalGraph,
        [int] $StartMapId
    )

    $reachable = [System.Collections.Generic.HashSet[int]]::new()
    $queue = [System.Collections.Queue]::new()
    [void] $reachable.Add($StartMapId)
    $queue.Enqueue($StartMapId)

    while ($queue.Count -gt 0) {
        $mapId = [int] $queue.Dequeue()
        foreach ($edge in @($PortalGraph | Where-Object { [int] $_.fromMapId -eq $mapId })) {
            $toMapId = [int] $edge.toMapId
            if ([int] $toMapId -eq 999999999) {
                continue
            }
            if ($reachable.Add($toMapId)) {
                $queue.Enqueue($toMapId)
            }
        }
    }

    $values = [System.Collections.Generic.List[int]]::new()
    foreach ($mapId in @($reachable | Sort-Object)) {
        [void] $values.Add([int] $mapId)
    }
    return @($values.ToArray())
}

function Get-QuestObjectiveById {
    param(
        [object[]] $QuestObjectiveCatalog,
        [int] $QuestId
    )

    return @($QuestObjectiveCatalog | Where-Object { [int] $_.questId -eq $QuestId } | Select-Object -First 1)[0]
}

function Get-MapIdsForNpc {
    param(
        [hashtable] $NpcById,
        [int] $NpcId
    )

    $npc = $NpcById[$NpcId]
    if ($null -eq $npc) {
        return @()
    }
    $values = [System.Collections.Generic.List[int]]::new()
    foreach ($placement in @($npc.placements)) {
        if ($null -ne $placement.mapId) {
            [void] $values.Add([int] $placement.mapId)
        }
    }
    return @($values.ToArray())
}

function Get-FirstReachableNpcMapId {
    param(
        [hashtable] $NpcById,
        [int] $NpcId,
        [object[]] $PreferredMapIds
    )

    $maps = @(Get-MapIdsForNpc $NpcById $NpcId)
    foreach ($preferred in @($PreferredMapIds)) {
        if (@($maps) -contains [int] $preferred) {
            return [int] $preferred
        }
    }
    return $null
}

function New-MapleQuestRule {
    param(
        [int] $QuestId,
        [string] $Availability,
        [string] $RouteRole,
        [string[]] $Overrides = @(),
        [string[]] $Notes = @(),
        [bool] $Required = $true
    )

    return [pscustomobject] @{
        questId = $QuestId
        availability = $Availability
        routeRole = $RouteRole
        required = $Required
        overrides = (New-StringList @($Overrides))
        notes = (New-StringList @($Notes))
    }
}

function Export-MapleIslandMvpCatalog {
    param(
        [object[]] $Maps,
        [object[]] $PortalGraph,
        [object[]] $QuestObjectiveCatalog,
        [object[]] $NpcCatalog,
        [object[]] $MobSpawnCatalog
    )

    $npcById = New-Index $NpcCatalog "npcId"
    $mapById = New-Index $Maps "mapId"
    $mobSpawnByMap = New-Index $MobSpawnCatalog "mapId"
    $reachableMapIds = @(Get-ReachableMapIds $PortalGraph 10000)
    $preferredNpcMaps = @(10000, 20000, 30000, 30001, 40000, 50000, 1000000, 1010000, 1020000, 2000000)

    $questRules = @(
        New-MapleQuestRule 1000 "reachable" "required"
        New-MapleQuestRule 1001 "reachable" "required"
        New-MapleQuestRule 1003 "reachable" "required"
        New-MapleQuestRule 1004 "reachable" "required"
        New-MapleQuestRule 1005 "reachable" "required"
        New-MapleQuestRule 1006 "reachable" "required"
        New-MapleQuestRule 1007 "reachable" "required"
        New-MapleQuestRule 1008 "reachable-with-override" "required" @("reactor-box-items") @("Required recycled goods come from reactor boxes.")
        New-MapleQuestRule 1009 "reachable" "required"
        New-MapleQuestRule 1010 "reachable" "required"
        New-MapleQuestRule 1011 "reachable" "required"
        New-MapleQuestRule 1012 "reachable" "required"
        New-MapleQuestRule 1013 "reachable" "required"
        New-MapleQuestRule 1014 "reachable" "required"
        New-MapleQuestRule 1015 "reachable" "required"
        New-MapleQuestRule 1016 "reachable" "required"
        New-MapleQuestRule 1017 "reachable" "required"
        New-MapleQuestRule 1018 "optional-review" "optional" @("legacy-todd-review") @("NPCs are reachable from map 10000 route, but likely old/tutorial-sensitive.") $false
        New-MapleQuestRule 1019 "reachable" "required"
        New-MapleQuestRule 1020 "reachable" "required"
        New-MapleQuestRule 1021 "reachable-with-override" "required" @("use-roger-apple") @("Agent must use item 2010007 before completing.")
        New-MapleQuestRule 1022 "reachable" "required"
        New-MapleQuestRule 1025 "reachable" "required"
        New-MapleQuestRule 1026 "reachable" "required"
        New-MapleQuestRule 1027 "reachable" "required"
        New-MapleQuestRule 1028 "excluded" "excluded" @("off-island-completion") @("Completes after leaving Maple Island.") $false
        New-MapleQuestRule 1029 "reachable" "required"
        New-MapleQuestRule 1030 "reachable-with-override" "required" @("auto-complete-no-complete-npc") @("Assume no-complete-NPC quest auto-completes.")
        New-MapleQuestRule 1031 "reachable" "required"
        New-MapleQuestRule 1032 "reachable" "required"
        New-MapleQuestRule 1033 "reachable" "required"
        New-MapleQuestRule 1034 "reachable" "required"
        New-MapleQuestRule 1035 "optional-review" "optional" @("legacy-todd-review") @("NPCs are reachable from map 10000 route, but likely old/tutorial-sensitive.") $false
        New-MapleQuestRule 1037 "reachable" "required"
        New-MapleQuestRule 1038 "reachable" "required"
        New-MapleQuestRule 1039 "reachable" "required"
        New-MapleQuestRule 1040 "reachable" "required"
        New-MapleQuestRule 1041 "reachable" "required"
        New-MapleQuestRule 1042 "reachable" "required"
        New-MapleQuestRule 1043 "reachable" "required"
        New-MapleQuestRule 1044 "reachable" "required"
        New-MapleQuestRule 1046 "start-only" "required-start-only" @("leave-active-at-southperry") @("Start from Biggs, leave incomplete because completion is on Victoria Island.")
        New-MapleQuestRule 8020 "reachable-with-override" "required" @("grant-cash-shop-shopping-guide") @("Shopping list item comes from Cash Shop flow; grant/spawn for agent.")
        New-MapleQuestRule 8021 "reachable" "required"
        New-MapleQuestRule 8022 "reachable" "required"
        New-MapleQuestRule 8023 "reachable-with-override" "required" @("auto-complete-no-complete-npc") @("Assume no-complete-NPC quest auto-completes.")
        New-MapleQuestRule 8024 "reachable" "required"
        New-MapleQuestRule 8025 "reachable" "required"
        New-MapleQuestRule 8031 "reachable" "required"
        New-MapleQuestRule 8142 "excluded" "excluded" @("old-map-unreachable-from-10000") @("Todd quest in old tutorial map; not reachable from map 10000.") $false
    )

    $questRows = New-ObjectList
    foreach ($rule in $questRules) {
        $quest = Get-QuestObjectiveById $QuestObjectiveCatalog ([int] $rule.questId)
        $startNpcId = if ($quest) { $quest.startNpcId } else { $null }
        $completeNpcId = if ($quest) { $quest.completeNpcId } else { $null }
        $startMapId = if ($startNpcId) { Get-FirstReachableNpcMapId $npcById ([int] $startNpcId) $preferredNpcMaps } else { $null }
        $completeMapId = if ($completeNpcId) { Get-FirstReachableNpcMapId $npcById ([int] $completeNpcId) $preferredNpcMaps } else { $null }

        [void] $questRows.Add([pscustomobject] @{
            questId = [int] $rule.questId
            availability = $rule.availability
            routeRole = $rule.routeRole
            required = $rule.required
            startNpcId = $startNpcId
            startMapId = $startMapId
            completeNpcId = $completeNpcId
            completeMapId = $completeMapId
            objectives = if ($quest) { $quest.objectives } else { @() }
            overrides = $rule.overrides
            notes = $rule.notes
        })
    }

    $routeMapRows = New-ObjectList
    foreach ($mapId in @(10000, 20000, 30000, 30001, 40000, 50000, 1000000, 1010000, 1020000, 2000000, 1000001, 1000003, 2000001)) {
        $map = $mapById[[int] $mapId]
        $spawn = $mobSpawnByMap[[int] $mapId]
        [void] $routeMapRows.Add([pscustomobject] @{
            mapId = [int] $mapId
            label = (Get-MapLabel $map)
            reachableFromStartMap = $reachableMapIds -contains [int] $mapId
            npcIds = if ($map) { (New-IntList @($map.npcIds)) } else { @() }
            mobIds = if ($spawn) { (New-IntList @($spawn.mobs | ForEach-Object { $_.mobId })) } else { @() }
        })
    }

    $fastIndexes = [pscustomobject] @{
        questId_to_mvpRule = @{}
        availability_to_questIds = @{}
        routeRole_to_questIds = @{}
        mapId_to_routeFacts = @{}
    }

    foreach ($quest in @($questRows)) {
        $fastIndexes.questId_to_mvpRule[[string] $quest.questId] = $quest
        if (!$fastIndexes.availability_to_questIds.ContainsKey($quest.availability)) {
            $fastIndexes.availability_to_questIds[$quest.availability] = New-Object System.Collections.ArrayList
        }
        [void] $fastIndexes.availability_to_questIds[$quest.availability].Add([int] $quest.questId)
        if (!$fastIndexes.routeRole_to_questIds.ContainsKey($quest.routeRole)) {
            $fastIndexes.routeRole_to_questIds[$quest.routeRole] = New-Object System.Collections.ArrayList
        }
        [void] $fastIndexes.routeRole_to_questIds[$quest.routeRole].Add([int] $quest.questId)
    }

    foreach ($map in @($routeMapRows)) {
        $fastIndexes.mapId_to_routeFacts[[string] $map.mapId] = $map
    }

    return [pscustomobject] @{
        schemaVersion = 1
        planId = "maple-island-mvp"
        startMapId = 10000
        finalMapId = 2000000
        routeMapIds = (New-IntList @(10000, 20000, 30000, 30001, 40000, 50000, 1000000, 1010000, 1020000, 2000000))
        sideMapIds = (New-IntList @(1000001, 1000003, 2000001))
        reachableMapIds = (New-IntList @($reachableMapIds))
        routeMaps = $routeMapRows
        quests = $questRows
        specialRules = @(
            [pscustomobject] @{ ruleId = "pio-reactor-boxes"; questIds = @(1008); capability = "reactor.open-box"; items = @(4031161, 4031162) }
            [pscustomobject] @{ ruleId = "auto-complete-no-complete-npc"; questIds = @(1030, 8023); capability = "quest.auto-complete" }
            [pscustomobject] @{ ruleId = "yoona-cash-shop-shopping-guide"; questIds = @(8020); capability = "inventory.grant-scripted-item"; items = @(4031180) }
            [pscustomobject] @{ ruleId = "roger-apple"; questIds = @(1021); capability = "inventory.use-item"; items = @(2010007) }
            [pscustomobject] @{ ruleId = "biggs-1046-start-only"; questIds = @(1046); capability = "npc.startQuest"; exitState = "quest-active-incomplete" }
        )
        forbiddenActions = @(
            [pscustomobject] @{ type = "npc-travel"; npcId = 22000; npcName = "Shanks"; reason = "MVP stops at Southperry and must not leave Maple Island." },
            [pscustomobject] @{ type = "quest-complete"; questIds = @(1028, 1046); reason = "Completion target is off-island for MVP." }
        )
        fastIndexes = $fastIndexes
    }
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
$mapleIslandMvpCatalog = Export-MapleIslandMvpCatalog $maps $portalGraph $questObjectiveCatalog $npcs $mobSpawnCatalog

$out = @{
    portalGraph = Join-Path $OutputDir "generated_portal_graph.json"
    mobSpawn = Join-Path $OutputDir "generated_mob_spawn_catalog.json"
    mapSummary = Join-Path $OutputDir "generated_map_summary_index.json"
    questObjectives = Join-Path $OutputDir "generated_quest_objective_catalog.json"
    itemSources = Join-Path $OutputDir "generated_item_source_index.json"
    resupply = Join-Path $OutputDir "generated_resupply_catalog.json"
    affordances = Join-Path $OutputDir "generated_action_affordance_catalog.json"
    mapleIslandMvp = Join-Path $OutputDir "generated_maple_island_mvp_catalog.json"
    mapleIslandMvpIndexes = Join-Path $OutputDir "generated_maple_island_mvp_fast_indexes.json"
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
$mapleIslandMvpCatalog | ConvertTo-Json -Depth 18 | Set-Content -Encoding UTF8 $out.mapleIslandMvp
$mapleIslandMvpCatalog.fastIndexes | ConvertTo-Json -Depth 16 | Set-Content -Encoding UTF8 $out.mapleIslandMvpIndexes

$counts = @{
    maps = @($maps).Count
    portalEdges = @($portalGraph).Count
    mobSpawnMaps = @($mobSpawnCatalog).Count
    mapSummaries = @($mapSummaryIndex).Count
    questObjectivePlans = @($questObjectiveCatalog).Count
    itemSourceIndexes = @($itemSourceIndex).Count
    resupplyShops = @($resupplyCatalog).Count
    actionAffordances = @($actionAffordanceCatalog).Count
    mapleIslandMvpQuests = @($mapleIslandMvpCatalog.quests).Count
    mapleIslandMvpReachableMaps = @($mapleIslandMvpCatalog.reachableMapIds).Count
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
    "- Maple Island MVP quest rules: $($counts.mapleIslandMvpQuests)",
    "- Maps reachable from Maple Island MVP start: $($counts.mapleIslandMvpReachableMaps)",
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
    "- `generated_maple_island_mvp_catalog.json` - Maple Island MVP route, quest availability, special handling, and forbidden actions.",
    "- `generated_maple_island_mvp_fast_indexes.json` - compact lookup maps for Maple Island MVP quest/map decisions.",
    "- `generated_catalog_manifest.json` - file list and generation metadata.",
    "",
    "## Integration Notes",
    "",
    "- LLM should read map summaries, quest objectives, item sources, and action affordances.",
    "- Agent engine should use portal graph, mob spawns, NPC approach points, and live server validation.",
    "- Catalog facts are planning hints, not authority. Runtime must still validate current map, NPC, quest, inventory, party, and combat state."
)
Set-Content -Encoding UTF8 -Path $out.summary -Value $summary

$outputFiles = @(
    [pscustomobject] @{ key = "portalGraph"; path = $out.portalGraph }
    [pscustomobject] @{ key = "mobSpawn"; path = $out.mobSpawn }
    [pscustomobject] @{ key = "mapSummary"; path = $out.mapSummary }
    [pscustomobject] @{ key = "questObjectives"; path = $out.questObjectives }
    [pscustomobject] @{ key = "itemSources"; path = $out.itemSources }
    [pscustomobject] @{ key = "resupply"; path = $out.resupply }
    [pscustomobject] @{ key = "affordances"; path = $out.affordances }
    [pscustomobject] @{ key = "mapleIslandMvp"; path = $out.mapleIslandMvp }
    [pscustomobject] @{ key = "mapleIslandMvpIndexes"; path = $out.mapleIslandMvpIndexes }
    [pscustomobject] @{ key = "manifest"; path = $out.manifest }
    [pscustomobject] @{ key = "summary"; path = $out.summary }
)

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    status = "OK"
    wzRoot = $WzRoot
    gameCatalogDir = $GameCatalogDir
    npcCatalogDir = $NpcCatalogDir
    outputDir = $OutputDir
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    outputFileCount = $outputFiles.Count
    returnedOutputFileCount = if ($SummaryOnly) { 0 } else { $outputFiles.Count }
    counts = [ordered]@{
        maps = $counts.maps
        portalEdges = $counts.portalEdges
        mobSpawnMaps = $counts.mobSpawnMaps
        mapSummaries = $counts.mapSummaries
        questObjectivePlans = $counts.questObjectivePlans
        itemSourceIndexes = $counts.itemSourceIndexes
        resupplyShops = $counts.resupplyShops
        actionAffordances = $counts.actionAffordances
        mapleIslandMvpQuests = $counts.mapleIslandMvpQuests
        mapleIslandMvpReachableMaps = $counts.mapleIslandMvpReachableMaps
    }
    outputFiles = if ($SummaryOnly) { $null } else { $outputFiles }
}

if ($Json) {
    $report | ConvertTo-Json -Depth 6
    return
}

Write-Host "Agent/LLM catalog export complete:"
Write-Host "  Output: $OutputDir"
Write-Host "  Portal edges: $($counts.portalEdges)"
Write-Host "  Quest objective plans: $($counts.questObjectivePlans)"
Write-Host "  Item source indexes: $($counts.itemSourceIndexes)"
Write-Host "  Maple Island MVP quest rules: $($counts.mapleIslandMvpQuests)"
