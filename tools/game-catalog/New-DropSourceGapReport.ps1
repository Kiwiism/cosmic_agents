param(
    [string] $CatalogDir = "tmp/game-catalog",
    [string] $OutputPath,
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Read-JsonArray {
    param([string] $Path)

    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        return @()
    }

    $value = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    if ($null -eq $value) {
        return @()
    }
    if ($value -is [System.Array]) {
        return @($value)
    }
    return @($value)
}

function New-IdSet {
    param(
        [object[]] $Rows,
        [string] $Property
    )

    $set = [System.Collections.Generic.HashSet[int]]::new()
    foreach ($row in $Rows) {
        $value = $row.$Property
        if ($null -ne $value -and "$value" -match "^-?\d+$") {
            [void] $set.Add([int] $value)
        }
    }
    return $set
}

function Read-SqlTupleFirstColumnSet {
    param([string] $Path)

    $set = [System.Collections.Generic.HashSet[int]]::new()
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $set
    }

    $text = Get-Content -LiteralPath $Path -Raw
    foreach ($match in [regex]::Matches($text, "\(([^()]*)\)")) {
        $first = (($match.Groups[1].Value -split ",")[0]).Trim().Trim("'")
        if ($first -match "^-?\d+$") {
            [void] $set.Add([int] $first)
        }
    }
    return $set
}

function Get-SourceClass {
    param(
        [int] $SourceId,
        [System.Collections.Generic.HashSet[int]] $MobIds,
        [System.Collections.Generic.HashSet[int]] $ItemIds,
        [System.Collections.Generic.HashSet[int]] $GlobalDropIds,
        [System.Collections.Generic.HashSet[int]] $ReactorDropIds
    )

    if ($MobIds.Contains($SourceId)) {
        return "mob"
    }
    if ($GlobalDropIds.Contains($SourceId)) {
        return "global-drop-source"
    }
    if ($ReactorDropIds.Contains($SourceId)) {
        return "reactor-drop-source"
    }
    if ($ItemIds.Contains($SourceId)) {
        return "item-id-as-source"
    }
    if ($SourceId -ge 9000000 -and $SourceId -lt 10000000) {
        return "event-or-special-mob-range"
    }
    if ($SourceId -ge 1000000 -and $SourceId -lt 6000000) {
        return "item-id-range"
    }
    if ($SourceId -lt 1000000) {
        return "low-id-special-source"
    }
    return "unknown"
}

