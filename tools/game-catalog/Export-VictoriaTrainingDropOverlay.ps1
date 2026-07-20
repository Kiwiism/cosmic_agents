param(
    [string]$TrainingCatalog = "src/main/resources/agents/catalogs/victoria-level15-30-training-catalog.json",
    [string]$DropCatalog = "tmp/game-catalog/generated_drop_catalog.json",
    [string]$OutputDirectory = "tmp/game-catalog",
    [string]$Revision = ""
)

$ErrorActionPreference = "Stop"

$trainingPath = (Resolve-Path -LiteralPath $TrainingCatalog).Path
$dropPath = (Resolve-Path -LiteralPath $DropCatalog).Path
$dropHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $dropPath).Hash.ToLowerInvariant()
if ([string]::IsNullOrWhiteSpace($Revision)) {
    $Revision = "sha256-$($dropHash.Substring(0, 12))"
}

$training = Get-Content -Raw -LiteralPath $trainingPath | ConvertFrom-Json
$dropEntries = Get-Content -Raw -LiteralPath $dropPath | ConvertFrom-Json
$dropsByMob = @{}
foreach ($drop in $dropEntries) {
    if ($drop.sourceType -ne "mob") {
        continue
    }
    $mobId = [int]$drop.sourceId
    if (-not $dropsByMob.ContainsKey($mobId)) {
        $dropsByMob[$mobId] = [System.Collections.Generic.List[object]]::new()
    }
    $dropsByMob[$mobId].Add([ordered]@{
        itemId = [int]$drop.itemId
        itemName = [string]$drop.itemName
        minimumQuantity = [int]$drop.minimumQuantity
        maximumQuantity = [int]$drop.maximumQuantity
        questId = [int]$drop.questId
        chance = [int]$drop.chance
        flags = @($drop.flags)
    })
}

$mapOverlays = foreach ($map in $training.trainingMaps) {
    $mobOverlays = foreach ($spawn in $map.spawns) {
        $drops = if ($dropsByMob.ContainsKey([int]$spawn.mobId)) {
            @($dropsByMob[[int]$spawn.mobId])
        } else {
            @()
        }
        [ordered]@{
            mobId = [int]$spawn.mobId
            mobName = [string]$spawn.mobName
            trainingRole = [string]$spawn.role
            drops = $drops
        }
    }
    [ordered]@{
        mapId = [int]$map.mapId
        mapName = [string]$map.mapName
        mobs = @($mobOverlays)
    }
}

$payload = [ordered]@{
    schemaVersion = 1
    catalogId = "victoria-level15-30-hunting-overlay-$Revision"
    trainingCatalogId = [string]$training.catalogId
    dropTableRevision = $Revision
    generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    source = [ordered]@{
        path = $dropPath
        sha256 = $dropHash
    }
    maps = @($mapOverlays)
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$safeRevision = $Revision -replace '[^A-Za-z0-9._-]', '-'
$outputPath = Join-Path $OutputDirectory "victoria-level15-30-hunting-overlay-$safeRevision.json"
$payload | ConvertTo-Json -Depth 10 | Set-Content -Encoding utf8 -LiteralPath $outputPath

$mobCount = @($mapOverlays | ForEach-Object { $_.mobs }).Count
$dropCount = @($mapOverlays | ForEach-Object { $_.mobs } | ForEach-Object { $_.drops }).Count
Write-Output "Wrote $outputPath ($($mapOverlays.Count) maps, $mobCount map-mob entries, $dropCount drops, revision $Revision)"
