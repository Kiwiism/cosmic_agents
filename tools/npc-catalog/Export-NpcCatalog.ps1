param(
    [string] $WzRoot = "wz",
    [string] $OutputDir = "tmp/npc-catalog",
    [int] $ApproachLeftPx = 120,
    [int] $ApproachRightPx = 120,
    [int] $ApproachUpPx = 80,
    [int] $ApproachDownPx = 180,
    [int] $ApproachSampleStepPx = 20,
    [int] $MaxApproachPointsPerNpc = 12,
    [int] $ValidationTopN = 100,
    [switch] $SkipApproach
)

$ErrorActionPreference = "Stop"

function Load-XmlDocument {
    param([string] $Path)

    $doc = New-Object System.Xml.XmlDocument
    $doc.PreserveWhitespace = $false
    $doc.Load((Resolve-Path $Path))
    return $doc
}

function Get-ChildByName {
    param(
        [System.Xml.XmlNode] $Node,
        [string] $Name
    )

    foreach ($child in $Node.ChildNodes) {
        if ($child.Attributes -and $child.Attributes["name"] -and $child.Attributes["name"].Value -eq $Name) {
            return $child
        }
    }
    return $null
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

function Get-StringTable {
    param(
        [string] $Path,
        [string] $FieldName = "name"
    )

    $result = @{}
    if (!(Test-Path $Path)) {
        return $result
    }

    $doc = Load-XmlDocument $Path
    foreach ($entry in $doc.DocumentElement.ChildNodes) {
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

            $streetName = Get-AttrValue (Get-ChildByName $map "streetName") "value"
            $mapName = Get-AttrValue (Get-ChildByName $map "mapName") "value"
            $result[[int] $id] = @{
                streetName = $streetName
                mapName = $mapName
            }
        }
    }
    return $result
}

function Get-ShopNpcIds {
    param([string] $Path)

    $result = @{}
    if (!(Test-Path $Path)) {
        return $result
    }

    $text = Get-Content -Raw $Path
    foreach ($match in [regex]::Matches($text, "\((\d+),\s*(\d+)\)")) {
        $shopId = [int] $match.Groups[1].Value
        $npcId = [int] $match.Groups[2].Value
        if (!$result.ContainsKey($npcId)) {
            $result[$npcId] = @()
        }
        $result[$npcId] += $shopId
    }
    return $result
}

function Get-ShopItems {
    param([string] $Path)

    $result = @{}
    if (!(Test-Path $Path)) {
        return $result
    }

    $text = Get-Content -Raw $Path
    foreach ($match in [regex]::Matches($text, "\((\d+),\s*(\d+),\s*(-?\d+),\s*(-?\d+),\s*(-?\d+)\)")) {
        $shopId = [int] $match.Groups[1].Value
        if (!$result.ContainsKey($shopId)) {
            $result[$shopId] = New-Object System.Collections.Generic.List[object]
        }

        [void] $result[$shopId].Add([pscustomobject] @{
            shopId = $shopId
            itemId = [int] $match.Groups[2].Value
            price = [int] $match.Groups[3].Value
            pitch = [int] $match.Groups[4].Value
            position = [int] $match.Groups[5].Value
        })
    }
    return $result
}

function Get-NpcShopInventoryRows {
    param(
        [hashtable] $ShopNpcIds,
        [hashtable] $ShopItems
    )

    $rows = New-Object System.Collections.Generic.List[object]
    foreach ($npcId in ($ShopNpcIds.Keys | Sort-Object)) {
        foreach ($shopId in @($ShopNpcIds[$npcId] | Sort-Object)) {
            $items = if ($ShopItems.ContainsKey([int] $shopId)) {
                @($ShopItems[[int] $shopId] | Sort-Object position, itemId)
            } else {
                @()
            }

            [void] $rows.Add([pscustomobject] @{
                schemaVersion = 1
                inventoryKey = "$npcId|$shopId"
                npcId = [int] $npcId
                shopId = [int] $shopId
                itemCount = $items.Count
                items = $items
                sources = @(
                    "src/main/resources/db/data/101-shops-data.sql",
                    "src/main/resources/db/data/102-shopitems-data.sql"
                )
            })
        }
    }
    return $rows
}

function Add-QuestNpc {
    param(
        [hashtable] $QuestMap,
        [int] $NpcId,
        [int] $QuestId,
        [string] $Phase
    )

    if (!$QuestMap.ContainsKey($NpcId)) {
        $QuestMap[$NpcId] = @{
            starts = New-Object System.Collections.Generic.HashSet[int]
            completes = New-Object System.Collections.Generic.HashSet[int]
        }
    }

    [void] $QuestMap[$NpcId][$Phase].Add($QuestId)
}

function Get-QuestNpcMap {
    param([string] $Path)

    $result = @{}
    if (!(Test-Path $Path)) {
        return $result
    }

    $doc = Load-XmlDocument $Path
    foreach ($quest in $doc.DocumentElement.ChildNodes) {
        $questIdText = Get-AttrValue $quest "name"
        if ($questIdText -notmatch "^\d+$") {
            continue
        }

        $questId = [int] $questIdText
        $start = Get-ChildByName $quest "0"
        $complete = Get-ChildByName $quest "1"
        $startNpc = Get-IntChildValue $start "npc"
        $completeNpc = Get-IntChildValue $complete "npc"

        if ($null -ne $startNpc) {
            Add-QuestNpc $result $startNpc $questId "starts"
        }
        if ($null -ne $completeNpc) {
            Add-QuestNpc $result $completeNpc $questId "completes"
        }
    }
    return $result
}

function Get-LifePlacements {
    param(
        [string] $MapRoot,
        [hashtable] $NpcNames,
        [hashtable] $MapNames
    )

    $placements = New-Object System.Collections.Generic.List[object]
    $mapFiles = Get-ChildItem -Path $MapRoot -Recurse -Filter "*.img.xml" |
        Where-Object { $_.BaseName -match "^\d+\.img$" }

    foreach ($file in $mapFiles) {
        $mapId = [int] ($file.BaseName -replace "\.img$", "")
        $doc = Load-XmlDocument $file.FullName
        $life = Get-ChildByName $doc.DocumentElement "life"
        if ($null -eq $life) {
            continue
        }

        foreach ($entry in $life.ChildNodes) {
            $type = Get-AttrValue (Get-ChildByName $entry "type") "value"
            if ($type -ne "n") {
                continue
            }

            $npcId = Get-IntChildValue $entry "id"
            if ($null -eq $npcId) {
                continue
            }

            $mapInfo = $MapNames[$mapId]
            $placements.Add([pscustomobject] @{
                mapId = $mapId
                mapName = if ($mapInfo) { $mapInfo.mapName } else { $null }
                streetName = if ($mapInfo) { $mapInfo.streetName } else { $null }
                lifeIndex = Get-AttrValue $entry "name"
                npcId = $npcId
                npcName = $NpcNames[$npcId]
                x = Get-IntChildValue $entry "x"
                y = Get-IntChildValue $entry "y"
                footholdId = Get-IntChildValue $entry "fh"
                source = $file.FullName
            })
        }
    }

    return $placements
}

function Get-MapFootholds {
    param([string] $Path)

    $result = New-Object System.Collections.Generic.List[object]
    $doc = Load-XmlDocument $Path
    $foothold = Get-ChildByName $doc.DocumentElement "foothold"
    if ($null -eq $foothold) {
        return $result
    }

    foreach ($root in $foothold.ChildNodes) {
        foreach ($category in $root.ChildNodes) {
            foreach ($entry in $category.ChildNodes) {
                $id = Get-AttrValue $entry "name"
                if ($id -notmatch "^\d+$") {
                    continue
                }

                $x1 = Get-IntChildValue $entry "x1"
                $y1 = Get-IntChildValue $entry "y1"
                $x2 = Get-IntChildValue $entry "x2"
                $y2 = Get-IntChildValue $entry "y2"
                if ($null -eq $x1 -or $null -eq $y1 -or $null -eq $x2 -or $null -eq $y2) {
                    continue
                }

                $result.Add([pscustomobject] @{
                    id = [int] $id
                    x1 = $x1
                    y1 = $y1
                    x2 = $x2
                    y2 = $y2
                    prev = Get-IntChildValue $entry "prev"
                    next = Get-IntChildValue $entry "next"
                    forbidFallDown = (Get-IntChildValue $entry "forbidFallDown") -eq 1
                    isWall = $x1 -eq $x2
                })
            }
        }
    }

    return $result
}

