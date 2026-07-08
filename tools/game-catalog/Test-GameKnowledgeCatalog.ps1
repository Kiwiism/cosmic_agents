param(
    [string] $CatalogDir = "tmp/game-catalog",
    [string] $DropSourceClassificationPath = "docs/agents/catalog-overrides/drop-source-classifications.catalog.json",
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Add-Check {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [Parameter(Mandatory = $true)] [string] $Id,
        [Parameter(Mandatory = $true)] [string] $Status,
        [Parameter(Mandatory = $true)] [string] $Message
    )

    $Checks.Add([ordered]@{
        id = $Id
        status = $Status
        message = $Message
    }) | Out-Null
}

function Read-JsonFile {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [string] $Id,
        [string] $Path
    )

    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        Add-Check $Checks "file:$Id" "FAIL" "Missing $Id at $Path."
        return @()
    }

    Add-Check $Checks "file:$Id" "PASS" "Found $Id at $Path."

    try {
        $value = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
        Add-Check $Checks "json:$Id" "PASS" "$Id is valid JSON."
        if ($null -eq $value) {
            return @()
        }
        if ($value -is [System.Array]) {
            return @($value)
        }
        return @($value)
    } catch {
        Add-Check $Checks "json:$Id" "FAIL" "$Id is not valid JSON: $($_.Exception.Message)"
        return @()
    }
}

function Test-NonEmpty {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [string] $Id,
        [object[]] $Rows
    )

    if ($Rows.Count -gt 0) {
        Add-Check $Checks "content:$Id" "PASS" "$Id has $($Rows.Count) row(s)."
    } else {
        Add-Check $Checks "content:$Id" "FAIL" "$Id is empty."
    }
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

function Test-RequiredProperties {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [string] $Id,
        [object[]] $Rows,
        [string[]] $Properties,
        [int] $SampleSize = 200
    )

    $sample = @($Rows | Select-Object -First $SampleSize)
    foreach ($property in $Properties) {
        $missing = @($sample | Where-Object {
            -not ($_.PSObject.Properties.Name -contains $property)
        }).Count

        if ($missing -eq 0) {
            Add-Check $Checks ("shape:{0}:{1}" -f $Id, $property) "PASS" "$Id rows include $property in sampled rows."
        } else {
            Add-Check $Checks ("shape:{0}:{1}" -f $Id, $property) "FAIL" "$Id has $missing sampled row(s) missing $property."
        }
    }
}

function Test-ReferenceCoverage {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [string] $Id,
        [object[]] $Rows,
        [string] $Property,
        [System.Collections.Generic.HashSet[int]] $TargetIds,
        [int] $WarnLimit
    )

    $missing = New-Object System.Collections.Generic.HashSet[int]
    foreach ($row in $Rows) {
        $value = $row.$Property
        if ($null -eq $value -or "$value" -notmatch "^-?\d+$") {
            continue
        }
        $referencedId = [int] $value
        if (!$TargetIds.Contains($referencedId)) {
            [void] $missing.Add($referencedId)
        }
    }

    if ($missing.Count -eq 0) {
        Add-Check $Checks "xref:$Id" "PASS" "$Id has complete reference coverage."
    } elseif ($missing.Count -le $WarnLimit) {
        Add-Check $Checks "xref:$Id" "WARN" "$Id has $($missing.Count) missing referenced id(s): $(@($missing | Sort-Object | Select-Object -First 20) -join ', ')."
    } else {
        Add-Check $Checks "xref:$Id" "FAIL" "$Id has $($missing.Count) missing referenced id(s), exceeding limit $WarnLimit."
    }
}

function Read-ReviewedDropSourceIds {
    param([string] $Path)

    $set = [System.Collections.Generic.HashSet[int]]::new()
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $set
    }

    $catalog = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    foreach ($source in @($catalog.sources)) {
        if ($null -ne $source.sourceId -and "$($source.sourceId)" -match "^-?\d+$") {
            [void] $set.Add([int] $source.sourceId)
        }
    }
    return $set
}

