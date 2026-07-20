param(
    [string] $NpcCatalogPath,
    [string] $QuestStatusCatalogPath,
    [string] $CatalogPath,
    [string] $OutputDir,
    [ValidateRange(1, 2000)]
    [int] $DefaultRadiusPx = 180,
    [switch] $AllQuestLevels,
    [switch] $ResetAdjustments,
    [switch] $CatalogOnly
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
if (!$NpcCatalogPath) {
    $NpcCatalogPath = Join-Path $repoRoot "tmp/npc-catalog/generated_npc_catalog.json"
}
if (!$QuestStatusCatalogPath) {
    $QuestStatusCatalogPath = Join-Path $repoRoot "docs/agents/catalog-overrides/victoria-lt30-quest-status.catalog.json"
}
if (!$CatalogPath) {
    $CatalogPath = Join-Path $repoRoot "docs/agents/catalog-overrides/victoria-quest-npc-interaction-circles.json"
}
if (!$OutputDir) {
    $OutputDir = Join-Path $repoRoot "docs/agents/images/victoria-quest-npc-radii"
}

# Victoria mainland and its directly associated v83 areas. Ereve (130),
# Rien (140), Aran's past (108), and event maps (109) are intentionally absent.
$victoriaMapPrefixes = @(100, 101, 102, 103, 104, 105, 106, 107, 110, 120)

function Get-PlacementKey {
    param([int] $MapId, [string] $LifeIndex, [int] $NpcId)
    return "$MapId`:$LifeIndex`:$NpcId"
}

function Get-ExistingAdjustments {
    $result = @{}
    if ($ResetAdjustments -or !(Test-Path -LiteralPath $CatalogPath)) {
        return $result
    }
    $existing = Get-Content -Raw -LiteralPath $CatalogPath | ConvertFrom-Json
    foreach ($entry in @($existing.entries)) {
        $result[[string] $entry.placementKey] = $entry
    }
    return $result
}

if (!(Test-Path -LiteralPath $NpcCatalogPath)) {
    throw "NPC catalog is missing: $NpcCatalogPath`nRun tools/npc-catalog/Export-NpcCatalog.ps1 first."
}
if (!$AllQuestLevels -and !(Test-Path -LiteralPath $QuestStatusCatalogPath)) {
    throw "Victoria level-30 quest status catalog is missing: $QuestStatusCatalogPath"
}

$npcCatalog = Get-Content -Raw -LiteralPath $NpcCatalogPath | ConvertFrom-Json
$existingAdjustments = Get-ExistingAdjustments
$questLinksByNpcMap = @{}
$scopedQuestCount = 0
if (!$AllQuestLevels) {
    $questStatusCatalog = Get-Content -Raw -LiteralPath $QuestStatusCatalogPath | ConvertFrom-Json
    $scopedQuestCount = @($questStatusCatalog.quests).Count
    foreach ($quest in @($questStatusCatalog.quests)) {
        foreach ($role in @("start", "complete")) {
            $endpoint = $quest.$role
            if ($null -eq $endpoint -or $null -eq $endpoint.npcId) {
                continue
            }
            foreach ($mapIdValue in @($endpoint.victoriaMaps)) {
                $linkKey = "$([int] $endpoint.npcId):$([int] $mapIdValue)"
                if (!$questLinksByNpcMap.ContainsKey($linkKey)) {
                    $questLinksByNpcMap[$linkKey] = [ordered] @{
                        starts = [System.Collections.Generic.HashSet[int]]::new()
                        completes = [System.Collections.Generic.HashSet[int]]::new()
                    }
                }
                $collectionName = if ($role -eq "start") { "starts" } else { "completes" }
                $null = $questLinksByNpcMap[$linkKey][$collectionName].Add([int] $quest.questId)
            }
        }
    }
}
$entries = @(
    foreach ($npc in @($npcCatalog)) {
        $allStarts = @($npc.interactions.quests.starts | ForEach-Object { [int] $_ } | Sort-Object -Unique)
        $allCompletes = @($npc.interactions.quests.completes | ForEach-Object { [int] $_ } | Sort-Object -Unique)
        if ($allStarts.Count -eq 0 -and $allCompletes.Count -eq 0) {
            continue
        }

        foreach ($placement in @($npc.placements)) {
            $mapId = [int] $placement.mapId
            $mapPrefix = [int] [math]::Floor($mapId / 1000000)
            if ($mapPrefix -notin $victoriaMapPrefixes) {
                continue
            }

            if ($AllQuestLevels) {
                $starts = $allStarts
                $completes = $allCompletes
            } else {
                $linkKey = "$([int] $npc.npcId):$mapId"
                $scopedLinks = $questLinksByNpcMap[$linkKey]
                if ($null -eq $scopedLinks) {
                    continue
                }
                $starts = @($scopedLinks.starts | Sort-Object)
                $completes = @($scopedLinks.completes | Sort-Object)
            }

            $lifeIndex = [string] $placement.lifeIndex
            $placementKey = Get-PlacementKey $mapId $lifeIndex ([int] $npc.npcId)
            $existing = $existingAdjustments[$placementKey]
            $offsetX = if ($null -ne $existing) { [int] $existing.centerOffset.x } else { 0 }
            $offsetY = if ($null -ne $existing) { [int] $existing.centerOffset.y } else { 0 }
            $radiusPx = if ($null -ne $existing) { [int] $existing.radiusPx } else { $DefaultRadiusPx }
            $dynamicSpread = if ($null -ne $existing -and $null -ne $existing.dynamicSpread) {
                [bool] $existing.dynamicSpread
            } else {
                $true
            }
            $notes = if ($null -ne $existing -and $null -ne $existing.notes) {
                [string] $existing.notes
            } else {
                ""
            }

            [pscustomobject] [ordered] @{
                placementKey = $placementKey
                mapId = $mapId
                mapName = [string] $placement.mapName
                streetName = [string] $placement.streetName
                lifeIndex = $lifeIndex
                npcId = [int] $npc.npcId
                npcName = [string] $npc.name
                npcPosition = [ordered] @{
                    x = [int] $placement.x
                    y = [int] $placement.y
                    footholdId = [int] $placement.footholdId
                }
                centerOffset = [ordered] @{
                    x = $offsetX
                    y = $offsetY
                }
                radiusPx = $radiusPx
                dynamicSpread = $dynamicSpread
                questLinks = [ordered] @{
                    starts = $starts.Count
                    completes = $completes.Count
                    startQuestIds = $starts
                    completeQuestIds = $completes
                }
                notes = $notes
            }
        }
    }
)
$entries = @($entries | Sort-Object mapId, { [int] $_.lifeIndex }, npcId)

$document = [ordered] @{
    schemaVersion = 1
    description = if ($AllQuestLevels) {
        "Adjustable cohort interaction circles for every quest-linked NPC placement in the Victoria Island scope."
    } else {
        "Adjustable cohort interaction circles for Victoria Island quests in the level-30-and-below progression scope."
    }
    scope = [ordered] @{
        questScope = if ($AllQuestLevels) { "all quest levels" } else { "Victoria level-30-and-below status catalog" }
        questCount = if ($AllQuestLevels) { $null } else { $scopedQuestCount }
        includedMapPrefixes = $victoriaMapPrefixes
        includedAreas = @(
            "Victoria mainland towns, roads, and dungeons",
            "Mushroom Castle",
            "Hut in the Swamp",
            "Florina Beach",
            "The Nautilus"
        )
        excludedAreas = @("Maple Island", "Ereve", "Rien", "Aran's Past", "event maps")
    }
    defaults = [ordered] @{
        centerOffset = [ordered] @{ x = 0; y = 0 }
        radiusPx = $DefaultRadiusPx
        dynamicSpread = $true
    }
    placementCount = $entries.Count
    mapCount = @($entries.mapId | Sort-Object -Unique).Count
    entries = $entries
}

$catalogParent = Split-Path -Parent $CatalogPath
New-Item -ItemType Directory -Force -Path $catalogParent | Out-Null
$document | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 -LiteralPath $CatalogPath
Write-Host "Victoria quest NPC circle catalog: $CatalogPath"
Write-Host "  placements=$($entries.Count) maps=$($document.mapCount)"

if ($CatalogOnly) {
    return
}

Add-Type -AssemblyName System.Drawing

function Get-MapXmlPath {
    param([int] $MapId)
    $padded = $MapId.ToString("000000000")
    return Join-Path $repoRoot "wz/Map.wz/Map/Map$($padded.Substring(0, 1))/$padded.img.xml"
}

function Get-ChildValue {
    param([System.Xml.XmlNode] $Node, [string] $Name)
    foreach ($child in $Node.ChildNodes) {
        if ($child.Attributes["name"] -and $child.Attributes["name"].Value -eq $Name) {
            return $child.Attributes["value"].Value
        }
    }
    return $null
}

function Get-MapGeometry {
    param([int] $MapId)
    $mapPath = Get-MapXmlPath $MapId
    if (!(Test-Path -LiteralPath $mapPath)) {
        throw "Map XML is missing for map $MapId`: $mapPath"
    }
    [xml] $mapXml = Get-Content -Raw -LiteralPath $mapPath
    $root = $mapXml.DocumentElement

    $footholds = [System.Collections.Generic.List[object]]::new()
    $footholdRoot = @($root.ChildNodes | Where-Object {
        $_.LocalName -eq "imgdir" -and $_.Attributes["name"].Value -eq "foothold"
    }) | Select-Object -First 1
    if ($footholdRoot) {
        foreach ($node in $footholdRoot.SelectNodes(".//imgdir")) {
            $x1 = Get-ChildValue $node "x1"
            $y1 = Get-ChildValue $node "y1"
            $x2 = Get-ChildValue $node "x2"
            $y2 = Get-ChildValue $node "y2"
            if ($null -ne $x1 -and $null -ne $y1 -and $null -ne $x2 -and $null -ne $y2) {
                $footholds.Add(@([int] $x1, [int] $y1, [int] $x2, [int] $y2))
            }
        }
    }

    $ropes = [System.Collections.Generic.List[object]]::new()
    $ladderRoot = @($root.ChildNodes | Where-Object {
        $_.LocalName -eq "imgdir" -and $_.Attributes["name"].Value -eq "ladderRope"
    }) | Select-Object -First 1
    if ($ladderRoot) {
        foreach ($node in $ladderRoot.ChildNodes) {
            $x = Get-ChildValue $node "x"
            $y1 = Get-ChildValue $node "y1"
            $y2 = Get-ChildValue $node "y2"
            if ($null -ne $x -and $null -ne $y1 -and $null -ne $y2) {
                $ropes.Add(@([int] $x, [int] $y1, [int] $x, [int] $y2))
            }
        }
    }

    $portals = [System.Collections.Generic.List[object]]::new()
    $portalRoot = @($root.ChildNodes | Where-Object {
        $_.LocalName -eq "imgdir" -and $_.Attributes["name"].Value -eq "portal"
    }) | Select-Object -First 1
    if ($portalRoot) {
        foreach ($node in $portalRoot.ChildNodes) {
            $portalType = Get-ChildValue $node "pt"
            $targetMapId = Get-ChildValue $node "tm"
            $x = Get-ChildValue $node "x"
            $y = Get-ChildValue $node "y"
            if ($null -eq $x -or $null -eq $y) {
                continue
            }
            $target = if ($null -eq $targetMapId) { 999999999 } else { [int] $targetMapId }
            $type = if ($null -eq $portalType) { -1 } else { [int] $portalType }
            if ($target -eq 999999999 -and $type -notin @(1, 7)) {
                continue
            }
            $portals.Add([ordered] @{
                name = [string] (Get-ChildValue $node "pn")
                x = [int] $x
                y = [int] $y
                targetMapId = $target
                arrival = $target -eq 999999999
            })
        }
    }

    return [ordered] @{
        footholds = @($footholds)
        ropes = @($ropes)
        portals = @($portals)
    }
}

function New-Pen {
    param([System.Drawing.Color] $Color, [float] $Width)
    return [System.Drawing.Pen]::new($Color, $Width)
}

function Get-Slug {
    param([string] $Value)
    $slug = $Value.ToLowerInvariant() -replace "[^a-z0-9]+", "-"
    return $slug.Trim("-")
}

function Save-MapPanel {
    param([int] $MapId, [object[]] $MapEntries, [object] $Geometry, [string] $Path)

    $width = 2200
    $height = 1300
    $plotLeft = 60
    $plotTop = 130
    $plotWidth = 1480
    $plotHeight = 1090
    $legendLeft = 1580

    $allX = [System.Collections.Generic.List[double]]::new()
    $allY = [System.Collections.Generic.List[double]]::new()
    foreach ($line in @($Geometry.footholds) + @($Geometry.ropes)) {
        $allX.Add([double] $line[0]); $allX.Add([double] $line[2])
        $allY.Add([double] $line[1]); $allY.Add([double] $line[3])
    }
    foreach ($portal in @($Geometry.portals)) {
        $allX.Add([double] $portal.x); $allY.Add([double] $portal.y)
    }
    foreach ($entry in $MapEntries) {
        $cx = [int] $entry.npcPosition.x + [int] $entry.centerOffset.x
        $cy = [int] $entry.npcPosition.y + [int] $entry.centerOffset.y
        $radius = [int] $entry.radiusPx
        $allX.Add($cx - $radius); $allX.Add($cx + $radius)
        $allY.Add($cy - $radius); $allY.Add($cy + $radius)
    }
    if ($allX.Count -eq 0) {
        $allX.Add(0); $allX.Add(1000); $allY.Add(0); $allY.Add(600)
    }

    $xMin = ($allX | Measure-Object -Minimum).Minimum
    $xMax = ($allX | Measure-Object -Maximum).Maximum
    $yMin = ($allY | Measure-Object -Minimum).Minimum
    $yMax = ($allY | Measure-Object -Maximum).Maximum
    $xPad = [math]::Max(40, ($xMax - $xMin) * 0.03)
    $yPad = [math]::Max(40, ($yMax - $yMin) * 0.06)
    $xMin -= $xPad; $xMax += $xPad; $yMin -= $yPad; $yMax += $yPad
    $scale = [math]::Min($plotWidth / [math]::Max(1, $xMax - $xMin), $plotHeight / [math]::Max(1, $yMax - $yMin))
    $usedWidth = ($xMax - $xMin) * $scale
    $usedHeight = ($yMax - $yMin) * $scale
    $xOffset = $plotLeft + (($plotWidth - $usedWidth) / 2)
    $yOffset = $plotTop + (($plotHeight - $usedHeight) / 2)

    $projectX = { param($X) [single] ($xOffset + (($X - $xMin) * $scale)) }
    $projectY = { param($Y) [single] ($yOffset + (($Y - $yMin) * $scale)) }

    $bitmap = [System.Drawing.Bitmap]::new($width, $height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.Clear([System.Drawing.Color]::FromArgb(244, 246, 248))

    $titleFont = [System.Drawing.Font]::new("Segoe UI", 28, [System.Drawing.FontStyle]::Bold)
    $headingFont = [System.Drawing.Font]::new("Segoe UI", 15, [System.Drawing.FontStyle]::Bold)
    $bodyFont = [System.Drawing.Font]::new("Segoe UI", 13)
    $smallFont = [System.Drawing.Font]::new("Segoe UI", 11)
    $darkBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(24, 35, 47))
    $mutedBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(79, 93, 107))
    $footholdPen = New-Pen ([System.Drawing.Color]::FromArgb(41, 62, 82)) 4
    $ropePen = New-Pen ([System.Drawing.Color]::FromArgb(155, 107, 53)) 3
    $portalPen = New-Pen ([System.Drawing.Color]::FromArgb(154, 91, 0)) 2
    $portalBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(245, 158, 11))

    $mapName = [string] $MapEntries[0].mapName
    $streetName = [string] $MapEntries[0].streetName
    $graphics.DrawString("$mapName  -  $MapId", $titleFont, $darkBrush, 60, 28)
    $graphics.DrawString("$streetName  |  $($MapEntries.Count) quest NPC placement(s)  |  scale $([math]::Round($scale, 2)) image px/game px", $bodyFont, $mutedBrush, 63, 82)

    foreach ($line in @($Geometry.footholds)) {
        $graphics.DrawLine($footholdPen, (& $projectX $line[0]), (& $projectY $line[1]), (& $projectX $line[2]), (& $projectY $line[3]))
    }
    foreach ($line in @($Geometry.ropes)) {
        $graphics.DrawLine($ropePen, (& $projectX $line[0]), (& $projectY $line[1]), (& $projectX $line[2]), (& $projectY $line[3]))
    }
    foreach ($portal in @($Geometry.portals)) {
        $px = & $projectX $portal.x
        $py = & $projectY $portal.y
        $points = [System.Drawing.PointF[]] @(
            [System.Drawing.PointF]::new($px, $py - 10),
            [System.Drawing.PointF]::new($px + 10, $py),
            [System.Drawing.PointF]::new($px, $py + 10),
            [System.Drawing.PointF]::new($px - 10, $py)
        )
        if ($portal.arrival) {
            $graphics.DrawPolygon($portalPen, $points)
        } else {
            $graphics.FillPolygon($portalBrush, $points)
            $graphics.DrawPolygon($portalPen, $points)
        }
    }

    $colors = @(
        [System.Drawing.Color]::FromArgb(30, 136, 229),
        [System.Drawing.Color]::FromArgb(236, 88, 64),
        [System.Drawing.Color]::FromArgb(117, 86, 201),
        [System.Drawing.Color]::FromArgb(13, 148, 136),
        [System.Drawing.Color]::FromArgb(230, 126, 34),
        [System.Drawing.Color]::FromArgb(190, 45, 110)
    )

    $graphics.DrawString("Editable interaction circles", $headingFont, $darkBrush, $legendLeft, 42)
    $graphics.DrawString("placement key = map:life:npc", $smallFont, $mutedBrush, $legendLeft, 78)
    for ($index = 0; $index -lt $MapEntries.Count; $index++) {
        $entry = $MapEntries[$index]
        $color = $colors[$index % $colors.Count]
        $circlePen = New-Pen ([System.Drawing.Color]::FromArgb(225, $color.R, $color.G, $color.B)) 4
        $circleBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(36, $color.R, $color.G, $color.B))
        $markerBrush = [System.Drawing.SolidBrush]::new($color)

        $npcX = & $projectX ([int] $entry.npcPosition.x)
        $npcY = & $projectY ([int] $entry.npcPosition.y)
        $centerX = & $projectX ([int] $entry.npcPosition.x + [int] $entry.centerOffset.x)
        $centerY = & $projectY ([int] $entry.npcPosition.y + [int] $entry.centerOffset.y)
        $radius = [single] ([int] $entry.radiusPx * $scale)
        $graphics.FillEllipse($circleBrush, $centerX - $radius, $centerY - $radius, 2 * $radius, 2 * $radius)
        $graphics.DrawEllipse($circlePen, $centerX - $radius, $centerY - $radius, 2 * $radius, 2 * $radius)
        $graphics.FillEllipse($markerBrush, $npcX - 8, $npcY - 8, 16, 16)
        $graphics.DrawString("$($index + 1)", $headingFont, $markerBrush, $npcX + 9, $npcY - 14)

        $rowY = 120 + ($index * 55)
        $graphics.FillEllipse($markerBrush, $legendLeft, $rowY + 6, 18, 18)
        $graphics.DrawString("#$($index + 1) $($entry.npcName)", $headingFont, $darkBrush, $legendLeft + 26, $rowY)
        $detail = "$($entry.placementKey)  offset=($([int]$entry.centerOffset.x),$([int]$entry.centerOffset.y))  r=$($entry.radiusPx)  quests S$($entry.questLinks.starts)/C$($entry.questLinks.completes)"
        $graphics.DrawString($detail, $smallFont, $mutedBrush, $legendLeft + 26, $rowY + 27)

        $circlePen.Dispose(); $circleBrush.Dispose(); $markerBrush.Dispose()
    }

    $graphics.DrawString("circle = cohort spread radius   dot = NPC   orange diamond = exit   hollow diamond = arrival   brown = ladder/rope", $smallFont, $mutedBrush, 64, 1260)
    $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)

    $graphics.Dispose(); $bitmap.Dispose(); $titleFont.Dispose(); $headingFont.Dispose(); $bodyFont.Dispose(); $smallFont.Dispose()
    $darkBrush.Dispose(); $mutedBrush.Dispose(); $footholdPen.Dispose(); $ropePen.Dispose(); $portalPen.Dispose(); $portalBrush.Dispose()
}

