param(
    [string] $QuestObjectiveCatalogPath = "tmp/agent-llm-catalog/generated_quest_objective_catalog.json",
    [string] $MobSpawnCatalogPath = "tmp/agent-llm-catalog/generated_mob_spawn_catalog.json",
    [string] $MapCatalogPath = "tmp/game-catalog/generated_map_catalog.json",
    [string] $QuestStatusCatalogPath = "docs/agents/catalog-overrides/victoria-lt30-quest-status.catalog.json",
    [string] $TrainingCatalogPath = "src/main/resources/agents/catalogs/victoria-level15-30-training-catalog.json",
    [string] $PolicyPath = "docs/agents/catalog-overrides/victoria-lt30-quest-hunting-policy.json",
    [string] $QuestScriptDirectory = "scripts/quest",
    [string] $OutputPath = "tmp/agent-llm-catalog/generated_victoria_lt30_quest_hunting_catalog.json",
    [int] $MaximumPreferredMaps = 5
)

$ErrorActionPreference = "Stop"

function Read-Json {
    param([string] $Path)
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing Victoria quest-hunting source: $Path"
    }
    return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

function Get-SourceHash {
    param([string] $Path)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Test-VictoriaMapId {
    param([int] $MapId)
    return ($MapId -ge 100000000 -and $MapId -lt 108000000) `
        -or ($MapId -ge 110000000 -and $MapId -lt 111000000) `
        -or ($MapId -ge 120000000 -and $MapId -lt 121000000)
}

function Test-EligibleHuntingMap {
    param([int] $MapId)
    if (!(Test-VictoriaMapId $MapId) -or !$mapById.ContainsKey($MapId)) {
        return $false
    }
    $map = $mapById[$MapId]
    $label = "$($map.streetName) $($map.mapName)"
    return $label -notmatch '(?i)accompaniment|bonus stage|last stage|waiting room|physical fitness test|wedding hall'
}

function Add-ToListIndex {
    param([hashtable] $Index, [int] $Key, [object] $Value)
    if (!$Index.ContainsKey($Key)) {
        $Index[$Key] = [System.Collections.Generic.List[object]]::new()
    }
    $Index[$Key].Add($Value)
}

function Get-DerivedCapacity {
    param([int] $SpawnEntries)
    if ($SpawnEntries -le 12) { return 1 }
    if ($SpawnEntries -le 30) { return 2 }
    if ($SpawnEntries -le 60) { return 3 }
    if ($SpawnEntries -le 90) { return 4 }
    return 5
}

function Get-PreferredMaps {
    param([int[]] $MobIds, [int] $PlanningLevel)

    $rows = @{}
    foreach ($mobId in @($MobIds | Sort-Object -Unique)) {
        foreach ($spawnMap in @($spawnMapsByMob[$mobId])) {
            $mapId = [int] $spawnMap.mapId
            if (!(Test-EligibleHuntingMap $mapId)) {
                continue
            }
            if (!$rows.ContainsKey($mapId)) {
                $rows[$mapId] = [ordered]@{
                    map = $spawnMap
                    targetMobIds = [System.Collections.Generic.HashSet[int]]::new()
                    targetSpawnEntries = 0
                }
            }
            $row = $rows[$mapId]
            if ($row.targetMobIds.Add($mobId)) {
                $match = @($spawnMap.mobs | Where-Object { [int] $_.mobId -eq $mobId })
                $row.targetSpawnEntries += @($match | Measure-Object -Property spawnEntries -Sum).Sum
            }
        }
    }

    $ranked = foreach ($row in $rows.Values) {
        $spawnMap = $row.map
        $mapId = [int] $spawnMap.mapId
        $coverage = $row.targetMobIds.Count
        $training = $trainingByMap[$mapId]
        $trainingRank = $null
        if ($trainingRanksByLevelMap.ContainsKey("$PlanningLevel|$mapId")) {
            $trainingRank = [int] $trainingRanksByLevelMap["$PlanningLevel|$mapId"]
        }
        $recommendedAgents = if ($null -ne $training) {
            [int] $training.recommendedAgents
        } else {
            Get-DerivedCapacity ([int] $spawnMap.spawnEntryCount)
        }
        $maximumAgents = if ($null -ne $training) {
            [int] $training.maximumAgents
        } else {
            $recommendedAgents + 2
        }
        $highLevelHazard = $PlanningLevel -gt 0 -and [int] $spawnMap.maxMobLevel -gt $PlanningLevel + 8
        $trainingBonus = if ($null -eq $trainingRank) { 0 } else { [Math]::Max(100, 1100 - ($trainingRank * 100)) }
        $hazardPenalty = if ($highLevelHazard) { 1500 } else { 0 }
        $score = ($coverage * 10000) + ([int] $row.targetSpawnEntries * 100) + $trainingBonus - $hazardPenalty
        $targetMobs = @($spawnMap.mobs | Where-Object {
            $row.targetMobIds.Contains([int] $_.mobId)
        } | ForEach-Object {
            [ordered]@{
                mobId = [int] $_.mobId
                mobName = [string] $_.mobName
                level = [int] $_.level
                spawnEntries = [int] $_.spawnEntries
            }
        })
        [pscustomobject] [ordered]@{
            mapId = $mapId
            mapName = [string] $spawnMap.mapName
            score = $score
            targetCoverage = $coverage
            targetSpawnEntries = [int] $row.targetSpawnEntries
            totalSpawnEntries = [int] $spawnMap.spawnEntryCount
            minMobLevel = [int] $spawnMap.minMobLevel
            maxMobLevel = [int] $spawnMap.maxMobLevel
            recommendedAgents = $recommendedAgents
            maximumAgents = $maximumAgents
            trainingRankAtQuestLevel = $trainingRank
            targetMobs = $targetMobs
            warnings = @(
                if ($highLevelHazard) { "map contains mobs more than eight levels above the planning level" }
                if ($null -ne $training) { @($training.hazards) }
                $mapFact = $mapById[$mapId]
                if ($null -ne $mapFact.forcedReturnMapId -and [int] $mapFact.forcedReturnMapId -ne 999999999) {
                    "scripted, hidden, or forced-return entry; verify entry availability before selecting"
                }
            )
        }
    }

    $selected = @($ranked | Sort-Object @{ Expression = "score"; Descending = $true }, mapId |
        Select-Object -First ([Math]::Max(1, $MaximumPreferredMaps)))
    $result = [System.Collections.Generic.List[object]]::new()
    for ($index = 0; $index -lt $selected.Count; $index++) {
        $candidate = $selected[$index]
        [void] $result.Add([ordered]@{
            rank = $index + 1
            mapId = $candidate.mapId
            mapName = $candidate.mapName
            score = $candidate.score
            targetCoverage = $candidate.targetCoverage
            targetSpawnEntries = $candidate.targetSpawnEntries
            totalSpawnEntries = $candidate.totalSpawnEntries
            minMobLevel = $candidate.minMobLevel
            maxMobLevel = $candidate.maxMobLevel
            recommendedAgents = $candidate.recommendedAgents
            maximumAgents = $candidate.maximumAgents
            trainingRankAtQuestLevel = $candidate.trainingRankAtQuestLevel
            targetMobs = @($candidate.targetMobs)
            warnings = @($candidate.warnings)
        })
    }
    return @($result)
}

$MaximumPreferredMaps = [Math]::Max(1, $MaximumPreferredMaps)
$questObjectives = Read-Json $QuestObjectiveCatalogPath
$mobSpawns = Read-Json $MobSpawnCatalogPath
$maps = Read-Json $MapCatalogPath
$statusCatalog = Read-Json $QuestStatusCatalogPath
$trainingCatalog = Read-Json $TrainingCatalogPath
$policy = Read-Json $PolicyPath

$objectiveByQuest = @{}
foreach ($plan in $questObjectives) { $objectiveByQuest[[int] $plan.questId] = $plan }
$mapById = @{}
foreach ($map in $maps) { $mapById[[int] $map.mapId] = $map }
$spawnMapsByMob = @{}
foreach ($spawnMap in $mobSpawns) {
    foreach ($mob in @($spawnMap.mobs)) {
        Add-ToListIndex $spawnMapsByMob ([int] $mob.mobId) $spawnMap
    }
}
$trainingByMap = @{}
foreach ($map in $trainingCatalog.trainingMaps) { $trainingByMap[[int] $map.mapId] = $map }
$trainingRanksByLevelMap = @{}
foreach ($levelPlan in $trainingCatalog.levelPlans) {
    foreach ($choice in $levelPlan.choices) {
        $trainingRanksByLevelMap["$([int] $levelPlan.level)|$([int] $choice.mapId)"] = [int] $choice.rank
    }
}
$policyByQuest = @{}
foreach ($questPolicy in $policy.questPolicies) { $policyByQuest[[int] $questPolicy.questId] = $questPolicy }

$entries = [System.Collections.Generic.List[object]]::new()
foreach ($status in @($statusCatalog.quests | Sort-Object questId)) {
    $questId = [int] $status.questId
    $planningLevel = if ($null -eq $status.minLevel) { 1 } else { [int] $status.minLevel }
    if ($planningLevel -gt 30) {
        continue
    }
    $plan = $objectiveByQuest[$questId]
    $manualPolicy = $policyByQuest[$questId]
    $warnings = [System.Collections.Generic.List[string]]::new()
    foreach ($flag in @($status.sourceFlags)) { [void] $warnings.Add([string] $flag) }
    foreach ($warning in @($manualPolicy.warnings)) {
        if (![string]::IsNullOrWhiteSpace([string] $warning)) {
            [void] $warnings.Add([string] $warning)
        }
    }

    $scriptWarpMapIds = [System.Collections.Generic.List[int]]::new()
    $scriptPath = Join-Path $QuestScriptDirectory "$questId.js"
    if (Test-Path -LiteralPath $scriptPath -PathType Leaf) {
        $scriptText = Get-Content -Raw -LiteralPath $scriptPath
        foreach ($match in [regex]::Matches($scriptText, '(?:warp|changeMap)\s*\(\s*(\d+)')) {
            $targetMapId = [int] $match.Groups[1].Value
            if (!$scriptWarpMapIds.Contains($targetMapId)) {
                $scriptWarpMapIds.Add($targetMapId)
            }
            if (!(Test-VictoriaMapId $targetMapId)) {
                [void] $warnings.Add("quest-script-warps-outside-victoria:$targetMapId")
            }
        }
    }

    $autonomousStartAllowed = [bool] $status.agentRunnableNow
    if ($status.status -eq "postpone-outside-current-region") {
        $autonomousStartAllowed = $false
    }
    if ($null -ne $manualPolicy -and $null -ne $manualPolicy.autonomousStartAllowed) {
        $autonomousStartAllowed = [bool] $manualPolicy.autonomousStartAllowed
    }
    if (@($scriptWarpMapIds | Where-Object { !(Test-VictoriaMapId $_) }).Count -gt 0) {
        $autonomousStartAllowed = $false
    }

    $huntObjectives = [System.Collections.Generic.List[object]]::new()
    $nonHuntingAcquisitionObjectives = [System.Collections.Generic.List[object]]::new()
    $allTargetMobIds = [System.Collections.Generic.HashSet[int]]::new()
    foreach ($objective in @($plan.objectives)) {
        $targetMobIds = @()
        $objectiveType = [string] $objective.type
        if ($objectiveType -eq "kill-mob") {
            $targetMobIds = @([int] $objective.mobId)
        } elseif ($objectiveType -eq "collect-item") {
            $targetMobIds = @($objective.candidateDropSources | Where-Object {
                $_.sourceType -eq "mob" -and ([int] $_.questId -eq 0 -or [int] $_.questId -eq $questId)
            } | ForEach-Object { [int] $_.sourceId } | Sort-Object -Unique)
            if ($targetMobIds.Count -eq 0) {
                [void] $nonHuntingAcquisitionObjectives.Add([ordered]@{
                    objectiveId = [string] $objective.objectiveId
                    type = $objectiveType
                    targetId = [int] $objective.itemId
                    targetName = [string] $objective.itemName
                    requiredCount = [int] $objective.count
                    reason = "no quest-valid mob drop source; resolve through shopping, inventory, scripted acquisition, or review"
                })
                continue
            }
        } else {
            continue
        }
        $preferredMaps = @(Get-PreferredMaps $targetMobIds $planningLevel)
        if ($objectiveType -eq "collect-item" -and $preferredMaps.Count -eq 0) {
            [void] $nonHuntingAcquisitionObjectives.Add([ordered]@{
                objectiveId = [string] $objective.objectiveId
                type = $objectiveType
                targetId = [int] $objective.itemId
                targetName = [string] $objective.itemName
                requiredCount = [int] $objective.count
                reason = "no quest-valid Victoria mob source; resolve through shopping, inventory, scripted acquisition, or review"
            })
            continue
        }
        foreach ($mobId in $targetMobIds) { [void] $allTargetMobIds.Add($mobId) }
        $objectiveWarnings = @(
            if ($preferredMaps.Count -eq 0) { "no-victoria-hunting-map-cataloged" }
        )
        [void] $huntObjectives.Add([ordered]@{
            objectiveId = [string] $objective.objectiveId
            type = $objectiveType
            targetId = if ($objectiveType -eq "kill-mob") { [int] $objective.mobId } else { [int] $objective.itemId }
            targetName = if ($objectiveType -eq "kill-mob") { [string] $objective.mobName } else { [string] $objective.itemName }
            requiredCount = [int] $objective.count
            sourceMobIds = @($targetMobIds)
            preferredMaps = $preferredMaps
            warnings = $objectiveWarnings
        })
    }

    $combinedPreferredMaps = if ($allTargetMobIds.Count -eq 0) {
        @()
    } else {
        @(Get-PreferredMaps @($allTargetMobIds) $planningLevel)
    }
    $disposition = if ($status.status -eq "postpone-outside-current-region" `
            -or ($null -ne $manualPolicy -and $manualPolicy.autonomousStartAllowed -eq $false)) {
        "excluded-off-island-or-chain-boundary"
    } elseif ($autonomousStartAllowed) {
        "eligible-now"
    } elseif ([bool] $status.agentRunnableAfterCapabilities) {
        "capability-gated"
    } else {
        "review-blocked"
    }

    [void] $entries.Add([ordered]@{
        questId = $questId
        questName = [string] $status.name
        minLevel = $status.minLevel
        maxLevel = $status.maxLevel
        status = [string] $status.status
        autonomousStartAllowed = $autonomousStartAllowed
        selectionDisposition = $disposition
        startNpcId = [int] $status.start.npcId
        startVictoriaMapIds = @($status.start.victoriaMaps)
        completeNpcId = [int] $status.complete.npcId
        completeVictoriaMapIds = @($status.complete.victoriaMaps)
        questScriptWarpMapIds = @($scriptWarpMapIds)
        blockAutoFollowupQuestIds = @(
            if ($null -ne $manualPolicy) { @($manualPolicy.blockAutoFollowupQuestIds) }
        )
        warnings = @($warnings | Where-Object { ![string]::IsNullOrWhiteSpace($_) } | Sort-Object -Unique)
        huntingObjectives = @($huntObjectives)
        nonHuntingAcquisitionObjectives = @($nonHuntingAcquisitionObjectives)
        combinedPreferredMaps = @($combinedPreferredMaps)
    })
}

$sourcePaths = [ordered]@{
    generator = (Resolve-Path -LiteralPath $PSCommandPath).Path
    questObjectives = (Resolve-Path -LiteralPath $QuestObjectiveCatalogPath).Path
    mobSpawns = (Resolve-Path -LiteralPath $MobSpawnCatalogPath).Path
    maps = (Resolve-Path -LiteralPath $MapCatalogPath).Path
    questStatuses = (Resolve-Path -LiteralPath $QuestStatusCatalogPath).Path
    training = (Resolve-Path -LiteralPath $TrainingCatalogPath).Path
    policy = (Resolve-Path -LiteralPath $PolicyPath).Path
}
$sourceHashes = [ordered]@{}
foreach ($key in $sourcePaths.Keys) { $sourceHashes[$key] = Get-SourceHash $sourcePaths[$key] }
$revisionMaterial = ($sourceHashes.Values -join "|")
$sha = [Security.Cryptography.SHA256]::Create()
try {
    $revisionBytes = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($revisionMaterial))
    $revision = ([BitConverter]::ToString($revisionBytes) -replace '-', '').ToLowerInvariant()
} finally {
    $sha.Dispose()
}

$payload = [ordered]@{
    schemaVersion = 1
    catalogId = "victoria-lt30-quest-hunting-$($revision.Substring(0, 12))"
    revision = $revision
    generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    scope = [ordered]@{
        region = "Victoria Island"
        maximumStartLevel = 30
        outsideRegionQuestStartsAllowed = $false
        maximumPreferredMapsPerObjective = $MaximumPreferredMaps
    }
    selectionPolicy = [ordered]@{
        ranking = "target-coverage, spawn-density, level-training-rank, hazard-penalty"
        occupancy = "fill rank order to recommendedAgents, then fall through; never exceed maximumAgents when alternatives exist"
        mapFacts = "generated mob spawns plus curated Victoria training capacities when available"
    }
    sourcePaths = $sourcePaths
    sourceHashes = $sourceHashes
    summary = [ordered]@{
        questCount = $entries.Count
        autonomousStartAllowedCount = @($entries | Where-Object autonomousStartAllowed).Count
        huntingQuestCount = @($entries | Where-Object { $_.huntingObjectives.Count -gt 0 }).Count
        huntingObjectiveCount = @($entries | ForEach-Object { $_.huntingObjectives }).Count
        nonHuntingAcquisitionObjectiveCount = @($entries | ForEach-Object { $_.nonHuntingAcquisitionObjectives }).Count
        autonomousHuntingObjectiveCount = @($entries | Where-Object autonomousStartAllowed |
            ForEach-Object { $_.huntingObjectives }).Count
        autonomousHuntingObjectivesWithoutPreferredMaps = @($entries | Where-Object autonomousStartAllowed |
            ForEach-Object { $_.huntingObjectives } | Where-Object { $_.preferredMaps.Count -eq 0 }).Count
        offIslandOrChainBoundaryCount = @($entries | Where-Object {
            $_.selectionDisposition -eq "excluded-off-island-or-chain-boundary"
        }).Count
    }
    entries = @($entries)
}

$parent = Split-Path -Parent $OutputPath
if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
$payload | ConvertTo-Json -Depth 16 | Set-Content -Encoding UTF8 -LiteralPath $OutputPath
Write-Output "Wrote $OutputPath ($($payload.summary.questCount) quests, $($payload.summary.huntingObjectiveCount) hunting objectives, revision $($revision.Substring(0, 12)))"
