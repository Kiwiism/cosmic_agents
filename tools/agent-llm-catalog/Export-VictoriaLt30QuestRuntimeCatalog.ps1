param(
    [string]$InputPath = "tmp/agent-llm-catalog/generated_victoria_lt30_quest_hunting_catalog.json",
    [string]$OutputPath = "src/main/resources/agents/catalogs/victoria-lt30-quest-runtime-catalog.json",
    # Curated zero-objective bridges whose completion unlocks a supported hunting quest.
    [int[]]$InteractionOnlyQuestIds = @(2090)
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $InputPath)) {
    throw "Victoria quest hunting catalog not found: $InputPath"
}

$source = Get-Content -Raw -LiteralPath $InputPath | ConvertFrom-Json
$entries = @($source.entries |
    Where-Object {
        $_.autonomousStartAllowed -and
        (@($_.huntingObjectives).Count -gt 0 `
            -or @($_.shopProcurementObjectives).Count -gt 0 `
            -or $_.questId -in $InteractionOnlyQuestIds) -and
        @($_.nonHuntingAcquisitionObjectives).Count -eq 0 -and
        @($_.startVictoriaMapIds).Count -gt 0 -and
        @($_.completeVictoriaMapIds).Count -gt 0 -and
        @($_.questScriptWarpMapIds).Count -eq 0
    } |
    ForEach-Object {
        $quest = $_
        [ordered]@{
            questId = [int]$quest.questId
            questName = [string]$quest.questName
            minLevel = $quest.minLevel
            maxLevel = $quest.maxLevel
            startNpcId = [int]$quest.startNpcId
            startMapIds = @($quest.startVictoriaMapIds | ForEach-Object { [int]$_ })
            completeNpcId = [int]$quest.completeNpcId
            completeMapIds = @($quest.completeVictoriaMapIds | ForEach-Object { [int]$_ })
            prerequisiteRequirements = @($quest.prerequisiteRequirements | ForEach-Object {
                [ordered]@{
                    questId = [int]$_.questId
                    requiredState = [int]$_.state
                }
            })
            startItemRequirements = @($quest.startItemRequirements | ForEach-Object {
                [ordered]@{
                    itemId = [int]$_.itemId
                    itemName = [string]$_.itemName
                    requiredCount = [int]$_.requiredCount
                    consumedOnStart = [bool]$_.consumedOnStart
                    producerQuestIds = @($_.producerQuestIds | ForEach-Object { [int]$_ })
                }
            })
            shopProcurementObjectives = @($quest.shopProcurementObjectives | ForEach-Object {
                [ordered]@{
                    objectiveId = [string]$_.objectiveId
                    type = [string]$_.type
                    targetId = [int]$_.targetId
                    requiredCount = [int]$_.requiredCount
                    shopSources = @($_.shopSources | ForEach-Object {
                        [ordered]@{
                            npcId = [int]$_.npcId
                            mapId = [int]$_.mapId
                            unitPrice = [int]$_.unitPrice
                        }
                    })
                }
            })
            huntingObjectives = @($quest.huntingObjectives | ForEach-Object {
                $objective = $_
                [ordered]@{
                    objectiveId = [string]$objective.objectiveId
                    type = [string]$objective.type
                    targetId = [int]$objective.targetId
                    requiredCount = [int]$objective.requiredCount
                    sourceMobIds = @($objective.sourceMobIds | ForEach-Object { [int]$_ } | Sort-Object -Unique)
                    huntMaps = @($objective.preferredMaps | ForEach-Object {
                        [ordered]@{
                            rank = [int]$_.rank
                            mapId = [int]$_.mapId
                            recommendedAgents = [int]$_.recommendedAgents
                            maximumAgents = [int]$_.maximumAgents
                            targetMobIds = @($_.targetMobs.mobId | ForEach-Object { [int]$_ } | Sort-Object -Unique)
                        }
                    })
                }
            })
        }
    } |
    Sort-Object questId)

$catalog = [ordered]@{
    schemaVersion = 1
    catalogId = "victoria-lt30-hunting-quest-runtime-v1"
    sourceCatalogId = [string]$source.catalogId
    sourceRevision = [string]$source.revision
    sourceSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $InputPath).Hash.ToLowerInvariant()
    entries = $entries
}

$parent = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Force -Path $parent | Out-Null
$catalog | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 -LiteralPath $OutputPath
Write-Host "Exported $($entries.Count) conservative Victoria quest runtime entries to $OutputPath"