function Test-DropSourceCoverage {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [object[]] $Drops,
        [System.Collections.Generic.HashSet[int]] $MobIds,
        [System.Collections.Generic.HashSet[int]] $ReviewedSourceIds
    )

    $missing = [System.Collections.Generic.HashSet[int]]::new()
    $reviewed = [System.Collections.Generic.HashSet[int]]::new()
    foreach ($drop in $Drops) {
        $value = $drop.sourceId
        if ($null -eq $value -or "$value" -notmatch "^-?\d+$") {
            continue
        }

        $sourceId = [int] $value
        if ($MobIds.Contains($sourceId)) {
            continue
        }
        if ($ReviewedSourceIds.Contains($sourceId)) {
            [void] $reviewed.Add($sourceId)
            continue
        }
        [void] $missing.Add($sourceId)
    }

    if ($missing.Count -eq 0) {
        Add-Check $Checks "xref:drops.sourceId_to_mobs" "PASS" "All non-mob drop source IDs are covered by reviewed classifications ($($reviewed.Count) reviewed source ID(s))."
    } else {
        Add-Check $Checks "xref:drops.sourceId_to_mobs" "WARN" "$($missing.Count) non-mob drop source ID(s) lack reviewed classification: $(@($missing | Sort-Object | Select-Object -First 20) -join ', ')."
    }
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()

$files = [ordered]@{
    maps = "generated_map_catalog.json"
    mobs = "generated_mob_catalog.json"
    drops = "generated_drop_catalog.json"
    items = "generated_item_catalog.json"
    shops = "generated_shop_catalog.json"
    quests = "generated_quest_catalog.json"
    skills = "generated_skill_catalog.json"
}

$rows = @{}
foreach ($key in $files.Keys) {
    $rows[$key] = Read-JsonFile $checks $key (Join-Path $CatalogDir $files[$key])
    Test-NonEmpty $checks $key $rows[$key]
}

Test-RequiredProperties $checks "maps" $rows.maps @("schemaVersion", "mapId", "portals", "npcIds", "mobIds", "footholdCount")
Test-RequiredProperties $checks "mobs" $rows.mobs @("schemaVersion", "mobId", "level", "maxHp", "exp", "mapIds")
Test-RequiredProperties $checks "drops" $rows.drops @("schemaVersion", "sourceType", "sourceId", "itemId", "minimumQuantity", "maximumQuantity", "questId", "chance")
Test-RequiredProperties $checks "items" $rows.items @("schemaVersion", "itemId", "category", "dropSourceCount", "shopSourceCount", "flags")
Test-RequiredProperties $checks "shops" $rows.shops @("schemaVersion", "shopId", "npcId", "mapIds", "itemCount", "items")
Test-RequiredProperties $checks "quests" $rows.quests @("schemaVersion", "questId", "requirements", "rewards", "flags")
Test-RequiredProperties $checks "skills" $rows.skills @("schemaVersion", "skillId", "sourceFile", "maxLevel")

$mapIds = New-IdSet $rows.maps "mapId"
$mobIds = New-IdSet $rows.mobs "mobId"
$itemIds = New-IdSet $rows.items "itemId"
$reviewedDropSourceIds = Read-ReviewedDropSourceIds $DropSourceClassificationPath

Test-ReferenceCoverage `
    -Checks $checks `
    -Id "drops.itemId_to_items" `
    -Rows $rows.drops `
    -Property "itemId" `
    -TargetIds $itemIds `
    -WarnLimit 0
Test-DropSourceCoverage `
    -Checks $checks `
    -Drops $rows.drops `
    -MobIds $mobIds `
    -ReviewedSourceIds $reviewedDropSourceIds

$shopItems = New-Object System.Collections.Generic.List[object]
foreach ($shop in $rows.shops) {
    foreach ($item in @($shop.items)) {
        if ($null -ne $item) {
            [void] $shopItems.Add($item)
        }
    }
}
Test-ReferenceCoverage `
    -Checks $checks `
    -Id "shopItems.itemId_to_items" `
    -Rows @($shopItems.ToArray()) `
    -Property "itemId" `
    -TargetIds $itemIds `
    -WarnLimit 0

$mapsWithPortals = @($rows.maps | Where-Object { @($_.portals).Count -gt 0 }).Count
$mapsWithMobs = @($rows.maps | Where-Object { @($_.mobIds).Count -gt 0 }).Count
$mobsWithMaps = @($rows.mobs | Where-Object { @($_.mapIds).Count -gt 0 }).Count
$itemsWithSources = @($rows.items | Where-Object { $_.dropSourceCount -gt 0 -or $_.shopSourceCount -gt 0 }).Count
$questsWithNpc = @($rows.quests | Where-Object { $null -ne $_.startNpcId -or $null -ne $_.completeNpcId }).Count

