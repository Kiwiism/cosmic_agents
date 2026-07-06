param(
    [Parameter(Mandatory = $true)]
    [string] $RunPath,

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

function Get-NonCommentLineCount {
    param([string] $Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        return 0
    }

    $lines = Get-Content -LiteralPath $Path
    return @($lines | Where-Object {
        $trimmed = $_.Trim()
        $trimmed.Length -gt 0 -and -not $trimmed.StartsWith("#")
    }).Count
}

function Test-JsonProperty {
    param(
        [object] $Object,
        [string] $Name
    )

    return $null -ne ($Object.PSObject.Properties | Where-Object { $_.Name -eq $Name })
}

$resolvedRunPath = Resolve-Path -LiteralPath $RunPath -ErrorAction Stop
$checks = [System.Collections.Generic.List[object]]::new()

$requiredFiles = @(
    "README.md",
    "evidence-checklist.md",
    "serverhealth-5min-samples.log",
    "scale-health.log",
    "slow-operations.log",
    "startup.log",
    "shutdown.log",
    "summary.json"
)

foreach ($file in $requiredFiles) {
    $path = Join-Path $resolvedRunPath $file
    if (Test-Path -LiteralPath $path) {
        Add-Check $checks "file:$file" "PASS" "Found $file."
    } else {
        Add-Check $checks "file:$file" "FAIL" "Missing $file."
    }
}

$optionalProvenanceFiles = @(
    "prep-verifier-before-run.log",
    "baseline-status-before-run.log"
)

foreach ($file in $optionalProvenanceFiles) {
    $path = Join-Path $resolvedRunPath $file
    if (Test-Path -LiteralPath $path) {
        Add-Check $checks "provenance:$file" "PASS" "Found $file."
    } else {
        Add-Check $checks "provenance:$file" "WARN" "Missing optional provenance file $file."
    }
}

$summaryPath = Join-Path $resolvedRunPath "summary.json"
$summary = $null

if (Test-Path -LiteralPath $summaryPath) {
    try {
        $summary = Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
        Add-Check $checks "summary:valid-json" "PASS" "summary.json is valid JSON."
    } catch {
        Add-Check $checks "summary:valid-json" "FAIL" "summary.json is not valid JSON: $($_.Exception.Message)"
    }
}

$requiredSummaryFields = @(
    "runId",
    "stage",
    "durationMinutes",
    "sampleIntervalMinutes",
    "onlinePlayerPeak",
    "onlineAgentPeak",
    "heapStartMb",
    "heapEndMb",
    "loadedMapStart",
    "loadedMapEnd",
    "dbWaitingMax",
    "threadRejectedDelta",
    "timerQueueMax",
    "slowSaveCount",
    "slowBroadcastCount",
    "stuckLoginCount",
    "shutdownClean",
    "restartClean",
    "notes"
)

if ($summary) {
    foreach ($field in $requiredSummaryFields) {
        if (Test-JsonProperty $summary $field) {
            Add-Check $checks "summary-field:$field" "PASS" "summary.json contains $field."
        } else {
            Add-Check $checks "summary-field:$field" "FAIL" "summary.json is missing $field."
        }
    }

    if ($summary.stage -eq "server-baseline") {
        Add-Check $checks "summary:stage" "PASS" "stage is server-baseline."
    } else {
        Add-Check $checks "summary:stage" "FAIL" "stage should be server-baseline."
    }

    if ($summary.durationMinutes -gt 0) {
        Add-Check $checks "summary:duration" "PASS" "durationMinutes is greater than zero."
    } else {
        Add-Check $checks "summary:duration" "WARN" "durationMinutes is not greater than zero."
    }

    if ($summary.sampleIntervalMinutes -gt 0) {
        Add-Check $checks "summary:sample-interval" "PASS" "sampleIntervalMinutes is greater than zero."
    } else {
        Add-Check $checks "summary:sample-interval" "WARN" "sampleIntervalMinutes is not greater than zero."
    }

    if ($summary.onlineAgentPeak -eq 0) {
        Add-Check $checks "baseline:no-agents" "PASS" "onlineAgentPeak is zero for server baseline."
    } else {
        Add-Check $checks "baseline:no-agents" "FAIL" "onlineAgentPeak should be zero for server baseline."
    }

    if ($summary.threadRejectedDelta -eq 0) {
        Add-Check $checks "runtime:thread-rejections" "PASS" "threadRejectedDelta is zero."
    } else {
        Add-Check $checks "runtime:thread-rejections" "FAIL" "threadRejectedDelta is nonzero."
    }

    if ($summary.dbWaitingMax -eq 0) {
        Add-Check $checks "runtime:db-waiting" "PASS" "dbWaitingMax is zero."
    } else {
        Add-Check $checks "runtime:db-waiting" "WARN" "dbWaitingMax is nonzero; review DB pressure."
    }

    if ($summary.stuckLoginCount -eq 0) {
        Add-Check $checks "runtime:stuck-login" "PASS" "stuckLoginCount is zero."
    } else {
        Add-Check $checks "runtime:stuck-login" "FAIL" "stuckLoginCount is nonzero."
    }

    if ($summary.shutdownClean -eq $true) {
        Add-Check $checks "runtime:shutdown-clean" "PASS" "shutdownClean is true."
    } else {
        Add-Check $checks "runtime:shutdown-clean" "WARN" "shutdownClean is not true yet."
    }

    if ($summary.restartClean -eq $true) {
        Add-Check $checks "runtime:restart-clean" "PASS" "restartClean is true."
    } else {
        Add-Check $checks "runtime:restart-clean" "WARN" "restartClean is not true yet."
    }
}

$serverHealthPath = Join-Path $resolvedRunPath "serverhealth-5min-samples.log"
$serverHealthSamples = Get-NonCommentLineCount $serverHealthPath
if ($serverHealthSamples -gt 0) {
    Add-Check $checks "evidence:serverhealth" "PASS" "serverhealth samples have non-comment content."
} else {
    Add-Check $checks "evidence:serverhealth" "WARN" "serverhealth samples are empty or template-only."
}

if ($summary -and $summary.durationMinutes -gt 0 -and $summary.sampleIntervalMinutes -gt 0) {
    $expectedSamples = [Math]::Max(1, [Math]::Ceiling([double]$summary.durationMinutes / [double]$summary.sampleIntervalMinutes))
    if ($serverHealthSamples -ge $expectedSamples) {
        Add-Check $checks "evidence:serverhealth-sample-count" "PASS" "serverhealth sample count $serverHealthSamples meets expected minimum $expectedSamples."
    } else {
        Add-Check $checks "evidence:serverhealth-sample-count" "WARN" "serverhealth sample count $serverHealthSamples is below expected minimum $expectedSamples."
    }
}

$startupPath = Join-Path $resolvedRunPath "startup.log"
$startupLines = Get-NonCommentLineCount $startupPath
if ($startupLines -gt 0) {
    Add-Check $checks "evidence:startup" "PASS" "startup.log has non-comment content."
} else {
    Add-Check $checks "evidence:startup" "WARN" "startup.log is empty or template-only."
}

$shutdownPath = Join-Path $resolvedRunPath "shutdown.log"
$shutdownLines = Get-NonCommentLineCount $shutdownPath
if ($shutdownLines -gt 0) {
    Add-Check $checks "evidence:shutdown" "PASS" "shutdown.log has non-comment content."
} else {
    Add-Check $checks "evidence:shutdown" "WARN" "shutdown.log is empty or template-only."
}

$checklistPath = Join-Path $resolvedRunPath "evidence-checklist.md"
if (Test-Path -LiteralPath $checklistPath) {
    $checklistLines = Get-Content -LiteralPath $checklistPath
    $uncheckedItems = @($checklistLines | Where-Object { $_ -match '^\s*-\s+\[\s\]' }).Count
    $checkedItems = @($checklistLines | Where-Object { $_ -match '^\s*-\s+\[[xX]\]' }).Count

    if ($checkedItems -gt 0 -and $uncheckedItems -eq 0) {
        Add-Check $checks "evidence:checklist" "PASS" "All checklist items are checked."
    } elseif ($checkedItems -gt 0) {
        Add-Check $checks "evidence:checklist" "WARN" "$uncheckedItems checklist items remain unchecked."
    } else {
        Add-Check $checks "evidence:checklist" "WARN" "Checklist has no checked items yet."
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
    status = $overall
    runPath = $resolvedRunPath.Path
    failCount = $failCount
    warnCount = $warnCount
    checks = @($checks)
}

if ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    Write-Host "Baseline evidence verification: $overall"
    Write-Host "Run path: $($resolvedRunPath.Path)"
    Write-Host "Failures: $failCount  Warnings: $warnCount"
    Write-Host ""

    foreach ($check in $checks) {
        Write-Host ("[{0}] {1} - {2}" -f $check.status, $check.id, $check.message)
    }
}

if ($failCount -gt 0) {
    exit 1
}
