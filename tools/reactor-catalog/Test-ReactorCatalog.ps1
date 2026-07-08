param(
    [string] $CatalogPath = "tmp/reactor-catalog/generated_reactor_catalog.json",
    [string] $OutputPath,
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Add-Check {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [string] $Id,
        [string] $Status,
        [string] $Message
    )

    [void] $Checks.Add([ordered]@{
        id = $Id
        status = $Status
        message = $Message
    })
}

function As-Array {
    param([object] $Value)

    if ($null -eq $Value) {
        return @()
    }
    if ($Value -is [System.Array]) {
        return @($Value)
    }
    return @($Value)
}

function ConvertTo-Markdown {
    param([object] $Report)

    $lines = [System.Collections.Generic.List[string]]::new()
    [void] $lines.Add("# Reactor Catalog Validation")
    [void] $lines.Add("")
    [void] $lines.Add("Generated: $($Report.generatedAt)")
    [void] $lines.Add("")
    [void] $lines.Add("| Field | Value |")
    [void] $lines.Add("| --- | --- |")
    [void] $lines.Add("| Status | $($Report.status) |")
    [void] $lines.Add("| Pass | $($Report.passCount) |")
    [void] $lines.Add("| Warn | $($Report.warnCount) |")
    [void] $lines.Add("| Fail | $($Report.failCount) |")
    [void] $lines.Add("")
    [void] $lines.Add("## Checks")
    [void] $lines.Add("")
    foreach ($check in @($Report.checks)) {
        [void] $lines.Add("- [$($check.status)] `$($check.id)`: $($check.message)")
    }
    if (@($Report.acceptedGaps).Count -gt 0) {
        [void] $lines.Add("")
        [void] $lines.Add("## Accepted Gaps")
        [void] $lines.Add("")
        foreach ($gap in @($Report.acceptedGaps)) {
            [void] $lines.Add("- `$($gap.id)`: $($gap.reason)")
        }
    }
    return ($lines -join "`n")
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()
$catalog = $null
$entries = @()
$acceptedGaps = @()

if (!(Test-Path -LiteralPath $CatalogPath -PathType Leaf)) {
    Add-Check $checks "reactor-catalog:exists" "FAIL" "Missing reactor catalog at $CatalogPath."
} else {
    Add-Check $checks "reactor-catalog:exists" "PASS" "Found reactor catalog at $CatalogPath."
    try {
        $catalog = Get-Content -LiteralPath $CatalogPath -Raw | ConvertFrom-Json
        Add-Check $checks "reactor-catalog:parse" "PASS" "Reactor catalog parses as JSON."
    } catch {
        Add-Check $checks "reactor-catalog:parse" "FAIL" "Could not parse reactor catalog: $($_.Exception.Message)"
    }
}

if ($catalog) {
    if ([int] $catalog.schemaVersion -eq 1) {
        Add-Check $checks "reactor-catalog:schema-version" "PASS" "Top-level schemaVersion is 1."
    } else {
        Add-Check $checks "reactor-catalog:schema-version" "FAIL" "Top-level schemaVersion must be 1."
    }

    $entries = @(As-Array $catalog.entries)
    $acceptedGaps = @(As-Array $catalog.acceptedGaps)
    if ($entries.Count -gt 0) {
        Add-Check $checks "reactor-catalog:entries" "PASS" "Catalog has $($entries.Count) reactor placement entries."
    } else {
        Add-Check $checks "reactor-catalog:entries" "FAIL" "Catalog has no reactor placement entries."
    }

    $missingRequired = @($entries | Where-Object {
        [int] $_.schemaVersion -ne 1 -or
        [string]::IsNullOrWhiteSpace([string] $_.objectType) -or
        $null -eq $_.mapId -or
        $null -eq $_.reactorId -or
        [string]::IsNullOrWhiteSpace([string] $_.mapSource)
    })
    if ($missingRequired.Count -eq 0) {
        Add-Check $checks "reactor-catalog:entry-required-fields" "PASS" "All entries have schemaVersion, objectType, mapId, reactorId, and mapSource."
    } else {
        Add-Check $checks "reactor-catalog:entry-required-fields" "FAIL" "$($missingRequired.Count) entries are missing required fields."
    }

    $missingPosition = @($entries | Where-Object { $null -eq $_.x -or $null -eq $_.y })
    if ($missingPosition.Count -eq 0) {
        Add-Check $checks "reactor-catalog:positions" "PASS" "All entries include x/y placement."
    } else {
        Add-Check $checks "reactor-catalog:positions" "WARN" "$($missingPosition.Count) entries are missing x/y placement."
    }

    $pio = @($entries | Where-Object {
        @(As-Array $_.inferredQuestIds) -contains 1008 -or
        @(As-Array $_.inferredItemIds) -contains 4031161 -or
        @(As-Array $_.inferredItemIds) -contains 4031162 -or
        @(As-Array $_.flags) -contains "maple-island-pio-candidate"
    })
    if ($pio.Count -gt 0) {
        Add-Check $checks "reactor-catalog:pio-maple-island" "PASS" "Found $($pio.Count) Pio/Maple Island candidate reactor placement(s)."
    } else {
        $gap = @($acceptedGaps | Where-Object { $_.id -eq "maple-island-pio-reactors-not-linked" })
        if ($gap.Count -gt 0) {
            Add-Check $checks "reactor-catalog:pio-maple-island" "WARN" "No Pio candidate placement found; accepted gap is documented."
        } else {
            Add-Check $checks "reactor-catalog:pio-maple-island" "FAIL" "No Pio candidate placement found and no accepted gap is documented."
        }
    }
}

$failures = @($checks | Where-Object { $_.status -eq "FAIL" })
$warnings = @($checks | Where-Object { $_.status -eq "WARN" })
$passes = @($checks | Where-Object { $_.status -eq "PASS" })
$status = if ($failures.Count -gt 0) {
    "FAIL"
} elseif ($warnings.Count -gt 0) {
    "READY_WITH_ACCEPTED_REACTOR_GAP"
} else {
    "PASS"
}

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    status = $status
    catalogPath = $CatalogPath
    checkCount = $checks.Count
    passCount = $passes.Count
    warnCount = $warnings.Count
    failCount = $failures.Count
    warningIds = @($warnings | ForEach-Object { $_.id })
    failureIds = @($failures | ForEach-Object { $_.id })
    acceptedGaps = @($acceptedGaps)
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    returnedCheckCount = if ($SummaryOnly) { 0 } else { $checks.Count }
    checks = if ($SummaryOnly) { $null } else { @($checks) }
}

if ($OutputPath) {
    ConvertTo-Markdown $report | Set-Content -LiteralPath $OutputPath -Encoding UTF8
}

if ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    Write-Host "Reactor catalog validation: $status"
    foreach ($check in $checks) {
        Write-Host ("[{0}] {1} - {2}" -f $check.status, $check.id, $check.message)
    }
}

if ($failures.Count -gt 0) {
    exit 1
}
