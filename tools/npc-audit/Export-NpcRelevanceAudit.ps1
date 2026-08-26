param(
    [string] $WzRoot = "wz",
    [string] $NpcScriptRoot = "scripts/npc",
    [string] $DatabaseDataRoot = "src/main/resources/db/data",
    [string] $OutputDir = "tmp/npc-audit"
)

$ErrorActionPreference = "Stop"

function Resolve-RequiredPath([string] $Path, [string] $Label) {
    if (!(Test-Path -LiteralPath $Path)) { throw "$Label does not exist: $Path" }
    return (Resolve-Path -LiteralPath $Path).Path
}

function Read-Xml([string] $Path) {
    $document = [System.Xml.XmlDocument]::new()
    $document.PreserveWhitespace = $false
    $document.Load($Path)
    return $document
}

function Add-SetValue($Map, [string] $Key, [string] $Value) {
    if (!$Map.ContainsKey($Key)) {
        $Map[$Key] = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    }
    [void] $Map[$Key].Add($Value)
}

function Get-DirectString([System.Xml.XmlElement] $Entry, [string] $Name) {
    $node = $Entry.SelectSingleNode("./string[@name='$Name']")
    if ($null -eq $node) { return $null }
    return [string] $node.GetAttribute('value')
}

function Get-DirectValue([System.Xml.XmlElement] $Entry, [string] $Name) {
    $node = $Entry.SelectSingleNode("./*[@name='$Name' and @value]")
    if ($null -eq $node) { return '' }
    return [string]$node.GetAttribute('value')
}

function Get-DateValue([string] $Value) {
    if ($Value -notmatch '^(\d{4})(\d{2})(\d{2})') { return $null }
    try { return [datetime]::new([int]$Matches[1], [int]$Matches[2], [int]$Matches[3]) } catch { return $null }
}

$wz = Resolve-RequiredPath $WzRoot "WZ root"
$scripts = Resolve-RequiredPath $NpcScriptRoot "NPC script root"
$database = Resolve-RequiredPath $DatabaseDataRoot "Database data root"
$output = [System.IO.Path]::GetFullPath($OutputDir)
New-Item -ItemType Directory -Force -Path $output | Out-Null

$npcStringPath = Join-Path $wz 'String.wz/Npc.img.xml'
$mapStringPath = Join-Path $wz 'String.wz/Map.img.xml'
$questCheckPath = Join-Path $wz 'Quest.wz/Check.img.xml'
$questInfoPath = Join-Path $wz 'Quest.wz/QuestInfo.img.xml'
$mapRoot = Join-Path $wz 'Map.wz/Map'
$npcAssetRoot = Join-Path $wz 'Npc.wz'

$npcNames = @{}
$npcTextPreviews = @{}
$npcTextCounts = @{}
$npcString = Read-Xml $npcStringPath
foreach ($entry in @($npcString.SelectNodes('/imgdir/imgdir[translate(@name,"0123456789","")=""]'))) {
    $npcId = [string]$entry.GetAttribute('name')
    $npcNames[$npcId] = Get-DirectString $entry 'name'
    $textNodes = @($entry.SelectNodes('./string[@name!="name" and @value]'))
    $npcTextCounts[$npcId] = $textNodes.Count
    $preview = ($textNodes | Select-Object -First 3 | ForEach-Object { $_.GetAttribute('value') }) -join ' | '
    if ($preview.Length -gt 600) { $preview = $preview.Substring(0, 600) + '…' }
    $npcTextPreviews[$npcId] = $preview
}

$mapNames = @{}
$mapString = Read-Xml $mapStringPath
foreach ($entry in @($mapString.SelectNodes('//imgdir[translate(@name,"0123456789","")=""]/string[@name="mapName"]'))) {
    $mapNames[[string]$entry.ParentNode.GetAttribute('name')] = [string]$entry.GetAttribute('value')
}

$allNpcIds = [System.Collections.Generic.HashSet[string]]::new()
foreach ($file in Get-ChildItem -LiteralPath $npcAssetRoot -File -Filter '*.img.xml') {
    [void] $allNpcIds.Add(($file.Name -replace '\.img\.xml$', ''))
}
foreach ($id in $npcNames.Keys) { [void] $allNpcIds.Add($id) }

$scriptText = @{}
foreach ($file in Get-ChildItem -LiteralPath $scripts -File -Filter '*.js') {
    if ($file.BaseName -notmatch '^\d+$') { continue }
    [void] $allNpcIds.Add($file.BaseName)
    $scriptText[$file.BaseName] = Get-Content -Raw -LiteralPath $file.FullName
}