function Get-FootholdsByMap {
    param(
        [string] $MapRoot,
        [object[]] $NeededMapIds
    )

    $result = @{}
    $needed = @{}
    foreach ($mapId in $NeededMapIds) {
        $needed[[int] $mapId] = $true
    }

    $mapFiles = Get-ChildItem -Path $MapRoot -Recurse -Filter "*.img.xml" |
        Where-Object { $_.BaseName -match "^\d+\.img$" }

    foreach ($file in $mapFiles) {
        $mapId = [int] ($file.BaseName -replace "\.img$", "")
        if (!$needed.ContainsKey($mapId)) {
            continue
        }
        $result[$mapId] = Get-MapFootholds $file.FullName
    }

    return $result
}

function Get-FootholdYAtX {
    param(
        [object] $Foothold,
        [int] $X
    )

    if ($Foothold.x1 -eq $Foothold.x2) {
        return $null
    }

    $ratio = ($X - $Foothold.x1) / ($Foothold.x2 - $Foothold.x1)
    return [int] [math]::Round($Foothold.y1 + (($Foothold.y2 - $Foothold.y1) * $ratio))
}

function New-ApproachCandidates {
    param(
        [object] $Placement,
        [object[]] $Footholds,
        [int] $LeftPx,
        [int] $RightPx,
        [int] $UpPx,
        [int] $DownPx,
        [int] $SampleStepPx,
        [int] $MaxPoints
    )

    $candidates = New-Object System.Collections.Generic.List[object]
    if ($null -eq $Placement.x -or $null -eq $Placement.y -or $null -eq $Footholds) {
        return $candidates
    }

    $minX = $Placement.x - $LeftPx
    $maxX = $Placement.x + $RightPx
    $minY = $Placement.y - $UpPx
    $maxY = $Placement.y + $DownPx
    $seen = @{}

    foreach ($foothold in $Footholds) {
        if ($foothold.isWall) {
            continue
        }

        $fhMinX = [math]::Min($foothold.x1, $foothold.x2)
        $fhMaxX = [math]::Max($foothold.x1, $foothold.x2)
        $startX = [math]::Max($minX, $fhMinX)
        $endX = [math]::Min($maxX, $fhMaxX)
        if ($startX -gt $endX) {
            continue
        }

        for ($x = $startX; $x -le $endX; $x += [math]::Max(1, $SampleStepPx)) {
            $y = Get-FootholdYAtX $foothold $x
            if ($null -eq $y -or $y -lt $minY -or $y -gt $maxY) {
                continue
            }

            $key = "$x,$y,$($foothold.id)"
            if ($seen.ContainsKey($key)) {
                continue
            }
            $seen[$key] = $true

            $sameFootholdBonus = if ($Placement.footholdId -eq $foothold.id) { -120 } else { 0 }
            $distance = [math]::Abs($x - $Placement.x) + [math]::Abs($y - $Placement.y)
            $centerPenalty = if ([math]::Abs($x - $Placement.x) -lt 8) { 20 } else { 0 }
            $score = $distance + $centerPenalty + $sameFootholdBonus

            $candidates.Add([pscustomobject] @{
                x = [int] $x
                y = [int] $y
                footholdId = [int] $foothold.id
                offsetX = [int] ($x - $Placement.x)
                offsetY = [int] ($y - $Placement.y)
                manhattanDistance = [int] $distance
                sameFootholdAsNpc = $Placement.footholdId -eq $foothold.id
                score = [int] $score
                source = "generated-box-foothold-sample"
            })
        }

        if ($endX -ne $startX -and (($endX - $startX) % [math]::Max(1, $SampleStepPx)) -ne 0) {
            $x = $endX
            $y = Get-FootholdYAtX $foothold $x
            $key = "$x,$y,$($foothold.id)"
            if ($null -ne $y -and $y -ge $minY -and $y -le $maxY -and !$seen.ContainsKey($key)) {
                $seen[$key] = $true
                $sameFootholdBonus = if ($Placement.footholdId -eq $foothold.id) { -120 } else { 0 }
                $distance = [math]::Abs($x - $Placement.x) + [math]::Abs($y - $Placement.y)
                $centerPenalty = if ([math]::Abs($x - $Placement.x) -lt 8) { 20 } else { 0 }
                $score = $distance + $centerPenalty + $sameFootholdBonus
                $candidates.Add([pscustomobject] @{
                    x = [int] $x
                    y = [int] $y
                    footholdId = [int] $foothold.id
                    offsetX = [int] ($x - $Placement.x)
                    offsetY = [int] ($y - $Placement.y)
                    manhattanDistance = [int] $distance
                    sameFootholdAsNpc = $Placement.footholdId -eq $foothold.id
                    score = [int] $score
                    source = "generated-box-foothold-sample"
                })
            }
        }
    }

    $ranked = @($candidates | Sort-Object score, manhattanDistance, footholdId, x | Select-Object -First $MaxPoints)
    $list = New-Object System.Collections.Generic.List[object]
    foreach ($candidate in $ranked) {
        $list.Add($candidate)
    }
    return ,$list
}

function Get-ApproachPointRows {
    param(
        [object[]] $Placements,
        [hashtable] $FootholdsByMap
    )

    $result = New-Object System.Collections.Generic.List[object]
    foreach ($placement in $Placements) {
        $footholds = $FootholdsByMap[$placement.mapId]
        $points = New-ApproachCandidates `
            $placement `
            @($footholds) `
            $ApproachLeftPx `
            $ApproachRightPx `
            $ApproachUpPx `
            $ApproachDownPx `
            $ApproachSampleStepPx `
            $MaxApproachPointsPerNpc

        $result.Add([pscustomobject] @{
            schemaVersion = 1
            mapId = $placement.mapId
            lifeIndex = $placement.lifeIndex
            npcId = $placement.npcId
            npcName = $placement.npcName
            npcPosition = @{
                x = $placement.x
                y = $placement.y
                footholdId = $placement.footholdId
            }
            interactionBox = @{
                left = $ApproachLeftPx
                right = $ApproachRightPx
                up = $ApproachUpPx
                down = $ApproachDownPx
            }
            sampleStepPx = $ApproachSampleStepPx
            candidates = $points
        })
    }
    return $result
}

function Convert-QuestSets {
    param([hashtable] $QuestInfo)

    if ($null -eq $QuestInfo) {
        return @{
            starts = @()
            completes = @()
        }
    }

    return @{
        starts = @($QuestInfo.starts | Sort-Object)
        completes = @($QuestInfo.completes | Sort-Object)
    }
}