function Save-ContactSheet {
    param([string] $Title, [object[]] $Items, [string] $Path)

    $columns = 2
    $tileWidth = 1100
    $tileHeight = 650
    $headerHeight = 90
    $rows = [math]::Ceiling($Items.Count / $columns)
    $bitmap = [System.Drawing.Bitmap]::new($columns * $tileWidth, $headerHeight + ($rows * $tileHeight))
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([System.Drawing.Color]::FromArgb(244, 246, 248))
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $titleFont = [System.Drawing.Font]::new("Segoe UI", 26, [System.Drawing.FontStyle]::Bold)
    $titleBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(24, 35, 47))
    $graphics.DrawString($Title, $titleFont, $titleBrush, 28, 24)

    for ($index = 0; $index -lt $Items.Count; $index++) {
        $column = $index % $columns
        $row = [math]::Floor($index / $columns)
        $source = [System.Drawing.Image]::FromFile([string] $Items[$index].path)
        try {
            $destination = [System.Drawing.Rectangle]::new(
                $column * $tileWidth,
                $headerHeight + ($row * $tileHeight),
                $tileWidth,
                $tileHeight)
            $graphics.DrawImage($source, $destination)
        } finally {
            $source.Dispose()
        }
    }

    $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    $titleFont.Dispose(); $titleBrush.Dispose(); $graphics.Dispose(); $bitmap.Dispose()
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
Get-ChildItem -LiteralPath $OutputDir -Filter "*.png" -File -ErrorAction SilentlyContinue | Remove-Item -Force