function ConvertTo-MarkdownReport {
    param([object] $Report)

    $lines = [System.Collections.Generic.List[string]]::new()
    [void] $lines.Add("# Drop Source Gap Report")
    [void] $lines.Add("")
    [void] $lines.Add("Generated: $($Report.generatedAt)")
    [void] $lines.Add("")
    [void] $lines.Add("| Field | Value |")
    [void] $lines.Add("| --- | --- |")
    [void] $lines.Add(("| Catalog dir | `{0}` |" -f $Report.catalogDir))
    [void] $lines.Add("| Missing source IDs | $($Report.summary.missingSourceIds) |")
    [void] $lines.Add("| Drop rows affected | $($Report.summary.affectedDropRows) |")
    [void] $lines.Add("")

    [void] $lines.Add("## Classification")
    [void] $lines.Add("")
    [void] $lines.Add("| Class | Source IDs | Drop Rows |")
    [void] $lines.Add("| --- | ---: | ---: |")
    foreach ($class in @($Report.classes)) {
        [void] $lines.Add("| $($class.sourceClass) | $($class.sourceIdCount) | $($class.dropRowCount) |")
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Missing Source IDs")
    [void] $lines.Add("")
    [void] $lines.Add("| Source ID | Class | Drop Rows | Sample Items |")
    [void] $lines.Add("| ---: | --- | ---: | --- |")
    foreach ($source in @($Report.sources)) {
        $sampleItems = @($source.sampleItems | ForEach-Object {
            if ($_.itemName) {
                "$($_.itemId) $($_.itemName)"
            } else {
                [string] $_.itemId
            }
        }) -join ", "
        $sampleItems = $sampleItems.Replace("|", "\|")
        [void] $lines.Add("| $($source.sourceId) | $($source.sourceClass) | $($source.dropRowCount) | $sampleItems |")
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Notes")
    [void] $lines.Add("")
    [void] $lines.Add("- This is a review report for generated catalog data only.")
    [void] $lines.Add("- It does not modify runtime code, Agent behavior, BotClient behavior, or config.")
    [void] $lines.Add("- `item-id-as-source` and `item-id-range` rows usually mean the SQL source is using a non-mob dropper convention.")
    [void] $lines.Add("- `event-or-special-mob-range` rows often need WZ/source review before runtime treats them as normal mobs.")
    [void] $lines.Add("- Runtime planners should not assume every drop source is a killable mob.")

    return ($lines -join "`n")
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$drops = Read-JsonArray (Join-Path $CatalogDir "generated_drop_catalog.json")
$mobs = Read-JsonArray (Join-Path $CatalogDir "generated_mob_catalog.json")
$items = Read-JsonArray (Join-Path $CatalogDir "generated_item_catalog.json")

$mobIds = New-IdSet $mobs "mobId"
$itemIds = New-IdSet $items "itemId"
$globalDropSourceIds = Read-SqlTupleFirstColumnSet "src/main/resources/db/data/151-global-drop-data.sql"
$reactorDropSourceIds = Read-SqlTupleFirstColumnSet "src/main/resources/db/data/131-reactordrops-data.sql"

$itemById = @{}
foreach ($item in $items) {
    if ($null -ne $item.itemId) {
        $itemById[[int] $item.itemId] = $item
    }
}

$missingBySource = @{}
foreach ($drop in $drops) {
    if ($null -eq $drop.sourceId -or "$($drop.sourceId)" -notmatch "^-?\d+$") {
        continue
    }

    $sourceId = [int] $drop.sourceId
    if ($mobIds.Contains($sourceId)) {
        continue
    }

    $key = [string] $sourceId
    if (!$missingBySource.ContainsKey($key)) {
        $missingBySource[$key] = [System.Collections.Generic.List[object]]::new()
    }
    [void] $missingBySource[$key].Add($drop)
}

$sourceRows = [System.Collections.Generic.List[object]]::new()
foreach ($key in @($missingBySource.Keys | Sort-Object { [int] $_ })) {
    $sourceId = [int] $key
    $sourceDrops = @($missingBySource[$key].ToArray())
    $sourceClass = Get-SourceClass $sourceId $mobIds $itemIds $globalDropSourceIds $reactorDropSourceIds
    $sampleItems = @($sourceDrops |
        Select-Object -First 8 |
        ForEach-Object {
            $item = $itemById[[int] $_.itemId]
            [pscustomobject]@{
                itemId = [int] $_.itemId
                itemName = if ($item) { $item.name } else { $_.itemName }
                questId = $_.questId
                chance = $_.chance
            }
        })

    [void] $sourceRows.Add([pscustomobject]@{
        sourceId = $sourceId
        sourceClass = $sourceClass
        dropRowCount = $sourceDrops.Count
        sampleItems = $sampleItems
    })
}

$classRows = @($sourceRows |
    Group-Object sourceClass |
    Sort-Object Name |
    ForEach-Object {
        [pscustomobject]@{
            sourceClass = $_.Name
            sourceIdCount = $_.Count
            dropRowCount = (@($_.Group | ForEach-Object { $_.dropRowCount }) | Measure-Object -Sum).Sum
        }
    })

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    repoRoot = $repoRoot
    catalogDir = $CatalogDir
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    sourceCount = $sourceRows.Count
    returnedSourceCount = if ($SummaryOnly) { 0 } else { $sourceRows.Count }
    classCount = $classRows.Count
    returnedClassCount = $classRows.Count
    summary = [ordered]@{
        missingSourceIds = $sourceRows.Count
        affectedDropRows = (@($sourceRows | ForEach-Object { $_.dropRowCount }) | Measure-Object -Sum).Sum
    }
    classes = @($classRows)
    sources = if ($SummaryOnly) { $null } else { @($sourceRows.ToArray()) }
}

if ($OutputPath) {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and !(Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }

    if ($Json) {
        $report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    } else {
        ConvertTo-MarkdownReport ([pscustomobject] $report) | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    }

    Write-Host "Drop source gap report written:"
    Write-Host "  $OutputPath"
} elseif ($Json) {
    $report | ConvertTo-Json -Depth 10
} else {
    ConvertTo-MarkdownReport ([pscustomobject] $report)
}
