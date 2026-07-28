param(
    [string] $RuntimeCatalog =
        "src/main/resources/agents/catalogs/victoria-lt30-quest-runtime-catalog.json",
    [string] $AdaptiveIndex =
        "src/main/resources/agents/catalogs/adaptive/victoria-quest-hunt-index.json",
    [string] $Level15Catalog =
        "src/main/resources/agents/catalogs/victoria-level15-mvp-catalog.json",
    [string] $OutputPath =
        "docs/agents/VICTORIA_ADAPTIVE_HUNT_SHADOW_BASELINE.md"
)

$ErrorActionPreference = "Stop"

function Read-Json([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing required catalog: $Path"
    }
    Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json
}

$runtime = Read-Json $RuntimeCatalog
$adaptive = Read-Json $AdaptiveIndex
$level15 = Read-Json $Level15Catalog

$adaptiveByQuest = @{}
foreach ($entry in @($adaptive.entries)) {
    $adaptiveByQuest[[int] $entry.questId] = $entry
}

$mvpQuestIds = [System.Collections.Generic.HashSet[int]]::new()
foreach ($pack in @($level15.questPacks)) {
    foreach ($questId in @($pack.questIds)) {
        [void] $mvpQuestIds.Add([int] $questId)
    }
}
foreach ($career in @($level15.careers)) {
    foreach ($step in @($career.trainingSteps)) {
        [void] $mvpQuestIds.Add([int] $step.questId)
    }
}

$comparisons = [System.Collections.Generic.List[object]]::new()
foreach ($quest in @($runtime.entries)) {
    $adaptiveQuest = $adaptiveByQuest[[int] $quest.questId]
    foreach ($objective in @($quest.huntingObjectives)) {
        $fixed = @($objective.huntMaps | Sort-Object rank | Select-Object -First 1)
        $adaptiveObjective = @($adaptiveQuest.objectives | Where-Object {
            [string] $_.objectiveId -eq [string] $objective.objectiveId
        } | Select-Object -First 1)
        $predicted = @($adaptiveObjective.candidates | Sort-Object rank | Select-Object -First 1)
        if ($fixed.Count -eq 0 -or $predicted.Count -eq 0) { continue }
        $same = [int] $fixed[0].mapId -eq [int] $predicted[0].mapId
        $comparisons.Add([pscustomobject] [ordered]@{
            questId = [int] $quest.questId
            questName = [string] $quest.questName
            objectiveId = [string] $objective.objectiveId
            mvp = $mvpQuestIds.Contains([int] $quest.questId)
            fixedMapId = [int] $fixed[0].mapId
            adaptiveMapId = [int] $predicted[0].mapId
            adaptiveMapName = [string] $predicted[0].mapName
            adaptiveScore = [long] $predicted[0].score
            same = $same
            evidence = $predicted[0].scoreEvidence
        })
    }
}

$sameCount = @($comparisons | Where-Object same).Count
$differentCount = $comparisons.Count - $sameCount
$mvp = @($comparisons | Where-Object mvp)
$mvpSame = @($mvp | Where-Object same).Count

$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add("# Victoria Adaptive Hunt Shadow Baseline")
$lines.Add("")
$lines.Add("Generated from the tested fixed runtime catalog and adaptive index revision ``$($adaptive.revision)``.")
$lines.Add("")
$lines.Add("| Scope | Compared | Same first choice | Different first choice |")
$lines.Add("|---|---:|---:|---:|")
$lines.Add("| All fixed hunting objectives | $($comparisons.Count) | $sameCount | $differentCount |")
$lines.Add("| Level 15 MVP quest-pack objectives | $($mvp.Count) | $mvpSame | $($mvp.Count - $mvpSame) |")
$lines.Add("")
$rolloutNote = "A different prediction is evidence to review, not permission to replace a proven MVP route. "
$rolloutNote += "The active policy remains PREFERRED_ADAPTIVE: fixed first, generated fallback only."
$lines.Add($rolloutNote)
$lines.Add("")
$lines.Add("## MVP differences")
$lines.Add("")
$lines.Add("| Quest | Objective | Fixed map | Predicted map | Score | Main evidence |")
$lines.Add("|---|---|---:|---|---:|---|")
foreach ($row in @($mvp | Where-Object { -not $_.same } | Sort-Object questId, objectiveId)) {
    $evidence = $row.evidence
    $summary = "spawn=$($evidence.targetSpawnScore), concentration=$($evidence.targetConcentrationScore), "
    $summary += "drops=$($evidence.expectedDropYieldScore), irrelevantPenalty=$($evidence.irrelevantSpawnPenalty)"
    $safeQuestName = $row.questName -replace '\|', '/'
    $safeMapName = $row.adaptiveMapName -replace '\|', '/'
    $tableRow = "| $($row.questId) $safeQuestName | $($row.objectiveId) | "
    $tableRow += "$($row.fixedMapId) | $($row.adaptiveMapId) $safeMapName | "
    $tableRow += "$($row.adaptiveScore) | $summary |"
    $lines.Add($tableRow)
}
if (@($mvp | Where-Object { -not $_.same }).Count -eq 0) {
    $lines.Add("| -- | -- | -- | -- | -- | All compared MVP first choices match. |")
}
$lines.Add("")
$lines.Add("## Runtime evidence")
$lines.Add("")
$runtimeNote = "With shadow mode enabled, each Agent/map selection emits one structured "
$runtimeNote += "Agent hunt shadow log entry containing the fixed choice, adaptive prediction, "
$runtimeNote += "catalog score, runtime-adjusted score, and score evidence. Repeated ticks with the same "
$runtimeNote += "decision are deduplicated per Agent."
$lines.Add($runtimeNote)

$directory = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$lines | Set-Content -Encoding UTF8 -LiteralPath $OutputPath
Write-Output "Wrote $OutputPath ($($comparisons.Count) comparisons)"
