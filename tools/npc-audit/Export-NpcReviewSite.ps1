param(
    [string] $AuditDir = "tmp/npc-audit",
    [string] $TemplatePath = "tools/npc-audit/review-template.html",
    [string] $OutputPath = "tmp/npc-audit/npc-review.html"
)

$ErrorActionPreference = "Stop"

function As-Bool($Value) { return [string]$Value -eq 'True' }
function As-Int($Value) { $number = 0; [void][int]::TryParse([string]$Value, [ref]$number); return $number }

$auditRoot = (Resolve-Path -LiteralPath $AuditDir).Path
$template = Get-Content -Raw -LiteralPath $TemplatePath
$npcRows = @(Import-Csv -LiteralPath (Join-Path $auditRoot 'npc-regular-town-review.csv'))
$placementRows = @(Import-Csv -LiteralPath (Join-Path $auditRoot 'npc-map-placements.csv') |
    Where-Object isRegularTown -eq 'True')

$placementsByNpc = @{}
foreach ($placement in $placementRows) {
    $npcKey = [int64]$placement.npcId
    if (!$placementsByNpc.ContainsKey($npcKey)) {
        $placementsByNpc[$npcKey] = [System.Collections.Generic.List[object]]::new()
    }
    $placementsByNpc[$npcKey].Add([ordered]@{
        mapId = [int64]$placement.mapId
        mapName = $placement.mapName
        lifeKey = $placement.lifeKey
        x = As-Int $placement.x
        y = As-Int $placement.y
        cy = As-Int $placement.cy
        foothold = As-Int $placement.fh
        rx0 = As-Int $placement.rx0
        rx1 = As-Int $placement.rx1
        flipped = As-Bool $placement.flip
        hidden = As-Bool $placement.hide
    })
}

$records = foreach ($row in $npcRows) {
    $npcKey = [int64]$row.npcId
    [ordered]@{
        npcId = $npcKey
        name = $row.name
        recommendation = $row.recommendation
        confidence = $row.confidence
        purpose = $row.purpose
        placements = if ($placementsByNpc.ContainsKey($npcKey)) { @($placementsByNpc[$npcKey]) } else { @() }
        allMaps = $row.maps
        eventMaps = $row.eventMaps
        hasScript = As-Bool $row.hasScript
        scriptPath = $row.scriptPath
        hasWarp = As-Bool $row.hasWarp
        hasRewardOrExchange = As-Bool $row.hasRewardOrExchange
        questCount = As-Int $row.questCount
        directQuestCount = As-Int $row.directQuestCount
        mentionedQuestCount = As-Int $row.mentionedQuestCount
        quests = $row.quests
        expiredQuestCount = As-Int $row.expiredQuestCount
        undatedQuestCount = As-Int $row.undatedQuestCount
        eventQuestCount = As-Int $row.eventQuestCount
        hasShop = As-Bool $row.hasShop
        shopIds = $row.shopIds
        shopItemCount = As-Int $row.shopItemCount
        shopItems = $row.shopItems
        perfectPitchItemCount = As-Int $row.perfectPitchItemCount
        eventEvidence = As-Bool $row.eventEvidence
        eventEvidenceDetail = $row.eventEvidenceDetail
        npcTextCount = As-Int $row.npcTextCount
        npcTextPreview = $row.npcTextPreview
    }
}

$payload = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString('o')
    mapleStoryIo = [ordered]@{
        region = 'GMS'
        version = '83'
        npcIconTemplate = 'https://maplestory.io/api/GMS/83/npc/{npcId}/icon'
    }
    records = @($records)
}
$json = $payload | ConvertTo-Json -Depth 10 -Compress
$base64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($json))
if (!$template.Contains('__NPC_AUDIT_BASE64__')) { throw 'Review template is missing its data placeholder.' }
$page = $template.Replace('__NPC_AUDIT_BASE64__', $base64)
$outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outputFullPath) | Out-Null
[System.IO.File]::WriteAllText($outputFullPath, $page, [System.Text.UTF8Encoding]::new($false))
Write-Output ([ordered]@{
    output = $outputFullPath
    npcCount = $records.Count
    placementCount = ($records | ForEach-Object { @($_.placements).Count } | Measure-Object -Sum).Sum
    bytes = (Get-Item -LiteralPath $outputFullPath).Length
} | ConvertTo-Json)
