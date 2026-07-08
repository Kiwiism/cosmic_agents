param(
    [string] $GameCatalogDir = "tmp/game-catalog",
    [string] $NpcCatalogDir = "tmp/npc-catalog",
    [string] $AgentLlmCatalogDir = "tmp/agent-llm-catalog",
    [string] $OutputPath,
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Invoke-CatalogVerifier {
    param(
        [string] $Name,
        [string] $ScriptPath,
        [string] $DirectoryParameter,
        [string] $CatalogDir
    )

    $arguments = @(
        "-ExecutionPolicy", "Bypass",
        "-File", $ScriptPath,
        $DirectoryParameter, $CatalogDir,
        "-Json"
    )

    $output = & powershell @arguments 2>&1
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        $parsed = $null
        try {
            $parsed = ($output | ConvertFrom-Json)
        } catch {
            return [ordered]@{
                name = $Name
                status = "FAIL"
                failCount = 1
                warnCount = 0
                catalogDir = $CatalogDir
                error = ($output -join "`n")
                checks = @()
            }
        }

        return [ordered]@{
            name = $Name
            status = $parsed.status
            failCount = $parsed.failCount
            warnCount = $parsed.warnCount
            catalogDir = $parsed.catalogDir
            error = $null
            checks = @($parsed.checks)
        }
    }

    $report = ($output | ConvertFrom-Json)
    return [ordered]@{
        name = $Name
        status = $report.status
        failCount = $report.failCount
        warnCount = $report.warnCount
        catalogDir = $report.catalogDir
        error = $null
        counts = $report.counts
        checks = @($report.checks)
    }
}