function New-IntList {
    param([object[]] $Values)

    $list = New-Object System.Collections.Generic.List[int]
    foreach ($value in $Values) {
        if ($null -eq $value) {
            continue
        }
        if ($value -is [System.Collections.IEnumerable] -and $value -isnot [string]) {
            foreach ($inner in $value) {
                if ($null -ne $inner) {
                    [void] $list.Add([int] $inner)
                }
            }
            continue
        }
        [void] $list.Add([int] $value)
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

function Get-NpcInteractionTypes {
    param(
        [int] $NpcId,
        [string] $Name,
        [bool] $HasShop,
        [object] $QuestInfo
    )

    $types = New-Object System.Collections.Generic.List[string]
    if ($HasShop) {
        [void] $types.Add("shop")
    }
    if ($null -ne $QuestInfo) {
        if ($QuestInfo.starts.Count -gt 0) {
            [void] $types.Add("quest-start")
        }
        if ($QuestInfo.completes.Count -gt 0) {
            [void] $types.Add("quest-complete")
        }
    }

    if ($Name) {
        if ($Name -match "(?i)storage|warehouse") {
            [void] $types.Add("storage-inferred")
        }
        if ($Name -match "(?i)taxi|cab|ticket|station|subway|airport|travel|guide|tour|ship|boat|train") {
            [void] $types.Add("travel-inferred")
        }
        if ($Name -match "(?i)job|instructor|advancement|master|warrior|magician|archer|thief|pirate") {
            [void] $types.Add("job-inferred")
        }
        if ($Name -match "(?i)party quest|pq|dimensional mirror|event|gatekeeper|admission|exit|entrance") {
            [void] $types.Add("event-script-inferred")
        }
    }

    if ($NpcId -ge 9000000 -and $NpcId -lt 9100000 -and !$types.Contains("event-script-inferred")) {
        [void] $types.Add("event-script-inferred")
    }
    if ($types.Count -eq 0) {
        [void] $types.Add("talk-script-unknown")
    }
    return ,$types
}

function Get-AutomationProfile {
    param(
        [object] $NpcRow,
        [object[]] $InteractionTypes
    )

    $reasons = New-Object System.Collections.Generic.List[string]
    $doNotAutoUse = $false
    $confidence = "auto"

    if (!$NpcRow.name) {
        $confidence = "blocked"
        $doNotAutoUse = $true
        [void] $reasons.Add("missing-npc-name")
    }
    if ($NpcRow.placements.Count -eq 0) {
        $confidence = "blocked"
        $doNotAutoUse = $true
        [void] $reasons.Add("missing-placement")
    }
    if ($InteractionTypes -contains "talk-script-unknown") {
        if ($confidence -eq "auto") {
            $confidence = "inferred"
        }
        [void] $reasons.Add("script-only-or-unknown")
    }
    if ($InteractionTypes -contains "event-script-inferred") {
        $confidence = "manual"
        $doNotAutoUse = $true
        [void] $reasons.Add("event-or-script-sensitive")
    }
    if ($NpcRow.placements.Count -gt 20) {
        if ($confidence -eq "auto") {
            $confidence = "inferred"
        }
        [void] $reasons.Add("many-placements")
    }

    return @{
        confidence = $confidence
        doNotAutoUse = $doNotAutoUse
        reasons = New-StringList @($reasons)
    }
}

function Get-VisibleDialogueText {
    param([string] $Text)

    if ($null -eq $Text) {
        return ""
    }

    $clean = $Text -replace "\\r\\n|\\n|\\r", " "
    $clean = $clean -replace "#[A-Za-z][^#]*#", ""
    $clean = $clean -replace "#[A-Za-z]\d*", ""
    $clean = $clean -replace "#[A-Za-z]", ""
    $clean = $clean -replace "\s+", " "
    return $clean.Trim()
}

function Measure-DialogueNode {
    param([System.Xml.XmlNode] $Node)

    if ($null -eq $Node) {
        return @{
            visibleChars = 0
            rawChars = 0
            stringCount = 0
            optionCount = 0
            askCount = 0
        }
    }

    $stringNodes = $Node.SelectNodes(".//string")
    $askNodes = $Node.SelectNodes(".//int[@name='ask']")
    $visibleChars = 0
    $rawChars = 0
    $optionCount = 0

    foreach ($stringNode in $stringNodes) {
        $value = Get-AttrValue $stringNode "value"
        $rawChars += if ($value) { $value.Length } else { 0 }
        $visible = Get-VisibleDialogueText $value
        $visibleChars += $visible.Length
        if ($value) {
            $optionCount += [regex]::Matches($value, "#L\d+#").Count
        }
    }

    return @{
        visibleChars = $visibleChars
        rawChars = $rawChars
        stringCount = $stringNodes.Count
        optionCount = $optionCount
        askCount = $askNodes.Count
    }
}

function Get-EstimatedDelayRange {
    param(
        [int] $VisibleChars,
        [int] $OptionCount,
        [int] $AskCount,
        [bool] $Repeat
    )

    $baseMin = if ($Repeat) { 350 } else { 700 }
    $baseMax = if ($Repeat) { 800 } else { 1400 }
    $fastCharsPerSecond = if ($Repeat) { 42 } else { 34 }
    $slowCharsPerSecond = if ($Repeat) { 24 } else { 16 }
    $choiceMin = ($OptionCount + $AskCount) * 250
    $choiceMax = ($OptionCount + $AskCount) * 900

    $min = $baseMin + [int] [math]::Round(($VisibleChars * 1000.0) / $fastCharsPerSecond) + $choiceMin
    $max = $baseMax + [int] [math]::Round(($VisibleChars * 1000.0) / $slowCharsPerSecond) + $choiceMax
    $min = [math]::Max(500, [math]::Min($min, 9000))
    $max = [math]::Max($min + 100, [math]::Min($max, 14000))
    return New-IntList @($min, $max)
}

function Get-QuestDialogueTiming {
    param([string] $Path)

    $result = New-Object System.Collections.Generic.List[object]
    if (!(Test-Path $Path)) {
        return $result
    }

    $doc = Load-XmlDocument $Path
    foreach ($quest in $doc.DocumentElement.ChildNodes) {
        $questIdText = Get-AttrValue $quest "name"
        if ($questIdText -notmatch "^\d+$") {
            continue
        }

        $questId = [int] $questIdText
        foreach ($phaseName in @("0", "1")) {
            $phaseNode = Get-ChildByName $quest $phaseName
            $metrics = Measure-DialogueNode $phaseNode
            if ($metrics.stringCount -eq 0 -and $metrics.askCount -eq 0) {
                continue
            }

            $phase = if ($phaseName -eq "0") { "start" } else { "complete" }
            $result.Add([pscustomobject] @{
                schemaVersion = 1
                questId = $questId
                phase = $phase
                visibleChars = $metrics.visibleChars
                rawChars = $metrics.rawChars
                stringCount = $metrics.stringCount
                optionCount = $metrics.optionCount
                askPromptCount = $metrics.askCount
                firstReadDelayMsRange = Get-EstimatedDelayRange $metrics.visibleChars $metrics.optionCount $metrics.askCount $false
                repeatReadDelayMsRange = Get-EstimatedDelayRange $metrics.visibleChars $metrics.optionCount $metrics.askCount $true
                source = "Quest.wz/Say.img.xml"
            })
        }
    }
    return $result
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

    return [pscustomobject] @{
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

    return [pscustomobject] @{
        exp = Get-IntChildValue $Phase "exp"
        mesos = Get-IntChildValue $Phase "money"
        items = $items
    }
}

function Get-NpcQuestActions {
    param(
        [string] $CheckPath,
        [string] $ActPath
    )

    $actsByQuest = @{}
    if (Test-Path $ActPath) {
        $actDoc = Load-XmlDocument $ActPath
        foreach ($quest in $actDoc.DocumentElement.ChildNodes) {
            $questIdText = Get-AttrValue $quest "name"
            if ($questIdText -notmatch "^\d+$") {
                continue
            }
            $actsByQuest[[int] $questIdText] = $quest
        }
    }

    $result = New-Object System.Collections.Generic.List[object]
    if (!(Test-Path $CheckPath)) {
        return $result
    }

    $checkDoc = Load-XmlDocument $CheckPath
    foreach ($quest in $checkDoc.DocumentElement.ChildNodes) {
        $questIdText = Get-AttrValue $quest "name"
        if ($questIdText -notmatch "^\d+$") {
            continue
        }

        $questId = [int] $questIdText
        $actQuest = if ($actsByQuest.ContainsKey($questId)) { $actsByQuest[$questId] } else { $null }
        foreach ($phaseInfo in @(
            @{ wzName = "0"; phase = "start"; actionType = "quest-start" },
            @{ wzName = "1"; phase = "complete"; actionType = "quest-complete" }
        )) {
            $phaseNode = Get-ChildByName $quest $phaseInfo.wzName
            $npcId = Get-IntChildValue $phaseNode "npc"
            if ($null -eq $npcId) {
                continue
            }

            [void] $result.Add([pscustomobject] @{
                schemaVersion = 1
                actionKey = "$npcId|$($phaseInfo.actionType)|$questId"
                actionType = $phaseInfo.actionType
                phase = $phaseInfo.phase
                npcId = $npcId
                questId = $questId
                requirements = Get-QuestPhaseChecks $phaseNode
                rewards = Get-QuestPhaseActs (Get-ChildByName $actQuest $phaseInfo.wzName)
                canonicalCatalog = "tmp/game-catalog/generated_quest_catalog.json"
                sources = @("Quest.wz/Check.img.xml", "Quest.wz/Act.img.xml")
            })
        }
    }
    return $result
}

function Get-ScriptVisibleTextLength {
    param([string] $Text)

    $total = 0
    foreach ($match in [regex]::Matches($Text, "(?s)cm\.(?:send|ask)\w*\s*\((.*?)\)")) {
        foreach ($stringMatch in [regex]::Matches($match.Groups[1].Value, '"((?:\\"|[^"])*)"')) {
            $visible = Get-VisibleDialogueText ($stringMatch.Groups[1].Value -replace '\\"', '"')
            $total += $visible.Length
        }
    }
    return $total
}

function Get-NpcScriptCatalogRows {
    param(
        [string] $ScriptsRoot,
        [hashtable] $NpcNames
    )

    $dialogueOptions = New-Object System.Collections.Generic.List[object]
    $services = New-Object System.Collections.Generic.List[object]
    if (!(Test-Path $ScriptsRoot)) {
        Write-Output -NoEnumerate ([pscustomobject] @{
            dialogueOptions = $dialogueOptions.ToArray()
            services = $services.ToArray()
        })
        return
    }

    $scriptFiles = Get-ChildItem -Path $ScriptsRoot -File -Filter "*.js" |
        Where-Object { $_.BaseName -match "^\d+$" }

    foreach ($file in $scriptFiles) {
        $npcId = [int] $file.BaseName
        $text = Get-Content -Raw $file.FullName
        $relativePath = $file.FullName
        $visibleChars = Get-ScriptVisibleTextLength $text
        $optionMatches = [regex]::Matches($text, "#L(-?\d+)#(.*?)#l")
        foreach ($match in $optionMatches) {
            $optionId = [int] $match.Groups[1].Value
            $label = Get-VisibleDialogueText $match.Groups[2].Value
            [void] $dialogueOptions.Add([pscustomobject] @{
                schemaVersion = 1
                optionKey = "$npcId|script|$optionId|$($dialogueOptions.Count)"
                npcId = $npcId
                npcName = $NpcNames[$npcId]
                mapId = $null
                scriptName = $file.Name
                optionId = $optionId
                actionType = "unknown"
                labelHint = $label
                selectionValue = $optionId
                safeForAutomation = $false
                requiresManualReview = $true
                confidence = "script-scan"
                visibleCharsInScript = $visibleChars
                firstReadDelayMsRange = Get-EstimatedDelayRange $visibleChars $optionMatches.Count 1 $false
                repeatReadDelayMsRange = Get-EstimatedDelayRange $visibleChars $optionMatches.Count 1 $true
                source = $relativePath
            })
        }

        $patterns = @(
            @{ type = "shop-script"; regex = "(?i)\b(openShop|sendShop)\b|cm\.openShop|cm\.sendShop"; safe = $false },
            @{ type = "storage-script"; regex = "(?i)\b(openStorage|sendStorage|getStorage)\b|cm\.sendStorage"; safe = $false },
            @{ type = "travel-script"; regex = "(?i)\b(warp|changeMap|goTo|startMapEffect)\b|cm\.warp"; safe = $false },
            @{ type = "job-advance-script"; regex = "(?i)\b(changeJob|jobAdvance|advancement)\b|cm\.changeJob"; safe = $false },
            @{ type = "quest-start-script"; regex = "(?i)\b(startQuest|forceStart)\b|cm\.startQuest|qm\.forceStartQuest"; safe = $false },
            @{ type = "quest-complete-script"; regex = "(?i)\b(completeQuest|forceComplete)\b|cm\.completeQuest|qm\.forceCompleteQuest"; safe = $false },
            @{ type = "reward-script"; regex = "(?i)\b(gainItem|gainMeso|gainExp|gainNX|gainFame)\b|cm\.gain"; safe = $false },
            @{ type = "party-event-script"; regex = "(?i)\b(getEventManager|party|PQ|startInstance|event)\b"; safe = $false },
            @{ type = "style-cosmetic-script"; regex = "(?i)\b(setHair|setFace|changeHair|changeFace|cosmetic|style)\b"; safe = $false },
            @{ type = "maker-crafting-script"; regex = "(?i)\b(Maker|maker|craft|createItem)\b"; safe = $false }
        )

        foreach ($pattern in $patterns) {
            if ($text -match $pattern.regex) {
                [void] $services.Add([pscustomobject] @{
                    schemaVersion = 1
                    serviceId = "$npcId|$($pattern.type)"
                    npcId = $npcId
                    npcName = $NpcNames[$npcId]
                    serviceType = $pattern.type
                    scriptName = $file.Name
                    safeForAutomation = [bool] $pattern.safe
                    requiresManualReview = $true
                    confidence = "script-pattern"
                    visibleCharsInScript = $visibleChars
                    optionCount = $optionMatches.Count
                    firstReadDelayMsRange = Get-EstimatedDelayRange $visibleChars $optionMatches.Count 1 $false
                    repeatReadDelayMsRange = Get-EstimatedDelayRange $visibleChars $optionMatches.Count 1 $true
                    source = $relativePath
                })
            }
        }
    }

    Write-Output -NoEnumerate ([pscustomobject] @{
        dialogueOptions = $dialogueOptions.ToArray()
        services = $services.ToArray()
    })
}

function Get-NpcRewardChoiceRows {
    param($QuestActions)

    $rows = New-Object System.Collections.Generic.List[object]
    foreach ($action in $QuestActions) {
        $rewardItems = @()
        if ($null -ne $action.rewards) {
            if ($action.rewards -is [System.Collections.IDictionary]) {
                $rewardItems = @($action.rewards["items"])
            } else {
                $rewardItemsProperty = @($action.rewards.PSObject.Properties | Where-Object { $_.Name -eq "items" -and $_.MemberType -eq "NoteProperty" } | Select-Object -First 1)
                if ($null -ne $rewardItemsProperty) {
                    $rewardItems = @($rewardItemsProperty.Value)
                }
            }
        }
        $choiceItems = @($rewardItems | Where-Object { $null -ne $_.prop -or $null -ne $_.job })
        if ($rewardItems.Count -le 1 -and $choiceItems.Count -eq 0) {
            continue
        }

        [void] $rows.Add([pscustomobject] @{
            schemaVersion = 1
            rewardChoiceKey = "$($action.npcId)|$($action.questId)|$($action.phase)"
            npcId = $action.npcId
            questId = $action.questId
            phase = $action.phase
            actionKey = $action.actionKey
            rewardItemCount = $rewardItems.Count
            hasProbabilityWeights = @($rewardItems | Where-Object { $null -ne $_.prop }).Count -gt 0
            hasJobSpecificRewards = @($rewardItems | Where-Object { $null -ne $_.job }).Count -gt 0
            candidates = $rewardItems
            policy = @{
                safeForAutomation = $false
                requiresRewardSelectionPolicy = $true
                note = "Generated from Quest.wz/Act rewards. Runtime must choose with job/build/inventory policy."
            }
            source = "Quest.wz/Act.img.xml"
        })
    }
    return $rows
}

function Add-IndexValue {
    param(
        [hashtable] $Index,
        [string] $Key,
        [object] $Value
    )

    if (!$Index.ContainsKey($Key)) {
        $Index[$Key] = New-Object System.Collections.Generic.List[object]
    }
    [void] $Index[$Key].Add($Value)
}

function New-PlacementKey {
    param([object] $Placement)
    return "$($Placement.mapId)|$($Placement.lifeIndex)|$($Placement.npcId)"
}

function New-QuestPhaseKey {
    param(
        [int] $QuestId,
        [string] $Phase
    )
    return "$QuestId|$Phase"
}

function Convert-IndexLists {
    param([hashtable] $Index)

    $result = [ordered] @{}
    foreach ($key in ($Index.Keys | Sort-Object)) {
        $values = New-Object System.Collections.Generic.List[object]
        foreach ($value in $Index[$key]) {
            [void] $values.Add($value)
        }
        $result[$key] = $values
    }
    return $result
}

function New-NpcFastIndexes {
    param(
        $Catalog,
        $Placements,
        $ApproachRows,
        $DialogueTiming,
        $QuestActions,
        $DialogueOptions,
        $Services,
        $RewardChoices,
        $ShopInventory,
        [hashtable] $ShopNpcIds,
        $MapSummaries
    )

    $npcIdToCatalogRow = [ordered] @{}
    $npcIdToPlacements = @{}
    $mapIdToNpcPlacements = @{}
    $mapIdToShopNpcIds = [ordered] @{}
    $mapIdToQuestNpcIds = [ordered] @{}
    $npcIdToStartedQuestIds = [ordered] @{}
    $npcIdToCompletedQuestIds = [ordered] @{}
    $questIdToStartNpcIds = @{}
    $questIdToCompleteNpcIds = @{}
    $npcIdToShopIds = [ordered] @{}
    $shopIdToNpcIds = @{}
    $placementKeyToApproachPoints = [ordered] @{}
    $placementKeyToInteractionBox = [ordered] @{}
    $questIdPhaseToDialogueTiming = [ordered] @{}
    $npcIdToManualReviewReasons = [ordered] @{}
    $mapIdToManualReviewNpcIds = @{}
    $npcIdToQuestActions = @{}
    $questIdPhaseToAction = [ordered] @{}
    $actionKeyToRequirements = [ordered] @{}
    $actionKeyToRewards = [ordered] @{}
    $npcIdToDialogueOptions = @{}
    $npcIdToServices = @{}
    $serviceTypeToNpcIds = @{}
    $npcIdToRewardChoices = @{}
    $questIdToRewardChoices = @{}
    $npcIdToShopInventory = @{}
    $shopIdToItems = [ordered] @{}

    foreach ($row in $Catalog) {
        $npcKey = [string] $row.npcId
        $npcIdToCatalogRow[$npcKey] = $row
        $npcIdToStartedQuestIds[$npcKey] = New-IntList @($row.interactions.quests.starts)
        $npcIdToCompletedQuestIds[$npcKey] = New-IntList @($row.interactions.quests.completes)
        $npcIdToShopIds[$npcKey] = New-IntList @($row.interactions.shop.shopIds)
        $npcIdToManualReviewReasons[$npcKey] = New-StringList @($row.automation.reasons)
    }

    foreach ($placement in $Placements) {
        Add-IndexValue $npcIdToPlacements ([string] $placement.npcId) $placement
        Add-IndexValue $mapIdToNpcPlacements ([string] $placement.mapId) $placement
    }

    foreach ($mapSummary in $MapSummaries) {
        $mapKey = [string] $mapSummary.mapId
        $mapIdToShopNpcIds[$mapKey] = New-IntList @($mapSummary.shopNpcIds)
        $mapIdToQuestNpcIds[$mapKey] = New-IntList @($mapSummary.questNpcIds)
        foreach ($npcId in @($mapSummary.npcIds)) {
            $catalogRow = @($Catalog | Where-Object { $_.npcId -eq $npcId })[0]
            if ($catalogRow -and $catalogRow.automation.doNotAutoUse) {
                Add-IndexValue $mapIdToManualReviewNpcIds $mapKey ([int] $npcId)
            }
        }
    }

    foreach ($row in $Catalog) {
        foreach ($questId in @($row.interactions.quests.starts)) {
            Add-IndexValue $questIdToStartNpcIds ([string] $questId) ([int] $row.npcId)
        }
        foreach ($questId in @($row.interactions.quests.completes)) {
            Add-IndexValue $questIdToCompleteNpcIds ([string] $questId) ([int] $row.npcId)
        }
    }

    foreach ($npcId in $ShopNpcIds.Keys) {
        foreach ($shopId in @($ShopNpcIds[$npcId])) {
            Add-IndexValue $shopIdToNpcIds ([string] $shopId) ([int] $npcId)
        }
    }

    foreach ($approach in $ApproachRows) {
        $key = New-PlacementKey $approach
        $candidates = New-Object System.Collections.Generic.List[object]
        foreach ($candidate in $approach.candidates) {
            [void] $candidates.Add($candidate)
        }
        $placementKeyToApproachPoints[$key] = $candidates
        $placementKeyToInteractionBox[$key] = $approach.interactionBox
    }

    foreach ($timing in $DialogueTiming) {
        $questPhaseKey = New-QuestPhaseKey $timing.questId $timing.phase
        $questIdPhaseToDialogueTiming[$questPhaseKey] = $timing
    }

    foreach ($action in $QuestActions) {
        Add-IndexValue $npcIdToQuestActions ([string] $action.npcId) $action
        $questPhaseKey = New-QuestPhaseKey $action.questId $action.phase
        $questIdPhaseToAction[$questPhaseKey] = $action
        $actionKeyToRequirements[$action.actionKey] = $action.requirements
        $actionKeyToRewards[$action.actionKey] = $action.rewards
    }

    foreach ($option in $DialogueOptions) {
        Add-IndexValue $npcIdToDialogueOptions ([string] $option.npcId) $option
    }

    foreach ($service in $Services) {
        Add-IndexValue $npcIdToServices ([string] $service.npcId) $service
        Add-IndexValue $serviceTypeToNpcIds ([string] $service.serviceType) ([int] $service.npcId)
    }

    foreach ($choice in $RewardChoices) {
        Add-IndexValue $npcIdToRewardChoices ([string] $choice.npcId) $choice
        Add-IndexValue $questIdToRewardChoices ([string] $choice.questId) $choice
    }

    foreach ($inventory in $ShopInventory) {
        Add-IndexValue $npcIdToShopInventory ([string] $inventory.npcId) $inventory
        $shopIdToItems[[string] $inventory.shopId] = $inventory.items
    }

    return [pscustomobject] @{
        schemaVersion = 1
        generatedBy = "tools/npc-catalog/Export-NpcCatalog.ps1"
        ownership = @{
            npcCatalogOwns = @(
                "npc names"
                "npc placements"
                "npc interaction labels"
                "npc shop links"
                "npc quest action links"
                "npc approach points"
                "npc dialogue timing"
                "npc script dialogue option hints"
                "npc script service hints"
                "npc-facing reward choice hints"
                "npc shop inventory snapshots"
                "npc automation review flags"
            )
            gameCatalogOwns = @(
                "canonical quest requirements"
                "canonical quest rewards"
                "mob, drop, item, shop item, skill, and map catalogs"
            )
            derivedNpcIndexes = @(
                "quest action requirement summaries copied for runtime lookup"
                "quest action reward summaries copied for runtime lookup"
            )
        }
        npcId_to_catalogRow = $npcIdToCatalogRow
        npcId_to_placements = Convert-IndexLists $npcIdToPlacements
        mapId_to_npcPlacements = Convert-IndexLists $mapIdToNpcPlacements
        mapId_to_shopNpcIds = $mapIdToShopNpcIds
        mapId_to_questNpcIds = $mapIdToQuestNpcIds
        npcId_to_startedQuestIds = $npcIdToStartedQuestIds
        npcId_to_completedQuestIds = $npcIdToCompletedQuestIds
        questId_to_startNpcIds = Convert-IndexLists $questIdToStartNpcIds
        questId_to_completeNpcIds = Convert-IndexLists $questIdToCompleteNpcIds
        npcId_to_shopIds = $npcIdToShopIds
        shopId_to_npcIds = Convert-IndexLists $shopIdToNpcIds
        placementKey_to_approachPoints = $placementKeyToApproachPoints
        placementKey_to_interactionBox = $placementKeyToInteractionBox
        questId_phase_to_dialogueTiming = $questIdPhaseToDialogueTiming
        npcId_to_manualReviewReasons = $npcIdToManualReviewReasons
        mapId_to_manualReviewNpcIds = Convert-IndexLists $mapIdToManualReviewNpcIds
        npcId_to_questActions = Convert-IndexLists $npcIdToQuestActions
        questId_phase_to_action = $questIdPhaseToAction
        actionKey_to_requirements = $actionKeyToRequirements
        actionKey_to_rewards = $actionKeyToRewards
        npcId_to_dialogueOptions = Convert-IndexLists $npcIdToDialogueOptions
        npcId_to_services = Convert-IndexLists $npcIdToServices
        serviceType_to_npcIds = Convert-IndexLists $serviceTypeToNpcIds
        npcId_to_rewardChoices = Convert-IndexLists $npcIdToRewardChoices
        questId_to_rewardChoices = Convert-IndexLists $questIdToRewardChoices
        npcId_to_shopInventory = Convert-IndexLists $npcIdToShopInventory
        shopId_to_items = $shopIdToItems
    }
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$npcNames = Get-StringTable (Join-Path $WzRoot "String.wz/Npc.img.xml")
$mapNames = Get-MapStringTable (Join-Path $WzRoot "String.wz/Map.img.xml")
$shopNpcIds = Get-ShopNpcIds "src/main/resources/db/data/101-shops-data.sql"
$shopItems = Get-ShopItems "src/main/resources/db/data/102-shopitems-data.sql"
$questNpcMap = Get-QuestNpcMap (Join-Path $WzRoot "Quest.wz/Check.img.xml")
$questDialogueTiming = Get-QuestDialogueTiming (Join-Path $WzRoot "Quest.wz/Say.img.xml")
$questActions = Get-NpcQuestActions (Join-Path $WzRoot "Quest.wz/Check.img.xml") (Join-Path $WzRoot "Quest.wz/Act.img.xml")
$scriptCatalog = Get-NpcScriptCatalogRows "scripts/npc" $npcNames
$dialogueOptions = @($scriptCatalog.dialogueOptions)
$services = @($scriptCatalog.services)
$rewardChoices = Get-NpcRewardChoiceRows $questActions
$shopInventory = Get-NpcShopInventoryRows $shopNpcIds $shopItems
$mapRoot = Join-Path $WzRoot "Map.wz/Map"
$placements = Get-LifePlacements $mapRoot $npcNames $mapNames
$approachRows = New-Object System.Collections.Generic.List[object]
if (!$SkipApproach) {
    $placementMapIds = @($placements | Select-Object -ExpandProperty mapId -Unique)
    $footholdsByMap = Get-FootholdsByMap $mapRoot $placementMapIds
    $approachRows = Get-ApproachPointRows @($placements) $footholdsByMap
}

$npcIds = New-Object System.Collections.Generic.HashSet[int]
foreach ($placement in $placements) {
    [void] $npcIds.Add($placement.npcId)
}
foreach ($npcId in $shopNpcIds.Keys) {
    [void] $npcIds.Add([int] $npcId)
}
foreach ($npcId in $questNpcMap.Keys) {
    [void] $npcIds.Add([int] $npcId)
}
foreach ($option in $dialogueOptions) {
    [void] $npcIds.Add([int] $option.npcId)
}
foreach ($service in $services) {
    [void] $npcIds.Add([int] $service.npcId)
}

$catalog = foreach ($npcId in ($npcIds | Sort-Object)) {
    $npcPlacements = @($placements | Where-Object { $_.npcId -eq $npcId })
    $hasShop = $shopNpcIds.ContainsKey($npcId)
    $questInfo = $questNpcMap[$npcId]
    $npcDialogueOptionCount = @($dialogueOptions | Where-Object { $_.npcId -eq $npcId }).Count
    $npcServices = @($services | Where-Object { $_.npcId -eq $npcId })
    $interactionTypes = Get-NpcInteractionTypes $npcId $npcNames[$npcId] $hasShop $questInfo
    if ($npcDialogueOptionCount -gt 0 -and !$interactionTypes.Contains("dialogue-option")) {
        [void] $interactionTypes.Add("dialogue-option")
    }
    foreach ($service in $npcServices) {
        $serviceType = [string] $service.serviceType
        if (!$interactionTypes.Contains($serviceType)) {
            [void] $interactionTypes.Add($serviceType)
        }
    }
    $baseRow = [pscustomobject] @{
        npcId = $npcId
        name = $npcNames[$npcId]
        placements = $npcPlacements
    }
    $automationProfile = Get-AutomationProfile $baseRow @($interactionTypes)
    [pscustomobject] @{
        schemaVersion = 1
        npcId = $npcId
        name = $npcNames[$npcId]
        interactions = @{
            types = New-StringList @($interactionTypes)
            shop = @{
                hasShop = $hasShop
                shopIds = if ($hasShop) { New-IntList @($shopNpcIds[$npcId] | Sort-Object) } else { New-IntList @() }
            }
            quests = Convert-QuestSets $questInfo
            dialogueOptions = @{
                hasOptions = $npcDialogueOptionCount -gt 0
                optionCount = $npcDialogueOptionCount
            }
            services = New-StringList @($npcServices | Select-Object -ExpandProperty serviceType -Unique | Sort-Object)
        }
        placements = $npcPlacements
        approach = @{
            defaultBox = @{
                left = 120
                right = 120
                up = 80
                down = 180
            }
            generatedCandidatePoints = @()
            source = "default-placeholder"
        }
        timing = @{
            firstReadDelayMsRange = @(1200, 4200)
            repeatDelayMsRange = @(500, 1600)
            source = "default-placeholder"
        }
        automation = $automationProfile
        confidence = $automationProfile.confidence
        sources = @("Map.wz", "String.wz", "Quest.wz/Check.img.xml", "src/main/resources/db/data/101-shops-data.sql")
    }
}

$catalogPath = Join-Path $OutputDir "generated_npc_catalog.json"
$placementsPath = Join-Path $OutputDir "generated_npc_placements.json"
$approachPath = Join-Path $OutputDir "generated_npc_approach_points.json"
$dialogueTimingPath = Join-Path $OutputDir "generated_quest_dialogue_timing.json"
$actionsPath = Join-Path $OutputDir "generated_npc_action_catalog.json"
$dialogueOptionsPath = Join-Path $OutputDir "generated_npc_dialogue_options.json"
$servicesPath = Join-Path $OutputDir "generated_npc_services.json"
$rewardChoicesPath = Join-Path $OutputDir "generated_npc_reward_choices.json"
$shopInventoryPath = Join-Path $OutputDir "generated_npc_shop_inventory.json"
$fastIndexesPath = Join-Path $OutputDir "generated_npc_fast_indexes.json"
$mapSummaryPath = Join-Path $OutputDir "generated_map_npc_summary.json"
$summaryPath = Join-Path $OutputDir "NPC_CATALOG_SUMMARY.md"
$gapsPath = Join-Path $OutputDir "NPC_CATALOG_GAPS.md"
$validationPath = Join-Path $OutputDir "NPC_CATALOG_VALIDATION.md"

$approachByPlacement = @{}
foreach ($row in $approachRows) {
    $key = "$($row.mapId)|$($row.lifeIndex)|$($row.npcId)"
    $approachByPlacement[$key] = $row
}

$mapSummaries = foreach ($group in ($placements | Group-Object mapId | Sort-Object { [int] $_.Name })) {
    $mapId = [int] $group.Name
    $mapInfo = $MapNames[$mapId]
    $mapPlacements = @($group.Group)
    $mapNpcIds = @($mapPlacements | Select-Object -ExpandProperty npcId -Unique | Sort-Object)
    $shopIdsForMap = @($mapNpcIds | Where-Object { $shopNpcIds.ContainsKey([int] $_) })
    $questIdsForMap = @($mapNpcIds | Where-Object { $questNpcMap.ContainsKey([int] $_) })
    $missingCandidateCount = 0
    foreach ($placement in $mapPlacements) {
        $key = "$($placement.mapId)|$($placement.lifeIndex)|$($placement.npcId)"
        if (!$approachByPlacement.ContainsKey($key) -or $approachByPlacement[$key].candidates.Count -eq 0) {
            $missingCandidateCount++
        }
    }

    [pscustomobject] @{
        schemaVersion = 1
        mapId = $mapId
        streetName = if ($mapInfo) { $mapInfo.streetName } else { $null }
        mapName = if ($mapInfo) { $mapInfo.mapName } else { $null }
        npcPlacementCount = $mapPlacements.Count
        uniqueNpcCount = $mapNpcIds.Count
        shopNpcIds = New-IntList @($shopIdsForMap)
        questNpcIds = New-IntList @($questIdsForMap)
        missingApproachCandidatePlacements = $missingCandidateCount
        npcIds = New-IntList @($mapNpcIds)
    }
}

$fastIndexes = New-NpcFastIndexes `
    -Catalog $catalog `
    -Placements $placements `
    -ApproachRows $approachRows `
    -DialogueTiming $questDialogueTiming `
    -QuestActions $questActions `
    -DialogueOptions $dialogueOptions `
    -Services $services `
    -RewardChoices $rewardChoices `
    -ShopInventory $shopInventory `
    -ShopNpcIds $shopNpcIds `
    -MapSummaries $mapSummaries

$catalog | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 $catalogPath
$placements | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $placementsPath
if (!$SkipApproach) {
    $approachRows | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 $approachPath
}
$questDialogueTiming | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $dialogueTimingPath
$questActions | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 $actionsPath
$dialogueOptions | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 $dialogueOptionsPath
$services | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 $servicesPath
$rewardChoices | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 $rewardChoicesPath
$shopInventory | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 $shopInventoryPath
$fastIndexes | ConvertTo-Json -Depth 16 | Set-Content -Encoding UTF8 $fastIndexesPath
$mapSummaries | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $mapSummaryPath

$placedNpcCount = @($catalog | Where-Object { $_.placements.Count -gt 0 }).Count
$shopNpcCount = @($catalog | Where-Object { $_.interactions.shop.hasShop }).Count
$questNpcCount = @($catalog | Where-Object {
    $_.interactions.quests.starts.Count -gt 0 -or $_.interactions.quests.completes.Count -gt 0
}).Count
$missingNameCount = @($catalog | Where-Object { !$_.name }).Count
$placementsWithApproachCount = @($approachRows | Where-Object { $_.candidates.Count -gt 0 }).Count
$approachCandidateCount = ($approachRows | ForEach-Object { $_.candidates.Count } | Measure-Object -Sum).Sum
$questDialogueTimingCount = @($questDialogueTiming).Count
$questActionCount = @($questActions).Count
$dialogueOptionCount = @($dialogueOptions).Count
$serviceCount = @($services).Count
$rewardChoiceCount = @($rewardChoices).Count
$shopInventoryCount = @($shopInventory).Count
$shopInventoryItemCount = ($shopInventory | ForEach-Object { $_.itemCount } | Measure-Object -Sum).Sum
$missingNameRows = @($catalog | Where-Object { !$_.name } | Sort-Object npcId)
$missingApproachRows = if (!$SkipApproach) { @($approachRows | Where-Object { $_.candidates.Count -eq 0 } | Sort-Object mapId, lifeIndex, npcId) } else { @() }
$questWithoutPlacementRows = @($catalog | Where-Object {
    $_.placements.Count -eq 0 -and ($_.interactions.quests.starts.Count -gt 0 -or $_.interactions.quests.completes.Count -gt 0)
} | Sort-Object npcId)
$shopWithoutPlacementRows = @($catalog | Where-Object {
    $_.placements.Count -eq 0 -and $_.interactions.shop.hasShop
} | Sort-Object npcId)
$doNotAutoUseRows = @($catalog | Where-Object { $_.automation.doNotAutoUse } | Sort-Object npcId)
$highPlacementRows = @($catalog | Where-Object { $_.placements.Count -gt 20 } | Sort-Object @{ Expression = { $_.placements.Count }; Descending = $true }, npcId)
$inferredTypeRows = @($catalog | Where-Object {
    @($_.interactions.types | Where-Object { $_ -like "*-inferred" }).Count -gt 0
} | Sort-Object npcId)

$topMultiMap = @(
    $catalog |
        Sort-Object @{ Expression = { $_.placements.Count }; Descending = $true }, npcId |
        Select-Object -First 20 |
        ForEach-Object {
            "| $($_.npcId) | $($_.name) | $($_.placements.Count) | $($_.interactions.shop.hasShop) | $($_.interactions.quests.starts.Count) | $($_.interactions.quests.completes.Count) |"
        }
)

$summary = @(
    "# Generated NPC Catalog Summary"
    ""
    "Generated by ``tools/npc-catalog/Export-NpcCatalog.ps1``."
    ""
    "This is offline preparation data only. It is not wired into Agent runtime code."
    ""
    "## Counts"
    ""
    "- Catalog NPCs: $(@($catalog).Count)"
    "- NPC placements: $(@($placements).Count)"
    "- NPCs with at least one placement: $placedNpcCount"
    "- NPCs with shops from SQL seed data: $shopNpcCount"
    "- NPCs referenced by quest start/complete checks: $questNpcCount"
    "- NPCs missing String.wz names: $missingNameCount"
    "- NPC placements with generated approach candidates: $placementsWithApproachCount"
    "- Generated approach candidates: $approachCandidateCount"
    "- Quest dialogue timing rows: $questDialogueTimingCount"
    "- NPC quest action rows: $questActionCount"
    "- NPC script dialogue option rows: $dialogueOptionCount"
    "- NPC script service hint rows: $serviceCount"
    "- NPC reward choice rows: $rewardChoiceCount"
    "- NPC shop inventory rows: $shopInventoryCount"
    "- NPC shop inventory item rows: $shopInventoryItemCount"
    ""
    "## Outputs"
    ""
    "- ``generated_npc_catalog.json``"
    "- ``generated_npc_placements.json``"
    $(if (!$SkipApproach) { "- ``generated_npc_approach_points.json``" } else { "- approach point generation skipped" })
    "- ``generated_quest_dialogue_timing.json``"
    "- ``generated_npc_action_catalog.json``"
    "- ``generated_npc_dialogue_options.json``"
    "- ``generated_npc_services.json``"
    "- ``generated_npc_reward_choices.json``"
    "- ``generated_npc_shop_inventory.json``"
    "- ``generated_npc_fast_indexes.json``"
    "- ``generated_map_npc_summary.json``"
    "- ``NPC_CATALOG_SUMMARY.md``"
    "- ``NPC_CATALOG_VALIDATION.md``"
    ""
    "## Top Multi-Placement NPCs"
    ""
    "| NPC ID | Name | Placements | Has Shop | Quest Starts | Quest Completes |"
    "| --- | --- | ---: | --- | ---: | ---: |"
    ($topMultiMap -join "`n")
    ""
    "## Notes"
    ""
    "- Approach boxes and timing ranges are default placeholders for future manual tuning."
    "- Quest interactions come from ``Quest.wz/Check.img.xml`` ``0`` start and ``1`` complete NPC checks."
    "- Quest requirements and rewards are canonical in ``tmp/game-catalog/generated_quest_catalog.json``; NPC action rows copy compact summaries for fast runtime lookup."
    "- Shop interactions come from ``src/main/resources/db/data/101-shops-data.sql``."
    "- Candidate standing points are generated from a default interaction box and raw foothold samples."
    "- Candidate points are not reachability/path validated yet; runtime integration should verify navigation before use."
    "- Quest dialogue timing is an estimate from WZ text length/options; runtime profiles should still apply per-agent jitter."
    "- Script dialogue options and services are pattern-scanned hints; they require runtime validators or manual overrides before automation."
    "- Reward choice rows identify NPC-facing quest rewards that need profile/build/inventory selection policy."
    "- Shop inventory snapshots come from SQL seed data and are useful for fast buy/sell planning."
    "- Fast indexes are generated to avoid full-array scans during Agent/LLM runtime queries."
    "- Interaction types and automation confidence are generated hints for review, not permission to execute NPC actions."
) -join "`n"

Set-Content -Encoding UTF8 -Path $summaryPath -Value $summary

$missingNameTable = @(
    $missingNameRows |
        Select-Object -First 100 |
        ForEach-Object {
            $placementCount = if ($_.placements) { $_.placements.Count } else { 0 }
            "| $($_.npcId) | $placementCount | $($_.interactions.shop.hasShop) | $($_.interactions.quests.starts.Count) | $($_.interactions.quests.completes.Count) |"
        }
)

$missingApproachTable = @(
    $missingApproachRows |
        Select-Object -First 100 |
        ForEach-Object {
            "| $($_.mapId) | $($_.lifeIndex) | $($_.npcId) | $($_.npcName) | $($_.npcPosition.x),$($_.npcPosition.y) | $($_.npcPosition.footholdId) |"
        }
)

$gaps = @(
    "# NPC Catalog Gaps"
    ""
    "Generated by ``tools/npc-catalog/Export-NpcCatalog.ps1``."
    ""
    "## Missing NPC Names"
    ""
    "Rows where `String.wz/Npc.img.xml` did not provide a name."
    ""
    "| NPC ID | Placements | Has Shop | Quest Starts | Quest Completes |"
    "| --- | ---: | --- | ---: | ---: |"
    $(if ($missingNameTable.Count -gt 0) { $missingNameTable -join "`n" } else { "| none | 0 | false | 0 | 0 |" })
    ""
    "## Missing Approach Candidates"
    ""
    $(if ($SkipApproach) { "Approach generation was skipped for this run." } else { "Placements where the default interaction box did not intersect any non-wall foothold." })
    ""
    "| Map ID | Life Index | NPC ID | Name | NPC Position | NPC Foothold |"
    "| --- | --- | --- | --- | --- | --- |"
    $(if ($missingApproachTable.Count -gt 0) { $missingApproachTable -join "`n" } else { "| none | - | - | - | - | - |" })
    ""
    "## Follow-Up"
    ""
    "- Missing names may be valid hidden/marker NPCs, or String.wz gaps."
    "- Missing approach candidates should usually get manual overrides or special-case risk levels."
    "- Runtime integration must still validate live range and navigation reachability."
) -join "`n"

Set-Content -Encoding UTF8 -Path $gapsPath -Value $gaps

function New-CatalogReviewTable {
    param(
        [object[]] $Rows,
        [int] $Limit = $ValidationTopN
    )

    $table = @(
        $Rows |
            Select-Object -First $Limit |
            ForEach-Object {
                $types = @($_.interactions.types) -join ", "
                $reasons = @($_.automation.reasons) -join ", "
                "| $($_.npcId) | $($_.name) | $($_.placements.Count) | $types | $($_.confidence) | $reasons |"
            }
    )
    if ($table.Count -eq 0) {
        return "| none | - | 0 | - | - | - |"
    }
    return $table -join "`n"
}

$validation = @(
    "# NPC Catalog Validation"
    ""
    "Generated by ``tools/npc-catalog/Export-NpcCatalog.ps1``."
    ""
    "This report is for offline review only. It does not change server runtime behavior."
    ""
    "## Review Counts"
    ""
    "- Missing names: $($missingNameRows.Count)"
    "- Missing approach candidates: $($missingApproachRows.Count)"
    "- Quest NPCs without placement: $($questWithoutPlacementRows.Count)"
    "- Shop NPCs without placement: $($shopWithoutPlacementRows.Count)"
    "- Do-not-auto-use NPCs: $($doNotAutoUseRows.Count)"
    "- NPCs with more than 20 placements: $($highPlacementRows.Count)"
    "- NPCs with inferred interaction types: $($inferredTypeRows.Count)"
    "- Script dialogue option hint rows: $dialogueOptionCount"
    "- Script service hint rows: $serviceCount"
    "- Reward choice rows needing policy: $rewardChoiceCount"
    "- Shop inventory rows: $shopInventoryCount"
    ""
    "## Do Not Auto Use"
    ""
    "| NPC ID | Name | Placements | Types | Confidence | Reasons |"
    "| --- | --- | ---: | --- | --- | --- |"
    (New-CatalogReviewTable $doNotAutoUseRows)
    ""
    "## Quest NPCs Without Placement"
    ""
    "| NPC ID | Name | Placements | Types | Confidence | Reasons |"
    "| --- | --- | ---: | --- | --- | --- |"
    (New-CatalogReviewTable $questWithoutPlacementRows)
    ""
    "## Shop NPCs Without Placement"
    ""
    "| NPC ID | Name | Placements | Types | Confidence | Reasons |"
    "| --- | --- | ---: | --- | --- | --- |"
    (New-CatalogReviewTable $shopWithoutPlacementRows)
    ""
    "## Inferred Interaction Types"
    ""
    "| NPC ID | Name | Placements | Types | Confidence | Reasons |"
    "| --- | --- | ---: | --- | --- | --- |"
    (New-CatalogReviewTable $inferredTypeRows)
    ""
    "## High Placement NPCs"
    ""
    "| NPC ID | Name | Placements | Types | Confidence | Reasons |"
    "| --- | --- | ---: | --- | --- | --- |"
    (New-CatalogReviewTable $highPlacementRows)
    ""
    "## Follow-Up Rules"
    ""
    "- Keep generated JSON replaceable."
    "- Put hand-tuned quirks in overrides, not generated output."
    "- Treat ``manual``, ``blocked``, or ``doNotAutoUse`` rows as runtime-gated until reviewed."
    "- Treat script-scanned dialogue option and service rows as hints, not executable scripts."
    "- Add overrides for reviewed service flows before enabling automation for travel, storage, job advancement, PQ, event, style, or maker NPCs."
    "- Add reward selection policy before enabling automated quest completion where multiple reward candidates exist."
    "- Reachability must still be validated by the future navigation/runtime layer."
) -join "`n"

Set-Content -Encoding UTF8 -Path $validationPath -Value $validation

Write-Host "NPC catalog generated:"
Write-Host "  $catalogPath"
Write-Host "  $placementsPath"
if (!$SkipApproach) {
    Write-Host "  $approachPath"
}
Write-Host "  $dialogueTimingPath"
Write-Host "  $actionsPath"
Write-Host "  $dialogueOptionsPath"
Write-Host "  $servicesPath"
Write-Host "  $rewardChoicesPath"
Write-Host "  $shopInventoryPath"
Write-Host "  $fastIndexesPath"
Write-Host "  $mapSummaryPath"
Write-Host "  $summaryPath"
Write-Host "  $gapsPath"
Write-Host "  $validationPath"
