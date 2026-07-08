param(
    [string] $PresetRoot = "docs/agents/soak-test-harness/presets",
    [double] $DefaultRatioTolerance = 0.0001,
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Add-Check {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [Parameter(Mandatory = $true)] [string] $Id,
        [Parameter(Mandatory = $true)] [string] $Status,
        [Parameter(Mandatory = $true)] [string] $Message
    )

    $Checks.Add([ordered]@{
        id = $Id
        status = $Status
        message = $Message
    }) | Out-Null
}

function Test-RequiredProperty {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [object] $Object,
        [string] $Property,
        [string] $Label
    )

    if ($null -eq $Object) {
        Add-Check $Checks "$Label.required:$Property" "FAIL" "Cannot check $Property because $Label is missing."
        return $false
    }

    if ($Object.PSObject.Properties.Name -contains $Property) {
        Add-Check $Checks "$Label.required:$Property" "PASS" "$Label has required property $Property."
        return $true
    }

    Add-Check $Checks "$Label.required:$Property" "FAIL" "$Label is missing required property $Property."
    return $false
}

function Test-RatioGroup {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [object[]] $Rows,
        [string] $Id,
        [double] $Tolerance
    )

    if ($Rows.Count -eq 0) {
        Add-Check $Checks $Id "FAIL" "$Id has no rows."
        return
    }

    $sum = 0.0
    $missingRatio = 0
    foreach ($row in @($Rows)) {
        if ($row.PSObject.Properties.Name -contains "ratio") {
            $sum += [double] $row.ratio
        } else {
            $missingRatio++
        }
    }

    if ($missingRatio -gt 0) {
        Add-Check $Checks $Id "FAIL" "$Id has $missingRatio row(s) missing ratio."
        return
    }

    if ([Math]::Abs($sum - 1.0) -le $Tolerance) {
        Add-Check $Checks $Id "PASS" "$Id ratios total $sum."
    } else {
        Add-Check $Checks $Id "FAIL" "$Id ratios total $sum, expected 1 within tolerance $Tolerance."
    }
}

function Test-UniqueIds {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [object[]] $Rows,
        [string] $Property,
        [string] $Id
    )

    $values = @(
        foreach ($row in @($Rows)) {
            if ($row.PSObject.Properties.Name -contains $Property) {
                [string] $row.$Property
            }
        }
    )

    $duplicates = @($values | Group-Object | Where-Object { $_.Count -gt 1 } | Select-Object -ExpandProperty Name)
    if ($duplicates.Count -eq 0) {
        Add-Check $Checks $Id "PASS" "$Id has unique $Property values."
    } else {
        Add-Check $Checks $Id "FAIL" "$Id has duplicate $Property value(s): $($duplicates -join ', ')."
    }
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()