function ConvertTo-MarkdownReport {
    param([object] $Report)

    $lines = [System.Collections.Generic.List[string]]::new()
    [void] $lines.Add("# Catalog Verification Report")
    [void] $lines.Add("")
    [void] $lines.Add("Generated: $($Report.generatedAt)")
    [void] $lines.Add("")
    [void] $lines.Add("| Field | Value |")
    [void] $lines.Add("| --- | --- |")
    [void] $lines.Add("| Status | $($Report.status) |")
    [void] $lines.Add("| Failures | $($Report.failCount) |")
    [void] $lines.Add("| Warnings | $($Report.warnCount) |")
    [void] $lines.Add("| Verifiers | $($Report.verifierCount) |")
    [void] $lines.Add("| Non-passing checks | $($Report.nonPassingCheckCount) |")
    [void] $lines.Add(("| Repo root | `{0}` |" -f $Report.repoRoot))
    [void] $lines.Add("")

    if ($Report.summaryOnly) {
        [void] $lines.Add("## Summary")
        [void] $lines.Add("")
        [void] $lines.Add("| Catalog | Status | Failures | Warnings | Directory |")
        [void] $lines.Add("| --- | --- | ---: | ---: | --- |")
        foreach ($verifier in @($Report.verifierSummaries)) {
            [void] $lines.Add(("| {0} | {1} | {2} | {3} | `{4}` |" -f $verifier.name, $verifier.status, $verifier.failCount, $verifier.warnCount, $verifier.catalogDir))
        }
        [void] $lines.Add("")
        [void] $lines.Add("Detailed verifier rows are omitted because `-SummaryOnly` was used.")
        [void] $lines.Add("")
    } else {
    [void] $lines.Add("## Verifiers")
    [void] $lines.Add("")
    [void] $lines.Add("| Catalog | Status | Failures | Warnings | Directory |")
    [void] $lines.Add("| --- | --- | ---: | ---: | --- |")
    foreach ($verifier in @($Report.verifiers)) {
        [void] $lines.Add(("| {0} | {1} | {2} | {3} | `{4}` |" -f $verifier.name, $verifier.status, $verifier.failCount, $verifier.warnCount, $verifier.catalogDir))
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Non-Passing Checks")
    [void] $lines.Add("")
    [void] $lines.Add("| Catalog | Check | Status | Message |")
    [void] $lines.Add("| --- | --- | --- | --- |")
    $nonPassingCount = 0
    foreach ($verifier in @($Report.verifiers)) {
        foreach ($check in @($verifier.checks | Where-Object { $_.status -ne "PASS" })) {
            $message = ([string] $check.message).Replace("|", "\|")
            [void] $lines.Add("| $($verifier.name) | $($check.id) | $($check.status) | $message |")
            $nonPassingCount++
        }
        if ($verifier.error) {
            $message = ([string] $verifier.error).Replace("|", "\|")
            [void] $lines.Add("| $($verifier.name) | verifier:error | FAIL | $message |")
            $nonPassingCount++
        }
    }
    if ($nonPassingCount -eq 0) {
        [void] $lines.Add("| none | - | PASS | All catalog checks passed. |")
    }
    [void] $lines.Add("")
    }

    [void] $lines.Add("## Notes")
    [void] $lines.Add("")
    [void] $lines.Add("- This report is standalone prep evidence.")
    [void] $lines.Add("- It does not modify runtime code, Agent behavior, BotClient behavior, or config.")
    [void] $lines.Add("- Use this after catalog exports before allowing runtime integration to consume refreshed bundles.")

    return ($lines -join "`n")
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$verifiers = @(
    Invoke-CatalogVerifier "game" "tools/game-catalog/Test-GameKnowledgeCatalog.ps1" "-CatalogDir" $GameCatalogDir
    Invoke-CatalogVerifier "npc" "tools/npc-catalog/Test-NpcCatalog.ps1" "-CatalogDir" $NpcCatalogDir
    Invoke-CatalogVerifier "agent-llm" "tools/agent-llm-catalog/Test-AgentLlmCatalog.ps1" "-CatalogDir" $AgentLlmCatalogDir
)

$failCount = 0
$warnCount = 0
foreach ($verifier in $verifiers) {
    $failCount += [int] $verifier.failCount
    $warnCount += [int] $verifier.warnCount
    if ($verifier.status -eq "FAIL" -and [int] $verifier.failCount -eq 0) {
        $failCount++
    }
}

$overall = if ($failCount -gt 0) {
    "FAIL"
} elseif ($warnCount -gt 0) {
    "INCOMPLETE"
} else {
    "PASS"
}

$verifierSummaries = @($verifiers | ForEach-Object {
    [ordered]@{
        name = $_.name
        status = $_.status
        failCount = $_.failCount
        warnCount = $_.warnCount
        catalogDir = $_.catalogDir
    }
})

$nonPassingChecks = [System.Collections.Generic.List[object]]::new()
foreach ($verifier in $verifiers) {
    foreach ($check in @($verifier.checks | Where-Object { $_.status -ne "PASS" })) {
        [void] $nonPassingChecks.Add([ordered]@{
            catalog = $verifier.name
            id = $check.id
            status = $check.status
            message = $check.message
        })
    }

    if ($verifier.error) {
        [void] $nonPassingChecks.Add([ordered]@{
            catalog = $verifier.name
            id = "verifier:error"
            status = "FAIL"
            message = $verifier.error
        })
    }
}

$checkCount = 0
foreach ($verifier in $verifiers) {
    $checkCount += @($verifier.checks).Count
}
$passCount = $checkCount - @($nonPassingChecks).Count
$warningIds = @($nonPassingChecks | Where-Object { $_.status -eq "WARN" } | ForEach-Object { "$($_.catalog):$($_.id)" })
$failureIds = @($nonPassingChecks | Where-Object { $_.status -eq "FAIL" } | ForEach-Object { "$($_.catalog):$($_.id)" })

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    repoRoot = $repoRoot
    status = $overall
    checkCount = $checkCount
    passCount = $passCount
    failCount = $failCount
    warnCount = $warnCount
    warningIds = @($warningIds)
    failureIds = @($failureIds)
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    verifierCount = @($verifiers).Count
    returnedVerifierCount = if ($SummaryOnly) { 0 } else { @($verifiers).Count }
    returnedCheckCount = if ($SummaryOnly) { 0 } else { $checkCount }
    nonPassingCheckCount = @($nonPassingChecks).Count
    verifierSummaries = @($verifierSummaries)
    nonPassingChecks = @($nonPassingChecks)
    verifiers = if ($SummaryOnly) { $null } else { @($verifiers) }
}

if ($OutputPath) {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and !(Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }

    if ($Json) {
        $report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    } else {
        ConvertTo-MarkdownReport ([pscustomobject] $report) | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    }

    Write-Host "Catalog verification report written:"
    Write-Host "  $OutputPath"
} elseif ($Json) {
    $report | ConvertTo-Json -Depth 12
} else {
    ConvertTo-MarkdownReport ([pscustomobject] $report)
}

if ($failCount -gt 0) {
    exit 1
}
