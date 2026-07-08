param(
    [string] $RunPath,
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Invoke-JsonScript {
    param(
        [string] $Path,
        [string[]] $Arguments = @()
    )

    $output = & powershell -ExecutionPolicy Bypass -File $Path @Arguments -Json 2>&1
    $exitCode = $LASTEXITCODE

    try {
        $report = ($output | ConvertFrom-Json)
    } catch {
        return [ordered]@{
            status = "FAIL"
            failCount = 1
            warnCount = 0
            message = ($output -join "`n")
        }
    }

    if ($exitCode -ne 0 -and $report.status -ne "FAIL") {
        $report | Add-Member -NotePropertyName status -NotePropertyValue "FAIL" -Force
        $report | Add-Member -NotePropertyName failCount -NotePropertyValue 1 -Force
    }

    return $report
}

function New-NextStep {
    param(
        [string] $Id,
        [string] $Status,
        [string] $Title,
        [string] $Evidence,
        [string] $Command
    )

    return [ordered]@{
        id = $Id
        status = $Status
        title = $Title
        evidence = $Evidence
        command = $Command
    }
}

function ConvertTo-ChecklistPattern {
    param([string] $Text)

    return [regex]::Escape($Text)
}

function ConvertTo-CommandLiteral {
    param([string] $Value)

    return $Value.Replace('`', '``').Replace('"', '`"')
}

function New-ChecklistCommand {
    param(
        [object] $Item,
        [string] $RunPath
    )

    $pattern = ConvertTo-CommandLiteral (ConvertTo-ChecklistPattern $Item.text)
    return [ordered]@{
        id = "checklist-line-$($Item.lineNumber)"
        lineNumber = $Item.lineNumber
        text = $Item.text
        command = "powershell -ExecutionPolicy Bypass -File .\tools\soak\Set-BaselineSoakChecklistItem.ps1 -RunPath `"$RunPath`" -Pattern `"$pattern`""
    }
}

function ConvertTo-Markdown {
    param([object] $Report)

    $lines = [System.Collections.Generic.List[string]]::new()
    [void] $lines.Add("# Baseline Soak Next Steps")
    [void] $lines.Add("")
    [void] $lines.Add("Run path: $($Report.runPath)")
    [void] $lines.Add("Status: $($Report.status)")
    [void] $lines.Add("")
    [void] $lines.Add("| Field | Value |")
    [void] $lines.Add("| --- | --- |")
    [void] $lines.Add("| Serverhealth samples | $($Report.evidenceSummary.serverHealthSampleCount)/$($Report.evidenceSummary.expectedServerHealthSampleCount) |")
    [void] $lines.Add("| Startup/shutdown lines | $($Report.evidenceSummary.startupLineCount)/$($Report.evidenceSummary.shutdownLineCount) |")
    [void] $lines.Add("| Checklist checked/total | $($Report.evidenceSummary.checklistCheckedCount)/$($Report.evidenceSummary.checklistItemCount) |")
    [void] $lines.Add("| Checklist unchecked | $($Report.evidenceSummary.checklistUncheckedCount) |")
    [void] $lines.Add("| Failures | $($Report.failCount) |")
    [void] $lines.Add("| Warnings | $($Report.warnCount) |")
    [void] $lines.Add("| Next steps | $($Report.nextStepCount) |")
    [void] $lines.Add("| Required next steps | $($Report.requiredNextStepCount) |")
    [void] $lines.Add("")

    if ($Report.summaryOnly) {
        [void] $lines.Add("Detailed next-step and unchecked-checklist rows are omitted because `-SummaryOnly` was used.")
        [void] $lines.Add("")
    } elseif (@($Report.nextSteps).Count -gt 0) {
        [void] $lines.Add("## Next Steps")
        [void] $lines.Add("")
        foreach ($step in @($Report.nextSteps)) {
            [void] $lines.Add("- [$($step.status)] $($step.title)")
            [void] $lines.Add("  Evidence: $($step.evidence)")
            if (![string]::IsNullOrWhiteSpace([string] $step.command)) {
                [void] $lines.Add("  Command: ``" + $step.command + "``")
            }
        }
        [void] $lines.Add("")
    }

    if (!$Report.summaryOnly -and @($Report.uncheckedChecklistItems).Count -gt 0) {
        [void] $lines.Add("## Unchecked Checklist Items")
        [void] $lines.Add("")
        foreach ($item in @($Report.uncheckedChecklistItems)) {
            [void] $lines.Add("- line $($item.lineNumber): $($item.text)")
        }
        [void] $lines.Add("")
    }

    if (!$Report.summaryOnly -and @($Report.uncheckedChecklistCommands).Count -gt 0) {
        [void] $lines.Add("## Checklist Commands")
        [void] $lines.Add("")
        foreach ($item in @($Report.uncheckedChecklistCommands)) {
            [void] $lines.Add("- line $($item.lineNumber): ``$($item.command)``")
        }
        [void] $lines.Add("")
    }

    return ($lines -join "`n")
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$statusPath = "tools/soak/Get-BaselineSoakStatus.ps1"
$verifierPath = "tools/soak/Test-BaselineSoakEvidencePackage.ps1"
$checklistPath = "tools/soak/Set-BaselineSoakChecklistItem.ps1"

if ([string]::IsNullOrWhiteSpace($RunPath)) {
    $statusReport = Invoke-JsonScript $statusPath
    if ([string]::IsNullOrWhiteSpace([string] $statusReport.latestRunPath)) {
        throw "No baseline soak run found. Create one with tools/soak/New-BaselineSoakEvidencePackage.ps1."
    }
    $RunPath = $statusReport.latestRunPath
}

$resolvedRunPath = (Resolve-Path -LiteralPath $RunPath -ErrorAction Stop).Path
$verification = Invoke-JsonScript $verifierPath @("-RunPath", $resolvedRunPath)
$checklist = Invoke-JsonScript $checklistPath @("-RunPath", $resolvedRunPath, "-List")

$uncheckedItems = @($checklist.items | Where-Object { -not $_.checked })
$uncheckedChecklistCommands = @($uncheckedItems | ForEach-Object { New-ChecklistCommand $_ $resolvedRunPath })
$warningChecks = @($verification.checks | Where-Object { $_.status -eq "WARN" })
$failureChecks = @($verification.checks | Where-Object { $_.status -eq "FAIL" })
$summary = $verification.evidenceSummary

$nextSteps = [System.Collections.Generic.List[object]]::new()

if (@($failureChecks).Count -gt 0) {
    [void] $nextSteps.Add((New-NextStep `
        "fix-failures" `
        "required" `
        "Fix verifier failure(s)." `
        "$(@($failureChecks | ForEach-Object { $_.id }) -join ', ')" `
        "powershell -ExecutionPolicy Bypass -File .\tools\soak\Test-BaselineSoakEvidencePackage.ps1 -RunPath `"$resolvedRunPath`""))
}

if ([int] $summary.serverHealthSampleCount -lt [int] $summary.expectedServerHealthSampleCount) {
    [void] $nextSteps.Add((New-NextStep `
        "add-serverhealth-sample" `
        "required" `
        "Append real !serverhealth sample output." `
        "Samples: $($summary.serverHealthSampleCount)/$($summary.expectedServerHealthSampleCount)." `
        "powershell -ExecutionPolicy Bypass -File .\tools\soak\Add-BaselineSoakSample.ps1 -RunPath `"$resolvedRunPath`" -Target serverhealth -FromClipboard"))
}

if (@($uncheckedItems).Count -gt 0) {
    [void] $nextSteps.Add((New-NextStep `
        "review-checklist" `
        "required" `
        "Review and check remaining baseline checklist items." `
        "Unchecked items: $(@($uncheckedItems).Count)." `
        "powershell -ExecutionPolicy Bypass -File .\tools\soak\Set-BaselineSoakChecklistItem.ps1 -RunPath `"$resolvedRunPath`" -List"))
}

if ([int] $summary.checklistUncheckedCount -eq 0 -and [int] $summary.serverHealthSampleCount -ge [int] $summary.expectedServerHealthSampleCount -and @($failureChecks).Count -eq 0) {
    [void] $nextSteps.Add((New-NextStep `
        "write-audit-entry" `
        "finalize" `
        "Generate the audit entry after verification passes." `
        "Evidence appears complete; rerun verifier before copying the audit entry." `
        "powershell -ExecutionPolicy Bypass -File .\tools\soak\New-BaselineSoakAuditEntry.ps1 -RunPath `"$resolvedRunPath`" -OutputPath `"$resolvedRunPath\audit-entry.md`""))
}

$requiredSteps = @($nextSteps | Where-Object { $_.status -eq "required" })
$nextRequiredCommand = if (@($requiredSteps).Count -gt 0) {
    [string] $requiredSteps[0].command
} else {
    $null
}

$report = [ordered]@{
    status = $verification.status
    repoRoot = $repoRoot
    runId = (Split-Path -Leaf $resolvedRunPath)
    latestRunId = (Split-Path -Leaf $resolvedRunPath)
    runPath = $resolvedRunPath
    latestRunPath = $resolvedRunPath
    failCount = $verification.failCount
    warnCount = $verification.warnCount
    warningIds = @($warningChecks | ForEach-Object { $_.id })
    failureIds = @($failureChecks | ForEach-Object { $_.id })
    nextStepIds = @($nextSteps | ForEach-Object { $_.id })
    requiredNextStepIds = @($requiredSteps | ForEach-Object { $_.id })
    nextStepCount = @($nextSteps).Count
    requiredNextStepCount = @($requiredSteps).Count
    nextRequiredCommand = $nextRequiredCommand
    serverHealthSampleCount = $summary.serverHealthSampleCount
    expectedServerHealthSampleCount = $summary.expectedServerHealthSampleCount
    checklistCheckedCount = $summary.checklistCheckedCount
    checklistUncheckedCount = $summary.checklistUncheckedCount
    checklistItemCount = $summary.checklistItemCount
    uncheckedChecklistItemCount = @($uncheckedItems).Count
    uncheckedChecklistCommandCount = @($uncheckedChecklistCommands).Count
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    returnedNextStepCount = if ($SummaryOnly) { 0 } else { @($nextSteps).Count }
    returnedUncheckedChecklistItemCount = if ($SummaryOnly) { 0 } else { @($uncheckedItems).Count }
    returnedUncheckedChecklistCommandCount = if ($SummaryOnly) { 0 } else { @($uncheckedChecklistCommands).Count }
    evidenceSummary = $summary
    uncheckedChecklistItems = if ($SummaryOnly) { $null } else { @($uncheckedItems) }
    uncheckedChecklistCommands = if ($SummaryOnly) { $null } else { @($uncheckedChecklistCommands) }
    nextSteps = if ($SummaryOnly) { $null } else { @($nextSteps) }
}

if ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    ConvertTo-Markdown ([pscustomobject] $report)
}
