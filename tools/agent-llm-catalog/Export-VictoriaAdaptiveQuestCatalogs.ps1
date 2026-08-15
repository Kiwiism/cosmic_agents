param(
    [string] $QuestHuntingCatalogPath = "tmp/agent-llm-catalog/generated_victoria_lt30_quest_hunting_catalog.json",
    [string] $MobSpawnCatalogPath = "tmp/agent-llm-catalog/generated_mob_spawn_catalog.json",
    [string] $MapCatalogPath = "tmp/game-catalog/generated_map_catalog.json",
    [string] $DropCatalogPath = "tmp/game-catalog/generated_drop_catalog.json",
    [string] $TopologyCatalogPath = "tmp/agent-llm-catalog/generated_navigation_topology_catalog.json",
    [string] $QuestChainCatalogPath = "tmp/agent-llm-catalog/generated_quest_chain_policy_catalog.json",
    [string] $OutputDirectory = "src/main/resources/agents/catalogs/adaptive",
    [int] $MaximumCandidatesPerObjective = 10,
    [int] $MaximumCombinedCandidates = 10
)

$ErrorActionPreference = "Stop"

function Read-Json {
    param([string] $Path)
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing adaptive catalog source: $Path"
    }
    Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json
}

function Source-Hash {
    param([string] $Path)
    (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Test-VictoriaMapId {
    param([int] $MapId)
    ($MapId -ge 100000000 -and $MapId -lt 108000000) `
        -or ($MapId -ge 110000000 -and $MapId -lt 111000000) `
        -or ($MapId -ge 120000000 -and $MapId -lt 121000000)
}

function Add-Indexed {
    param([hashtable] $Index, [int] $Key, [object] $Value)
    if (!$Index.ContainsKey($Key)) {
        $Index[$Key] = [System.Collections.Generic.List[object]]::new()
    }
    $Index[$Key].Add($Value)
}

function Capacity-ForSpawnCount {
    param([int] $SpawnCount)
    if ($SpawnCount -le 12) { return 1 }
    if ($SpawnCount -le 30) { return 2 }
    if ($SpawnCount -le 60) { return 3 }
    if ($SpawnCount -le 90) { return 4 }
    return 5
}

function Complexity-Penalty {
    param([string] $Complexity)
    switch ($Complexity) {
        "high" { 2000 }
        "medium" { 800 }
        default { 0 }
    }
}

function Get-MapMetric {
    param([int] $MapId)
    if (!$mapFactsById.ContainsKey($MapId)) {
        return $null
    }
    $mapFactsById[$MapId]
}

function Get-DropEvidence {
    param([int] $QuestId, [int] $ItemId, [int[]] $MobIds)
    $rows = foreach ($mobId in $MobIds) {
        foreach ($drop in @($dropsByMob[$mobId])) {
            if ([int] $drop.itemId -ne $ItemId) { continue }
            $dropQuestId = [int] $drop.questId
            if ($dropQuestId -ne 0 -and $dropQuestId -ne $QuestId) { continue }
            [ordered]@{
                mobId = $mobId
                chance = [int] $drop.chance
                minimumQuantity = [int] $drop.minimumQuantity
                maximumQuantity = [int] $drop.maximumQuantity
                questId = $dropQuestId
            }
        }
    }
    @($rows)
}

function Get-Candidate {
    param(
        [int] $QuestId,
        [object] $Objective,
        [int[]] $OtherRequiredMobIds,
        [object] $SpawnMap
    )
    $mapId = [int] $SpawnMap.mapId
    $facts = Get-MapMetric $mapId
    if ($null -eq $facts) { return $null }

    $sourceMobIds = @($Objective.sourceMobIds | ForEach-Object { [int] $_ } | Sort-Object -Unique)
    $targetRows = @($SpawnMap.mobs | Where-Object { $sourceMobIds -contains [int] $_.mobId })
    if ($targetRows.Count -eq 0) { return $null }
    $targetSpawnEntries = [int] (@($targetRows | Measure-Object spawnEntries -Sum).Sum)
    $totalSpawnEntries = [Math]::Max(1, [int] $SpawnMap.spawnEntryCount)
    $irrelevantSpawnEntries = [Math]::Max(0, $totalSpawnEntries - $targetSpawnEntries)
    $targetConcentrationBasisPoints = [int] [Math]::Round(
        $targetSpawnEntries * 10000.0 / $totalSpawnEntries)

    $otherRows = @($SpawnMap.mobs | Where-Object {
        $OtherRequiredMobIds -contains [int] $_.mobId -and
        $sourceMobIds -notcontains [int] $_.mobId
    })
    $otherRequiredSpawnEntries = [int] (@($otherRows | Measure-Object spawnEntries -Sum).Sum)
    $coObjectiveCoverageCount = @($otherRows | Select-Object -ExpandProperty mobId -Unique).Count

    $targetComponentIds = @($facts.spawnPoints | Where-Object {
        $null -ne $_.PSObject.Properties["mobId"] -and
        $null -ne $_.PSObject.Properties["componentId"] -and
        $sourceMobIds -contains [int] $_.mobId
    } | ForEach-Object { [int] $_.componentId } | Sort-Object -Unique)
    $targetComponentCount = $targetComponentIds.Count
    $componentSpreadPenalty = [Math]::Max(0, $targetComponentCount - 1) * 1500
    $targetPoints = @($facts.spawnPoints | Where-Object {
        $sourceMobIds -contains [int] $_.mobId
    })
    $targetHorizontalSpan = if ($targetPoints.Count -eq 0) { 0 } else {
        [int] ((@($targetPoints.x | Measure-Object -Maximum).Maximum) `
            - (@($targetPoints.x | Measure-Object -Minimum).Minimum))
    }
    $targetVerticalSpan = if ($targetPoints.Count -eq 0) { 0 } else {
        [int] ((@($targetPoints.y | Measure-Object -Maximum).Maximum) `
            - (@($targetPoints.y | Measure-Object -Minimum).Minimum))
    }

    $planningLevel = if ($null -eq $questById[$QuestId].minLevel) {
        1
    } else {
        [int] $questById[$QuestId].minLevel
    }
    $highLevelHazard = [int] $SpawnMap.maxMobLevel -gt $planningLevel + 8
    $hazardPenalty = if ($highLevelHazard) { 3000 } else { 0 }
    $complexityPenalty = Complexity-Penalty ([string] $facts.topology.complexity)
    $widthPenalty = [int] [Math]::Round([int] $facts.topology.traversableWidth / 10.0)
    $climbPenalty = [int] $facts.topology.climbableCount * 100

    $dropEvidence = @()
    $dropYieldScore = 0
    $expectedUnitsPerSweepBasisPoints = $targetSpawnEntries * 10000
    if ([string] $Objective.type -eq "collect-item") {
        $dropEvidence = @(Get-DropEvidence $QuestId ([int] $Objective.targetId) $sourceMobIds)
        $expectedUnitsPerSweepBasisPoints = 0
        foreach ($drop in $dropEvidence) {
            $spawnCount = @($targetRows | Where-Object { [int] $_.mobId -eq [int] $drop.mobId } |
                Measure-Object spawnEntries -Sum).Sum
            $averageQuantity = ([int] $drop.minimumQuantity + [int] $drop.maximumQuantity) / 2.0
            $dropYieldScore += [int] [Math]::Round(
                [int] $spawnCount * [int] $drop.chance * $averageQuantity / 1000.0)
            $expectedUnitsPerSweepBasisPoints += [int] [Math]::Round(
                [int] $spawnCount * [int] $drop.chance * $averageQuantity / 100.0)
        }
    }

    # Concentration is a throughput modifier, not a flat reward. A one-spawn
    # 100%-pure map must not outrank a compact map with several target spawns.
    $concentrationScore = [int] [Math]::Round(
        $targetSpawnEntries * $targetConcentrationBasisPoints * 0.5)
    $scarcityPenalty = [Math]::Max(0, 3 - $targetSpawnEntries) * 3000

    $scoreEvidence = [ordered]@{
        targetSpawnScore = $targetSpawnEntries * 1000
        targetConcentrationScore = $concentrationScore
        coObjectiveCoverageScore = $coObjectiveCoverageCount * 5000
        otherRequiredSpawnScore = $otherRequiredSpawnEntries * 500
        expectedDropYieldScore = $dropYieldScore
        irrelevantSpawnPenalty = $irrelevantSpawnEntries * 200
        scarcityPenalty = $scarcityPenalty
        traversableWidthPenalty = $widthPenalty
        componentSpreadPenalty = $componentSpreadPenalty
        climbablePenalty = $climbPenalty
        topologyComplexityPenalty = $complexityPenalty
        levelHazardPenalty = $hazardPenalty
    }
    $score = $scoreEvidence.targetSpawnScore `
        + $scoreEvidence.targetConcentrationScore `
        + $scoreEvidence.coObjectiveCoverageScore `
        + $scoreEvidence.otherRequiredSpawnScore `
        + $scoreEvidence.expectedDropYieldScore `
        - $scoreEvidence.irrelevantSpawnPenalty `
        - $scoreEvidence.scarcityPenalty `
        - $scoreEvidence.traversableWidthPenalty `
        - $scoreEvidence.componentSpreadPenalty `
        - $scoreEvidence.climbablePenalty `
        - $scoreEvidence.topologyComplexityPenalty `
        - $scoreEvidence.levelHazardPenalty

    $capacity = Capacity-ForSpawnCount $targetSpawnEntries
    [pscustomobject] [ordered]@{
        mapId = $mapId
        mapName = [string] $SpawnMap.mapName
        score = [long] $score
        targetMobIds = @($targetRows.mobId | ForEach-Object { [int] $_ } | Sort-Object -Unique)
        targetSpawnEntries = $targetSpawnEntries
        totalSpawnEntries = $totalSpawnEntries
        targetConcentrationBasisPoints = $targetConcentrationBasisPoints
        coObjectiveCoverageCount = $coObjectiveCoverageCount
        targetComponentCount = $targetComponentCount
        expectedUnitsPerSweepBasisPoints = $expectedUnitsPerSweepBasisPoints
        targetHorizontalSpan = $targetHorizontalSpan
        targetVerticalSpan = $targetVerticalSpan
        climbableCount = [int] $facts.topology.climbableCount
        maxMobLevel = [int] $SpawnMap.maxMobLevel
        entryKind = if ([string] $SpawnMap.mapName -like "Mini Dungeon:*") {
            "mini-dungeon"
        } else {
            "ordinary"
        }
        recommendedAgents = $capacity
        maximumAgents = $capacity + 2
        scoreEvidence = $scoreEvidence
    }
}

function Rank-Candidates {
    param([object[]] $Candidates, [int] $Maximum)
    $selected = @($Candidates | Where-Object { $null -ne $_ } |
        Sort-Object @{ Expression = "score"; Descending = $true }, mapId |
        Select-Object -First $Maximum)
    $result = [System.Collections.Generic.List[object]]::new()
    for ($index = 0; $index -lt $selected.Count; $index++) {
        $candidate = $selected[$index]
        [void] $result.Add([ordered]@{
            rank = $index + 1
            mapId = [int] $candidate.mapId
            mapName = [string] $candidate.mapName
            score = [long] $candidate.score
            targetMobIds = @($candidate.targetMobIds)
            targetSpawnEntries = [int] $candidate.targetSpawnEntries
            totalSpawnEntries = [int] $candidate.totalSpawnEntries
            targetConcentrationBasisPoints = [int] $candidate.targetConcentrationBasisPoints
            coObjectiveCoverageCount = [int] $candidate.coObjectiveCoverageCount
            targetComponentCount = [int] $candidate.targetComponentCount
            expectedUnitsPerSweepBasisPoints = [int] $candidate.expectedUnitsPerSweepBasisPoints
            targetHorizontalSpan = [int] $candidate.targetHorizontalSpan
            targetVerticalSpan = [int] $candidate.targetVerticalSpan
            climbableCount = [int] $candidate.climbableCount
            maxMobLevel = [int] $candidate.maxMobLevel
            entryKind = [string] $candidate.entryKind
            recommendedAgents = [int] $candidate.recommendedAgents
            maximumAgents = [int] $candidate.maximumAgents
            scoreEvidence = $candidate.scoreEvidence
        })
    }
    @($result)
}

$MaximumCandidatesPerObjective = [Math]::Max(1, $MaximumCandidatesPerObjective)
$MaximumCombinedCandidates = [Math]::Max(1, $MaximumCombinedCandidates)
$questHunting = Read-Json $QuestHuntingCatalogPath
$mobSpawns = Read-Json $MobSpawnCatalogPath
$maps = Read-Json $MapCatalogPath
$drops = Read-Json $DropCatalogPath
$topologies = Read-Json $TopologyCatalogPath
$questChains = Read-Json $QuestChainCatalogPath

$sourcePaths = [ordered]@{
    generator = (Resolve-Path -LiteralPath $PSCommandPath).Path
    questHunting = (Resolve-Path -LiteralPath $QuestHuntingCatalogPath).Path
    mobSpawns = (Resolve-Path -LiteralPath $MobSpawnCatalogPath).Path
    maps = (Resolve-Path -LiteralPath $MapCatalogPath).Path
    drops = (Resolve-Path -LiteralPath $DropCatalogPath).Path
    topology = (Resolve-Path -LiteralPath $TopologyCatalogPath).Path
    questChains = (Resolve-Path -LiteralPath $QuestChainCatalogPath).Path
}
$sourceHashes = [ordered]@{}
foreach ($key in $sourcePaths.Keys) {
    $sourceHashes[$key] = Source-Hash $sourcePaths[$key]
}
$revisionMaterial = $sourceHashes.Values -join "|"
$sha = [Security.Cryptography.SHA256]::Create()
try {
    $revision = ([BitConverter]::ToString(
        $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($revisionMaterial))) -replace '-', '').ToLowerInvariant()
} finally {
    $sha.Dispose()
}
$shortRevision = $revision.Substring(0, 12)

$questById = @{}
foreach ($quest in $questHunting.entries) {
    $questById[[int] $quest.questId] = $quest
}
$questChainById = @{}
foreach ($quest in $questChains) {
    $questChainById[[int] $quest.questId] = $quest
}
$spawnByMap = @{}
$spawnMapsByMob = @{}
$mobById = @{}
foreach ($spawnMap in $mobSpawns) {
    $mapId = [int] $spawnMap.mapId
    $spawnByMap[$mapId] = $spawnMap
    foreach ($mob in @($spawnMap.mobs)) {
        $mobById[[int] $mob.mobId] = $mob
        Add-Indexed $spawnMapsByMob ([int] $mob.mobId) $spawnMap
    }
}
$mapById = @{}
foreach ($map in $maps) { $mapById[[int] $map.mapId] = $map }
$topologyByMap = @{}
foreach ($topology in $topologies) { $topologyByMap[[int] $topology.mapId] = $topology }
$dropsByMob = @{}
foreach ($drop in $drops) {
    if ([string] $drop.sourceType -eq "mob") {
        Add-Indexed $dropsByMob ([int] $drop.sourceId) $drop
    }
}

$relevantMobIds = [System.Collections.Generic.HashSet[int]]::new()
$relevantItemIds = [System.Collections.Generic.HashSet[int]]::new()
foreach ($quest in $questHunting.entries) {
    foreach ($objective in @($quest.huntingObjectives)) {
        foreach ($mobId in @($objective.sourceMobIds)) {
            [void] $relevantMobIds.Add([int] $mobId)
        }
        if ([string] $objective.type -eq "collect-item") {
            [void] $relevantItemIds.Add([int] $objective.targetId)
        }
    }
}

$mapFactIds = [System.Collections.Generic.HashSet[int]]::new()
foreach ($topology in @($topologies)) {
    $mapId = [int] $topology.mapId
    if (Test-VictoriaMapId $mapId -and $mapById.ContainsKey($mapId)) {
        [void] $mapFactIds.Add($mapId)
    }
}
$victoriaMobIds = [System.Collections.Generic.HashSet[int]]::new()
foreach ($mapId in $mapFactIds) {
    foreach ($mob in @($spawnByMap[$mapId].mobs)) {
        [void] $victoriaMobIds.Add([int] $mob.mobId)
    }
}

$mapFacts = [System.Collections.Generic.List[object]]::new()
$mapFactsById = @{}
foreach ($mapId in @($mapFactIds | Sort-Object)) {
    $map = $mapById[$mapId]
    $spawn = $spawnByMap[$mapId]
    $topology = $topologyByMap[$mapId]
    if ($null -eq $map -or $null -eq $topology) { continue }
    $spawnMobs = if ($null -eq $spawn) { @() } else { @($spawn.mobs) }
    $spawnEntryCount = if ($null -eq $spawn) { 0 } else { [int] $spawn.spawnEntryCount }
    $uniqueMobCount = if ($null -eq $spawn) { 0 } else { [int] $spawn.uniqueMobCount }
    $minMobLevel = if ($null -eq $spawn) { 0 } else { [int] $spawn.minMobLevel }
    $maxMobLevel = if ($null -eq $spawn) { 0 } else { [int] $spawn.maxMobLevel }
    $components = @($topology.components)
    $minX = if ($components.Count -eq 0) { 0 } else {
        [int] (@($components | ForEach-Object { [int] $_.bounds.minX } | Measure-Object -Minimum).Minimum)
    }
    $maxX = if ($components.Count -eq 0) { 0 } else {
        [int] (@($components | ForEach-Object { [int] $_.bounds.maxX } | Measure-Object -Maximum).Maximum)
    }
    $traversableWidth = [int] (@($components | Measure-Object totalWidth -Sum).Sum)
    $largestComponentWidth = if ($components.Count -eq 0) { 0 } else {
        [int] (@($components | Measure-Object totalWidth -Maximum).Maximum)
    }
    $mobFacts = foreach ($mob in $spawnMobs) {
        [ordered]@{
            mobId = [int] $mob.mobId
            mobName = [string] $mob.mobName
            level = [int] $mob.level
            spawnEntries = [int] $mob.spawnEntries
            spawnShareBasisPoints = [int] [Math]::Round(
                [int] $mob.spawnEntries * 10000.0 / [Math]::Max(1, $spawnEntryCount))
        }
    }
    $fact = [ordered]@{
        mapId = $mapId
        mapName = [string] $map.mapName
        streetName = [string] $map.streetName
        source = [string] $map.source
        totalSpawnEntries = $spawnEntryCount
        uniqueMobCount = $uniqueMobCount
        minMobLevel = $minMobLevel
        maxMobLevel = $maxMobLevel
        mobs = @($mobFacts)
        spawnPoints = @($topology.mobSpawns | ForEach-Object {
            [pscustomobject] [ordered]@{
                mobId = [int] $_.mobId
                x = [int] $_.x
                y = [int] $_.y
                footholdId = [int] $_.footholdId
                componentId = [int] $_.componentId
                roamLeft = [int] $_.roamLeft
                roamRight = [int] $_.roamRight
            }
        })
        topology = [ordered]@{
            horizontalSpan = [Math]::Max(0, $maxX - $minX)
            traversableWidth = $traversableWidth
            largestComponentWidth = $largestComponentWidth
            componentCount = [int] $topology.terrain.componentCount
            footholdCount = [int] $topology.terrain.footholdCount
            climbableCount = [int] $topology.terrain.climbableCount
            ropeCount = @($topology.climbables | Where-Object type -eq "rope").Count
            ladderCount = @($topology.climbables | Where-Object type -eq "ladder").Count
            verticalSpan = [int] $topology.terrain.verticalSpan
            complexity = [string] $topology.terrain.complexity
        }
    }
    $mapFacts.Add($fact)
    $mapFactsById[$mapId] = [pscustomobject] $fact
}

$questFacts = foreach ($quest in @($questHunting.entries | Sort-Object questId)) {
    $chain = $questChainById[[int] $quest.questId]
    [ordered]@{
        questId = [int] $quest.questId
        questName = [string] $quest.questName
        minLevel = $quest.minLevel
        maxLevel = $quest.maxLevel
        jobs = if ($null -eq $chain) { @() } else {
            @($chain.eligibility.jobs | ForEach-Object { [int] $_ })
        }
        prerequisiteRequirements = if ($null -eq $chain) { @() } else {
            @($chain.prerequisiteRequirements | ForEach-Object {
                [ordered]@{
                    questId = [int] $_.questId
                    state = [int] $_.state
                }
            })
        }
        autonomousStartAllowed = [bool] $quest.autonomousStartAllowed
        selectionDisposition = [string] $quest.selectionDisposition
        startNpcId = [int] $quest.startNpcId
        startMapIds = @($quest.startVictoriaMapIds | ForEach-Object { [int] $_ })
        completeNpcId = [int] $quest.completeNpcId
        completeMapIds = @($quest.completeVictoriaMapIds | ForEach-Object { [int] $_ })
        objectives = @($quest.huntingObjectives | ForEach-Object {
            [ordered]@{
                objectiveId = [string] $_.objectiveId
                type = [string] $_.type
                targetId = [int] $_.targetId
                targetName = [string] $_.targetName
                requiredCount = [int] $_.requiredCount
                sourceMobIds = @($_.sourceMobIds | ForEach-Object { [int] $_ })
            }
        })
        nonHuntingAcquisitionObjectives = @($quest.nonHuntingAcquisitionObjectives)
        warnings = @($quest.warnings)
    }
}

$questItemDemandRows = @{}
foreach ($quest in $questFacts) {
    $collectObjectives = @($quest.objectives) + @($quest.nonHuntingAcquisitionObjectives)
    foreach ($objective in @($collectObjectives | Where-Object {
        [string] $_.type -eq "collect-item" -and [int] $_.targetId -gt 0 -and [int] $_.requiredCount -gt 0
    })) {
        $itemId = [int] $objective.targetId
        if (!$questItemDemandRows.ContainsKey($itemId)) {
            $questItemDemandRows[$itemId] = [ordered]@{
                itemId = $itemId
                itemName = [string] $objective.targetName
                quests = [System.Collections.Generic.List[object]]::new()
            }
        }
        $questItemDemandRows[$itemId].quests.Add([ordered]@{
            questId = [int] $quest.questId
            questName = [string] $quest.questName
            requiredCount = [int] $objective.requiredCount
            minLevel = $quest.minLevel
            maxLevel = $quest.maxLevel
            jobs = @($quest.jobs)
            prerequisiteRequirements = @($quest.prerequisiteRequirements)
            autonomousStartAllowed = [bool] $quest.autonomousStartAllowed
            selectionDisposition = [string] $quest.selectionDisposition
        })
    }
}
$questItemDemandIndex = @($questItemDemandRows.Values | ForEach-Object {
    $totalRequiredCount = 0
    foreach ($questDemand in $_.quests) {
        $totalRequiredCount += [int] $questDemand.requiredCount
    }
    [ordered]@{
        itemId = [int] $_.itemId
        itemName = [string] $_.itemName
        totalRequiredCount = $totalRequiredCount
        quests = @($_.quests | Sort-Object questId)
    }
} | Sort-Object itemId)

$mobDropFacts = foreach ($mobId in @($victoriaMobIds | Sort-Object)) {
    $mobDrops = @($dropsByMob[$mobId] | ForEach-Object {
        [ordered]@{
            itemId = [int] $_.itemId
            itemName = [string] $_.itemName
            minimumQuantity = [int] $_.minimumQuantity
            maximumQuantity = [int] $_.maximumQuantity
            questId = [int] $_.questId
            chance = [int] $_.chance
            flags = @($_.flags)
            relevantQuestTargetItem = $relevantItemIds.Contains([int] $_.itemId)
        }
    })
    [ordered]@{
        mobId = $mobId
        mobName = [string] $mobById[$mobId].mobName
        level = [int] $mobById[$mobId].level
        drops = $mobDrops
    }
}

$indexEntries = [System.Collections.Generic.List[object]]::new()
foreach ($quest in @($questHunting.entries | Where-Object {
    @($_.huntingObjectives).Count -gt 0
} | Sort-Object questId)) {
    $allRequiredMobIds = @($quest.huntingObjectives |
        ForEach-Object { $_.sourceMobIds } | ForEach-Object { [int] $_ } | Sort-Object -Unique)
    $objectiveIndexes = [System.Collections.Generic.List[object]]::new()
    $combinedRowsByMap = @{}
    foreach ($objective in @($quest.huntingObjectives)) {
        $sourceMobIds = @($objective.sourceMobIds | ForEach-Object { [int] $_ } | Sort-Object -Unique)
        $otherRequiredMobIds = @($allRequiredMobIds | Where-Object { $sourceMobIds -notcontains $_ })
        $candidateMaps = @{}
        foreach ($mobId in $sourceMobIds) {
            foreach ($spawnMap in @($spawnMapsByMob[$mobId])) {
                $mapId = [int] $spawnMap.mapId
                if (Test-VictoriaMapId $mapId) { $candidateMaps[$mapId] = $spawnMap }
            }
        }
        $candidates = foreach ($spawnMap in $candidateMaps.Values) {
            Get-Candidate ([int] $quest.questId) $objective $otherRequiredMobIds $spawnMap
        }
        $ranked = @(Rank-Candidates $candidates $MaximumCandidatesPerObjective)
        foreach ($candidate in $ranked) {
            $mapId = [int] $candidate.mapId
            if (!$combinedRowsByMap.ContainsKey($mapId)) {
                $combinedRowsByMap[$mapId] = [ordered]@{
                    mapId = $mapId
                    mapName = [string] $candidate.mapName
                    score = 0L
                    coveredObjectiveIds = [System.Collections.Generic.List[string]]::new()
                    targetMobIds = [System.Collections.Generic.HashSet[int]]::new()
                    recommendedAgents = [int] $candidate.recommendedAgents
                    maximumAgents = [int] $candidate.maximumAgents
                }
            }
            $combined = $combinedRowsByMap[$mapId]
            $combined.score += [long] $candidate.score
            $combined.coveredObjectiveIds.Add([string] $objective.objectiveId)
            foreach ($mobId in @($candidate.targetMobIds)) {
                [void] $combined.targetMobIds.Add([int] $mobId)
            }
        }
        $objectiveIndexes.Add([ordered]@{
            objectiveId = [string] $objective.objectiveId
            type = [string] $objective.type
            targetId = [int] $objective.targetId
            requiredCount = [int] $objective.requiredCount
            sourceMobIds = @($sourceMobIds)
            candidates = $ranked
        })
    }
    $combinedCandidates = @($combinedRowsByMap.Values | ForEach-Object {
        [pscustomobject] [ordered]@{
            mapId = [int] $_.mapId
            mapName = [string] $_.mapName
            score = [long] $_.score
            objectiveCoverageCount = $_.coveredObjectiveIds.Count
            coveredObjectiveIds = @($_.coveredObjectiveIds)
            targetMobIds = @($_.targetMobIds)
            recommendedAgents = [int] $_.recommendedAgents
            maximumAgents = [int] $_.maximumAgents
        }
    } | Sort-Object @{ Expression = "objectiveCoverageCount"; Descending = $true },
        @{ Expression = "score"; Descending = $true }, mapId |
        Select-Object -First $MaximumCombinedCandidates)
    for ($index = 0; $index -lt $combinedCandidates.Count; $index++) {
        $combinedCandidates[$index] | Add-Member -NotePropertyName rank -NotePropertyValue ($index + 1)
    }
    $indexEntries.Add([ordered]@{
        questId = [int] $quest.questId
        questName = [string] $quest.questName
        objectives = @($objectiveIndexes)
        combinedCandidates = @($combinedCandidates)
    })
}

$mobIndexEntries = [System.Collections.Generic.List[object]]::new()
foreach ($mobId in @($victoriaMobIds | Sort-Object)) {
    $candidateMaps = @($spawnMapsByMob[$mobId] | Where-Object {
        Test-VictoriaMapId ([int] $_.mapId)
    })
    if ($candidateMaps.Count -eq 0) { continue }
    $genericObjective = [pscustomobject]@{
        type = "kill-mob"
        targetId = $mobId
        sourceMobIds = @($mobId)
    }
    $candidates = foreach ($spawnMap in $candidateMaps) {
        Get-Candidate 0 $genericObjective @() $spawnMap
    }
    $ranked = @(Rank-Candidates $candidates $MaximumCandidatesPerObjective)
    if ($ranked.Count -eq 0) { continue }
    $mobIndexEntries.Add([ordered]@{
        mobId = $mobId
        mobName = [string] $mobById[$mobId].mobName
        candidates = $ranked
    })
}

$metadata = [ordered]@{
    schemaVersion = 1
    revision = $revision
    sourcePaths = $sourcePaths
    sourceHashes = $sourceHashes
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$outputs = [ordered]@{
    "victoria-quest-facts.json" = [ordered]@{
        schemaVersion = 1
        catalogId = "victoria-quest-facts-$shortRevision"
        revision = $revision
        sourceHashes = $sourceHashes
        entries = @($questFacts)
    }
    "victoria-mob-drop-facts.json" = [ordered]@{
        schemaVersion = 1
        catalogId = "victoria-mob-drop-facts-$shortRevision"
        revision = $revision
        sourceHashes = $sourceHashes
        entries = @($mobDropFacts)
    }
    "victoria-map-facts.json" = [ordered]@{
        schemaVersion = 1
        catalogId = "victoria-map-facts-$shortRevision"
        revision = $revision
        sourceHashes = $sourceHashes
        entries = @($mapFacts)
    }
    "victoria-quest-hunt-index.json" = [ordered]@{
        schemaVersion = 2
        catalogId = "victoria-quest-hunt-index-$shortRevision"
        revision = $revision
        sourceHashes = $sourceHashes
        scoringPolicy = [ordered]@{
            purpose = "expected relevant quest progress with topology, concentration, drop, filler, and hazard evidence"
            runtimeAdjustments = @("remaining objective count", "route distance", "occupancy", "current map", "recent map failures", "agent progression profile")
        }
        entries = @($indexEntries)
        mobEntries = @($mobIndexEntries)
    }
    "victoria-quest-item-demand-index.json" = [ordered]@{
        schemaVersion = 1
        catalogId = "victoria-quest-item-demand-index-$shortRevision"
        revision = $revision
        sourceHashes = $sourceHashes
        demandHorizons = @(5, 15, 25)
        entries = @($questItemDemandIndex)
    }
}

foreach ($name in $outputs.Keys) {
    $path = Join-Path $OutputDirectory $name
    $outputs[$name] | ConvertTo-Json -Depth 18 -Compress |
        Set-Content -Encoding UTF8 -LiteralPath $path
    Write-Output "Wrote $path"
}
Write-Output "Adaptive revision ${shortRevision}: $($questFacts.Count) quests, $($mobDropFacts.Count) mobs, $($mapFacts.Count) maps, $($indexEntries.Count) indexed quests, $($mobIndexEntries.Count) indexed mobs"