if ($mapsWithPortals -gt 0) {
    Add-Check $checks "coverage:maps-with-portals" "PASS" "$mapsWithPortals map(s) have portals."
} else {
    Add-Check $checks "coverage:maps-with-portals" "FAIL" "No maps have portals."
}

if ($mapsWithMobs -gt 0 -and $mobsWithMaps -gt 0) {
    Add-Check $checks "coverage:mob-map-links" "PASS" "$mapsWithMobs map(s) list mobs and $mobsWithMaps mob(s) list maps."
} else {
    Add-Check $checks "coverage:mob-map-links" "FAIL" "Mob/map placement links are missing."
}

if ($itemsWithSources -gt 0) {
    Add-Check $checks "coverage:item-sources" "PASS" "$itemsWithSources item(s) have drop or shop sources."
} else {
    Add-Check $checks "coverage:item-sources" "FAIL" "No items have drop or shop source counts."
}

if ($questsWithNpc -gt 0) {
    Add-Check $checks "coverage:quest-npc-links" "PASS" "$questsWithNpc quest(s) have start or complete NPC data."
} else {
    Add-Check $checks "coverage:quest-npc-links" "WARN" "No quest start/complete NPC data found."
}

foreach ($requiredMapId in @(10000, 2000000)) {
    if ($mapIds.Contains($requiredMapId)) {
        Add-Check $checks "known-map:$requiredMapId" "PASS" "Known Maple Island MVP map $requiredMapId exists."
    } else {
        Add-Check $checks "known-map:$requiredMapId" "FAIL" "Known Maple Island MVP map $requiredMapId is missing."
    }
}

foreach ($requiredQuestId in @(1008, 1021, 1046, 8020)) {
    $found = @($rows.quests | Where-Object { [int] $_.questId -eq $requiredQuestId }).Count -gt 0
    if ($found) {
        Add-Check $checks "known-quest:$requiredQuestId" "PASS" "Known Maple Island MVP quest $requiredQuestId exists."
    } else {
        Add-Check $checks "known-quest:$requiredQuestId" "FAIL" "Known Maple Island MVP quest $requiredQuestId is missing."
    }
}

$failCount = @($checks | Where-Object { $_.status -eq "FAIL" }).Count
$warnCount = @($checks | Where-Object { $_.status -eq "WARN" }).Count

$overall = if ($failCount -gt 0) {
    "FAIL"
} elseif ($warnCount -gt 0) {
    "INCOMPLETE"
} else {
    "PASS"
}

$report = [ordered]@{
    status = $overall
    repoRoot = $repoRoot
    catalogDir = $CatalogDir
    failCount = $failCount
    warnCount = $warnCount
    checkCount = @($checks).Count
    passCount = @($checks | Where-Object { $_.status -eq "PASS" }).Count
    warningIds = @($checks | Where-Object { $_.status -eq "WARN" } | ForEach-Object { $_.id })
    failureIds = @($checks | Where-Object { $_.status -eq "FAIL" } | ForEach-Object { $_.id })
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    returnedCheckCount = if ($SummaryOnly) { 0 } else { @($checks).Count }
    counts = [ordered]@{
        maps = $rows.maps.Count
        mobs = $rows.mobs.Count
        drops = $rows.drops.Count
        items = $rows.items.Count
        shops = $rows.shops.Count
        shopItems = $shopItems.Count
        quests = $rows.quests.Count
        skills = $rows.skills.Count
        reviewedNonMobDropSources = $reviewedDropSourceIds.Count
    }
    checks = if ($SummaryOnly) { $null } else { @($checks) }
}

if ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    Write-Host "Game knowledge catalog verification: $overall"
    Write-Host "Repo root: $repoRoot"
    Write-Host "Catalog dir: $CatalogDir"
    Write-Host "Failures: $failCount  Warnings: $warnCount"
    Write-Host ""

    if ($SummaryOnly) {
        Write-Host "Detailed check rows omitted."
    } else {
        foreach ($check in $checks) {
            Write-Host ("[{0}] {1} - {2}" -f $check.status, $check.id, $check.message)
        }
    }
}

if ($failCount -gt 0) {
    exit 1
}
