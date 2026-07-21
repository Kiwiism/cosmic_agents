param(
    [string] $WzRoot = "wz",
    [string] $GameCatalogDir = "tmp/game-catalog",
    [string] $CatalogDir = "tmp/agent-llm-catalog",
    [switch] $AllowPartial,
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Add-Check {
    param([System.Collections.Generic.List[object]] $Checks, [string] $Id, [string] $Status, [string] $Message)
    $Checks.Add([ordered] @{ id = $Id; status = $Status; message = $Message }) | Out-Null
}

function Read-Catalog {
    param([System.Collections.Generic.List[object]] $Checks, [string] $Id, [string] $Path)
    if (!(Test-Path -LiteralPath $Path)) { Add-Check $Checks "file:$Id" "FAIL" "Missing $Id at $Path."; return $null }
    try {
        $value = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
        Add-Check $Checks "json:$Id" "PASS" "$Id is valid JSON."
        return $value
    } catch {
        Add-Check $Checks "json:$Id" "FAIL" "$Id is invalid JSON: $($_.Exception.Message)"
        return $null
    }
}

function Assert-NonEmpty {
    param([System.Collections.Generic.List[object]] $Checks, [string] $Id, [object] $Value)
    if (@($Value).Count -gt 0) { Add-Check $Checks "content:$Id" "PASS" "$Id has $(@($Value).Count) row(s)." } else { Add-Check $Checks "content:$Id" "FAIL" "$Id is empty." }
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

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) { throw "git rev-parse --show-toplevel failed: $repoRootText" }
$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()
$files = [ordered] @{
    navigation = Join-Path $CatalogDir 'generated_navigation_topology_catalog.json'
    combat = Join-Path $CatalogDir 'generated_combat_map_policy_catalog.json'
    travel = Join-Path $CatalogDir 'generated_travel_service_catalog.json'
    progressionItems = Join-Path $CatalogDir 'generated_progression_item_policy_catalog.json'
    questChains = Join-Path $CatalogDir 'generated_quest_chain_policy_catalog.json'
    manifest = Join-Path $CatalogDir 'generated_agent_decision_catalog_manifest.json'
}
$navigation = Read-Catalog $checks 'navigation' $files.navigation
$combat = Read-Catalog $checks 'combat' $files.combat
$travel = Read-Catalog $checks 'travel' $files.travel
$progressionItems = Read-Catalog $checks 'progression-items' $files.progressionItems
$questChains = Read-Catalog $checks 'quest-chains' $files.questChains
$manifest = Read-Catalog $checks 'manifest' $files.manifest

Assert-NonEmpty $checks 'navigation' $navigation
Assert-NonEmpty $checks 'combat' $combat
Assert-NonEmpty $checks 'travel' $travel
Assert-NonEmpty $checks 'equipment' $progressionItems.equipment
Assert-NonEmpty $checks 'supplies' $progressionItems.supplies
Assert-NonEmpty $checks 'scrolls' $progressionItems.scrolls
Assert-NonEmpty $checks 'quest-chains' $questChains

if ($null -ne $manifest) {
    if (!$manifest.partialExport) { Add-Check $checks 'manifest:full-export' 'PASS' 'Decision manifest records a full export.' } elseif ($AllowPartial) { Add-Check $checks 'manifest:full-export' 'WARN' 'Decision manifest correctly records a partial smoke export.' } else { Add-Check $checks 'manifest:full-export' 'FAIL' 'Partial decision catalogs cannot pass the production gate.' }
    $countMatches = ([int] $manifest.counts.navigationMaps -eq @($navigation).Count) -and ([int] $manifest.counts.combatMaps -eq @($combat).Count) -and ([int] $manifest.counts.questPolicies -eq @($questChains).Count)
    if ($countMatches) { Add-Check $checks 'manifest:counts' 'PASS' 'Manifest counts match the generated catalog rows.' } else { Add-Check $checks 'manifest:counts' 'FAIL' 'Manifest counts do not match generated catalog rows.' }
}

if ($null -ne $navigation) {
    $badComponentRefs = New-Object System.Collections.ArrayList
    $badTransitionAuthority = New-Object System.Collections.ArrayList
    foreach ($map in @($navigation)) {
        $componentIds = @($map.components | ForEach-Object { [int] $_.componentId })
        foreach ($foothold in @($map.footholds)) {
            if (!($componentIds -contains [int] $foothold.componentId)) { [void] $badComponentRefs.Add("$($map.mapId):foothold:$($foothold.footholdId)") }
        }
        foreach ($climb in @($map.climbables)) {
            if ($climb.type -notin @('ladder','rope')) { [void] $badComponentRefs.Add("$($map.mapId):climb:$($climb.climbId):type") }
            if ($climb.executable -or !$climb.requiresRuntimePhysicsValidation) { [void] $badTransitionAuthority.Add("$($map.mapId):climb:$($climb.climbId)") }
        }
        foreach ($transition in @($map.transitionCandidates)) {
            if ($transition.executable -or !$transition.requiresRuntimePhysicsValidation) { [void] $badTransitionAuthority.Add("$($map.mapId):$($transition.type)") }
        }
    }
    if ($badComponentRefs.Count -eq 0) { Add-Check $checks 'navigation:component-integrity' 'PASS' 'All footholds and climb types have valid component structure.' } else { Add-Check $checks 'navigation:component-integrity' 'FAIL' "$($badComponentRefs.Count) invalid topology reference(s)." }
    if ($badTransitionAuthority.Count -eq 0) { Add-Check $checks 'navigation:runtime-authority' 'PASS' 'Every inferred movement transition requires runtime physics validation.' } else { Add-Check $checks 'navigation:runtime-authority' 'FAIL' "$($badTransitionAuthority.Count) inferred transition(s) incorrectly claim execution authority." }

    $mapCatalog = Get-Content -LiteralPath (Join-Path $GameCatalogDir 'generated_map_catalog.json') -Raw | ConvertFrom-Json
    $coverageRegions = @($manifest.regions)
    $expected = @($mapCatalog | Where-Object { [int] $_.footholdCount -gt 0 -and (Test-MapInScope $_ $coverageRegions ([bool] $manifest.allRegions)) }).Count
    $scopeLabel = if ($manifest.allRegions) { 'all regions' } else { ($coverageRegions -join ', ') }
    if (@($navigation).Count -eq $expected) { Add-Check $checks 'navigation:coverage' 'PASS' "All $expected traversable maps in scope ($scopeLabel) are cataloged." } elseif ($AllowPartial) { Add-Check $checks 'navigation:coverage' 'WARN' "Partial validation: $(@($navigation).Count) of $expected traversable maps in scope ($scopeLabel)." } else { Add-Check $checks 'navigation:coverage' 'FAIL' "Catalog has $(@($navigation).Count) of $expected traversable maps in scope ($scopeLabel)." }
    if (!$AllowPartial -and ($manifest.allRegions -or $coverageRegions -contains 'maple')) {
        $trainingCenter = @($navigation | Where-Object { [int] $_.mapId -eq 1010100 })
        if ($trainingCenter.Count -eq 1 -and @($trainingCenter[0].climbables).Count -gt 0) { Add-Check $checks 'navigation:maple-training-center' 'PASS' 'Map 1010100 has topology and climbable facts.' } else { Add-Check $checks 'navigation:maple-training-center' 'FAIL' 'Map 1010100 is missing topology or climbable facts.' }
    }
    if (!$AllowPartial -and ($manifest.allRegions -or $coverageRegions -contains 'victoria')) {
        if (@($navigation | Where-Object { [int] $_.mapId -eq 100000000 }).Count -eq 1) { Add-Check $checks 'navigation:henesys' 'PASS' 'Henesys topology is present.' } else { Add-Check $checks 'navigation:henesys' 'FAIL' 'Henesys topology is missing.' }
    }
}

if ($null -ne $combat -and $null -ne $navigation) {
    $topologyByMap = @{}; foreach ($map in @($navigation)) { $topologyByMap[[string] $map.mapId] = $map }
    $badAnchors = New-Object System.Collections.ArrayList
    foreach ($policy in @($combat)) {
        if (!$topologyByMap.ContainsKey([string] $policy.mapId)) { [void] $badAnchors.Add("$($policy.mapId):missing-topology"); continue }
        $componentIds = @($topologyByMap[[string] $policy.mapId].components | ForEach-Object { [int] $_.componentId })
        foreach ($anchor in @($policy.anchors)) { if (!($componentIds -contains [int] $anchor.componentId)) { [void] $badAnchors.Add($anchor.anchorId) } }
        if ([int] $policy.maximumAgents -lt [int] $policy.recommendedAgents) { [void] $badAnchors.Add("$($policy.mapId):capacity") }
        if (@($policy.partyPartitions | ForEach-Object partySize) -notcontains 4) { [void] $badAnchors.Add("$($policy.mapId):partitions") }
    }
    if ($badAnchors.Count -eq 0) { Add-Check $checks 'combat:topology-and-capacity' 'PASS' 'Combat anchors, capacities, and party partitions are structurally valid.' } else { Add-Check $checks 'combat:topology-and-capacity' 'FAIL' "$($badAnchors.Count) combat policy issue(s)." }
}

if ($null -ne $travel) {
    $badTravel = @($travel | Where-Object { $_.automation.safeForExecution -or ($_.automation.safeForPlanning -and @($_.destinations).Count -eq 0) })
    if ($badTravel.Count -eq 0) { Add-Check $checks 'travel:authority-and-evidence' 'PASS' 'Travel execution stays live-validated and planning-safe rows have literal destinations.' } else { Add-Check $checks 'travel:authority-and-evidence' 'FAIL' "$($badTravel.Count) travel row(s) overclaim authority or lack evidence." }
    if (@($travel | ForEach-Object destinations).Count -gt 0) { Add-Check $checks 'travel:destinations' 'PASS' 'Literal travel destinations were extracted.' } else { Add-Check $checks 'travel:destinations' 'FAIL' 'No travel destinations were extracted.' }
    $victoriaTaxi = @($travel | Where-Object { [int] $_.npcId -eq 1002000 } | Select-Object -First 1)
    if ($victoriaTaxi.Count -eq 1 -and @($victoriaTaxi[0].destinations).Count -ge 5 -and $victoriaTaxi[0].conditions.beginnerDiscount) { Add-Check $checks 'travel:victoria-taxi' 'PASS' 'Victoria taxi destinations and beginner discount were extracted.' } else { Add-Check $checks 'travel:victoria-taxi' 'FAIL' 'Victoria taxi literal route/cost policy is incomplete.' }
}

if ($null -ne $progressionItems) {
    $badSupply = @($progressionItems.supplies | Where-Object { ([Math]::Abs([int] $_.recovery.hp) + [Math]::Abs([int] $_.recovery.mp) + [Math]::Abs([int] $_.recovery.hpPercent) + [Math]::Abs([int] $_.recovery.mpPercent)) -le 0 })
    if ($badSupply.Count -eq 0) { Add-Check $checks 'items:supply-effects' 'PASS' 'Every supply/effect row has a non-zero HP or MP effect.' } else { Add-Check $checks 'items:supply-effects' 'FAIL' "$($badSupply.Count) supply row(s) have no HP/MP effect." }
    $badScroll = @($progressionItems.scrolls | Where-Object { [int] $_.successPercent -lt 0 -or [int] $_.successPercent -gt 100 })
    if ($badScroll.Count -eq 0) { Add-Check $checks 'items:scroll-rates' 'PASS' 'Scroll success rates are bounded from 0 to 100.' } else { Add-Check $checks 'items:scroll-rates' 'FAIL' "$($badScroll.Count) scroll rate(s) are outside 0..100." }
    $expectedEquipmentFiles = @(Get-ChildItem -LiteralPath (Join-Path $WzRoot 'Character.wz') -Filter '*.img.xml' -File -Recurse).Count
    if (@($progressionItems.equipment).Count -gt 1000) { Add-Check $checks 'items:equipment-coverage' 'PASS' "$(@($progressionItems.equipment).Count) equipment definitions were extracted from $expectedEquipmentFiles source files." } elseif ($AllowPartial) { Add-Check $checks 'items:equipment-coverage' 'WARN' "Partial validation: only $(@($progressionItems.equipment).Count) equipment definitions." } else { Add-Check $checks 'items:equipment-coverage' 'FAIL' "Only $(@($progressionItems.equipment).Count) equipment definitions were extracted." }
    if (@($progressionItems.inventoryRules).Count -ge 5 -and $progressionItems.policyBoundary.marketDemandIsNotCataloged) { Add-Check $checks 'items:policy-boundary' 'PASS' 'Inventory defaults and live market/build boundaries are explicit.' } else { Add-Check $checks 'items:policy-boundary' 'FAIL' 'Inventory policy or live-state boundary is incomplete.' }
    $redPotion = @($progressionItems.supplies | Where-Object { [int] $_.itemId -eq 2000000 })
    if ($redPotion.Count -eq 1 -and [int] $redPotion[0].recovery.hp -eq 50) { Add-Check $checks 'items:red-potion-source-fact' 'PASS' 'Red Potion recovery is the WZ-sourced 50 HP.' } else { Add-Check $checks 'items:red-potion-source-fact' 'FAIL' 'Red Potion recovery fact is missing or incorrect.' }
    $helmetScroll = @($progressionItems.scrolls | Where-Object { [int] $_.itemId -eq 2040000 })
    if ($helmetScroll.Count -eq 1 -and [int] $helmetScroll[0].successPercent -eq 100) { Add-Check $checks 'items:scroll-source-fact' 'PASS' 'Representative 100% scroll fact is present.' } else { Add-Check $checks 'items:scroll-source-fact' 'FAIL' 'Representative scroll success fact is missing or incorrect.' }
}

if ($null -ne $questChains) {
    $questCatalog = Get-Content -LiteralPath (Join-Path $GameCatalogDir 'generated_quest_catalog.json') -Raw | ConvertFrom-Json
    $uniqueIds = @($questChains | ForEach-Object { [int] $_.questId } | Sort-Object -Unique)
    if (@($questChains).Count -eq @($questCatalog).Count -and $uniqueIds.Count -eq @($questCatalog).Count) { Add-Check $checks 'quests:coverage' 'PASS' "All $($uniqueIds.Count) quests have one policy row." } else { Add-Check $checks 'quests:coverage' 'FAIL' "Quest policy coverage is $(@($questChains).Count) rows / $($uniqueIds.Count) unique versus $(@($questCatalog).Count) canonical quests." }
    $byId = @{}; foreach ($row in @($questChains)) { $byId[[string] $row.questId] = $row }
    $badInverse = New-Object System.Collections.ArrayList
    foreach ($row in @($questChains)) {
        foreach ($prereq in @($row.prerequisites)) {
            if ($byId.ContainsKey([string] $prereq) -and !(@($byId[[string] $prereq].dependents) -contains [int] $row.questId)) { [void] $badInverse.Add("$prereq->$($row.questId)") }
        }
        if (@($row.specialHandlers) -contains 'reward-choice' -and $row.automation.planningSafe) { [void] $badInverse.Add("$($row.questId):unsafe-reward-choice") }
    }
    if ($badInverse.Count -eq 0) { Add-Check $checks 'quests:chain-and-handler-integrity' 'PASS' 'Prerequisite inverses and reward-choice safety are valid.' } else { Add-Check $checks 'quests:chain-and-handler-integrity' 'FAIL' "$($badInverse.Count) quest chain/handler issue(s)." }
}

$failCount = @($checks | Where-Object status -eq 'FAIL').Count
$warnCount = @($checks | Where-Object status -eq 'WARN').Count
$status = if ($failCount -gt 0) { 'FAIL' } elseif ($warnCount -gt 0) { 'INCOMPLETE' } else { 'PASS' }
$report = [ordered] @{ status = $status; repoRoot = $repoRoot; catalogDir = $CatalogDir; allowPartial = [bool] $AllowPartial; failCount = $failCount; warnCount = $warnCount; passCount = @($checks | Where-Object status -eq 'PASS').Count; checkCount = $checks.Count; failureIds = @($checks | Where-Object status -eq 'FAIL' | ForEach-Object id); warningIds = @($checks | Where-Object status -eq 'WARN' | ForEach-Object id); summaryOnly = [bool] $SummaryOnly; rowsOmitted = [bool] $SummaryOnly; checks = if ($SummaryOnly) { $null } else { @($checks) } }
if ($Json) { $report | ConvertTo-Json -Depth 8 } else { Write-Host "Agent decision catalog verification: $status"; Write-Host "Failures: $failCount  Warnings: $warnCount"; if (!$SummaryOnly) { $checks | ForEach-Object { Write-Host "[$($_.status)] $($_.id) - $($_.message)" } } }
if ($failCount -gt 0) { exit 1 }