if (!(Test-Path -LiteralPath $PresetRoot -PathType Container)) {
    Add-Check $checks "preset-root" "FAIL" "Preset root does not exist: $PresetRoot."
} else {
    Add-Check $checks "preset-root" "PASS" "Found preset root $PresetRoot."

    $presetFiles = @(Get-ChildItem -LiteralPath $PresetRoot -Filter "*.population-preset.json" -File | Sort-Object Name)
    if ($presetFiles.Count -eq 0) {
        Add-Check $checks "preset-files" "FAIL" "No *.population-preset.json files found in $PresetRoot."
    } else {
        Add-Check $checks "preset-files" "PASS" "Found $($presetFiles.Count) population preset file(s)."
    }

    foreach ($file in $presetFiles) {
        $label = "preset:$($file.BaseName)"
        $preset = $null

        try {
            $preset = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
            Add-Check $checks "$label.json" "PASS" "$($file.Name) is valid JSON."
        } catch {
            Add-Check $checks "$label.json" "FAIL" "$($file.Name) is not valid JSON: $($_.Exception.Message)"
            continue
        }

        foreach ($property in @(
            "schemaVersion",
            "preset",
            "description",
            "seedPolicy",
            "defaultSeed",
            "targetAgentCount",
            "levelBands",
            "regions",
            "jobs",
            "archetypes",
            "roles",
            "cohorts",
            "mapCapacityPolicy"
        )) {
            Test-RequiredProperty $checks $preset $property $label | Out-Null
        }

        if ($null -eq $preset) {
            continue
        }

        $tolerance = $DefaultRatioTolerance
        if ($preset.validation -and $preset.validation.PSObject.Properties.Name -contains "ratioTolerance") {
            $tolerance = [double] $preset.validation.ratioTolerance
        }

        foreach ($group in @(
            @{ name = "levelBands"; property = "name" },
            @{ name = "regions"; property = "regionId" },
            @{ name = "jobs"; property = "name" },
            @{ name = "archetypes"; property = "name" },
            @{ name = "roles"; property = "name" },
            @{ name = "cohorts"; property = "cohortId" }
        )) {
            $rows = @($preset.$($group.name))
            Test-RatioGroup $checks $rows "$label.$($group.name).ratio" $tolerance
            Test-UniqueIds $checks $rows $group.property "$label.$($group.name).unique"
        }

        if ([int] $preset.targetAgentCount -gt 0) {
            Add-Check $checks "$label.targetAgentCount" "PASS" "$label targets $($preset.targetAgentCount) Agent(s)."
        } else {
            Add-Check $checks "$label.targetAgentCount" "FAIL" "$label targetAgentCount must be positive."
        }

        $capacity = $preset.mapCapacityPolicy
        if ($null -ne $capacity) {
            if ([int] $capacity.defaultHardAgentCap -ge [int] $capacity.defaultSoftAgentCap) {
                Add-Check $checks "$label.capacity.default-cap-order" "PASS" "Default hard cap is greater than or equal to soft cap."
            } else {
                Add-Check $checks "$label.capacity.default-cap-order" "FAIL" "Default hard cap is below soft cap."
            }

            if ([int] $capacity.townSoftAgentCap -ge [int] $capacity.defaultSoftAgentCap) {
                Add-Check $checks "$label.capacity.town-cap" "PASS" "Town soft cap is greater than or equal to default soft cap."
            } else {
                Add-Check $checks "$label.capacity.town-cap" "WARN" "Town soft cap is below default soft cap; verify this is intentional."
            }
        }
    }
}

$failCount = @($checks | Where-Object { $_.status -eq "FAIL" }).Count
$warnCount = @($checks | Where-Object { $_.status -eq "WARN" }).Count
$overall = if ($failCount -gt 0) {
    "FAIL"
} elseif ($warnCount -gt 0) {
    "INCOMPLETE"
} else {
    "PASS"
}

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    repoRoot = $repoRoot
    presetRoot = $PresetRoot
    status = $overall
    failCount = $failCount
    warnCount = $warnCount
    checkCount = @($checks).Count
    passCount = @($checks | Where-Object { $_.status -eq "PASS" }).Count
    warningIds = @($checks | Where-Object { $_.status -eq "WARN" } | ForEach-Object { $_.id })
    failureIds = @($checks | Where-Object { $_.status -eq "FAIL" } | ForEach-Object { $_.id })
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    returnedCheckCount = if ($SummaryOnly) { 0 } else { @($checks).Count }
    checks = if ($SummaryOnly) { $null } else { @($checks) }
}

if ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    Write-Host "Soak population preset verification: $overall"
    Write-Host "Failures: $failCount  Warnings: $warnCount"
    Write-Host ""
    if ($SummaryOnly) {
        Write-Host "Detailed check rows omitted."
    } else {
        foreach ($check in $checks) {
            Write-Host ("[{0}] {1} - {2}" -f $check.status, $check.id, $check.message)
        }
    }
}

if ($failCount -gt 0) {
    exit 1
}