$indexRows = [System.Collections.Generic.List[string]]::new()
$indexRows.Add($(if ($AllQuestLevels) { "# Victoria Quest NPC Interaction Circles" } else { "# Victoria Level-30-and-Below Quest NPC Interaction Circles" }))
$indexRows.Add("")
$indexRows.Add("Generated from ``docs/agents/catalog-overrides/victoria-quest-npc-interaction-circles.json``.")
$indexRows.Add("")
$indexRows.Add("| Map | Area | Quest NPC placements | Diagram |")
$indexRows.Add("| ---: | --- | ---: | --- |")
$renderedMaps = [System.Collections.Generic.List[object]]::new()

foreach ($mapGroup in @($entries | Group-Object mapId | Sort-Object { [int] $_.Name })) {
    $mapId = [int] $mapGroup.Name
    $mapEntries = @($mapGroup.Group)
    $geometry = Get-MapGeometry $mapId
    $slug = Get-Slug ([string] $mapEntries[0].mapName)
    $fileName = "$($mapId.ToString('000000000'))-$slug.png"
    $renderedPath = Join-Path $OutputDir $fileName
    Save-MapPanel $mapId $mapEntries $geometry $renderedPath
    $renderedMaps.Add([pscustomobject] @{ mapId = $mapId; path = $renderedPath })
    $escapedName = ([string] $mapEntries[0].mapName).Replace("|", "\|")
    $indexRows.Add("| $mapId | $escapedName | $($mapEntries.Count) | [$fileName]($fileName) |")
}