$npcMaps = @{}
$npcPlacements = @{}
$placementRows = [System.Collections.Generic.List[object]]::new()
$mapTownFlags = @{}
$mapEventFlags = @{}
$eventMapPattern = '(?i)christmas|xmas|snow\s*man|frosty|happyville|halloween|valentine|anniversary|holiday|sheep ranch|leaving the event|\bevent\b'
$mapFiles = @(Get-ChildItem -LiteralPath $mapRoot -Recurse -File -Filter '*.img.xml' |
    Where-Object { $_.BaseName -match '^\d+\.img$' } | Sort-Object FullName)
foreach ($file in $mapFiles) {
    $mapId = $file.Name -replace '\.img\.xml$', ''
    $document = Read-Xml $file.FullName
    $info = $document.SelectSingleNode('//imgdir[@name="info"]')
    $townNode = if ($null -ne $info) { $info.SelectSingleNode('./int[@name="town"]') } else { $null }
    $mapTownFlags[$mapId] = $null -ne $townNode -and $townNode.GetAttribute('value') -eq '1'
    $mapMetadata = @(
        if ($mapNames.ContainsKey($mapId)) { $mapNames[$mapId] }
        if ($null -ne $info) { @($info.SelectNodes('./string[@value]') | ForEach-Object { $_.GetAttribute('value') }) }
    ) -join ' '
    $mapEventFlags[$mapId] = $mapMetadata -match $eventMapPattern
    foreach ($life in @($document.SelectNodes('/imgdir/imgdir[@name="life"]/imgdir[string[@name="type" and @value="n"]]'))) {
        $idNode = $life.SelectSingleNode('./string[@name="id"]')
        if ($null -eq $idNode) { continue }
        $npcId = [string]$idNode.GetAttribute('value')
        [void] $allNpcIds.Add($npcId)
        Add-SetValue $npcMaps $npcId $mapId
        if (!$npcPlacements.ContainsKey($npcId)) { $npcPlacements[$npcId] = [System.Collections.Generic.List[object]]::new() }
        $placement = [pscustomobject]@{
            npcId = $npcId
            name = if ($npcNames.ContainsKey($npcId)) { $npcNames[$npcId] } else { '' }
            mapId = $mapId
            mapName = if ($mapNames.ContainsKey($mapId)) { $mapNames[$mapId] } else { '' }
            isTown = [bool]$mapTownFlags[$mapId]
            isEventMap = [bool]$mapEventFlags[$mapId]
            isRegularTown = ([bool]$mapTownFlags[$mapId] -or $mapId -eq '910000000') -and ![bool]$mapEventFlags[$mapId]
            lifeKey = [string]$life.GetAttribute('name')
            x = Get-DirectValue $life 'x'
            y = Get-DirectValue $life 'y'
            cy = Get-DirectValue $life 'cy'
            fh = Get-DirectValue $life 'fh'
            rx0 = Get-DirectValue $life 'rx0'
            rx1 = Get-DirectValue $life 'rx1'
            flip = Get-DirectValue $life 'f'
            hide = Get-DirectValue $life 'hide'
        }
        $npcPlacements[$npcId].Add($placement)
        $placementRows.Add($placement)
    }
}

$questNames = @{}
$npcQuestMentions = @{}
$questInfo = Read-Xml $questInfoPath
foreach ($entry in @($questInfo.SelectNodes('/imgdir/imgdir'))) {
    $questId = [string]$entry.GetAttribute('name')
    $questNames[$questId] = Get-DirectString $entry 'name'
    foreach ($textNode in @($entry.SelectNodes('.//string[@value]'))) {
        foreach ($match in [regex]::Matches($textNode.GetAttribute('value'), '#p0*(\d{1,7})#', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)) {
            $npcId = $match.Groups[1].Value
            [void] $allNpcIds.Add($npcId)
            Add-SetValue $npcQuestMentions $npcId $questId
        }
    }
}

