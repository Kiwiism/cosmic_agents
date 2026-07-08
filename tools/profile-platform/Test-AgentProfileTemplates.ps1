param(
    [string] $TemplateDir = "docs/agents/profile-platform/templates",
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

    $Checks.Add([ordered]@{
        id = $Id
        status = $Status
        message = $Message
    }) | Out-Null
}

function Get-Array {
    param([object] $Value)
    if ($null -eq $Value) {
        return @()
    }
    if ($Value -is [System.Array]) {
        return @($Value)
    }
    return @($Value)
}

function Test-Template {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [string] $Path,
        [string] $ExpectedTemplateId
    )

    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        Add-Check $Checks "file:$ExpectedTemplateId" "FAIL" "Missing template $ExpectedTemplateId at $Path."
        return
    }

    Add-Check $Checks "file:$ExpectedTemplateId" "PASS" "Found template $ExpectedTemplateId."

    try {
        $profile = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
        Add-Check $Checks "json:$ExpectedTemplateId" "PASS" "Template $ExpectedTemplateId is valid JSON."
    } catch {
        Add-Check $Checks "json:$ExpectedTemplateId" "FAIL" "Template $ExpectedTemplateId is invalid JSON: $($_.Exception.Message)"
        return
    }

    foreach ($field in @("schemaVersion", "agentId", "identity", "traits", "policy", "planProfile")) {
        if ($profile.PSObject.Properties.Name -contains $field) {
            Add-Check $Checks ("required:{0}:{1}" -f $ExpectedTemplateId, $field) "PASS" "$ExpectedTemplateId has required field $field."
        } else {
            Add-Check $Checks ("required:{0}:{1}" -f $ExpectedTemplateId, $field) "FAIL" "$ExpectedTemplateId is missing required field $field."
        }
    }

    if ($profile.metadata.templateId -eq $ExpectedTemplateId) {
        Add-Check $Checks "template-id:$ExpectedTemplateId" "PASS" "$ExpectedTemplateId metadata matches file expectation."
    } else {
        Add-Check $Checks "template-id:$ExpectedTemplateId" "FAIL" "$ExpectedTemplateId metadata.templateId is $($profile.metadata.templateId)."
    }

    if ($profile.identity.archetype -eq $ExpectedTemplateId) {
        Add-Check $Checks "archetype:$ExpectedTemplateId" "PASS" "$ExpectedTemplateId archetype matches template id."
    } else {
        Add-Check $Checks "archetype:$ExpectedTemplateId" "FAIL" "$ExpectedTemplateId archetype is $($profile.identity.archetype)."
    }

    $constraints = $profile.planProfile.hardConstraints
    $forbiddenNpcActions = @(Get-Array $constraints.forbiddenNpcActions)
    if ($forbiddenNpcActions -contains "shanks.leave-maple-island") {
        Add-Check $Checks ("constraint:{0}:shanks" -f $ExpectedTemplateId) "PASS" "$ExpectedTemplateId forbids Shanks leave-island action."
    } else {
        Add-Check $Checks ("constraint:{0}:shanks" -f $ExpectedTemplateId) "FAIL" "$ExpectedTemplateId must forbid Shanks leave-island action."
    }

    $allowedRegions = @(Get-Array $constraints.allowedRegionIds)
    if ($allowedRegions -contains "maple-island") {
        Add-Check $Checks ("constraint:{0}:maple-region" -f $ExpectedTemplateId) "PASS" "$ExpectedTemplateId is constrained to Maple Island."
    } else {
        Add-Check $Checks ("constraint:{0}:maple-region" -f $ExpectedTemplateId) "FAIL" "$ExpectedTemplateId must include Maple Island allowed region."
    }

    foreach ($trait in $profile.traits.PSObject.Properties) {
        $value = [double] $trait.Value
        if ($value -ge 0 -and $value -le 1) {
            Add-Check $Checks ("trait:{0}:{1}" -f $ExpectedTemplateId, $trait.Name) "PASS" "$ExpectedTemplateId trait $($trait.Name) is within 0..1."
        } else {
            Add-Check $Checks ("trait:{0}:{1}" -f $ExpectedTemplateId, $trait.Name) "FAIL" "$ExpectedTemplateId trait $($trait.Name) is $value, expected 0..1."
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

Test-Template $checks (Join-Path $TemplateDir "maple-island-mvp-tester.profile.json") "maple-island-mvp-tester"
Test-Template $checks (Join-Path $TemplateDir "islander.profile.json") "islander"

$failCount = @($checks | Where-Object { $_.status -eq "FAIL" }).Count
$warnCount = @($checks | Where-Object { $_.status -eq "WARN" }).Count
$status = if ($failCount -gt 0) {
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
    templateDir = $TemplateDir
    status = $status
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
    Write-Host "Agent profile template verification: $status"
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