$contactSheets = @(
    [pscustomobject] @{ title = "Victoria level-30 quest NPC circles - Henesys and Ellinia"; file = "overview-1-henesys-ellinia.png"; prefixes = @(100, 101) },
    [pscustomobject] @{ title = "Victoria level-30 quest NPC circles - Perion and Kerning City"; file = "overview-2-perion-kerning.png"; prefixes = @(102, 103) },
    [pscustomobject] @{ title = "Victoria level-30 quest NPC circles - Lith Harbor and Dungeon"; file = "overview-3-lith-dungeon.png"; prefixes = @(104, 105, 106, 107, 110) },
    [pscustomobject] @{ title = "Victoria level-30 quest NPC circles - The Nautilus"; file = "overview-4-nautilus.png"; prefixes = @(120) }
)
$indexRows.Add("")
$indexRows.Add("## Contact sheets")
$indexRows.Add("")
foreach ($sheet in $contactSheets) {
    $sheetItems = @($renderedMaps | Where-Object {
        [int] [math]::Floor(([int] $_.mapId) / 1000000) -in $sheet.prefixes
    })
    if ($sheetItems.Count -eq 0) {
        continue
    }
    Save-ContactSheet $sheet.title $sheetItems (Join-Path $OutputDir $sheet.file)
    $indexRows.Add("- [$($sheet.title)]($($sheet.file))")
}

$indexRows | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $OutputDir "README.md")
Write-Host "Rendered $($document.mapCount) map diagrams and $($contactSheets.Count) contact sheets: $OutputDir"
