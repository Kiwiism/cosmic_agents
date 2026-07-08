param(
    [string] $WzRoot = "wz",
    [string] $OutputDir = "tmp/game-catalog",
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path ".").Path

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

function Get-StringChildValue {
    param(
        [System.Xml.XmlNode] $Node,
        [string] $Name
    )

    return Get-AttrValue (Get-ChildByName $Node $Name) "value"
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

function ConvertTo-RepoRelativePath {
    param([string] $Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $Path
    }

    $fullPath = (Get-Item -LiteralPath $Path).FullName
    if ($fullPath.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        return ($fullPath.Substring($repoRoot.Length).TrimStart("\", "/") -replace "\\", "/")
    }
    return ($fullPath -replace "\\", "/")
}

function ConvertTo-RowArray {
    param($Rows)

    $result = New-Object System.Collections.Generic.List[object]
    if ($null -eq $Rows) {
        return ,$result
    }
    if ($Rows.PSObject.Properties["schemaVersion"] -or $Rows.PSObject.Properties["itemId"] -or $Rows.PSObject.Properties["mapId"]) {
        [void] $result.Add($Rows)
        return ,$result
    }
    if ($Rows -is [System.Collections.IEnumerable] -and -not ($Rows -is [string])) {
        foreach ($row in $Rows) {
            if ($null -ne $row) {
                [void] $result.Add($row)
            }
        }
        return ,$result
    }
    [void] $result.Add($Rows)
    return ,$result
}

function Get-StringTableFlat {
    param(
        [string] $Path,
        [string] $FieldName = "name"
    )

    $result = @{}
    if (!(Test-Path $Path)) {
        return $result
    }

    $doc = Load-XmlDocument $Path
    foreach ($entry in $doc.SelectNodes("//imgdir[@name]")) {
        $id = Get-AttrValue $entry "name"
        if ($id -notmatch "^\d+$") {
            continue
        }
        $field = Get-ChildByName $entry $FieldName
        $value = Get-AttrValue $field "value"
        if ($value) {
            $result[[int] $id] = $value
        }
    }
    return $result
}

function Get-MapStringTable {
    param([string] $Path)

    $result = @{}
    if (!(Test-Path $Path)) {
        return $result
    }

    $doc = Load-XmlDocument $Path
    foreach ($region in $doc.DocumentElement.ChildNodes) {
        foreach ($map in $region.ChildNodes) {
            $id = Get-AttrValue $map "name"
            if ($id -notmatch "^\d+$") {
                continue
            }
            $result[[int] $id] = @{
                region = Get-AttrValue $region "name"
                streetName = Get-StringChildValue $map "streetName"
                mapName = Get-StringChildValue $map "mapName"
            }
        }
    }
    return $result
}

function Get-ItemCategory {
    param([int] $ItemId)

    if ($ItemId -ge 1000000 -and $ItemId -lt 2000000) { return "equip" }
    if ($ItemId -ge 2000000 -and $ItemId -lt 3000000) { return "consume" }
    if ($ItemId -ge 3000000 -and $ItemId -lt 4000000) { return "install" }
    if ($ItemId -ge 4000000 -and $ItemId -lt 5000000) { return "etc" }
    if ($ItemId -ge 5000000 -and $ItemId -lt 6000000) { return "pet" }
    return "unknown"
}

function Get-ItemSubCategory {
    param([int] $ItemId)

    if ($ItemId -ge 2000000 -and $ItemId -lt 2010000) { return "hp-mp-potion" }
    if ($ItemId -ge 2040000 -and $ItemId -lt 2050000) { return "scroll" }
    if ($ItemId -ge 2060000 -and $ItemId -lt 2070000) { return "projectile" }
    if ($ItemId -ge 2070000 -and $ItemId -lt 2080000) { return "throwing-star" }
    if ($ItemId -ge 2330000 -and $ItemId -lt 2340000) { return "bullet" }
    if ($ItemId -ge 4000000 -and $ItemId -lt 4040000) { return "mob-etc" }
    if ($ItemId -ge 4010000 -and $ItemId -lt 4030000) { return "ore-gem" }
    if ($ItemId -ge 4030000 -and $ItemId -lt 4040000) { return "quest-item" }
    return $null
}

function Get-ItemNames {
    param([string] $StringRoot)

    $result = @{}
    foreach ($fileName in @("Consume.img.xml", "Eqp.img.xml", "Etc.img.xml", "Ins.img.xml", "Pet.img.xml", "Cash.img.xml")) {
        $table = Get-StringTableFlat (Join-Path $StringRoot $fileName)
        foreach ($key in $table.Keys) {
            $result[[int] $key] = $table[$key]
        }
    }
    return $result
}

function Read-SqlTupleRows {
    param(
        [string] $Path,
        [int] $ExpectedColumns
    )

    $rows = New-Object System.Collections.Generic.List[object]
    if (!(Test-Path $Path)) {
        return $rows
    }

    $text = Get-Content -Raw $Path
    foreach ($match in [regex]::Matches($text, "\(([^()]*)\)")) {
        $parts = @($match.Groups[1].Value -split "," | ForEach-Object { $_.Trim().Trim("'") })
        if ($parts.Count -ne $ExpectedColumns) {
            continue
        }
        if ($parts[0] -notmatch "^-?\d+$") {
            continue
        }
        [void] $rows.Add($parts)
    }
    return $rows
}

function Export-MapCatalog {
    param(
        [string] $MapRoot,
        [hashtable] $MapNames
    )

    $rows = New-Object System.Collections.Generic.List[object]
    $files = Get-ChildItem -Path $MapRoot -Recurse -Filter "*.img.xml" |
        Where-Object { $_.BaseName -match "^\d+\.img$" }

    foreach ($file in $files) {
        $mapId = [int] ($file.BaseName -replace "\.img$", "")
        $doc = Load-XmlDocument $file.FullName
        $info = Get-ChildByName $doc.DocumentElement "info"
        $portalNode = Get-ChildByName $doc.DocumentElement "portal"
        $lifeNode = Get-ChildByName $doc.DocumentElement "life"
        $footholdNode = Get-ChildByName $doc.DocumentElement "foothold"
        $mapInfo = $MapNames[$mapId]

        $portals = New-Object System.Collections.Generic.List[object]
        if ($portalNode) {
            foreach ($portal in $portalNode.ChildNodes) {
                $toMap = Get-IntChildValue $portal "tm"
                [void] $portals.Add([pscustomobject] @{
                    name = Get-StringChildValue $portal "pn"
                    type = Get-IntChildValue $portal "pt"
                    x = Get-IntChildValue $portal "x"
                    y = Get-IntChildValue $portal "y"
                    targetMapId = $toMap
                    targetPortalName = Get-StringChildValue $portal "tn"
                    script = Get-StringChildValue $portal "script"
                })
            }
        }

        $npcIds = New-Object System.Collections.Generic.HashSet[int]
        $mobIds = New-Object System.Collections.Generic.HashSet[int]
        if ($lifeNode) {
            foreach ($life in $lifeNode.ChildNodes) {
                $type = Get-StringChildValue $life "type"
                $id = Get-IntChildValue $life "id"
                if ($null -eq $id) { continue }
                if ($type -eq "n") { [void] $npcIds.Add($id) }
                if ($type -eq "m") { [void] $mobIds.Add($id) }
            }
        }

        $footholdCount = 0
        if ($footholdNode) {
            $footholdCount = $footholdNode.SelectNodes(".//imgdir[int[@name='x1']]").Count
        }

        $flags = New-Object System.Collections.Generic.List[string]
        if ((Get-IntChildValue $info "town") -eq 1) { [void] $flags.Add("town") }
        if ($mapId -ge 910000000 -and $mapId -lt 911000000) { [void] $flags.Add("free-market") }
        if ((Get-IntChildValue $info "swim") -eq 1) { [void] $flags.Add("swim") }
        if ((Get-IntChildValue $info "fieldLimit") -gt 0) { [void] $flags.Add("field-limit") }

        [void] $rows.Add([pscustomobject] @{
            schemaVersion = 1
            mapId = $mapId
            region = if ($mapInfo) { $mapInfo.region } else { $null }
            streetName = if ($mapInfo) { $mapInfo.streetName } else { $null }
            mapName = if ($mapInfo) { $mapInfo.mapName } else { $null }
            returnMapId = Get-IntChildValue $info "returnMap"
            forcedReturnMapId = Get-IntChildValue $info "forcedReturn"
            fieldLimit = Get-IntChildValue $info "fieldLimit"
            flags = New-StringList @($flags)
            portals = $portals
            npcIds = New-IntList @($npcIds | Sort-Object)
            mobIds = New-IntList @($mobIds | Sort-Object)
            footholdCount = $footholdCount
            source = ConvertTo-RepoRelativePath $file.FullName
        })
    }
    return $rows
}

function Export-MobCatalog {
    param(
        [string] $MobRoot,
        [hashtable] $MobNames,
        [hashtable] $MapsByMob
    )

    $rows = New-Object System.Collections.Generic.List[object]
    foreach ($file in (Get-ChildItem -Path $MobRoot -Filter "*.img.xml")) {
        if ($file.BaseName -notmatch "^\d+\.img$") {
            continue
        }
        $mobId = [int] ($file.BaseName -replace "\.img$", "")
        $doc = Load-XmlDocument $file.FullName
        $info = Get-ChildByName $doc.DocumentElement "info"
        $level = Get-IntChildValue $info "level"
        $hp = Get-IntChildValue $info "maxHP"
        $exp = Get-IntChildValue $info "exp"
        $boss = (Get-IntChildValue $info "boss") -eq 1
        $undead = (Get-IntChildValue $info "undead") -eq 1
        $bodyAttack = (Get-IntChildValue $info "bodyAttack") -ne 0

        [void] $rows.Add([pscustomobject] @{
            schemaVersion = 1
            mobId = $mobId
            name = $MobNames[$mobId]
            level = $level
            maxHp = $hp
            maxMp = Get-IntChildValue $info "maxMP"
            exp = $exp
            boss = $boss
            undead = $undead
            bodyAttack = $bodyAttack
            pad = Get-IntChildValue $info "PADamage"
            pdd = Get-IntChildValue $info "PDDamage"
            mad = Get-IntChildValue $info "MADamage"
            mdd = Get-IntChildValue $info "MDDamage"
            acc = Get-IntChildValue $info "acc"
            eva = Get-IntChildValue $info "eva"
            speed = Get-IntChildValue $info "speed"
            elemAttr = Get-StringChildValue $info "elemAttr"
            mapIds = if ($MapsByMob.ContainsKey($mobId)) { New-IntList @($MapsByMob[$mobId] | Sort-Object) } else { New-IntList @() }
            planning = @{
                expPerHp = if ($hp -and $hp -gt 0 -and $null -ne $exp) { [math]::Round($exp / [double] $hp, 4) } else { $null }
                danger = if ($boss) { "boss" } elseif ($level -ge 100) { "high" } elseif ($level -ge 50) { "medium" } else { "low" }
            }
            source = ConvertTo-RepoRelativePath $file.FullName
        })
    }
    return $rows
}

function Export-DropCatalog {
    param(
        [string] $DropSqlPath,
        [hashtable] $ItemNames,
        [hashtable] $MobNames,
        [hashtable] $MapsByMob
    )

    $rows = New-Object System.Collections.Generic.List[object]
    foreach ($parts in (Read-SqlTupleRows $DropSqlPath 6)) {
        $dropperId = [int] $parts[0]
        $itemId = [int] $parts[1]
        [void] $rows.Add([pscustomobject] @{
            schemaVersion = 1
            sourceType = "mob"
            sourceId = $dropperId
            sourceName = $MobNames[$dropperId]
            itemId = $itemId
            itemName = $ItemNames[$itemId]
            minimumQuantity = [int] $parts[2]
            maximumQuantity = [int] $parts[3]
            questId = [int] $parts[4]
            chance = [int] $parts[5]
            mapIds = if ($MapsByMob.ContainsKey($dropperId)) { New-IntList @($MapsByMob[$dropperId] | Sort-Object) } else { New-IntList @() }
            flags = New-StringList @(if ([int] $parts[4] -gt 0) { "quest-drop" } else { $null })
        })
    }
    return $rows
}

function Export-ItemCatalog {
    param(
        [hashtable] $ItemNames,
        $Drops,
        $ShopItems
    )

    $Drops = ConvertTo-RowArray $Drops
    $ShopItems = ConvertTo-RowArray $ShopItems

    $itemIds = New-Object System.Collections.Generic.HashSet[int]
    foreach ($id in $ItemNames.Keys) { [void] $itemIds.Add([int] $id) }
    foreach ($drop in $Drops) { [void] $itemIds.Add([int] $drop.itemId) }
    foreach ($shopItem in $ShopItems) { [void] $itemIds.Add([int] $shopItem.itemId) }

    $dropCounts = @{}
    foreach ($drop in $Drops) {
        if (!$dropCounts.ContainsKey($drop.itemId)) { $dropCounts[$drop.itemId] = 0 }
        $dropCounts[$drop.itemId]++
    }

    $shopCounts = @{}
    foreach ($shopItem in $ShopItems) {
        if (!$shopCounts.ContainsKey($shopItem.itemId)) { $shopCounts[$shopItem.itemId] = 0 }
        $shopCounts[$shopItem.itemId]++
    }

    $rows = New-Object System.Collections.Generic.List[object]
    foreach ($itemId in ($itemIds | Sort-Object)) {
        $flags = New-Object System.Collections.Generic.List[string]
        if ($dropCounts.ContainsKey($itemId)) { [void] $flags.Add("drops-from-mobs") }
        if ($shopCounts.ContainsKey($itemId)) { [void] $flags.Add("sold-in-shop") }
        if ($itemId -ge 4030000 -and $itemId -lt 4040000) { [void] $flags.Add("quest-item-range") }

        [void] $rows.Add([pscustomobject] @{
            schemaVersion = 1
            itemId = [int] $itemId
            name = $ItemNames[[int] $itemId]
            category = Get-ItemCategory ([int] $itemId)
            subCategory = Get-ItemSubCategory ([int] $itemId)
            dropSourceCount = if ($dropCounts.ContainsKey($itemId)) { $dropCounts[$itemId] } else { 0 }
            shopSourceCount = if ($shopCounts.ContainsKey($itemId)) { $shopCounts[$itemId] } else { 0 }
            flags = New-StringList @($flags)
        })
    }
    return $rows
}

function Export-ShopCatalog {
    param(
        [string] $ShopSqlPath,
        [string] $ShopItemsSqlPath,
        [hashtable] $ItemNames,
        [hashtable] $NpcNames,
        [hashtable] $MapsByNpc
    )

    $shopsById = @{}
    foreach ($parts in (Read-SqlTupleRows $ShopSqlPath 2)) {
        $shopId = [int] $parts[0]
        $npcId = [int] $parts[1]
        $shopsById[$shopId] = @{
            shopId = $shopId
            npcId = $npcId
            items = New-Object System.Collections.Generic.List[object]
        }
    }

    $shopItems = New-Object System.Collections.Generic.List[object]
    foreach ($parts in (Read-SqlTupleRows $ShopItemsSqlPath 5)) {
        $shopId = [int] $parts[0]
        $itemId = [int] $parts[1]
        $row = [pscustomobject] @{
            shopId = $shopId
            itemId = $itemId
            itemName = $ItemNames[$itemId]
            price = [int] $parts[2]
            pitch = [int] $parts[3]
            position = [int] $parts[4]
        }
        [void] $shopItems.Add($row)
        if ($shopsById.ContainsKey($shopId)) {
            [void] $shopsById[$shopId].items.Add($row)
        }
    }

    $shops = New-Object System.Collections.Generic.List[object]
    foreach ($shopId in ($shopsById.Keys | Sort-Object)) {
        $shop = $shopsById[$shopId]
        $npcId = $shop.npcId
        [void] $shops.Add([pscustomobject] @{
            schemaVersion = 1
            shopId = $shopId
            npcId = $npcId
            npcName = $NpcNames[$npcId]
            mapIds = if ($MapsByNpc.ContainsKey($npcId)) { New-IntList @($MapsByNpc[$npcId] | Sort-Object) } else { New-IntList @() }
            itemCount = $shop.items.Count
            items = @($shop.items | Sort-Object position, itemId)
        })
    }

    return @{
        shops = $shops
        shopItems = $shopItems
    }
}

function Export-QuestCatalog {
    param(
        [string] $CheckPath,
        [string] $ActPath
    )

    $actsByQuest = @{}
    if (Test-Path $ActPath) {
        $actDoc = Load-XmlDocument $ActPath
        foreach ($quest in $actDoc.DocumentElement.ChildNodes) {
            $questIdText = Get-AttrValue $quest "name"
            if ($questIdText -notmatch "^\d+$") { continue }
            $questId = [int] $questIdText
            $actsByQuest[$questId] = $quest
        }
    }

    $rows = New-Object System.Collections.Generic.List[object]
    if (!(Test-Path $CheckPath)) {
        return $rows
    }

    $checkDoc = Load-XmlDocument $CheckPath
    foreach ($quest in $checkDoc.DocumentElement.ChildNodes) {
        $questIdText = Get-AttrValue $quest "name"
        if ($questIdText -notmatch "^\d+$") { continue }
        $questId = [int] $questIdText
        $start = Get-ChildByName $quest "0"
        $complete = Get-ChildByName $quest "1"
        $actQuest = if ($actsByQuest.ContainsKey($questId)) { $actsByQuest[$questId] } else { $null }

        $requirements = @{
            start = Get-QuestPhaseChecks $start
            complete = Get-QuestPhaseChecks $complete
        }
        $rewards = @{
            start = Get-QuestPhaseActs (Get-ChildByName $actQuest "0")
            complete = Get-QuestPhaseActs (Get-ChildByName $actQuest "1")
        }

        [void] $rows.Add([pscustomobject] @{
            schemaVersion = 1
            questId = $questId
            startNpcId = Get-IntChildValue $start "npc"
            completeNpcId = Get-IntChildValue $complete "npc"
            requirements = $requirements
            rewards = $rewards
            flags = New-StringList @(
                if ((Get-IntChildValue $start "interval") -gt 0 -or (Get-IntChildValue $complete "interval") -gt 0) { "repeatable-or-timed" } else { $null }
            )
        })
    }
    return $rows
}

function Get-QuestPhaseChecks {
    param([System.Xml.XmlNode] $Phase)

    $items = New-Object System.Collections.Generic.List[object]
    $mobs = New-Object System.Collections.Generic.List[object]
    $quests = New-Object System.Collections.Generic.List[object]
    $jobs = New-Object System.Collections.Generic.List[int]

    foreach ($item in @((Get-ChildByName $Phase "item").ChildNodes)) {
        $id = Get-IntChildValue $item "id"
        if ($null -ne $id) {
            [void] $items.Add([pscustomobject] @{
                itemId = $id
                count = Get-IntChildValue $item "count"
            })
        }
    }
    foreach ($mob in @((Get-ChildByName $Phase "mob").ChildNodes)) {
        $id = Get-IntChildValue $mob "id"
        if ($null -ne $id) {
            [void] $mobs.Add([pscustomobject] @{
                mobId = $id
                count = Get-IntChildValue $mob "count"
            })
        }
    }
    foreach ($quest in @((Get-ChildByName $Phase "quest").ChildNodes)) {
        $id = Get-IntChildValue $quest "id"
        if ($null -ne $id) {
            [void] $quests.Add([pscustomobject] @{
                questId = $id
                state = Get-IntChildValue $quest "state"
            })
        }
    }
    foreach ($job in @((Get-ChildByName $Phase "job").ChildNodes)) {
        $value = Get-AttrValue $job "value"
        if ($value -match "^\d+$") {
            [void] $jobs.Add([int] $value)
        }
    }

    return @{
        npcId = Get-IntChildValue $Phase "npc"
        minLevel = Get-IntChildValue $Phase "lvmin"
        maxLevel = Get-IntChildValue $Phase "lvmax"
        interval = Get-IntChildValue $Phase "interval"
        items = $items
        mobs = $mobs
        prerequisiteQuests = $quests
        jobs = New-IntList @($jobs)
    }
}

function Get-QuestPhaseActs {
    param([System.Xml.XmlNode] $Phase)

    $items = New-Object System.Collections.Generic.List[object]
    foreach ($item in @((Get-ChildByName $Phase "item").ChildNodes)) {
        $id = Get-IntChildValue $item "id"
        if ($null -ne $id) {
            [void] $items.Add([pscustomobject] @{
                itemId = $id
                count = Get-IntChildValue $item "count"
                prop = Get-IntChildValue $item "prop"
                job = Get-IntChildValue $item "job"
            })
        }
    }

    return @{
        exp = Get-IntChildValue $Phase "exp"
        mesos = Get-IntChildValue $Phase "money"
        items = $items
    }
}

function Export-SkillCatalog {
    param(
        [string] $SkillRootPath,
        [hashtable] $SkillNames
    )

    $rows = New-Object System.Collections.Generic.List[object]
    foreach ($file in (Get-ChildItem -Path $SkillRootPath -Filter "*.img.xml")) {
        $doc = Load-XmlDocument $file.FullName
        $skillContainer = $doc.SelectSingleNode("/imgdir/imgdir[@name='skill']")
        if ($null -eq $skillContainer) { continue }
        foreach ($skill in $skillContainer.SelectNodes("imgdir[@name]")) {
            $skillIdText = Get-AttrValue $skill "name"
            if ($skillIdText -notmatch "^\d{4,}$") { continue }
            $skillId = [int] $skillIdText
            $levelNode = $skill.SelectSingleNode("imgdir[@name='level']")
            $levels = @()
            if ($levelNode) {
                $levels = @($levelNode.ChildNodes | Where-Object { (Get-AttrValue $_ "name") -match "^\d+$" })
            }
            $firstLevel = @($levels | Sort-Object { [int] (Get-AttrValue $_ "name") } | Select-Object -First 1)[0]
            $maxLevel = @($levels | Sort-Object { [int] (Get-AttrValue $_ "name") } | Select-Object -Last 1)[0]

            [void] $rows.Add([pscustomobject] @{
                schemaVersion = 1
                skillId = $skillId
                name = $SkillNames[$skillId]
                sourceFile = $file.Name
                elemAttr = Get-StringChildValue $skill "elemAttr"
                skillType = Get-IntChildValue $skill "skillType"
                maxLevel = if ($maxLevel) { [int] (Get-AttrValue $maxLevel "name") } else { 0 }
                levelOne = Get-SkillLevelSummary $firstLevel
                maxLevelStats = Get-SkillLevelSummary $maxLevel
                classificationHints = New-StringList @(
                    if ((Get-IntChildValue $skill "skillType") -eq 2) { "buff-hint" } else { $null }
                    if ((Get-StringChildValue $skill "elemAttr")) { "elemental" } else { $null }
                )
            })
        }
    }
    return $rows
}

function Get-SkillLevelSummary {
    param([System.Xml.XmlNode] $LevelNode)

    if ($null -eq $LevelNode) {
        return $null
    }
    return @{
        mpCon = Get-IntChildValue $LevelNode "mpCon"
        hpCon = Get-IntChildValue $LevelNode "hpCon"
        damage = Get-IntChildValue $LevelNode "damage"
        mobCount = Get-IntChildValue $LevelNode "mobCount"
        attackCount = Get-IntChildValue $LevelNode "attackCount"
        bulletCount = Get-IntChildValue $LevelNode "bulletCount"
        bulletConsume = Get-IntChildValue $LevelNode "bulletConsume"
        range = Get-IntChildValue $LevelNode "range"
        timeSeconds = Get-IntChildValue $LevelNode "time"
        prop = Get-IntChildValue $LevelNode "prop"
    }
}

function Build-MobAndNpcMapIndexes {
    param($MapRows)

    $MapRows = ConvertTo-RowArray $MapRows
    $mapsByMob = @{}
    $mapsByNpc = @{}
    foreach ($map in $MapRows) {
        foreach ($mobId in $map.mobIds) {
            if (!$mapsByMob.ContainsKey($mobId)) { $mapsByMob[$mobId] = New-Object System.Collections.Generic.HashSet[int] }
            [void] $mapsByMob[$mobId].Add($map.mapId)
        }
        foreach ($npcId in $map.npcIds) {
            if (!$mapsByNpc.ContainsKey($npcId)) { $mapsByNpc[$npcId] = New-Object System.Collections.Generic.HashSet[int] }
            [void] $mapsByNpc[$npcId].Add($map.mapId)
        }
    }
    return @{
        mapsByMob = $mapsByMob
        mapsByNpc = $mapsByNpc
    }
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$stringRoot = Join-Path $WzRoot "String.wz"
$mapNames = Get-MapStringTable (Join-Path $stringRoot "Map.img.xml")
$mobNames = Get-StringTableFlat (Join-Path $stringRoot "Mob.img.xml")
$npcNames = Get-StringTableFlat (Join-Path $stringRoot "Npc.img.xml")
$skillNames = Get-StringTableFlat (Join-Path $stringRoot "Skill.img.xml")
$itemNames = Get-ItemNames $stringRoot

$mapRows = Export-MapCatalog (Join-Path $WzRoot "Map.wz/Map") $mapNames
$indexes = Build-MobAndNpcMapIndexes $mapRows
$shopExport = Export-ShopCatalog `
    "src/main/resources/db/data/101-shops-data.sql" `
    "src/main/resources/db/data/102-shopitems-data.sql" `
    $itemNames `
    $npcNames `
    $indexes.mapsByNpc
$dropRows = Export-DropCatalog "src/main/resources/db/data/152-drop-data.sql" $itemNames $mobNames $indexes.mapsByMob
$itemRows = Export-ItemCatalog $itemNames $dropRows $shopExport.shopItems
$mobRows = Export-MobCatalog (Join-Path $WzRoot "Mob.wz") $mobNames $indexes.mapsByMob
$questRows = Export-QuestCatalog (Join-Path $WzRoot "Quest.wz/Check.img.xml") (Join-Path $WzRoot "Quest.wz/Act.img.xml")
$skillRows = Export-SkillCatalog (Join-Path $WzRoot "Skill.wz") $skillNames

$paths = @{
    maps = Join-Path $OutputDir "generated_map_catalog.json"
    mobs = Join-Path $OutputDir "generated_mob_catalog.json"
    drops = Join-Path $OutputDir "generated_drop_catalog.json"
    items = Join-Path $OutputDir "generated_item_catalog.json"
    shops = Join-Path $OutputDir "generated_shop_catalog.json"
    quests = Join-Path $OutputDir "generated_quest_catalog.json"
    skills = Join-Path $OutputDir "generated_skill_catalog.json"
    summary = Join-Path $OutputDir "GAME_CATALOG_SUMMARY.md"
}

$mapRows | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 $paths.maps
$mobRows | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 $paths.mobs
$dropRows | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $paths.drops
$itemRows | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $paths.items
$shopExport.shops | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 $paths.shops
$questRows | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 $paths.quests
$skillRows | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 $paths.skills

$mapCount = (ConvertTo-RowArray $mapRows).Count
$mobCount = (ConvertTo-RowArray $mobRows).Count
$dropCount = (ConvertTo-RowArray $dropRows).Count
$itemCount = (ConvertTo-RowArray $itemRows).Count
$shopCount = (ConvertTo-RowArray $shopExport.shops).Count
$shopItemCount = (ConvertTo-RowArray $shopExport.shopItems).Count
$questCount = (ConvertTo-RowArray $questRows).Count
$skillCount = (ConvertTo-RowArray $skillRows).Count
$townCount = @($mapRows | Where-Object { $_.flags -contains "town" }).Count
$fmCount = @($mapRows | Where-Object { $_.flags -contains "free-market" }).Count
$mobWithMapCount = @($mobRows | Where-Object { $_.mapIds.Count -gt 0 }).Count
$itemWithDropCount = @($itemRows | Where-Object { $_.dropSourceCount -gt 0 }).Count
$itemWithShopCount = @($itemRows | Where-Object { $_.shopSourceCount -gt 0 }).Count
$questWithNpcCount = @($questRows | Where-Object { $null -ne $_.startNpcId -or $null -ne $_.completeNpcId }).Count

$summary = @(
    "# Generated Game Knowledge Catalog Summary"
    ""
    "Generated by ``tools/game-catalog/Export-GameKnowledgeCatalog.ps1``."
    ""
    "This is offline preparation data for future Agent engine and LLM planning."
    "It is not wired into runtime code."
    ""
    "## Counts"
    ""
    "- Maps: $mapCount"
    "- Town maps: $townCount"
    "- Free Market maps: $fmCount"
    "- Mobs: $mobCount"
    "- Mobs with map placements: $mobWithMapCount"
    "- Drop rows: $dropCount"
    "- Items: $itemCount"
    "- Items with mob drops: $itemWithDropCount"
    "- Items sold in shops: $itemWithShopCount"
    "- Shops: $shopCount"
    "- Shop item rows: $shopItemCount"
    "- Quests: $questCount"
    "- Quests with start/complete NPC data: $questWithNpcCount"
    "- Skills: $skillCount"
    ""
    "## Outputs"
    ""
    "- ``generated_map_catalog.json``"
    "- ``generated_mob_catalog.json``"
    "- ``generated_drop_catalog.json``"
    "- ``generated_item_catalog.json``"
    "- ``generated_shop_catalog.json``"
    "- ``generated_quest_catalog.json``"
    "- ``generated_skill_catalog.json``"
    "- ``GAME_CATALOG_SUMMARY.md``"
    ""
    "## Notes"
    ""
    "- NPC placements and approach points remain in ``tmp/npc-catalog``."
    "- Quest data is parsed from ``Quest.wz/Check.img.xml`` and ``Quest.wz/Act.img.xml``."
    "- Drops and shops are parsed from SQL seed data."
    "- Skill classification fields are hints only. Runtime should use server skill logic as source of truth."
    "- Route reachability, script behavior, market prices, and live perception are not solved by this static catalog."
) -join "`n"

Set-Content -Encoding UTF8 -Path $paths.summary -Value $summary

$outputFiles = @(
    [pscustomobject] @{ key = "maps"; path = $paths.maps }
    [pscustomobject] @{ key = "mobs"; path = $paths.mobs }
    [pscustomobject] @{ key = "drops"; path = $paths.drops }
    [pscustomobject] @{ key = "items"; path = $paths.items }
    [pscustomobject] @{ key = "shops"; path = $paths.shops }
    [pscustomobject] @{ key = "quests"; path = $paths.quests }
    [pscustomobject] @{ key = "skills"; path = $paths.skills }
    [pscustomobject] @{ key = "summary"; path = $paths.summary }
)

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    status = "OK"
    wzRoot = $WzRoot
    outputDir = $OutputDir
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    outputFileCount = $outputFiles.Count
    returnedOutputFileCount = if ($SummaryOnly) { 0 } else { $outputFiles.Count }
    counts = [ordered]@{
        maps = $mapCount
        townMaps = $townCount
        freeMarketMaps = $fmCount
        mobs = $mobCount
        mobsWithMapPlacements = $mobWithMapCount
        drops = $dropCount
        items = $itemCount
        itemsWithMobDrops = $itemWithDropCount
        itemsSoldInShops = $itemWithShopCount
        shops = $shopCount
        shopItems = $shopItemCount
        quests = $questCount
        questsWithNpcData = $questWithNpcCount
        skills = $skillCount
    }
    outputFiles = if ($SummaryOnly) { $null } else { $outputFiles }
}

if ($Json) {
    $report | ConvertTo-Json -Depth 6
    return
}

Write-Host "Game knowledge catalog generated:"
foreach ($key in @("maps", "mobs", "drops", "items", "shops", "quests", "skills", "summary")) {
    Write-Host "  $($paths[$key])"
}