$npcQuests = @{}
$questEndDates = @{}
$questCheck = Read-Xml $questCheckPath
foreach ($quest in @($questCheck.SelectNodes('/imgdir/imgdir'))) {
    $questId = [string]$quest.GetAttribute('name')
    $ends = @($quest.SelectNodes('.//string[@name="end"]') | ForEach-Object { Get-DateValue $_.GetAttribute('value') } | Where-Object { $null -ne $_ })
    if ($ends.Count -gt 0) { $questEndDates[$questId] = ($ends | Sort-Object -Descending | Select-Object -First 1) }
    foreach ($npcNode in @($quest.SelectNodes('.//int[@name="npc"]'))) {
        $npcId = [string]$npcNode.GetAttribute('value')
        [void] $allNpcIds.Add($npcId)
        Add-SetValue $npcQuests $npcId $questId
    }
}

$npcShops = @{}
$shopItems = @{}
$shopsSql = Get-Content -Raw -LiteralPath (Join-Path $database '101-shops-data.sql')
foreach ($match in [regex]::Matches($shopsSql, '\((\d+)\s*,\s*(\d+)\)')) {
    $shopId = $match.Groups[1].Value
    $npcId = $match.Groups[2].Value
    [void] $allNpcIds.Add($npcId)
    Add-SetValue $npcShops $npcId $shopId
}
$shopItemsSql = Get-Content -Raw -LiteralPath (Join-Path $database '102-shopitems-data.sql')
foreach ($match in [regex]::Matches($shopItemsSql, '\((\d+)\s*,\s*(\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\)')) {
    $shopId = $match.Groups[1].Value
    if (!$shopItems.ContainsKey($shopId)) { $shopItems[$shopId] = [System.Collections.Generic.List[object]]::new() }
    $shopItems[$shopId].Add([pscustomobject]@{
        itemId = $match.Groups[2].Value
        price = [int]$match.Groups[3].Value
        pitch = [int]$match.Groups[4].Value
    })
}

$seasonalPattern = '(?i)christmas|xmas|snow\s*man|frosty|santa|rudolph|happyville|halloween|valentine|anniversary|thanksgiving|easter|holiday|perfect pitch|maple administrator|cassandra|\bevent\b'
$today = (Get-Date).Date
$rows = [System.Collections.Generic.List[object]]::new()

