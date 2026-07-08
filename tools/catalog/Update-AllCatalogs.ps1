param(
    [string] $WzRoot = "wz",
    [string] $GameCatalogDir = "tmp/game-catalog",
    [string] $NpcCatalogDir = "tmp/npc-catalog",
    [string] $AgentLlmCatalogDir = "tmp/agent-llm-catalog",
    [string] $ReactorCatalogDir = "tmp/reactor-catalog",
    [string] $ReportDir = "tmp/catalog-refresh",
    [switch] $SkipExport,
    [switch] $SkipNpcApproach,
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Invoke-Step {
    param(
        [string] $Name,
        [scriptblock] $Script
    )

    if ($Json) {
        & $Script > $null
        return
    }

    Write-Host ""
    Write-Host "== $Name =="
    & $Script
}

function Copy-DirectorySnapshot {
    param(
        [string] $Source,
        [string] $Destination
    )

    if (!(Test-Path -LiteralPath $Source -PathType Container)) {
        return $false
    }

    if (Test-Path -LiteralPath $Destination) {
        Remove-Item -LiteralPath $Destination -Recurse -Force
    }

    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    foreach ($item in Get-ChildItem -LiteralPath $Source -Force) {
        Copy-Item -LiteralPath $item.FullName -Destination $Destination -Recurse -Force
    }
    return $true
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $ReportDir "catalog-refresh-$timestamp"
$snapshotDir = Join-Path $runDir "before-agent-llm-catalog"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

if (!$Json) {
    Write-Host "Catalog refresh run:"
    Write-Host "  Run dir: $runDir"
    Write-Host "  Skip export: $SkipExport"
}

$hadSnapshot = Copy-DirectorySnapshot $AgentLlmCatalogDir $snapshotDir

if (!$SkipExport) {
    Invoke-Step "Export game knowledge catalog" {
        powershell -ExecutionPolicy Bypass -File "tools/game-catalog/Export-GameKnowledgeCatalog.ps1" `
            -WzRoot $WzRoot `
            -OutputDir $GameCatalogDir
    }

    Invoke-Step "Export NPC catalog" {
        $arguments = @(
            "-ExecutionPolicy", "Bypass",
            "-File", "tools/npc-catalog/Export-NpcCatalog.ps1",
            "-WzRoot", $WzRoot,
            "-OutputDir", $NpcCatalogDir
        )
        if ($SkipNpcApproach) {
            $arguments += "-SkipApproach"
        }
        & powershell @arguments
    }

    Invoke-Step "Export Agent/LLM catalog" {
        powershell -ExecutionPolicy Bypass -File "tools/agent-llm-catalog/Export-AgentLlmCatalog.ps1" `
            -WzRoot $WzRoot `
            -GameCatalogDir $GameCatalogDir `
            -NpcCatalogDir $NpcCatalogDir `
            -OutputDir $AgentLlmCatalogDir
    }

    Invoke-Step "Export reactor catalog" {
        powershell -ExecutionPolicy Bypass -File "tools/reactor-catalog/Export-ReactorCatalog.ps1" `
            -WzRoot $WzRoot `
            -GameCatalogDir $GameCatalogDir `
            -OutputDir $ReactorCatalogDir
    }
} else {
    if (!$Json) {
        Write-Host "Skipping export steps; verifying existing generated catalogs."
    }
}

Invoke-Step "Write drop source gap report" {
    powershell -ExecutionPolicy Bypass -File "tools/game-catalog/New-DropSourceGapReport.ps1" `
        -CatalogDir $GameCatalogDir `
        -OutputPath (Join-Path $runDir "drop-source-gap-report.md")
}

Invoke-Step "Write combined verification report" {
    powershell -ExecutionPolicy Bypass -File "tools/catalog/Test-AllCatalogs.ps1" `
        -GameCatalogDir $GameCatalogDir `
        -NpcCatalogDir $NpcCatalogDir `
        -AgentLlmCatalogDir $AgentLlmCatalogDir `
        -OutputPath (Join-Path $runDir "catalog-verification-report.md")
}

Invoke-Step "Write Maple Island MVP validation report" {
    powershell -ExecutionPolicy Bypass -File "tools/agent-llm-catalog/New-MapleIslandMvpValidationReport.ps1" `
        -CatalogDir $AgentLlmCatalogDir `
        -OutputPath (Join-Path $runDir "maple-island-mvp-validation-report.md")
}

Invoke-Step "Write reactor catalog validation report" {
    powershell -ExecutionPolicy Bypass -File "tools/reactor-catalog/Test-ReactorCatalog.ps1" `
        -CatalogPath (Join-Path $ReactorCatalogDir "generated_reactor_catalog.json") `
        -OutputPath (Join-Path $runDir "reactor-catalog-validation-report.md")
}

Invoke-Step "Write catalog bundle prep report" {
    powershell -ExecutionPolicy Bypass -File "tools/catalog/Test-CatalogBundlePrep.ps1" `
        -WzRoot $WzRoot `
        -GameCatalogDir $GameCatalogDir `
        -NpcCatalogDir $NpcCatalogDir `
        -AgentLlmCatalogDir $AgentLlmCatalogDir `
        -ReactorCatalogDir $ReactorCatalogDir `
        -OutputPath (Join-Path $runDir "catalog-bundle-prep-report.md") `
        -OutputManifestPath (Join-Path $runDir "draft-catalog-bundle-manifest.json") `
        -OutputSourceHashesPath (Join-Path $runDir "catalog-source-hashes.json")
}

if ($hadSnapshot) {
    Invoke-Step "Write Agent/LLM catalog diff" {
        powershell -ExecutionPolicy Bypass -File "tools/agent-llm-catalog/Compare-AgentLlmCatalog.ps1" `
            -OldCatalogDir $snapshotDir `
            -NewCatalogDir $AgentLlmCatalogDir `
            -OutputPath (Join-Path $runDir "agent-llm-catalog-diff.md")
    }
} else {
    if (!$Json) {
        Write-Host "No previous Agent/LLM catalog snapshot existed; skipping diff."
    }
}

$summary = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    status = "OK"
    repoRoot = $repoRoot
    runDir = $runDir
    skipExport = [bool] $SkipExport
    skipNpcApproach = [bool] $SkipNpcApproach
    hadAgentLlmSnapshot = [bool] $hadSnapshot
    reports = [ordered]@{
        dropSourceGap = Join-Path $runDir "drop-source-gap-report.md"
        catalogVerification = Join-Path $runDir "catalog-verification-report.md"
        mapleIslandMvpValidation = Join-Path $runDir "maple-island-mvp-validation-report.md"
        reactorCatalogValidation = Join-Path $runDir "reactor-catalog-validation-report.md"
        catalogBundlePrep = Join-Path $runDir "catalog-bundle-prep-report.md"
        draftCatalogBundleManifest = Join-Path $runDir "draft-catalog-bundle-manifest.json"
        catalogSourceHashes = Join-Path $runDir "catalog-source-hashes.json"
        catalogStatus = Join-Path $runDir "catalog-status.md"
        catalogRuntimeReadiness = Join-Path $runDir "catalog-runtime-readiness.md"
        agentLlmDiff = if ($hadSnapshot) { Join-Path $runDir "agent-llm-catalog-diff.md" } else { $null }
    }
}

$summaryPath = Join-Path $runDir "summary.json"
$summary | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $summaryPath -Encoding UTF8

Invoke-Step "Write catalog status report" {
    powershell -ExecutionPolicy Bypass -File "tools/catalog/Get-CatalogStatus.ps1" `
        -GameCatalogDir $GameCatalogDir `
        -NpcCatalogDir $NpcCatalogDir `
        -AgentLlmCatalogDir $AgentLlmCatalogDir `
        -ReportDir $ReportDir `
        -OutputPath (Join-Path $runDir "catalog-status.md")
}

Invoke-Step "Write catalog runtime readiness report" {
    powershell -ExecutionPolicy Bypass -File "tools/catalog/Get-CatalogRuntimeReadiness.ps1" `
        -GameCatalogDir $GameCatalogDir `
        -NpcCatalogDir $NpcCatalogDir `
        -AgentLlmCatalogDir $AgentLlmCatalogDir `
        -ReactorCatalogDir $ReactorCatalogDir `
        -OutputPath (Join-Path $runDir "catalog-runtime-readiness.md")
}

$reportRows = @(
    [pscustomobject] @{ key = "dropSourceGap"; path = $summary.reports.dropSourceGap }
    [pscustomobject] @{ key = "catalogVerification"; path = $summary.reports.catalogVerification }
    [pscustomobject] @{ key = "mapleIslandMvpValidation"; path = $summary.reports.mapleIslandMvpValidation }
    [pscustomobject] @{ key = "reactorCatalogValidation"; path = $summary.reports.reactorCatalogValidation }
    [pscustomobject] @{ key = "catalogBundlePrep"; path = $summary.reports.catalogBundlePrep }
    [pscustomobject] @{ key = "draftCatalogBundleManifest"; path = $summary.reports.draftCatalogBundleManifest }
    [pscustomobject] @{ key = "catalogSourceHashes"; path = $summary.reports.catalogSourceHashes }
    [pscustomobject] @{ key = "catalogStatus"; path = $summary.reports.catalogStatus }
    [pscustomobject] @{ key = "catalogRuntimeReadiness"; path = $summary.reports.catalogRuntimeReadiness }
    if ($summary.reports.agentLlmDiff) { [pscustomobject] @{ key = "agentLlmDiff"; path = $summary.reports.agentLlmDiff } }
    [pscustomobject] @{ key = "summary"; path = $summaryPath }
)

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    status = "OK"
    repoRoot = $repoRoot
    runDir = $runDir
    skipExport = [bool] $SkipExport
    skipNpcApproach = [bool] $SkipNpcApproach
    hadAgentLlmSnapshot = [bool] $hadSnapshot
    summaryPath = $summaryPath
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    reportCount = $reportRows.Count
    returnedReportCount = if ($SummaryOnly) { 0 } else { $reportRows.Count }
    reports = if ($SummaryOnly) { $null } else { $reportRows }
}

if ($Json) {
    $report | ConvertTo-Json -Depth 6
    return
}

Write-Host ""
Write-Host "Catalog refresh complete:"
Write-Host "  $runDir"
