param(
    [string] $GameCatalogDir = "tmp/game-catalog",
    [string] $NpcCatalogDir = "tmp/npc-catalog",
    [string] $AgentLlmCatalogDir = "tmp/agent-llm-catalog",
    [string] $ReportDir = "tmp/catalog-refresh",
    [string] $OutputPath,
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Get-FileStatus {
    param([string] $Path)

    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        return [ordered]@{
            path = $Path
            exists = $false
            length = 0
            lastWriteTime = $null
        }
    }

    $item = Get-Item -LiteralPath $Path
    return [ordered]@{
        path = $item.FullName
        exists = $true
        length = $item.Length
        lastWriteTime = $item.LastWriteTime.ToString("o")
    }
}

function Get-DirectoryStatus {
    param([string] $Path)

    if (!(Test-Path -LiteralPath $Path -PathType Container)) {
        return [ordered]@{
            path = $Path
            exists = $false
            fileCount = 0
            lastWriteTime = $null
        }
    }

    $item = Get-Item -LiteralPath $Path
    $files = @(Get-ChildItem -LiteralPath $Path -File -ErrorAction SilentlyContinue)
    return [ordered]@{
        path = $item.FullName
        exists = $true
        fileCount = $files.Count
        lastWriteTime = $item.LastWriteTime.ToString("o")
    }
}

function Invoke-CombinedCatalogVerifier {
    param(
        [string] $GameDir,
        [string] $NpcDir,
        [string] $AgentLlmDir
    )

    $output = & powershell -ExecutionPolicy Bypass -File "tools/catalog/Test-AllCatalogs.ps1" `
        -GameCatalogDir $GameDir `
        -NpcCatalogDir $NpcDir `
        -AgentLlmCatalogDir $AgentLlmDir `
        -Json 2>&1
    $exitCode = $LASTEXITCODE

    try {
        $parsed = ($output | ConvertFrom-Json)
    } catch {
        return [ordered]@{
            status = "FAIL"
            failCount = 1
            warnCount = 0
            error = ($output -join "`n")
            verifiers = @()
        }
    }

    if ($exitCode -ne 0 -and $parsed.failCount -eq 0) {
        $parsed.failCount = 1
        $parsed.status = "FAIL"
    }

    return $parsed
}

function Get-LatestRefreshRun {
    param([string] $Path)

    if (!(Test-Path -LiteralPath $Path -PathType Container)) {
        return $null
    }

    return Get-ChildItem -LiteralPath $Path -Directory -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
}

