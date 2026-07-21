param(
    [string] $WzRoot = "wz",
    [string] $GameCatalogDir = "tmp/game-catalog",
    [string] $NpcCatalogDir = "tmp/npc-catalog",
    [string] $AgentCatalogDir = "tmp/agent-llm-catalog",
    [string] $OutputDir = "tmp/agent-llm-catalog",
    [string[]] $Regions = @('maple', 'victoria'),
    [switch] $AllRegions,
    [int] $MapLimit = 0,
    [int] $EquipmentFileLimit = 0,
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Read-JsonArray {
    param([string] $Path)
    if (!(Test-Path -LiteralPath $Path)) { return @() }
    $value = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    if ($null -eq $value) { return @() }
    return @($value)
}

function New-ObjectList { return ,(New-Object System.Collections.ArrayList) }

function Get-Attr {
    param([System.Xml.XmlNode] $Node, [string] $Name)
    if ($null -eq $Node -or $null -eq $Node.Attributes -or $null -eq $Node.Attributes[$Name]) { return $null }
    return $Node.Attributes[$Name].Value
}

function Get-Child {
    param([System.Xml.XmlNode] $Node, [string] $Name)
    if ($null -eq $Node) { return $null }
    foreach ($child in $Node.ChildNodes) {
        if ((Get-Attr $child "name") -eq $Name) { return $child }
    }
    return $null
}

function Get-Int {
    param([System.Xml.XmlNode] $Node, [string] $Name, [object] $Default = $null)
    $child = Get-Child $Node $Name
    $value = Get-Attr $child "value"
    if ($null -eq $value -or $value -notmatch "^-?\d+$") { return $Default }
    return [int] $value
}

function Get-Value {
    param([System.Xml.XmlNode] $Node, [string] $Name)
    return Get-Attr (Get-Child $Node $Name) "value"
}

function Load-Xml {
    param([string] $Path)
    $doc = New-Object System.Xml.XmlDocument
    $doc.PreserveWhitespace = $false
    $doc.Load((Resolve-Path -LiteralPath $Path))
    return $doc
}

function Get-SourcePath {
    param([object] $Map)
    if ([string]::IsNullOrWhiteSpace([string] $Map.source)) {
        throw "Map $($Map.mapId) has no WZ source path."
    }
    $path = [string] $Map.source
    if (Test-Path -LiteralPath $path) { return $path }
    $candidate = Join-Path $WzRoot ($path -replace '^wz[\\/]', '')
    if (Test-Path -LiteralPath $candidate) { return $candidate }
    throw "Map $($Map.mapId) WZ source does not exist: $path (fallback: $candidate)"
}

function Test-MapInScope {
    param([object] $Map, [string[]] $SelectedRegions, [bool] $IncludeAllRegions)
    if ($IncludeAllRegions) { return $true }
    $mapId = [int64] $Map.mapId
    if ($SelectedRegions -contains 'maple' -and $mapId -ge 0 -and $mapId -lt 100000000) { return $true }
    if ($SelectedRegions -contains 'victoria' -and $mapId -ge 100000000 -and $mapId -lt 130000000) { return $true }
    $otherRegions = @($SelectedRegions | Where-Object { $_ -notin @('maple', 'victoria') })
    return $otherRegions -contains [string] $Map.region
}

function Get-SegmentDistance {
    param([object] $Segment, [int] $X, [int] $Y)
    $left = [Math]::Min([int] $Segment.x1, [int] $Segment.x2)
    $right = [Math]::Max([int] $Segment.x1, [int] $Segment.x2)
    $dx = if ($X -lt $left) { $left - $X } elseif ($X -gt $right) { $X - $right } else { 0 }
    $midY = [int] [Math]::Round(([int] $Segment.y1 + [int] $Segment.y2) / 2.0)
    return [Math]::Sqrt(($dx * $dx) + (($Y - $midY) * ($Y - $midY)))
}

function Find-NearestComponent {
    param([object[]] $Segments, [int] $X, [int] $Y)
    $best = $null
    $bestDistance = [double]::MaxValue
    foreach ($segment in $Segments) {
        $distance = Get-SegmentDistance $segment $X $Y
        if ($distance -lt $bestDistance) {
            $best = $segment
            $bestDistance = $distance
        }
    }
    if ($null -eq $best) { return $null }
    return [pscustomobject] @{ componentId = $best.componentId; footholdId = $best.footholdId; distance = [int] [Math]::Round($bestDistance) }
}

function Get-MapTopology {
    param([object] $Map)
    $sourcePath = Get-SourcePath $Map
    $xml = Load-Xml $sourcePath
    $root = $xml.SelectSingleNode('/imgdir')
    $footholdRoot = $root.SelectSingleNode('./imgdir[@name="foothold"]')
    $segments = New-ObjectList
    if ($null -ne $footholdRoot) {
        foreach ($node in $footholdRoot.SelectNodes('.//imgdir[./int[@name="x1"] and ./int[@name="x2"] and ./int[@name="y1"] and ./int[@name="y2"]]')) {
            $idText = Get-Attr $node "name"
            if ($idText -notmatch '^\d+$') { continue }
            $groupNode = $node.ParentNode
            $pageNode = if ($null -ne $groupNode) { $groupNode.ParentNode } else { $null }
            $x1 = Get-Int $node "x1" 0; $x2 = Get-Int $node "x2" 0
            $y1 = Get-Int $node "y1" 0; $y2 = Get-Int $node "y2" 0
            [void] $segments.Add([pscustomobject] @{
                footholdId = [int] $idText
                page = if ((Get-Attr $pageNode "name") -match '^\d+$') { [int] (Get-Attr $pageNode "name") } else { $null }
                group = if ((Get-Attr $groupNode "name") -match '^\d+$') { [int] (Get-Attr $groupNode "name") } else { $null }
                x1 = $x1; y1 = $y1; x2 = $x2; y2 = $y2
                prev = Get-Int $node "prev" 0
                next = Get-Int $node "next" 0
                width = [Math]::Abs($x2 - $x1)
                rise = [Math]::Abs($y2 - $y1)
                wall = ($x1 -eq $x2)
                componentId = $null
            })
        }
    }

    $segmentArray = @($segments.ToArray())
    $byId = @{}
    foreach ($segment in $segmentArray) { $byId[[string] $segment.footholdId] = $segment }
    $visited = New-Object 'System.Collections.Generic.HashSet[int]'
    $components = New-ObjectList
    $componentId = 0
    foreach ($segment in $segmentArray) {
        if ($visited.Contains([int] $segment.footholdId)) { continue }
        $componentId++
        $queue = New-Object 'System.Collections.Generic.Queue[int]'
        $queue.Enqueue([int] $segment.footholdId)
        $members = New-ObjectList
        while ($queue.Count -gt 0) {
            $id = $queue.Dequeue()
            if (!$visited.Add($id)) { continue }
            if (!$byId.ContainsKey([string] $id)) { continue }
            $current = $byId[[string] $id]
            $current.componentId = $componentId
            [void] $members.Add($current)
            foreach ($neighbor in @([int] $current.prev, [int] $current.next)) {
                if ($neighbor -gt 0 -and $byId.ContainsKey([string] $neighbor) -and !$visited.Contains($neighbor)) { $queue.Enqueue($neighbor) }
            }
        }
        $memberArray = @($members.ToArray())
        if ($memberArray.Count -eq 0) { continue }
        $minX = ($memberArray | ForEach-Object { [Math]::Min($_.x1, $_.x2) } | Measure-Object -Minimum).Minimum
        $maxX = ($memberArray | ForEach-Object { [Math]::Max($_.x1, $_.x2) } | Measure-Object -Maximum).Maximum
        $minY = ($memberArray | ForEach-Object { [Math]::Min($_.y1, $_.y2) } | Measure-Object -Minimum).Minimum
        $maxY = ($memberArray | ForEach-Object { [Math]::Max($_.y1, $_.y2) } | Measure-Object -Maximum).Maximum
        $walkable = @($memberArray | Where-Object { !$_.wall })
        $horizontalSafe = @($walkable | Where-Object { [int] $_.rise -le 12 })
        $widest = if ($horizontalSafe.Count -gt 0) { @($horizontalSafe | Sort-Object width -Descending | Select-Object -First 1) } else { @($walkable | Sort-Object width -Descending | Select-Object -First 1) }
        [void] $components.Add([pscustomobject] @{
            componentId = $componentId
            footholdIds = @($memberArray | ForEach-Object { [int] $_.footholdId })
            bounds = [pscustomobject] @{ minX = [int] $minX; maxX = [int] $maxX; minY = [int] $minY; maxY = [int] $maxY }
            center = [pscustomobject] @{ x = [int] [Math]::Round(($minX + $maxX) / 2.0); y = [int] [Math]::Round(($minY + $maxY) / 2.0) }
            totalWidth = [int] (($walkable | Measure-Object width -Sum).Sum)
            safePoint = if ($widest.Count -gt 0) { [pscustomobject] @{ x = [int] [Math]::Round(($widest[0].x1 + $widest[0].x2) / 2.0); y = [int] [Math]::Round(($widest[0].y1 + $widest[0].y2) / 2.0); footholdId = [int] $widest[0].footholdId; clearanceUnverified = $true } } else { $null }
        })
    }
    $componentArray = @($components.ToArray())

    $climbs = New-ObjectList
    $ladderRoot = $root.SelectSingleNode('./imgdir[@name="ladderRope"]')
    if ($null -ne $ladderRoot) {
        foreach ($node in $ladderRoot.SelectNodes('./imgdir')) {
            $x = Get-Int $node "x" 0; $y1 = Get-Int $node "y1" 0; $y2 = Get-Int $node "y2" 0
            $top = Find-NearestComponent $segmentArray $x ([Math]::Min($y1, $y2))
            $bottom = Find-NearestComponent $segmentArray $x ([Math]::Max($y1, $y2))
            [void] $climbs.Add([pscustomobject] @{
                climbId = Get-Attr $node "name"
                type = if ((Get-Int $node "l" 0) -eq 1) { "ladder" } else { "rope" }
                x = $x; y1 = $y1; y2 = $y2; page = Get-Int $node "page" $null; upperFoothold = Get-Int $node "uf" $null
                topAttachment = $top; bottomAttachment = $bottom
                executable = $false; requiresRuntimePhysicsValidation = $true
            })
        }
    }

    $portalAnchors = New-ObjectList
    foreach ($portal in @($Map.portals)) {
        $near = Find-NearestComponent $segmentArray ([int] $portal.x) ([int] $portal.y)
        [void] $portalAnchors.Add([pscustomobject] @{
            name = $portal.name; type = $portal.type; x = $portal.x; y = $portal.y
            targetMapId = $portal.targetMapId; targetPortalName = $portal.targetPortalName; script = $portal.script
            componentId = if ($near) { $near.componentId } else { $null }
            footholdId = if ($near) { $near.footholdId } else { $null }
        })
    }

    $spawns = New-ObjectList
    $lifeRoot = $root.SelectSingleNode('./imgdir[@name="life"]')
    if ($null -ne $lifeRoot) {
        foreach ($node in $lifeRoot.SelectNodes('./imgdir')) {
            if ((Get-Value $node "type") -ne "m") { continue }
            $fh = Get-Int $node "fh" $null
            $x = Get-Int $node "x" 0; $y = Get-Int $node "y" 0
            $segment = if ($null -ne $fh -and $byId.ContainsKey([string] $fh)) { $byId[[string] $fh] } else { $null }
            $nearest = if ($segment) { $null } else { Find-NearestComponent $segmentArray $x $y }
            [void] $spawns.Add([pscustomobject] @{
                lifeIndex = Get-Attr $node "name"; mobId = [int] (Get-Value $node "id")
                x = $x; y = $y; footholdId = if ($segment) { $fh } elseif ($nearest) { $nearest.footholdId } else { $null }
                componentId = if ($segment) { $segment.componentId } elseif ($nearest) { $nearest.componentId } else { $null }
                roamLeft = Get-Int $node "rx0" $null; roamRight = Get-Int $node "rx1" $null
            })
        }
    }

    $transitions = New-ObjectList
    $transitionComponents = @($componentArray | Sort-Object @{ Expression = { $_.center.x }; Ascending = $true }, @{ Expression = { $_.center.y }; Ascending = $true })
    $neighborScanLimit = 12
    for ($i = 0; $i -lt $transitionComponents.Count; $i++) {
        $lastNeighbor = [Math]::Min($transitionComponents.Count - 1, $i + $neighborScanLimit)
        for ($j = $i + 1; $j -le $lastNeighbor; $j++) {
            $a = $transitionComponents[$i]; $b = $transitionComponents[$j]
            $horizontalGap = [Math]::Max(0, [Math]::Max($a.bounds.minX, $b.bounds.minX) - [Math]::Min($a.bounds.maxX, $b.bounds.maxX))
            $verticalGap = [Math]::Abs($a.center.y - $b.center.y)
            $overlap = [Math]::Min($a.bounds.maxX, $b.bounds.maxX) - [Math]::Max($a.bounds.minX, $b.bounds.minX)
            if ($overlap -ge 24 -and $verticalGap -ge 20 -and $verticalGap -le 450) {
                $upper = if ($a.center.y -lt $b.center.y) { $a } else { $b }; $lower = if ($upper -eq $a) { $b } else { $a }
                [void] $transitions.Add([pscustomobject] @{ type = "drop-candidate"; fromComponentId = $upper.componentId; toComponentId = $lower.componentId; verticalGap = [int] $verticalGap; overlap = [int] $overlap; executable = $false; requiresRuntimePhysicsValidation = $true })
            } elseif ($horizontalGap -le 120 -and $verticalGap -le 160) {
                [void] $transitions.Add([pscustomobject] @{ type = "jump-candidate"; componentA = $a.componentId; componentB = $b.componentId; horizontalGap = [int] $horizontalGap; verticalGap = [int] $verticalGap; executable = $false; requiresRuntimePhysicsValidation = $true })
            }
            if ($transitions.Count -ge 96) { break }
        }
        if ($transitions.Count -ge 96) { break }
    }

    return [pscustomobject] @{
        schemaVersion = 1; mapId = [int] $Map.mapId; mapName = $Map.mapName; streetName = $Map.streetName
        source = $Map.source; footholds = $segmentArray; components = $componentArray
        climbables = @($climbs.ToArray()); portalAnchors = @($portalAnchors.ToArray()); mobSpawns = @($spawns.ToArray())
        transitionCandidates = @($transitions.ToArray())
        terrain = [pscustomobject] @{
            componentCount = $componentArray.Count; footholdCount = $segmentArray.Count; climbableCount = $climbs.Count
            verticalSpan = if ($componentArray.Count -gt 0) { [int] ((($componentArray | ForEach-Object bounds | Measure-Object maxY -Maximum).Maximum) - (($componentArray | ForEach-Object bounds | Measure-Object minY -Minimum).Minimum)) } else { 0 }
            complexity = if ($componentArray.Count -ge 8 -or $climbs.Count -ge 5) { "high" } elseif ($componentArray.Count -ge 3 -or $climbs.Count -ge 1) { "medium" } else { "low" }
        }
        policy = [pscustomobject] @{ coordinatesAreFacts = $true; transitionsAreHints = $true; transitionCandidateCoverage = "nearest-12-components-by-x-then-y"; runtimeMustValidateMovement = $true }
    }
}

function Export-CombatPolicies {
    param([object[]] $Navigation, [hashtable] $MobById, [hashtable] $TrainingByMap)
    $rows = New-ObjectList
    foreach ($map in $Navigation) {
        $spawns = @($map.mobSpawns)
        if ($spawns.Count -eq 0) { continue }
        $anchors = New-ObjectList
        foreach ($group in @($spawns | Group-Object componentId)) {
            if ([string]::IsNullOrWhiteSpace([string] $group.Name)) { continue }
            $component = @($map.components | Where-Object { [int] $_.componentId -eq [int] $group.Name } | Select-Object -First 1)
            $mobIds = @($group.Group | ForEach-Object { [int] $_.mobId } | Sort-Object -Unique)
            $levels = @($mobIds | ForEach-Object { if ($MobById.ContainsKey([string] $_)) { [int] $MobById[[string] $_].level } })
            [void] $anchors.Add([pscustomobject] @{
                anchorId = "$($map.mapId):component:$($group.Name)"; componentId = [int] $group.Name
                center = if ($component.Count -gt 0) { $component[0].center } else { [pscustomobject] @{ x = [int] (($group.Group | Measure-Object x -Average).Average); y = [int] (($group.Group | Measure-Object y -Average).Average) } }
                spawnCount = $group.Count; mobIds = $mobIds
                minMobLevel = if ($levels.Count -gt 0) { ($levels | Measure-Object -Minimum).Minimum } else { $null }
                maxMobLevel = if ($levels.Count -gt 0) { ($levels | Measure-Object -Maximum).Maximum } else { $null }
            })
        }
        $anchorArray = @($anchors.ToArray() | Sort-Object @{ Expression = { $_.center.y }; Ascending = $true }, @{ Expression = { $_.center.x }; Ascending = $true })
        $recommended = [Math]::Min(4, [Math]::Max(1, [Math]::Ceiling($anchorArray.Count / 2.0)))
        $maximum = [Math]::Min(8, [Math]::Max($recommended, $anchorArray.Count))
        $overlay = if ($TrainingByMap.ContainsKey([string] $map.mapId)) { $TrainingByMap[[string] $map.mapId] } else { $null }
        if ($overlay) { $recommended = [int] $overlay.recommendedAgents; $maximum = [int] $overlay.maximumAgents }
        $partitions = New-ObjectList
        foreach ($size in 1..4) {
            $groups = New-ObjectList
            for ($slot = 0; $slot -lt $size; $slot++) {
                $assigned = New-ObjectList
                for ($idx = 0; $idx -lt $anchorArray.Count; $idx++) {
                    $assignedSlot = [Math]::Min($size - 1, [int] [Math]::Floor(($idx * $size) / [double] [Math]::Max(1, $anchorArray.Count)))
                    if ($assignedSlot -eq $slot) { [void] $assigned.Add($anchorArray[$idx].anchorId) }
                }
                [void] $groups.Add([pscustomobject] @{ slot = $slot + 1; anchorIds = @($assigned.ToArray()) })
            }
            [void] $partitions.Add([pscustomobject] @{ partySize = $size; groups = @($groups.ToArray()); strategy = "spatial-contiguous-y-then-x" })
        }
        $allLevels = @($anchorArray | Where-Object { $null -ne $_.minMobLevel } | ForEach-Object { $_.minMobLevel; $_.maxMobLevel })
        [void] $rows.Add([pscustomobject] @{
            schemaVersion = 1; mapId = [int] $map.mapId; mapName = $map.mapName
            recommendedLevelBand = if ($overlay) { [pscustomobject] @{ min = [int] $overlay.recommendedMinLevel; max = [int] $overlay.recommendedMaxLevel; source = "victoria-training-overlay" } } elseif ($allLevels.Count -gt 0) { [pscustomobject] @{ min = [Math]::Max(1, [int] (($allLevels | Measure-Object -Minimum).Minimum) - 2); max = [int] (($allLevels | Measure-Object -Maximum).Maximum) + 5; source = "mob-level-heuristic" } } else { $null }
            anchors = $anchorArray; recommendedAgents = $recommended; maximumAgents = $maximum
            partyPartitions = @($partitions.ToArray())
            incidentalMobPolicy = [pscustomobject] @{ allowWhenInAttackArc = $true; allowWhenBlockingRoute = $true; doNotRetargetAcrossComponents = $true }
            terrain = $map.terrain; trainingOverlay = $overlay
            policy = [pscustomobject] @{ liveOccupancyRequired = $true; anchorReachabilityRequiresRuntimeValidation = $true; bossMapsRequireExplicitPolicy = $true }
        })
    }
    return @($rows.ToArray())
}

function Write-NavigationAndCombatCatalogs {
    param(
        [object[]] $Maps,
        [hashtable] $MobById,
        [hashtable] $TrainingByMap,
        [string] $NavigationPath,
        [string] $CombatPath
    )

    $utf8 = New-Object System.Text.UTF8Encoding($false)
    $navigationWriter = New-Object System.IO.StreamWriter($NavigationPath, $false, $utf8)
    $combatWriter = New-Object System.IO.StreamWriter($CombatPath, $false, $utf8)
    $navigationCount = 0; $footholdCount = 0; $climbableCount = 0
    $combatCount = 0; $anchorCount = 0; $processed = 0
    $navigationWritten = 0; $combatWritten = 0
    $navigationChunk = New-ObjectList; $combatChunk = New-ObjectList
    try {
        $navigationWriter.Write('['); $combatWriter.Write('[')
        foreach ($map in $Maps) {
            $topology = Get-MapTopology $map
            [void] $navigationChunk.Add($topology)
            $navigationCount++; $footholdCount += @($topology.footholds).Count; $climbableCount += @($topology.climbables).Count

            $combatRows = @(Export-CombatPolicies -Navigation @($topology) -MobById $MobById -TrainingByMap $TrainingByMap)
            foreach ($combatRow in $combatRows) {
                [void] $combatChunk.Add($combatRow)
                $combatCount++; $anchorCount += @($combatRow.anchors).Count
            }
            $processed++
            if (($processed % 100) -eq 0) {
                if ($navigationChunk.Count -gt 0) {
                    $json = ConvertTo-Json -InputObject @($navigationChunk.ToArray()) -Depth 16 -Compress
                    $inner = $json.Substring(1, $json.Length - 2)
                    if ($navigationWritten -gt 0 -and $inner.Length -gt 0) { $navigationWriter.Write(',') }
                    $navigationWriter.Write($inner); $navigationWritten += $navigationChunk.Count; $navigationChunk.Clear()
                }
                if ($combatChunk.Count -gt 0) {
                    $json = ConvertTo-Json -InputObject @($combatChunk.ToArray()) -Depth 18 -Compress
                    $inner = $json.Substring(1, $json.Length - 2)
                    if ($combatWritten -gt 0 -and $inner.Length -gt 0) { $combatWriter.Write(',') }
                    $combatWriter.Write($inner); $combatWritten += $combatChunk.Count; $combatChunk.Clear()
                }
                $navigationWriter.Flush(); $combatWriter.Flush()
                Write-Host "  Spatial catalogs: $processed/$($Maps.Count) maps; $navigationCount topology rows; $combatCount combat rows"
            }
        }
        if ($navigationChunk.Count -gt 0) {
            $json = ConvertTo-Json -InputObject @($navigationChunk.ToArray()) -Depth 16 -Compress; $inner = $json.Substring(1, $json.Length - 2)
            if ($navigationWritten -gt 0 -and $inner.Length -gt 0) { $navigationWriter.Write(',') }; $navigationWriter.Write($inner)
        }
        if ($combatChunk.Count -gt 0) {
            $json = ConvertTo-Json -InputObject @($combatChunk.ToArray()) -Depth 18 -Compress; $inner = $json.Substring(1, $json.Length - 2)
            if ($combatWritten -gt 0 -and $inner.Length -gt 0) { $combatWriter.Write(',') }; $combatWriter.Write($inner)
        }
        $navigationWriter.Write(']'); $combatWriter.Write(']')
    } finally {
        $navigationWriter.Dispose(); $combatWriter.Dispose()
    }

    return [pscustomobject] @{ navigationMaps = $navigationCount; footholds = $footholdCount; climbables = $climbableCount; combatMaps = $combatCount; combatAnchors = $anchorCount }
}

function Parse-NumberArray {
    param([string] $Script, [string] $Variable)
    $match = [regex]::Match($Script, "(?ms)\bvar\s+$([regex]::Escape($Variable))\s*=\s*\[(?<values>[^\]]*)\]")
    if (!$match.Success) { return @() }
    return @([regex]::Matches($match.Groups['values'].Value, '-?\d+') | ForEach-Object { [int] $_.Value })
}

function Export-TravelServices {
    param([object[]] $Services, [object[]] $Spots)
    $spotsByNpc = @{}
    foreach ($spot in $Spots) {
        if (!(@($spot.serviceTypes) -contains "travel-script")) { continue }
        $key = [string] $spot.npcId
        if (!$spotsByNpc.ContainsKey($key)) { $spotsByNpc[$key] = New-ObjectList }
        [void] $spotsByNpc[$key].Add([pscustomobject] @{ interactionSpotKey = $spot.interactionSpotKey; mapId = $spot.mapId; npcPosition = $spot.npcPosition; candidateSpots = @($spot.candidateSpots | Select-Object -First 5) })
    }
    $rows = New-ObjectList
    foreach ($service in @($Services | Where-Object { $_.serviceType -eq "travel-script" })) {
        $sourcePath = [string] $service.source
        $script = if (Test-Path -LiteralPath $sourcePath) { Get-Content -LiteralPath $sourcePath -Raw } else { "" }
        $maps = Parse-NumberArray $script "maps"
        $costs = Parse-NumberArray $script "cost"
        $dynamic = ($script -match 'cm\.warp\s*\(\s*maps\s*\[')
        $beginnerDiscount = ($script -match 'getJobId\(\)\s*==\s*0' -and ($script -match '\?\s*10\s*:\s*1' -or $script -match 'cost[^;\r\n]*/\s*10'))
        $destinations = New-ObjectList
        for ($i = 0; $i -lt $maps.Count; $i++) {
            $fullCost = if ($i -lt $costs.Count) { [int] $costs[$i] } else { $null }
            [void] $destinations.Add([pscustomobject] @{ selectionIndex = $i; mapId = [int] $maps[$i]; fullCost = $fullCost; beginnerCost = if ($beginnerDiscount -and $null -ne $fullCost) { [int] ($fullCost / 10) } else { $fullCost }; evidence = "literal-script-array" })
        }
        foreach ($match in [regex]::Matches($script, 'cm\.warp\s*\(\s*(?<map>\d{5,9})')) {
            $mapId = [int] $match.Groups['map'].Value
            if (!(@($destinations | ForEach-Object mapId) -contains $mapId)) { [void] $destinations.Add([pscustomobject] @{ selectionIndex = $null; mapId = $mapId; fullCost = $null; beginnerCost = $null; evidence = "literal-warp-call" }) }
        }
        $placementRows = @()
        if ($spotsByNpc.ContainsKey([string] $service.npcId)) { $placementRows = @($spotsByNpc[[string] $service.npcId].ToArray()) }
        $strong = ($destinations.Count -gt 0 -and ($dynamic -or $script -match 'cm\.warp\s*\(\s*\d'))
        [void] $rows.Add([pscustomobject] @{
            schemaVersion = 1; serviceId = $service.serviceId; npcId = $service.npcId; npcName = $service.npcName; scriptName = $service.scriptName
            placements = $placementRows; destinations = @($destinations.ToArray())
            conditions = [pscustomobject] @{ beginnerDiscount = $beginnerDiscount; hasItemGate = ($script -match 'haveItem|hasItem'); hasMesoGate = ($script -match 'getMeso'); selectionDriven = $dynamic }
            automation = [pscustomobject] @{ safeForPlanning = $strong; safeForExecution = $false; requiresLiveMesoAndQuestValidation = $true; requiresScriptReview = !$strong }
            source = $sourcePath
        })
    }
    return @($rows.ToArray())
}

function Get-ItemName {
    param([hashtable] $ItemById, [int] $ItemId)
    if ($ItemById.ContainsKey([string] $ItemId)) { return $ItemById[[string] $ItemId].name }
    return $null
}

function Export-ProgressionItemPolicies {
    param([hashtable] $ItemById, [object[]] $Shops, [int] $FileLimit = 0)
    $shopByItem = @{}
    foreach ($shop in $Shops) {
        foreach ($item in @($shop.items)) {
            $key = [string] $item.itemId
            if (!$shopByItem.ContainsKey($key)) { $shopByItem[$key] = New-ObjectList }
            [void] $shopByItem[$key].Add([pscustomobject] @{ shopId = $shop.shopId; npcId = $shop.npcId; mapIds = @($shop.mapIds); price = $item.price })
        }
    }
    $equipment = New-ObjectList
    $equipmentFiles = @(Get-ChildItem -LiteralPath (Join-Path $WzRoot 'Character.wz') -Filter '*.img.xml' -File -Recurse)
    if ($FileLimit -gt 0) { $equipmentFiles = @($equipmentFiles | Select-Object -First $FileLimit) }
    foreach ($file in $equipmentFiles) {
        if ($file.BaseName -notmatch '^(?<id>\d{8})\.img$') { continue }
        $itemId = [int] $Matches.id
        $xml = Load-Xml $file.FullName; $root = $xml.SelectSingleNode('/imgdir'); $info = $root.SelectSingleNode('./imgdir[@name="info"]')
        if ($null -eq $info -or $null -eq (Get-Child $info 'islot')) { continue }
        $stats = [ordered]@{}
        foreach ($name in @('incSTR','incDEX','incINT','incLUK','incMHP','incMMP','incPAD','incMAD','incPDD','incMDD','incACC','incEVA','incSpeed','incJump')) {
            $value = Get-Int $info $name $null; if ($null -ne $value -and $value -ne 0) { $stats[$name] = $value }
        }
        [void] $equipment.Add([pscustomobject] @{
            itemId = $itemId; itemName = Get-ItemName $ItemById $itemId; slot = Get-Value $info 'islot'; visualSlot = Get-Value $info 'vslot'
            requirements = [pscustomobject] @{ level = Get-Int $info 'reqLevel' 0; jobMask = Get-Int $info 'reqJob' 0; str = Get-Int $info 'reqSTR' 0; dex = Get-Int $info 'reqDEX' 0; int = Get-Int $info 'reqINT' 0; luk = Get-Int $info 'reqLUK' 0 }
            stats = [pscustomobject] $stats; upgradeSlots = Get-Int $info 'tuc' 0; attackSpeed = Get-Int $info 'attackSpeed' $null; cash = ((Get-Int $info 'cash' 0) -eq 1); price = Get-Int $info 'price' $null
            policy = [pscustomobject] @{ compareByBuildScore = $true; preserveWhenStatBridge = $true; runtimeMustValidateJobAndRequirements = $true }
            source = $file.FullName.Substring($repoRoot.Length + 1).Replace('\','/')
        })
    }

    $supplies = New-ObjectList; $scrolls = New-ObjectList
    foreach ($file in Get-ChildItem -LiteralPath (Join-Path $WzRoot 'Item.wz/Consume') -Filter '*.img.xml' -File -Recurse) {
        $xml = Load-Xml $file.FullName
        foreach ($node in $xml.SelectNodes('/imgdir/imgdir')) {
            $idText = Get-Attr $node 'name'; if ($idText -notmatch '^\d{8}$') { continue }
            $itemId = [int] $idText; $info = $node.SelectSingleNode('./imgdir[@name="info"]'); $spec = $node.SelectSingleNode('./imgdir[@name="spec"]')
            if ($null -eq $info) { continue }
            $success = Get-Int $info 'success' $null
            if ($null -ne $success) {
                $effects = [ordered]@{}
                foreach ($name in @('incSTR','incDEX','incINT','incLUK','incMHP','incMMP','incPAD','incMAD','incPDD','incMDD','incACC','incEVA','incSpeed','incJump')) { $value = Get-Int $info $name $null; if ($null -ne $value -and $value -ne 0) { $effects[$name] = $value } }
                [void] $scrolls.Add([pscustomobject] @{ itemId = $itemId; itemName = Get-ItemName $ItemById $itemId; successPercent = $success; cursedPercent = Get-Int $info 'cursed' 0; effects = [pscustomobject] $effects; policy = [pscustomobject] @{ useRequiresUpgradePlan = $true; destructiveRiskRequiresExplicitApproval = ((Get-Int $info 'cursed' 0) -gt 0); compatibilityMustBeValidatedByRuntime = $true }; source = $file.FullName.Substring($repoRoot.Length + 1).Replace('\','/') })
                continue
            }
            if ($null -eq $spec) { continue }
            $hp = Get-Int $spec 'hp' 0; $mp = Get-Int $spec 'mp' 0; $hpR = Get-Int $spec 'hpR' 0; $mpR = Get-Int $spec 'mpR' 0
            if (($hp + $mp + $hpR + $mpR) -eq 0) { continue }
            $sources = @()
            if ($shopByItem.ContainsKey([string] $itemId)) { $sources = @($shopByItem[[string] $itemId].ToArray()) }
            $prices = @($sources | Where-Object { $null -ne $_.price -and [int64] $_.price -gt 0 } | ForEach-Object { [int64] $_.price })
            $minPrice = if ($prices.Count -gt 0) { ($prices | Measure-Object -Minimum).Minimum } else { Get-Int $info 'price' $null }
            [void] $supplies.Add([pscustomobject] @{
                itemId = $itemId; itemName = Get-ItemName $ItemById $itemId; recovery = [pscustomobject] @{ hp = $hp; mp = $mp; hpPercent = $hpR; mpPercent = $mpR }
                shopSources = $sources; minimumKnownPrice = $minPrice
                efficiency = [pscustomobject] @{ hpPerMeso = if ($minPrice -gt 0 -and $hp -gt 0) { [Math]::Round($hp / [double] $minPrice, 4) } else { $null }; mpPerMeso = if ($minPrice -gt 0 -and $mp -gt 0) { [Math]::Round($mp / [double] $minPrice, 4) } else { $null } }
                policy = [pscustomobject] @{ role = if ($hp -lt 0 -or $mp -lt 0 -or $hpR -lt 0 -or $mpR -lt 0) { 'harmful-or-tradeoff-effect' } elseif ($hp -gt 0 -and $mp -gt 0) { 'hybrid-recovery' } elseif ($hp -gt 0 -or $hpR -gt 0) { 'hp-recovery' } else { 'mp-recovery' }; doNotProcureByDefault = ($hp -lt 0 -or $mp -lt 0 -or $hpR -lt 0 -or $mpR -lt 0); procurementRequiresLivePriceAndCapacity = $true }
                source = $file.FullName.Substring($repoRoot.Length + 1).Replace('\','/')
            })
        }
    }
    return [pscustomobject] @{
        schemaVersion = 1; catalogId = 'progression-item-policy-v1'
        equipment = @($equipment.ToArray()); supplies = @($supplies.ToArray()); scrolls = @($scrolls.ToArray())
        inventoryRules = @(
            [pscustomobject] @{ priority = 10; rule = 'protect-active-quest-items'; action = 'keep'; runtimeQuestStateRequired = $true },
            [pscustomobject] @{ priority = 20; rule = 'protect-equipped-and-build-upgrades'; action = 'keep'; buildProfileRequired = $true },
            [pscustomobject] @{ priority = 30; rule = 'maintain-hp-mp-and-ammunition-reserves'; action = 'restock'; liveThresholdsRequired = $true },
            [pscustomobject] @{ priority = 40; rule = 'sell-obsolete-vendor-items'; action = 'sell'; demandAndFutureQuestCheckRequired = $true },
            [pscustomobject] @{ priority = 50; rule = 'drop-only-when-no-shop-route-and-inventory-blocked'; action = 'drop'; destructiveAction = $true }
        )
        policyBoundary = [pscustomobject] @{ factsComeFromWzAndShops = $true; buildScoringIsRuntimePolicy = $true; marketDemandIsNotCataloged = $true; inventoryStateIsNotCataloged = $true }
    }
}

function Export-QuestChainPolicies {
    param([object[]] $Quests, [object[]] $Objectives, [object[]] $Interactions, [object[]] $RewardChoices)
    $objectiveByQuest = @{}; foreach ($row in $Objectives) { $objectiveByQuest[[string] $row.questId] = $row }
    $interactionByQuest = @{}; foreach ($row in $Interactions) { $interactionByQuest[[string] $row.questId] = $row }
    $rewardsByQuest = @{}; foreach ($row in $RewardChoices) { $key = [string] $row.questId; if (!$rewardsByQuest.ContainsKey($key)) { $rewardsByQuest[$key] = New-ObjectList }; [void] $rewardsByQuest[$key].Add($row) }
    $dependents = @{}
    foreach ($quest in $Quests) {
        foreach ($prereq in @($quest.requirements.start.prerequisiteQuests)) {
            $prereqId = if ($null -ne $prereq.questId) { [int] $prereq.questId } else { [int] $prereq }
            $key = [string] $prereqId; if (!$dependents.ContainsKey($key)) { $dependents[$key] = New-ObjectList }; [void] $dependents[$key].Add([int] $quest.questId)
        }
    }
    $rows = New-ObjectList
    foreach ($quest in $Quests) {
        $key = [string] $quest.questId; $objective = $objectiveByQuest[$key]; $interaction = $interactionByQuest[$key]
        $prerequisiteRequirements = @($quest.requirements.start.prerequisiteQuests | ForEach-Object { if ($null -ne $_.questId) { [pscustomobject] @{ questId = [int] $_.questId; state = if ($null -ne $_.state) { [int] $_.state } else { 2 } } } else { [pscustomobject] @{ questId = [int] $_; state = 2 } } })
        $prereqs = @($prerequisiteRequirements | ForEach-Object { [int] $_.questId })
        $flags = @($quest.flags); $handlers = New-ObjectList
        foreach ($flag in $flags) { [void] $handlers.Add([string] $flag) }
        if ($rewardsByQuest.ContainsKey($key)) { [void] $handlers.Add('reward-choice') }
        if ($interaction -and (($interaction.start.status -eq 'missing-placement') -or ($interaction.complete.status -eq 'missing-placement'))) { [void] $handlers.Add('missing-npc-placement') }
        if ($interaction -and (($interaction.start.status -eq 'not-npc-driven') -or ($interaction.complete.status -eq 'not-npc-driven'))) { [void] $handlers.Add('non-npc-driven-phase') }
        $objectiveTypes = @($objective.objectives | ForEach-Object type | Sort-Object -Unique)
        $requiredCapabilities = New-ObjectList
        if ($objectiveTypes -match 'interact-npc') { [void] $requiredCapabilities.Add('npc-interaction') }
        if ($objectiveTypes -contains 'kill-mob') { [void] $requiredCapabilities.Add('combat') }
        if ($objectiveTypes -contains 'collect-item') { [void] $requiredCapabilities.Add('loot-and-inventory') }
        if ($prereqs.Count -gt 0) { [void] $requiredCapabilities.Add('quest-state') }
        if ($rewardsByQuest.ContainsKey($key)) { [void] $requiredCapabilities.Add('reward-selection-policy') }
        $special = @($handlers.ToArray() | Sort-Object -Unique)
        $blocking = @($special | Where-Object { $_ -in @('missing-npc-placement','non-npc-driven-phase','reward-choice') })
        [void] $rows.Add([pscustomobject] @{
            schemaVersion = 1; questId = [int] $quest.questId; questName = $quest.questName; questParent = $quest.questParent; questArea = $quest.questArea
            prerequisites = $prereqs; prerequisiteRequirements = $prerequisiteRequirements; dependents = if ($dependents.ContainsKey($key)) { @($dependents[$key].ToArray() | Sort-Object -Unique) } else { @() }
            eligibility = [pscustomobject] @{ minLevel = $quest.requirements.start.minLevel; maxLevel = $quest.requirements.start.maxLevel; jobs = @($quest.requirements.start.jobs); interval = $quest.requirements.start.interval }
            objectiveTypes = $objectiveTypes; specialHandlers = $special; requiredCapabilities = @($requiredCapabilities.ToArray() | Sort-Object -Unique)
            rewardChoices = if ($rewardsByQuest.ContainsKey($key)) { @($rewardsByQuest[$key].ToArray()) } else { @() }
            interactionStatus = if ($interaction) { [pscustomobject] @{ start = $interaction.start.status; complete = $interaction.complete.status } } else { $null }
            automation = [pscustomobject] @{ planningSafe = ($blocking.Count -eq 0); executionSafe = $false; blockedBy = $blocking; liveQuestStateRequired = $true; specialHandlerReviewRequired = ($special.Count -gt 0) }
            source = 'Quest.wz + generated NPC/objective catalogs'
        })
    }
    return @($rows.ToArray())
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) { throw "git rev-parse --show-toplevel failed: $repoRootText" }
$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$required = [ordered]@{
    maps = Join-Path $GameCatalogDir 'generated_map_catalog.json'
    mobs = Join-Path $GameCatalogDir 'generated_mob_catalog.json'
    items = Join-Path $GameCatalogDir 'generated_item_catalog.json'
    shops = Join-Path $GameCatalogDir 'generated_shop_catalog.json'
    quests = Join-Path $GameCatalogDir 'generated_quest_catalog.json'
    objectives = Join-Path $AgentCatalogDir 'generated_quest_objective_catalog.json'
    services = Join-Path $NpcCatalogDir 'generated_npc_services.json'
    spots = Join-Path $NpcCatalogDir 'generated_npc_interaction_spot_catalog.json'
    interactions = Join-Path $NpcCatalogDir 'generated_quest_npc_interaction_catalog.json'
    rewardChoices = Join-Path $NpcCatalogDir 'generated_npc_reward_choices.json'
}
foreach ($entry in $required.GetEnumerator()) { if (!(Test-Path -LiteralPath $entry.Value)) { throw "Missing required $($entry.Key) catalog: $($entry.Value)" } }
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$outputs = [ordered]@{
    navigationTopology = Join-Path $OutputDir 'generated_navigation_topology_catalog.json'
    combatMapPolicy = Join-Path $OutputDir 'generated_combat_map_policy_catalog.json'
    travelService = Join-Path $OutputDir 'generated_travel_service_catalog.json'
    progressionItemPolicy = Join-Path $OutputDir 'generated_progression_item_policy_catalog.json'
    questChainPolicy = Join-Path $OutputDir 'generated_quest_chain_policy_catalog.json'
    manifest = Join-Path $OutputDir 'generated_agent_decision_catalog_manifest.json'
    summary = Join-Path $OutputDir 'AGENT_DECISION_CATALOG_SUMMARY.md'
}

$maps = @(Read-JsonArray $required.maps | Where-Object { [int] $_.footholdCount -gt 0 -and (Test-MapInScope $_ $Regions ([bool] $AllRegions)) })
if ($MapLimit -gt 0) { $maps = @($maps | Select-Object -First $MapLimit) }
$mobs = Read-JsonArray $required.mobs
$mobById = @{}; foreach ($mob in $mobs) { $mobById[[string] $mob.mobId] = $mob }
$trainingByMap = @{}
$trainingPath = 'src/main/resources/agents/catalogs/victoria-level15-30-training-catalog.json'
if (Test-Path -LiteralPath $trainingPath) { $training = Get-Content -LiteralPath $trainingPath -Raw | ConvertFrom-Json; foreach ($row in @($training.trainingMaps)) { $trainingByMap[[string] $row.mapId] = $row } }

Write-Host 'Exporting navigation topology and combat map policies...'
$spatialCounts = Write-NavigationAndCombatCatalogs $maps $mobById $trainingByMap $outputs.navigationTopology $outputs.combatMapPolicy
$maps = $null; $mobs = $null; $mobById = $null; [GC]::Collect()

$items = Read-JsonArray $required.items; $shops = Read-JsonArray $required.shops
$services = Read-JsonArray $required.services; $spots = Read-JsonArray $required.spots
$itemById = @{}; foreach ($item in $items) { $itemById[[string] $item.itemId] = $item }
Write-Host 'Exporting travel services...'
$travel = Export-TravelServices $services $spots
Write-Host 'Exporting equipment, inventory, supply, and scroll policies...'
$progressionItems = Export-ProgressionItemPolicies $itemById $shops $EquipmentFileLimit
$travel | ConvertTo-Json -Depth 14 | Set-Content -LiteralPath $outputs.travelService -Encoding UTF8
$progressionItems | ConvertTo-Json -Depth 16 | Set-Content -LiteralPath $outputs.progressionItemPolicy -Encoding UTF8

$items = $null; $shops = $null; $services = $null; $spots = $null; $itemById = $null; [GC]::Collect()
$quests = Read-JsonArray $required.quests; $objectives = Read-JsonArray $required.objectives
$interactions = Read-JsonArray $required.interactions; $rewardChoices = Read-JsonArray $required.rewardChoices
Write-Host 'Exporting quest chain and special-handler classification...'
$questChains = Export-QuestChainPolicies $quests $objectives $interactions $rewardChoices
$questChains | ConvertTo-Json -Depth 16 | Set-Content -LiteralPath $outputs.questChainPolicy -Encoding UTF8

$counts = [ordered]@{
    navigationMaps = $spatialCounts.navigationMaps; footholds = $spatialCounts.footholds; climbables = $spatialCounts.climbables
    combatMaps = $spatialCounts.combatMaps; combatAnchors = $spatialCounts.combatAnchors; travelServices = $travel.Count; travelDestinations = @($travel | ForEach-Object destinations).Count
    equipment = @($progressionItems.equipment).Count; supplies = @($progressionItems.supplies).Count; scrolls = @($progressionItems.scrolls).Count
    questPolicies = $questChains.Count; questsWithSpecialHandlers = @($questChains | Where-Object { $_.specialHandlers.Count -gt 0 }).Count
}
$summaryLines = @(
    '# Agent Decision Catalog Summary', '', "Generated: $((Get-Date).ToString('o'))", '',
    'These catalogs separate immutable source facts from replaceable policy hints. All movement, purchases, inventory mutations, quest transitions, and travel execution still require live server validation.', '',
    '## Counts', '',
    "- Navigation maps: $($counts.navigationMaps)", "- Footholds: $($counts.footholds)", "- Ladders and ropes: $($counts.climbables)",
    "- Combat maps: $($counts.combatMaps)", "- Combat anchors: $($counts.combatAnchors)",
    "- Travel services: $($counts.travelServices)", "- Travel destinations: $($counts.travelDestinations)",
    "- Equipment facts: $($counts.equipment)", "- Recovery supplies: $($counts.supplies)", "- Scroll facts: $($counts.scrolls)",
    "- Quest policies: $($counts.questPolicies)", "- Quests with special handlers: $($counts.questsWithSpecialHandlers)", '',
    '## Policy boundaries', '',
    '- Foothold, spawn, item, quest, NPC, shop, and script literals are source facts.',
    '- Jump/drop edges, farming capacity, party partitions, equipment scoring, restock thresholds, and automation readiness are replaceable planning policy.',
    '- Candidate movement transitions are never executable until validated against the runtime physics engine.',
    '- Script travel is never executed from catalog data alone; live meso, item, quest, selection, and destination checks remain mandatory.'
)
Set-Content -LiteralPath $outputs.summary -Encoding UTF8 -Value $summaryLines

$decisionManifest = [ordered] @{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString('o')
    purpose = 'Source-derived facts and replaceable policy hints for autonomous Agent decisions.'
    partialExport = ($MapLimit -gt 0 -or $EquipmentFileLimit -gt 0)
    allRegions = [bool] $AllRegions
    regions = if ($AllRegions) { @('*') } else { @($Regions) }
    mapLimit = $MapLimit
    equipmentFileLimit = $EquipmentFileLimit
    counts = $counts
    files = @($outputs.GetEnumerator() | Where-Object { $_.Key -ne 'manifest' } | ForEach-Object { [pscustomobject] @{ name = $_.Key; path = $_.Value } })
}
$decisionManifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $outputs.manifest -Encoding UTF8

$report = [ordered]@{ schemaVersion = 1; generatedAt = (Get-Date).ToString('o'); status = 'OK'; allRegions = [bool] $AllRegions; regions = if ($AllRegions) { @('*') } else { @($Regions) }; mapLimit = $MapLimit; equipmentFileLimit = $EquipmentFileLimit; counts = $counts; summaryOnly = [bool] $SummaryOnly; rowsOmitted = [bool] $SummaryOnly; outputFiles = if ($SummaryOnly) { $null } else { @($outputs.GetEnumerator() | ForEach-Object { [pscustomobject] @{ key = $_.Key; path = $_.Value } }) } }
if ($Json) { $report | ConvertTo-Json -Depth 8 } else { Write-Host "Agent decision catalog export complete: $OutputDir"; $counts.GetEnumerator() | ForEach-Object { Write-Host "  $($_.Key): $($_.Value)" } }