foreach ($npcId in @($allNpcIds | Sort-Object { [long]$_ })) {
    $name = if ($npcNames.ContainsKey($npcId)) { [string]$npcNames[$npcId] } else { '' }
    $maps = if ($npcMaps.ContainsKey($npcId)) { @($npcMaps[$npcId] | Sort-Object) } else { @() }
    $directQuests = @()
    if ($npcQuests.ContainsKey($npcId)) {
        foreach ($questValue in $npcQuests[$npcId]) { $directQuests += [string]$questValue }
    }
    $mentionedQuests = @()
    if ($npcQuestMentions.ContainsKey($npcId)) {
        foreach ($questValue in $npcQuestMentions[$npcId]) { $mentionedQuests += [string]$questValue }
    }
    $quests = @(($directQuests + $mentionedQuests) | Sort-Object { [int]$_ } -Unique)
    $shopsForNpc = if ($npcShops.ContainsKey($npcId)) { @($npcShops[$npcId] | Sort-Object) } else { @() }
    $script = if ($scriptText.ContainsKey($npcId)) { [string]$scriptText[$npcId] } else { '' }

    $hasScript = $script.Length -gt 0
    $hasWarp = $hasScript -and $script -match '(?i)\b(?:warp|changeMap|forceStartWarp|playPortalSE)\s*\('
    $hasScriptShop = $hasScript -and $script -match '(?i)\b(?:openShop|openNpcShop)\s*\('
    $hasReward = $hasScript -and $script -match '(?i)\b(?:gainItem|gainMeso|gainExp|gainCloseness|addItem)\s*\('
    $hasEventRuntime = $hasScript -and $script -match '(?i)getEventManager|EventInstance|startEvent|eventMap'
    $hasShop = $shopsForNpc.Count -gt 0 -or $hasScriptShop

    $shopItemCount = 0
    $pitchItemCount = 0
    $shopItemDetails = [System.Collections.Generic.List[string]]::new()
    foreach ($shopId in $shopsForNpc) {
        if (!$shopItems.ContainsKey($shopId)) { continue }
        $shopItemCount += $shopItems[$shopId].Count
        $pitchItemCount += @($shopItems[$shopId] | Where-Object pitch -gt 0).Count
        foreach ($shopItem in $shopItems[$shopId]) {
            $cost = if ($shopItem.price -gt 0) { "$($shopItem.price) mesos" } elseif ($shopItem.pitch -gt 0) { "$($shopItem.pitch) Perfect Pitch" } else { 'recharge-only/zero' }
            $shopItemDetails.Add("$($shopItem.itemId) ($cost)")
        }
    }

    $questLabels = foreach ($questId in $quests) {
        $questName = if ($questNames.ContainsKey($questId)) { [string]$questNames[$questId] } else { '' }
        if ($questName) { "$questId`:$questName" } else { $questId }
    }
    $expiredQuestCount = @($quests | Where-Object { $questEndDates.ContainsKey($_) -and $questEndDates[$_] -lt $today }).Count
    $undatedQuestCount = @($quests | Where-Object { !$questEndDates.ContainsKey($_) }).Count
    $eventQuestCount = @($quests | Where-Object {
        ($questNames.ContainsKey($_) -and [string]$questNames[$_] -match $seasonalPattern) -or
        ($questEndDates.ContainsKey($_) -and $questEndDates[$_] -lt $today)
    }).Count

    $mapLabels = foreach ($mapId in $maps) {
        $mapName = if ($mapNames.ContainsKey($mapId)) { [string]$mapNames[$mapId] } else { '' }
        if ($mapName) { "$mapId`:$mapName" } else { $mapId }
    }
    $regularTownMaps = @($maps | Where-Object {
        (($mapTownFlags.ContainsKey($_) -and $mapTownFlags[$_]) -or $_ -eq '910000000') -and
        !($mapEventFlags.ContainsKey($_) -and $mapEventFlags[$_])
    })
    $eventMaps = @($maps | Where-Object { $mapEventFlags.ContainsKey($_) -and $mapEventFlags[$_] })
    $regularTownMapLabels = foreach ($mapId in $regularTownMaps) {
        $mapName = if ($mapNames.ContainsKey($mapId)) { [string]$mapNames[$mapId] } else { '' }
        if ($mapName) { "$mapId`:$mapName" } else { $mapId }
    }
    $eventMapLabels = foreach ($mapId in $eventMaps) {
        $mapName = if ($mapNames.ContainsKey($mapId)) { [string]$mapNames[$mapId] } else { '' }
        if ($mapName) { "$mapId`:$mapName" } else { $mapId }
    }
    $seasonalName = $name -match $seasonalPattern
    $seasonalMap = @($mapLabels | Where-Object { $_ -match $seasonalPattern }).Count -gt 0
    $seasonalScript = $hasScript -and $script -match '(?i)Perfect Pitch|4310000'
    $eventEvidence = $seasonalName -or $seasonalMap -or $seasonalScript -or $eventQuestCount -gt 0

    $functional = $quests.Count -gt 0 -or $hasShop -or $hasWarp -or $hasReward -or $hasEventRuntime
    if ($eventEvidence -and $functional) {
        $recommendation = 'REVIEW_EVENT_FUNCTIONAL'
        $confidence = 'medium'
    } elseif ($eventEvidence -and $maps.Count -gt 0) {
        $recommendation = 'REVIEW_EVENT_PLACEMENT'
        $confidence = 'medium'
    } elseif ($maps.Count -gt 0 -and !$functional -and !$hasScript) {
        $recommendation = 'REVIEW_NO_FUNCTION'
        $confidence = 'medium'
    } elseif ($maps.Count -gt 0 -and !$functional) {
        $recommendation = 'REVIEW_DIALOGUE_ONLY'
        $confidence = 'medium'
    } elseif ($maps.Count -eq 0 -and !$functional -and !$hasScript) {
        $recommendation = 'UNUSED_ASSET'
        $confidence = 'high'
    } elseif ($maps.Count -eq 0 -and $hasScript -and !$functional) {
        $recommendation = 'REVIEW_UNPLACED_SCRIPT'
        $confidence = 'medium'
    } else {
        $recommendation = 'KEEP_FUNCTIONAL'
        $confidence = 'high'
    }

    $purposeFlags = @()
    if ($quests.Count -gt 0) { $purposeFlags += 'quest' }
    if ($hasShop) { $purposeFlags += 'shop' }
    if ($hasWarp) { $purposeFlags += 'warp' }
    if ($hasReward) { $purposeFlags += 'exchange/reward' }
    if ($hasEventRuntime) { $purposeFlags += 'event-runtime' }
    if ($hasScript -and $purposeFlags.Count -eq 0) { $purposeFlags += 'dialogue/other-script' }

    $rows.Add([pscustomobject]@{
        npcId = [int64]$npcId
        name = $name
        recommendation = $recommendation
        confidence = $confidence
        purpose = ($purposeFlags -join '; ')
        placed = $maps.Count -gt 0
        mapCount = $maps.Count
        placementCount = if ($npcPlacements.ContainsKey($npcId)) { $npcPlacements[$npcId].Count } else { 0 }
        maps = ($mapLabels -join '; ')
        regularTownPlacement = $regularTownMaps.Count -gt 0
        regularTownMapCount = $regularTownMaps.Count
        regularTownMaps = ($regularTownMapLabels -join '; ')
        eventMapCount = $eventMaps.Count
        eventMaps = ($eventMapLabels -join '; ')
        hasScript = $hasScript
        scriptPath = if ($hasScript) { "scripts/npc/$npcId.js" } else { '' }
        hasWarp = $hasWarp
        hasRewardOrExchange = $hasReward
        questCount = $quests.Count
        directQuestCount = $directQuests.Count
        mentionedQuestCount = $mentionedQuests.Count
        quests = ($questLabels -join '; ')
        expiredQuestCount = $expiredQuestCount
        undatedQuestCount = $undatedQuestCount
        eventQuestCount = $eventQuestCount
        hasShop = $hasShop
        shopIds = ($shopsForNpc -join '; ')
        shopItemCount = $shopItemCount
        shopItems = ($shopItemDetails -join '; ')
        perfectPitchItemCount = $pitchItemCount
        npcTextCount = if ($npcTextCounts.ContainsKey($npcId)) { $npcTextCounts[$npcId] } else { 0 }
        npcTextPreview = if ($npcTextPreviews.ContainsKey($npcId)) { $npcTextPreviews[$npcId] } else { '' }
        eventEvidence = $eventEvidence
        eventEvidenceDetail = (@(
            if ($seasonalName) { 'name' }
            if ($seasonalMap) { 'map' }
            if ($seasonalScript) { 'script' }
            if ($eventQuestCount -gt 0) { "event-or-expired-quests=$eventQuestCount" }
        ) -join '; ')
    })
}

