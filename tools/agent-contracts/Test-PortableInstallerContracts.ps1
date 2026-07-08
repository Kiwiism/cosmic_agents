param(
    [string] $ManifestSchemaPath = "docs/agents/server-adapter/portable-install-manifest.schema.json",
    [string] $PlanSchemaPath = "docs/agents/server-adapter/portable-install-plan.schema.json",
    [string] $PatchSchemaPath = "docs/agents/server-adapter/portable-patch-operation.schema.json",
    [string] $VerifyReportSchemaPath = "docs/agents/server-adapter/portable-install-verify-report.schema.json",
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
    $Checks.Add([ordered]@{ id = $Id; status = $Status; message = $Message }) | Out-Null
}

function Read-Json {
    param([System.Collections.Generic.List[object]] $Checks, [string] $Path, [string] $Label)
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        Add-Check $Checks "file:$Label" "FAIL" "Missing $Label at $Path."
        return $null
    }
    Add-Check $Checks "file:$Label" "PASS" "Found $Label."
    try {
        $value = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
        Add-Check $Checks "json:$Label" "PASS" "$Label is valid JSON."
        return $value
    } catch {
        Add-Check $Checks "json:$Label" "FAIL" "$Label is invalid JSON: $($_.Exception.Message)"
        return $null
    }
}

function Test-Required {
    param([System.Collections.Generic.List[object]] $Checks, [object] $Schema, [string] $Label, [string[]] $Fields)
    $required = @($Schema.required)
    foreach ($field in $Fields) {
        if ($required -contains $field) {
            Add-Check $Checks "$Label.required:$field" "PASS" "$Label requires $field."
        } else {
            Add-Check $Checks "$Label.required:$field" "FAIL" "$Label should require $field."
        }
    }
}

function Test-EnumContains {
    param([System.Collections.Generic.List[object]] $Checks, [object[]] $Enum, [string] $Label, [string[]] $Values)
    foreach ($value in $Values) {
        if (@($Enum) -contains $value) {
            Add-Check $Checks "$Label.enum:$value" "PASS" "$Label allows $value."
        } else {
            Add-Check $Checks "$Label.enum:$value" "FAIL" "$Label should allow $value."
        }
    }
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()
$manifest = Read-Json $checks $ManifestSchemaPath "portable-install-manifest-schema"
$plan = Read-Json $checks $PlanSchemaPath "portable-install-plan-schema"
$patch = Read-Json $checks $PatchSchemaPath "portable-patch-operation-schema"
$verify = Read-Json $checks $VerifyReportSchemaPath "portable-install-verify-report-schema"

if ($null -ne $manifest) {
    Test-Required $checks $manifest "portable-install-manifest" @(
        "schemaVersion", "installerVersion", "targetFamily", "agentPlatformVersion",
        "markerPrefix", "defaultEnabled", "installedFiles", "patchedFiles", "configKeys"
    )
    if ($manifest.properties.defaultEnabled.const -eq $false) {
        Add-Check $checks "portable-install-manifest.defaultEnabled:false" "PASS" "Manifest requires defaultEnabled false."
    } else {
        Add-Check $checks "portable-install-manifest.defaultEnabled:false" "FAIL" "Manifest must require defaultEnabled false."
    }
}

if ($null -ne $plan) {
    Test-Required $checks $plan "portable-install-plan" @(
        "schemaVersion", "planId", "mode", "targetRoot", "agentRoot", "status",
        "filesToCopy", "patchesToApply", "configChanges", "risks"
    )
    Test-EnumContains $checks $plan.properties.mode.enum "portable-install-plan.mode" @("PLAN", "INSTALL", "VERIFY", "UPDATE", "UNINSTALL")
    Test-EnumContains $checks $plan.properties.status.enum "portable-install-plan.status" @("PASS", "WARN", "FAIL", "UNKNOWN")
}

if ($null -ne $patch) {
    Test-Required $checks $patch "portable-patch-operation" @(
        "schemaVersion", "patchId", "targetFile", "markerId", "markerPrefix",
        "anchor", "position", "content", "required"
    )
    Test-EnumContains $checks $patch.properties.position.enum "portable-patch-operation.position" @("BEFORE", "AFTER", "REPLACE_MARKER_CONTENT")
}

if ($null -ne $verify) {
    Test-Required $checks $verify "portable-install-verify-report" @(
        "schemaVersion", "status", "targetRoot", "installedVersion",
        "checks", "warnings", "errors"
    )
    Test-EnumContains $checks $verify.properties.status.enum "portable-install-verify-report.status" @("PASS", "WARN", "FAIL", "UNKNOWN")
}

$failures = @($checks | Where-Object { $_.status -eq "FAIL" })
$warnings = @($checks | Where-Object { $_.status -eq "WARN" })
$failCount = $failures.Count
$warnCount = $warnings.Count
$passCount = @($checks | Where-Object { $_.status -eq "PASS" }).Count
$overall = if ($failCount -gt 0) { "FAIL" } elseif ($warnCount -gt 0) { "INCOMPLETE" } else { "PASS" }

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    repoRoot = $repoRoot
    status = $overall
    checkCount = $checks.Count
    passCount = $passCount
    failCount = $failCount
    warnCount = $warnCount
    failureIds = @($failures | ForEach-Object { $_.id })
    warningIds = @($warnings | ForEach-Object { $_.id })
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    returnedCheckCount = if ($SummaryOnly) { 0 } else { $checks.Count }
    checks = if ($SummaryOnly) { $null } else { @($checks) }
}

if ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    Write-Host "Portable installer contract verification: $overall"
    Write-Host "Failures: $failCount  Warnings: $warnCount"
    Write-Host ""
    foreach ($check in $checks) {
        Write-Host ("[{0}] {1} - {2}" -f $check.status, $check.id, $check.message)
    }
}

if ($failCount -gt 0) {
    exit 1
}
