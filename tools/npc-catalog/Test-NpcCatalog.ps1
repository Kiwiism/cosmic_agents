param(
    [string] $CatalogDir = "tmp/npc-catalog",
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

function Test-ReferenceCoverage {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [string] $Id,
        [object[]] $Rows,
        [string] $Property,
        [System.Collections.Generic.HashSet[int]] $TargetIds,
        [int] $WarnLimit
    )

    $missing = [System.Collections.Generic.HashSet[int]]::new()
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

function Get-IndexPropertyNames {
    param([object[]] $Rows)

    if ($Rows.Count -eq 0) {
        return @()
    }
    return @($Rows[0].PSObject.Properties.Name)
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()

$files = [ordered]@{
    npcs = "generated_npc_catalog.json"
    placements = "generated_npc_placements.json"
    approach = "generated_npc_approach_points.json"
    dialogueTiming = "generated_quest_dialogue_timing.json"
    actions = "generated_npc_action_catalog.json"
    dialogueOptions = "generated_npc_dialogue_options.json"
    services = "generated_npc_services.json"
    rewardChoices = "generated_npc_reward_choices.json"
    shopInventory = "generated_npc_shop_inventory.json"
    fastIndexes = "generated_npc_fast_indexes.json"
    mapNpcSummary = "generated_map_npc_summary.json"
}

$rows = @{}
foreach ($key in $files.Keys) {
    $rows[$key] = Read-JsonFile $checks $key (Join-Path $CatalogDir $files[$key])
    Test-NonEmpty $checks $key $rows[$key]
}

foreach ($reportFile in @("NPC_CATALOG_SUMMARY.md", "NPC_CATALOG_VALIDATION.md", "NPC_CATALOG_GAPS.md")) {
    $path = Join-Path $CatalogDir $reportFile
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        Add-Check $checks "file:$reportFile" "PASS" "Found report $reportFile."
    } else {
        Add-Check $checks "file:$reportFile" "WARN" "Missing review report $reportFile."
    }
}

Test-RequiredProperties $checks "npcs" $rows.npcs @("schemaVersion", "npcId", "name", "interactions", "placements", "approach", "timing", "automation", "confidence")
Test-RequiredProperties $checks "placements" $rows.placements @("mapId", "lifeIndex", "npcId", "x", "y", "footholdId")
Test-RequiredProperties $checks "approach" $rows.approach @("schemaVersion", "mapId", "lifeIndex", "npcId", "npcPosition", "interactionBox", "candidates")
Test-RequiredProperties $checks "dialogueTiming" $rows.dialogueTiming @("schemaVersion", "questId", "phase", "firstReadDelayMsRange", "repeatReadDelayMsRange", "source")
Test-RequiredProperties $checks "actions" $rows.actions @("schemaVersion", "questId", "phase", "npcId", "actionType")
Test-RequiredProperties $checks "dialogueOptions" $rows.dialogueOptions @("schemaVersion", "npcId", "scriptName", "actionType", "confidence")
Test-RequiredProperties $checks "services" $rows.services @("schemaVersion", "npcId", "scriptName", "serviceType", "confidence")
Test-RequiredProperties $checks "rewardChoices" $rows.rewardChoices @("schemaVersion", "questId", "phase", "npcId", "candidates")
Test-RequiredProperties $checks "shopInventory" $rows.shopInventory @("schemaVersion", "inventoryKey", "npcId", "shopId", "itemCount", "items")
Test-RequiredProperties $checks "mapNpcSummary" $rows.mapNpcSummary @("schemaVersion", "mapId", "npcPlacementCount", "uniqueNpcCount", "npcIds")

$npcIds = New-IdSet $rows.npcs "npcId"
$placementNpcIds = New-IdSet $rows.placements "npcId"

Test-ReferenceCoverage -Checks $checks -Id "placements.npcId_to_npcs" -Rows $rows.placements -Property "npcId" -TargetIds $npcIds -WarnLimit 0
Test-ReferenceCoverage -Checks $checks -Id "approach.npcId_to_npcs" -Rows $rows.approach -Property "npcId" -TargetIds $npcIds -WarnLimit 0
Test-ReferenceCoverage -Checks $checks -Id "actions.npcId_to_npcs" -Rows $rows.actions -Property "npcId" -TargetIds $npcIds -WarnLimit 25
Test-ReferenceCoverage -Checks $checks -Id "shopInventory.npcId_to_npcs" -Rows $rows.shopInventory -Property "npcId" -TargetIds $npcIds -WarnLimit 0

$placedNpcCount = @($rows.npcs | Where-Object { @($_.placements).Count -gt 0 }).Count
$shopNpcCount = @($rows.npcs | Where-Object { $_.interactions.shop.hasShop }).Count
$questNpcCount = @($rows.npcs | Where-Object {
    @($_.interactions.quests.starts).Count -gt 0 -or @($_.interactions.quests.completes).Count -gt 0
}).Count
$approachWithCandidates = @($rows.approach | Where-Object { @($_.candidates).Count -gt 0 }).Count
$dialogueTimingWithDelays = @($rows.dialogueTiming | Where-Object {
    @($_.firstReadDelayMsRange).Count -eq 2 -and @($_.repeatReadDelayMsRange).Count -eq 2
}).Count
$doNotAutoUseCount = @($rows.npcs | Where-Object { $_.automation.doNotAutoUse }).Count

if ($placedNpcCount -gt 0) {
    Add-Check $checks "coverage:placed-npcs" "PASS" "$placedNpcCount NPC(s) have placements."
} else {
    Add-Check $checks "coverage:placed-npcs" "FAIL" "No NPCs have placements."
}

if ($shopNpcCount -gt 0) {
    Add-Check $checks "coverage:shop-npcs" "PASS" "$shopNpcCount NPC(s) have shops."
} else {
    Add-Check $checks "coverage:shop-npcs" "WARN" "No shop NPCs found."
}

if ($questNpcCount -gt 0) {
    Add-Check $checks "coverage:quest-npcs" "PASS" "$questNpcCount NPC(s) have quest start/complete interactions."
} else {
    Add-Check $checks "coverage:quest-npcs" "FAIL" "No quest NPC interactions found."
}

if ($approachWithCandidates -gt 0) {
    Add-Check $checks "coverage:approach-candidates" "PASS" "$approachWithCandidates NPC placement(s) have generated approach candidates."
} else {
    Add-Check $checks "coverage:approach-candidates" "FAIL" "No approach candidates found."
}

if ($dialogueTimingWithDelays -gt 0) {
    Add-Check $checks "coverage:dialogue-timing" "PASS" "$dialogueTimingWithDelays quest dialogue timing row(s) have delay ranges."
} else {
    Add-Check $checks "coverage:dialogue-timing" "WARN" "No dialogue timing delay ranges found."
}

if ($doNotAutoUseCount -gt 0) {
    Add-Check $checks "review:do-not-auto-use" "PASS" "$doNotAutoUseCount NPC(s) are gated as do-not-auto-use for review."
} else {
    Add-Check $checks "review:do-not-auto-use" "WARN" "No do-not-auto-use NPC review gates found."
}

$fastIndexProperties = Get-IndexPropertyNames $rows.fastIndexes
foreach ($indexName in @("npcId_to_catalogRow", "npcId_to_placements", "mapId_to_npcPlacements", "npcId_to_questActions", "questId_phase_to_action")) {
    if ($fastIndexProperties -contains $indexName) {
        Add-Check $checks "fast-index:$indexName" "PASS" "Fast index contains $indexName."
    } else {
        Add-Check $checks "fast-index:$indexName" "FAIL" "Fast index is missing $indexName."
    }
}

foreach ($requiredNpcId in @(2100, 2101, 2102, 22000)) {
    if ($npcIds.Contains($requiredNpcId)) {
        Add-Check $checks "known-npc:$requiredNpcId" "PASS" "Known Maple Island NPC $requiredNpcId exists."
    } else {
        Add-Check $checks "known-npc:$requiredNpcId" "WARN" "Known Maple Island NPC $requiredNpcId is missing; verify current WZ version."
    }
}

$mapleIslandPlacements = @($rows.placements | Where-Object {
    $_.mapId -in @(10000, 20000, 30000, 30001, 40000, 50000, 1000000, 1010000, 1020000, 2000000)
}).Count
if ($mapleIslandPlacements -gt 0) {
    Add-Check $checks "mvp:maple-island-placements" "PASS" "$mapleIslandPlacements NPC placement(s) found on Maple Island MVP route maps."
} else {
    Add-Check $checks "mvp:maple-island-placements" "FAIL" "No NPC placements found on Maple Island MVP route maps."
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
        npcs = $rows.npcs.Count
        placements = $rows.placements.Count
        approach = $rows.approach.Count
        dialogueTiming = $rows.dialogueTiming.Count
        actions = $rows.actions.Count
        dialogueOptions = $rows.dialogueOptions.Count
        services = $rows.services.Count
        rewardChoices = $rows.rewardChoices.Count
        shopInventory = $rows.shopInventory.Count
        mapNpcSummary = $rows.mapNpcSummary.Count
        placedNpcs = $placedNpcCount
        shopNpcs = $shopNpcCount
        questNpcs = $questNpcCount
        doNotAutoUse = $doNotAutoUseCount
    }
    checks = if ($SummaryOnly) { $null } else { @($checks) }
}

if ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    Write-Host "NPC catalog verification: $overall"
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