$allPath = Join-Path $output 'npc-relevance-all.csv'
$reviewPath = Join-Path $output 'npc-relevance-review.csv'
$placedReviewPath = Join-Path $output 'npc-placed-review.csv'
$eventReviewPath = Join-Path $output 'npc-event-review.csv'
$regularTownReviewPath = Join-Path $output 'npc-regular-town-review.csv'
$placementsPath = Join-Path $output 'npc-map-placements.csv'
$summaryPath = Join-Path $output 'summary.json'
$rows | Export-Csv -LiteralPath $allPath -NoTypeInformation -Encoding UTF8
$rows | Where-Object recommendation -ne 'KEEP_FUNCTIONAL' | Export-Csv -LiteralPath $reviewPath -NoTypeInformation -Encoding UTF8
$rows | Where-Object { $_.placed -and $_.recommendation -ne 'KEEP_FUNCTIONAL' } |
    Export-Csv -LiteralPath $placedReviewPath -NoTypeInformation -Encoding UTF8
$rows | Where-Object eventEvidence | Export-Csv -LiteralPath $eventReviewPath -NoTypeInformation -Encoding UTF8
$rows | Where-Object { $_.regularTownPlacement -and $_.recommendation -ne 'KEEP_FUNCTIONAL' } |
    Export-Csv -LiteralPath $regularTownReviewPath -NoTypeInformation -Encoding UTF8
$placementRows | Export-Csv -LiteralPath $placementsPath -NoTypeInformation -Encoding UTF8

$summary = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString('o')
    npcCount = $rows.Count
    placedNpcCount = @($rows | Where-Object placed).Count
    scriptedNpcCount = @($rows | Where-Object hasScript).Count
    questNpcCount = @($rows | Where-Object questCount -gt 0).Count
    shopNpcCount = @($rows | Where-Object hasShop).Count
    warpNpcCount = @($rows | Where-Object hasWarp).Count
    eventEvidenceNpcCount = @($rows | Where-Object eventEvidence).Count
    recommendations = @($rows | Group-Object recommendation | Sort-Object Name | ForEach-Object {
        [ordered]@{ recommendation = $_.Name; count = $_.Count }
    })
    outputs = [ordered]@{
        all = $allPath
        review = $reviewPath
        placedReview = $placedReviewPath
        eventReview = $eventReviewPath
        regularTownReview = $regularTownReviewPath
        placements = $placementsPath
    }
}
$summary | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $summaryPath -Encoding UTF8
$summary | ConvertTo-Json -Depth 6
