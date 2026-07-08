param(
    [string] $WzRoot = "wz",
    [string] $SqlRoot = "src/main/resources/db",
    [string] $ScriptRoot = "scripts",
    [string] $GameCatalogDir = "tmp/game-catalog",
    [string] $OutputDir = "tmp/reactor-catalog",
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

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

    $value = Get-AttrValue (Get-ChildByName $Node $Name) "value"
    if ([string]::IsNullOrWhiteSpace($value)) {
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

function ConvertTo-RelativePath {
    param([string] $Path)

    $full = (Get-Item -LiteralPath $Path).FullName
    if ($full.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $full.Substring($repoRoot.Length).TrimStart("\", "/") -replace "\\", "/"
    }
    return $full -replace "\\", "/"
}

function Read-MapNames {
    param([string] $CatalogDir)

    $result = New-Object 'System.Collections.Generic.Dictionary[int,string]'
    $path = Join-Path $CatalogDir "generated_map_catalog.json"
    if (!(Test-Path -LiteralPath $path -PathType Leaf)) {
        return ,$result
    }

    $rows = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    foreach ($row in @($rows | ForEach-Object { $_ })) {
        $labelParts = @()
        if (![string]::IsNullOrWhiteSpace([string] $row.streetName)) {
            $labelParts += [string] $row.streetName
        }
        if (![string]::IsNullOrWhiteSpace([string] $row.mapName)) {
            $labelParts += [string] $row.mapName
        }
        $result[[int] $row.mapId] = ($labelParts -join " - ")
    }
    return ,$result
}

function Read-ReactorInfo {
    param([string] $ReactorRoot)

    $result = New-Object 'System.Collections.Generic.Dictionary[string,object]'
    if (!(Test-Path -LiteralPath $ReactorRoot -PathType Container)) {
        return ,$result
    }

    foreach ($file in Get-ChildItem -LiteralPath $ReactorRoot -Filter "*.img.xml") {
        $idText = $file.BaseName -replace "\.img$", ""
        if ($idText -notmatch "^\d+$") {
            continue
        }

        $id = [int] $idText
        $doc = Load-XmlDocument $file.FullName
        $info = Get-ChildByName $doc.DocumentElement "info"
        $action = Get-StringChildValue $doc.DocumentElement "action"
        $description = Get-StringChildValue $info "info"
        $stateTypes = New-Object System.Collections.Generic.List[int]
        $activatesByTouch = (Get-IntChildValue $info "activateByTouch") -eq 1

        foreach ($state in $doc.DocumentElement.ChildNodes) {
            $stateName = Get-AttrValue $state "name"
            if ($stateName -notmatch "^\d+$") {
                continue
            }
            foreach ($event in $state.SelectNodes("imgdir[@name='event']/imgdir")) {
                $type = Get-IntChildValue $event "type"
                if ($null -ne $type -and -not $stateTypes.Contains($type)) {
                    [void] $stateTypes.Add($type)
                }
            }
        }

        $result[[string] $id] = [ordered]@{
            reactorInfoSource = ConvertTo-RelativePath $file.FullName
            reactorAction = $action
            reactorDescription = $description
            activatesByTouch = [bool] $activatesByTouch
            stateTypes = @($stateTypes | Sort-Object)
        }
    }
    return ,$result
}

function Read-ReactorDrops {
    param([string] $Root)

    $result = New-Object 'System.Collections.Generic.Dictionary[string,object]'
    $candidates = @(
        (Join-Path $Root "data/131-reactordrops-data.sql"),
        (Join-Path $Root "db/data/131-reactordrops-data.sql"),
        (Join-Path "src/main/resources/db" "data/131-reactordrops-data.sql")
    )
    $path = @($candidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1)
    if ($path.Count -eq 0) {
        return ,$result
    }

    $text = Get-Content -LiteralPath $path[0] -Raw
    foreach ($match in [regex]::Matches($text, "\(([^()]*)\)")) {
        $parts = @($match.Groups[1].Value -split "," | ForEach-Object { $_.Trim().Trim("'") })
        if ($parts.Count -lt 4) {
            continue
        }
        if ($parts[0] -notmatch "^-?\d+$" -or $parts[1] -notmatch "^-?\d+$") {
            continue
        }

        $reactorId = [int] $parts[0]
        $drop = [ordered]@{
            itemId = [int] $parts[1]
            chance = if ($parts[2] -match "^-?\d+$") { [int] $parts[2] } else { $null }
            questId = if ($parts[3] -match "^-?\d+$") { [int] $parts[3] } else { $null }
            source = ConvertTo-RelativePath $path[0]
        }
        $key = [string] $reactorId
        if (!$result.ContainsKey($key)) {
            $result[$key] = New-Object System.Collections.Generic.List[object]
        }
        [void] $result[$key].Add($drop)
    }
    return ,$result
}

function Read-ReactorScriptHints {
    param([string] $Root)

    $result = New-Object 'System.Collections.Generic.Dictionary[string,object]'
    $reactorScriptDir = Join-Path $Root "reactor"
    if (!(Test-Path -LiteralPath $reactorScriptDir -PathType Container)) {
        return ,$result
    }

    foreach ($file in Get-ChildItem -LiteralPath $reactorScriptDir -Filter "*.js") {
        $idText = $file.BaseName
        if ($idText -notmatch "^\d+$") {
            continue
        }
        $text = Get-Content -LiteralPath $file.FullName -Raw
        $itemIds = New-Object System.Collections.Generic.HashSet[int]
        $questIds = New-Object System.Collections.Generic.HashSet[int]
        foreach ($match in [regex]::Matches($text, "\b[1-5]\d{6}\b")) {
            [void] $itemIds.Add([int] $match.Value)
        }
        foreach ($match in [regex]::Matches($text, "\b(?:10\d{2}|20\d{2}|30\d{2}|60\d{2})\b")) {
            [void] $questIds.Add([int] $match.Value)
        }
        $result[[string] ([int] $idText)] = [ordered]@{
            scriptSource = ConvertTo-RelativePath $file.FullName
            inferredItemIdsFromScript = @($itemIds | Sort-Object)
            inferredQuestIdsFromScript = @($questIds | Sort-Object)
        }
    }
    return ,$result
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$mapNames = Read-MapNames $GameCatalogDir
$reactorInfo = Read-ReactorInfo (Join-Path $WzRoot "Reactor.wz")
$reactorDrops = Read-ReactorDrops $SqlRoot
$scriptHints = Read-ReactorScriptHints $ScriptRoot
$entries = New-Object System.Collections.Generic.List[object]
$mapRoot = Join-Path $WzRoot "Map.wz/Map"

foreach ($file in Get-ChildItem -LiteralPath $mapRoot -Recurse -Filter "*.img.xml") {
    $idText = $file.BaseName -replace "\.img$", ""
    if ($idText -notmatch "^\d+$") {
        continue
    }
    $mapId = [int] $idText
    $doc = Load-XmlDocument $file.FullName
    $reactors = $doc.SelectNodes("/imgdir/imgdir[@name='reactor']/imgdir")
    foreach ($node in $reactors) {
        $reactorIdText = Get-StringChildValue $node "id"
        if ([string]::IsNullOrWhiteSpace($reactorIdText) -or $reactorIdText -notmatch "^\d+$") {
            continue
        }
        $reactorId = [int] $reactorIdText
        $reactorKey = [string] $reactorId
        $dropValue = $null
        $scriptValue = $null
        $infoValue = $null
        $drops = @()
        if ($reactorDrops.TryGetValue($reactorKey, [ref] $dropValue) -and $null -ne $dropValue) {
            $dropRows = New-Object System.Collections.Generic.List[object]
            foreach ($dropRow in $dropValue) {
                [void] $dropRows.Add($dropRow)
            }
            $drops = @($dropRows | ForEach-Object { $_ })
        }
        $script = if ($scriptHints.TryGetValue($reactorKey, [ref] $scriptValue)) { $scriptValue } else { $null }
        $info = if ($reactorInfo.TryGetValue($reactorKey, [ref] $infoValue)) { $infoValue } else { $null }
        $questIds = New-Object System.Collections.Generic.HashSet[int]
        $itemIds = New-Object System.Collections.Generic.HashSet[int]
        foreach ($drop in $drops) {
            if ($null -ne $drop.questId -and [int] $drop.questId -gt 0) { [void] $questIds.Add([int] $drop.questId) }
            if ($null -ne $drop.itemId -and [int] $drop.itemId -gt 0) { [void] $itemIds.Add([int] $drop.itemId) }
        }
        if ($script) {
            foreach ($questId in @($script.inferredQuestIdsFromScript)) { [void] $questIds.Add([int] $questId) }
            foreach ($itemId in @($script.inferredItemIdsFromScript)) { [void] $itemIds.Add([int] $itemId) }
        }

        $flags = New-Object System.Collections.Generic.List[string]
        if ($drops.Count -gt 0) { [void] $flags.Add("has-reactor-drop-table") }
        if ($script) { [void] $flags.Add("has-reactor-script") }
        if ($info) { [void] $flags.Add("has-reactor-wz-info") }
        if ($questIds.Contains(1008) -or $itemIds.Contains(4031161) -or $itemIds.Contains(4031162)) {
            [void] $flags.Add("maple-island-pio-candidate")
        }

        [void] $entries.Add([ordered]@{
            schemaVersion = 1
            objectType = "reactor"
            mapId = $mapId
            mapName = if ($mapNames.ContainsKey($mapId)) { $mapNames[$mapId] } else { $null }
            objectIndex = Get-AttrValue $node "name"
            reactorId = $reactorId
            reactorName = Get-StringChildValue $node "name"
            x = Get-IntChildValue $node "x"
            y = Get-IntChildValue $node "y"
            footholdId = Get-IntChildValue $node "fh"
            facing = Get-IntChildValue $node "f"
            reactorTimeSeconds = Get-IntChildValue $node "reactorTime"
            scriptSource = if ($script) { $script.scriptSource } else { $null }
            mapSource = ConvertTo-RelativePath $file.FullName
            reactorInfoSource = if ($info) { $info.reactorInfoSource } else { $null }
            reactorAction = if ($info) { $info.reactorAction } else { $null }
            reactorDescription = if ($info) { $info.reactorDescription } else { $null }
            activatesByTouch = if ($info) { $info.activatesByTouch } else { $false }
            stateTypes = if ($info) { @($info.stateTypes) } else { @() }
            inferredQuestIds = @($questIds | Sort-Object)
            inferredItemIds = @($itemIds | Sort-Object)
            drops = @($drops)
            confidence = if ($drops.Count -gt 0 -or $script -or $info) { "generated" } else { "low-confidence" }
            flags = @($flags)
        })
    }
}

$entryArray = @($entries | ForEach-Object { $_ })
$pioReactors = @($entryArray | Where-Object {
    ($_.inferredQuestIds -contains 1008) -or
    ($_.inferredItemIds -contains 4031161) -or
    ($_.inferredItemIds -contains 4031162)
})
$reactorMapIds = New-Object System.Collections.Generic.HashSet[int]
$reactorIds = New-Object System.Collections.Generic.HashSet[int]
foreach ($entry in $entryArray) {
    [void] $reactorMapIds.Add([int] $entry.mapId)
    [void] $reactorIds.Add([int] $entry.reactorId)
}
$acceptedGaps = New-Object System.Collections.Generic.List[object]
if ($pioReactors.Count -eq 0) {
    [void] $acceptedGaps.Add([ordered]@{
        id = "maple-island-pio-reactors-not-linked"
        reason = "No generated reactor placement links to quest 1008 or items 4031161/4031162 in available WZ/SQL/script sources."
    })
}

$acceptedGapArray = @($acceptedGaps | ForEach-Object { $_ })

$catalog = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    source = [ordered]@{
        wzRoot = $WzRoot
        sqlRoot = $SqlRoot
        scriptRoot = $ScriptRoot
        gameCatalogDir = $GameCatalogDir
    }
    counts = [ordered]@{
        entries = $entryArray.Count
        mapsWithReactors = $reactorMapIds.Count
        distinctReactorIds = $reactorIds.Count
        mapleIslandPioCandidatePlacements = $pioReactors.Count
        acceptedGaps = $acceptedGapArray.Count
    }
    acceptedGaps = $acceptedGapArray
    entries = $entryArray
}

$catalogPath = Join-Path $OutputDir "generated_reactor_catalog.json"
$catalog | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $catalogPath -Encoding UTF8

$summaryPath = Join-Path $OutputDir "REACTOR_CATALOG_SUMMARY.md"
$summary = @(
    "# Reactor Catalog Summary",
    "",
    "Generated: $($catalog.generatedAt)",
    "",
    "| Metric | Value |",
    "| --- | ---: |",
    "| Entries | $($catalog.counts.entries) |",
    "| Maps with reactors | $($catalog.counts.mapsWithReactors) |",
    "| Distinct reactor ids | $($catalog.counts.distinctReactorIds) |",
    "| Maple Island/Pio candidates | $($catalog.counts.mapleIslandPioCandidatePlacements) |",
    "| Accepted gaps | $($catalog.counts.acceptedGaps) |",
    "",
    "## Source Files",
    "",
    "- Map placements: ``$WzRoot/Map.wz/Map/**/*.img.xml``",
    "- Reactor WZ metadata: ``$WzRoot/Reactor.wz/*.img.xml``",
    "- Reactor drops: ``$SqlRoot/data/131-reactordrops-data.sql``",
    "- Reactor scripts: ``$ScriptRoot/reactor/*.js``",
    "",
    "## Accepted Gaps",
    ""
)
if ($acceptedGaps.Count -eq 0) {
    $summary += "- None."
} else {
    foreach ($gap in $acceptedGaps) {
        $summary += "- `$($gap.id)`: $($gap.reason)"
    }
}
$summary -join "`n" | Set-Content -LiteralPath $summaryPath -Encoding UTF8

$exportStatus = "EMPTY"
if ([int] $catalog.counts.entries -gt 0) {
    $exportStatus = "OK"
}

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = $catalog.generatedAt
    status = $exportStatus
    catalogPath = (Get-Item -LiteralPath $catalogPath).FullName
    summaryPath = (Get-Item -LiteralPath $summaryPath).FullName
    counts = [ordered]@{
        entries = [int] $catalog.counts.entries
        mapsWithReactors = [int] $catalog.counts.mapsWithReactors
        distinctReactorIds = [int] $catalog.counts.distinctReactorIds
        mapleIslandPioCandidatePlacements = [int] $catalog.counts.mapleIslandPioCandidatePlacements
        acceptedGaps = [int] $catalog.counts.acceptedGaps
    }
    acceptedGaps = $acceptedGapArray
    summaryOnly = [bool] $SummaryOnly
}

if ($Json) {
    $report | ConvertTo-Json -Depth 8
    return
}

Write-Host "Reactor catalog export: $($report.status)"
Write-Host "Catalog: $catalogPath"
Write-Host "Entries: $($entryArray.Count)"
if ($acceptedGaps.Count -gt 0) {
    Write-Host "Accepted gaps: $($acceptedGaps.Count)"
}
