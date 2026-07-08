param(
    [string] $ProfilePatchSchemaPath = "docs/agents/profile-platform/profile-patch.schema.json",
    [string] $ExperienceEventSchemaPath = "docs/agents/profile-platform/agent-experience-event.schema.json",
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

function Read-Json {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [string] $Path,
        [string] $Label
    )

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
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [object] $Schema,
        [string] $Label,
        [string[]] $Fields
    )

    $required = @($Schema.required)
    foreach ($field in $Fields) {
        if ($required -contains $field) {
            Add-Check $Checks "$Label.required:$field" "PASS" "$Label requires $field."
        } else {
            Add-Check $Checks "$Label.required:$field" "FAIL" "$Label should require $field."
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
$patchSchema = Read-Json $checks $ProfilePatchSchemaPath "profile-patch-schema"
$eventSchema = Read-Json $checks $ExperienceEventSchemaPath "agent-experience-event-schema"

if ($null -ne $patchSchema) {
    Test-Required $checks $patchSchema "profile-patch" @(
        "schemaVersion",
        "patchId",
        "agentId",
        "createdAtMs",
        "source",
        "reason",
        "operations",
        "safety"
    )

    $safety = $patchSchema.properties.safety
    if ($null -ne $safety) {
        $safetyRequired = @($safety.required)
        foreach ($field in @("hardPolicyMutation", "requiresOperatorApproval", "requiresLlmReview", "boundedNumericMutation")) {
            if ($safetyRequired -contains $field) {
                Add-Check $checks "profile-patch.safety.required:$field" "PASS" "Safety block requires $field."
            } else {
                Add-Check $checks "profile-patch.safety.required:$field" "FAIL" "Safety block should require $field."
            }
        }

        $hardPolicy = $safety.properties.hardPolicyMutation
        if ($null -ne $hardPolicy -and $hardPolicy.const -eq $false) {
            Add-Check $checks "profile-patch.hard-policy-const" "PASS" "hardPolicyMutation is constrained to false."
        } else {
            Add-Check $checks "profile-patch.hard-policy-const" "FAIL" "hardPolicyMutation must be constrained to false."
        }

        $blockedDefaults = @($safety.properties.blockedPaths.default)
        foreach ($blockedPath in @("/policy", "/planProfile/hardConstraints")) {
            if ($blockedDefaults -contains $blockedPath) {
                Add-Check $checks "profile-patch.blocked-path:$blockedPath" "PASS" "Default blocked paths include $blockedPath."
            } else {
                Add-Check $checks "profile-patch.blocked-path:$blockedPath" "FAIL" "Default blocked paths should include $blockedPath."
            }
        }
    } else {
        Add-Check $checks "profile-patch.safety" "FAIL" "Profile patch schema is missing safety block."
    }
}

if ($null -ne $eventSchema) {
    Test-Required $checks $eventSchema "agent-experience-event" @(
        "schemaVersion",
        "eventId",
        "agentId",
        "occurredAtMs",
        "eventType",
        "source",
        "subject",
        "outcome",
        "influences",
        "evidence"
    )

    $eventTypes = @($eventSchema.properties.eventType.enum)
    foreach ($eventType in @("plan.completed", "combat.death", "market.purchase", "relationship.trade_good", "llm.suggestion")) {
        if ($eventTypes -contains $eventType) {
            Add-Check $checks "experience-event.type:$eventType" "PASS" "Experience event schema includes $eventType."
        } else {
            Add-Check $checks "experience-event.type:$eventType" "FAIL" "Experience event schema should include $eventType."
        }
    }
}

$failures = @($checks | Where-Object { $_.status -eq "FAIL" })
$warnings = @($checks | Where-Object { $_.status -eq "WARN" })
$failCount = $failures.Count
$warnCount = $warnings.Count
$passCount = @($checks | Where-Object { $_.status -eq "PASS" }).Count
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
    Write-Host "Profile patch safety verification: $overall"
    Write-Host "Failures: $failCount  Warnings: $warnCount"
    Write-Host ""
    foreach ($check in $checks) {
        Write-Host ("[{0}] {1} - {2}" -f $check.status, $check.id, $check.message)
    }
}

if ($failCount -gt 0) {
    exit 1
}
