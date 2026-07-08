param(
    [string] $CatalogDir = "tmp/agent-llm-catalog",
    [string] $OutputPath,
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Get-ArrayCount {
    param([object] $Value)

    if ($null -eq $Value) {
        return 0
    }
    if ($Value -is [System.Array]) {
        return $Value.Count
    }
    return 1
}

function Read-JsonFile {
    param([string] $Path)

    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $null
    }
    return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}

function ConvertTo-MarkdownReport {
    param([object] $Report)

    $lines = [System.Collections.Generic.List[string]]::new()
    [void] $lines.Add("# Maple Island MVP Validation Report")
    [void] $lines.Add("")
    [void] $lines.Add("Generated: $($Report.generatedAt)")
    [void] $lines.Add("")
    [void] $lines.Add("| Field | Value |")
    [void] $lines.Add("| --- | --- |")
    [void] $lines.Add("| Status | $($Report.status) |")
    [void] $lines.Add("| Failures | $($Report.failCount) |")
    [void] $lines.Add("| Warnings | $($Report.warnCount) |")
    [void] $lines.Add(("| Catalog dir | `{0}` |" -f $Report.catalogDir))
    [void] $lines.Add(("| Plan id | `{0}` |" -f $Report.summary.planId))
    [void] $lines.Add(("| Start map | `{0}` |" -f $Report.summary.startMapId))
    [void] $lines.Add(("| Final map | `{0}` |" -f $Report.summary.finalMapId))
    [void] $lines.Add("| MVP quests | $($Report.summary.questCount) |")
    [void] $lines.Add("| MVP maps | $($Report.summary.mapCount) |")
    [void] $lines.Add("| Special rules | $($Report.summary.specialRuleCount) |")
    [void] $lines.Add("| Forbidden actions | $($Report.summary.forbiddenActionCount) |")
    [void] $lines.Add("")

    [void] $lines.Add("## Required Checks")
    [void] $lines.Add("")
    [void] $lines.Add("| Status | ID | Message |")
    [void] $lines.Add("| --- | --- | --- |")
    foreach ($check in @($Report.mvpChecks)) {
        $message = ([string] $check.message).Replace("|", "\|")
        [void] $lines.Add(("| {0} | `{1}` | {2} |" -f $check.status, $check.id, $message))
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Quest Rules")
    [void] $lines.Add("")
    [void] $lines.Add("| Quest | NPC | Status | Completion | Notes |")
    [void] $lines.Add("| ---: | --- | --- | --- | --- |")
    foreach ($quest in @($Report.questRules)) {
        $notes = (@($quest.notes) -join "<br>").Replace("|", "\|")
        [void] $lines.Add(("| {0} | {1} | {2} | {3} | {4} |" -f $quest.questId, $quest.npc, $quest.mvpStatus, $quest.completionPolicy, $notes))
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Special Rules")
    [void] $lines.Add("")
    [void] $lines.Add("| Rule | Applies To | Notes |")
    [void] $lines.Add("| --- | --- | --- |")
    foreach ($rule in @($Report.specialRules)) {
        $appliesTo = (@($rule.appliesTo) -join ", ").Replace("|", "\|")
        $notes = (@($rule.notes) -join "<br>").Replace("|", "\|")
        [void] $lines.Add(("| `{0}` | {1} | {2} |" -f $rule.ruleId, $appliesTo, $notes))
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Forbidden Actions")
    [void] $lines.Add("")
    [void] $lines.Add("| Type | Target | Reason |")
    [void] $lines.Add("| --- | --- | --- |")
    foreach ($action in @($Report.forbiddenActions)) {
        $target = if ($action.npcId) {
            '{0} `{1}`' -f $action.npcName, $action.npcId
        } elseif ($action.questIds) {
            "quests $(@($action.questIds) -join ', ')"
        } else {
            ""
        }
        $reason = ([string] $action.reason).Replace("|", "\|")
        [void] $lines.Add(("| {0} | {1} | {2} |" -f $action.type, $target, $reason))
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Notes")
    [void] $lines.Add("")
    [void] $lines.Add("- This report validates generated catalog prep only.")
    [void] $lines.Add("- It does not assign a plan to an Agent or execute any live gameplay behavior.")
    [void] $lines.Add("- Runtime execution remains blocked until Agent reconstruction boundaries are stable.")

    return ($lines -join "`n")
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$catalogPath = Join-Path $CatalogDir "generated_maple_island_mvp_catalog.json"
$indexPath = Join-Path $CatalogDir "generated_maple_island_mvp_fast_indexes.json"
$manifestPath = Join-Path $CatalogDir "generated_catalog_manifest.json"

$catalog = Read-JsonFile $catalogPath
$indexes = Read-JsonFile $indexPath
$manifest = Read-JsonFile $manifestPath

$verificationJson = powershell -ExecutionPolicy Bypass -File "tools/agent-llm-catalog/Test-AgentLlmCatalog.ps1" -CatalogDir $CatalogDir -Json 2>&1
$verification = $verificationJson | ConvertFrom-Json
$mvpChecks = @($verification.checks | Where-Object {
    $_.id -like "mvp:*" -or $_.id -eq "manifest:maple-mvp-quests"
})

$failures = @($mvpChecks | Where-Object { $_.status -eq "FAIL" })
$warnings = @($mvpChecks | Where-Object { $_.status -eq "WARN" })
$failCount = $failures.Count
$warnCount = $warnings.Count
$passCount = @($mvpChecks | Where-Object { $_.status -eq "PASS" }).Count
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
    catalogDir = $CatalogDir
    status = $status
    checkCount = $mvpChecks.Count
    passCount = $passCount
    failCount = $failCount
    warnCount = $warnCount
    failureIds = @($failures | ForEach-Object { $_.id })
    warningIds = @($warnings | ForEach-Object { $_.id })
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    returnedCheckCount = if ($SummaryOnly) { 0 } else { $mvpChecks.Count }
    returnedQuestRuleCount = if ($SummaryOnly) { 0 } else { if ($catalog) { Get-ArrayCount $catalog.quests } else { 0 } }
    returnedSpecialRuleCount = if ($SummaryOnly) { 0 } else { if ($catalog) { Get-ArrayCount $catalog.specialRules } else { 0 } }
    returnedForbiddenActionCount = if ($SummaryOnly) { 0 } else { if ($catalog) { Get-ArrayCount $catalog.forbiddenActions } else { 0 } }
    summary = [ordered]@{
        planId = if ($catalog) { $catalog.planId } else { $null }
        startMapId = if ($catalog) { $catalog.startMapId } else { $null }
        finalMapId = if ($catalog) { $catalog.finalMapId } else { $null }
        questCount = if ($catalog) { Get-ArrayCount $catalog.quests } else { 0 }
        mapCount = if ($catalog) {
            @(
                @($catalog.routeMapIds)
                @($catalog.sideMapIds)
                @($catalog.reachableMapIds)
            ) | ForEach-Object { $_ } | Where-Object { $null -ne $_ } | Sort-Object -Unique | Measure-Object | Select-Object -ExpandProperty Count
        } else { 0 }
        specialRuleCount = if ($catalog) { Get-ArrayCount $catalog.specialRules } else { 0 }
        forbiddenActionCount = if ($catalog) { Get-ArrayCount $catalog.forbiddenActions } else { 0 }
        manifestMapleIslandMvpQuests = if ($manifest -and $manifest.counts) { $manifest.counts.mapleIslandMvpQuests } else { $null }
        hasQuestIndex = [bool] ($indexes -and $indexes.questId_to_mvpRule)
    }
    mvpChecks = if ($SummaryOnly) { $null } else { $mvpChecks }
    questRules = if ($SummaryOnly) { $null } elseif ($catalog) { @($catalog.quests) } else { @() }
    specialRules = if ($SummaryOnly) { $null } elseif ($catalog) { @($catalog.specialRules) } else { @() }
    forbiddenActions = if ($SummaryOnly) { $null } elseif ($catalog) { @($catalog.forbiddenActions) } else { @() }
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

    Write-Host "Maple Island MVP validation report written:"
    Write-Host "  $OutputPath"
} elseif ($Json) {
    $report | ConvertTo-Json -Depth 12
} else {
    ConvertTo-MarkdownReport ([pscustomobject] $report)
}

if ($failCount -gt 0) {
    exit 1
}