function ConvertTo-MarkdownStatus {
    param([object] $Status)

    $lines = [System.Collections.Generic.List[string]]::new()
    [void] $lines.Add("# Catalog Status")
    [void] $lines.Add("")
    [void] $lines.Add("Generated: $($Status.generatedAt)")
    [void] $lines.Add("")
    [void] $lines.Add("| Field | Value |")
    [void] $lines.Add("| --- | --- |")
    [void] $lines.Add("| Status | $($Status.status) |")
    [void] $lines.Add("| Failures | $($Status.failCount) |")
    [void] $lines.Add("| Warnings | $($Status.warnCount) |")
    [void] $lines.Add(("| Repo root | `{0}` |" -f $Status.repoRoot))
    [void] $lines.Add("")

    [void] $lines.Add("## Catalog Directories")
    [void] $lines.Add("")
    [void] $lines.Add("| Catalog | Exists | Files | Last Write | Path |")
    [void] $lines.Add("| --- | --- | ---: | --- | --- |")
    foreach ($entry in $Status.catalogDirectories.GetEnumerator()) {
        $value = $entry.Value
        [void] $lines.Add(("| {0} | {1} | {2} | {3} | `{4}` |" -f $entry.Key, $value.exists, $value.fileCount, $value.lastWriteTime, $value.path))
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Current Verification")
    [void] $lines.Add("")
    [void] $lines.Add("| Verifier | Status | Failures | Warnings |")
    [void] $lines.Add("| --- | --- | ---: | ---: |")
    foreach ($verifier in @($Status.currentVerification.verifiers)) {
        [void] $lines.Add(("| {0} | {1} | {2} | {3} |" -f $verifier.name, $verifier.status, $verifier.failCount, $verifier.warnCount))
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Latest Refresh")
    [void] $lines.Add("")
    if ($Status.latestRefresh.exists) {
        [void] $lines.Add(('Latest run: `{0}`' -f $Status.latestRefresh.path))
        [void] $lines.Add("")
        [void] $lines.Add("| Report | Exists | Path |")
        [void] $lines.Add("| --- | --- | --- |")
        foreach ($report in $Status.latestRefresh.reports.GetEnumerator()) {
            [void] $lines.Add(("| {0} | {1} | `{2}` |" -f $report.Key, $report.Value.exists, $report.Value.path))
        }
    } else {
        [void] $lines.Add("No catalog refresh run was found.")
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Notes")
    [void] $lines.Add("")
    [void] $lines.Add("- This status report is read-only prep evidence.")
    [void] $lines.Add("- It does not modify runtime code, Agent behavior, BotClient behavior, or config.")
    [void] $lines.Add("- Use it before plugging a refreshed catalog bundle into runtime integration.")

    return ($lines -join "`n")
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$verification = Invoke-CombinedCatalogVerifier $GameCatalogDir $NpcCatalogDir $AgentLlmCatalogDir
$latestRun = Get-LatestRefreshRun $ReportDir

$latestRefresh = if ($latestRun) {
    $summaryPath = Join-Path $latestRun.FullName "summary.json"
    $summary = $null
    if (Test-Path -LiteralPath $summaryPath -PathType Leaf) {
        try {
            $summary = Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
        } catch {
            $summary = $null
        }
    }

    [ordered]@{
        exists = $true
        path = $latestRun.FullName
        lastWriteTime = $latestRun.LastWriteTime.ToString("o")
        summary = $summary
        reports = [ordered]@{
            dropSourceGap = Get-FileStatus (Join-Path $latestRun.FullName "drop-source-gap-report.md")
            catalogVerification = Get-FileStatus (Join-Path $latestRun.FullName "catalog-verification-report.md")
            catalogBundlePrep = Get-FileStatus (Join-Path $latestRun.FullName "catalog-bundle-prep-report.md")
            draftCatalogBundleManifest = Get-FileStatus (Join-Path $latestRun.FullName "draft-catalog-bundle-manifest.json")
            catalogRuntimeReadiness = Get-FileStatus (Join-Path $latestRun.FullName "catalog-runtime-readiness.md")
            agentLlmDiff = Get-FileStatus (Join-Path $latestRun.FullName "agent-llm-catalog-diff.md")
        }
    }
} else {
    [ordered]@{
        exists = $false
        path = $ReportDir
        lastWriteTime = $null
        summary = $null
        reports = [ordered]@{}
    }
}

$status = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    repoRoot = $repoRoot
    status = $verification.status
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    failCount = [int] $verification.failCount
    warnCount = [int] $verification.warnCount
    catalogDirectoryCount = 3
    existingCatalogDirectoryCount = @(
        Get-DirectoryStatus $GameCatalogDir
        Get-DirectoryStatus $NpcCatalogDir
        Get-DirectoryStatus $AgentLlmCatalogDir
    ) | Where-Object { $_.exists } | Measure-Object | Select-Object -ExpandProperty Count
    verifierCount = @($verification.verifiers).Count
    latestRefreshExists = [bool] $latestRefresh.exists
    latestRefreshPath = if ($latestRefresh.exists) { $latestRefresh.path } else { $null }
    latestRefreshReportCount = @($latestRefresh.reports.GetEnumerator()).Count
    existingLatestRefreshReportCount = @($latestRefresh.reports.GetEnumerator() | Where-Object { $_.Value.exists }).Count
    returnedCatalogDirectoryCount = if ($SummaryOnly) { 0 } else { 3 }
    returnedVerifierCount = if ($SummaryOnly) { 0 } else { @($verification.verifiers).Count }
    returnedReportCount = if ($SummaryOnly) { 0 } else { @($latestRefresh.reports.GetEnumerator()).Count }
    returnedRunCount = if ($SummaryOnly -or !$latestRefresh.exists) { 0 } else { 1 }
    catalogDirectories = if ($SummaryOnly) {
        $null
    } else {
        [ordered]@{
            game = Get-DirectoryStatus $GameCatalogDir
            npc = Get-DirectoryStatus $NpcCatalogDir
            agentLlm = Get-DirectoryStatus $AgentLlmCatalogDir
        }
    }
    currentVerification = [ordered]@{
        status = $verification.status
        failCount = [int] $verification.failCount
        warnCount = [int] $verification.warnCount
        verifiers = if ($SummaryOnly) { $null } else { @($verification.verifiers) }
    }
    latestRefresh = if ($SummaryOnly) { $null } else { $latestRefresh }
}

if ($OutputPath) {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and !(Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }

    if ($Json) {
        $status | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    } else {
        ConvertTo-MarkdownStatus ([pscustomobject] $status) | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    }

    Write-Host "Catalog status report written:"
    Write-Host "  $OutputPath"
} elseif ($Json) {
    $status | ConvertTo-Json -Depth 12
} else {
    ConvertTo-MarkdownStatus ([pscustomobject] $status)
}

if ($status.failCount -gt 0) {
    exit 1
}
