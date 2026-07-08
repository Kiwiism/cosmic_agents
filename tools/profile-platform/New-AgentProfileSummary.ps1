param(
    [string] $ProfilePath = "docs/agents/profile-platform/templates/maple-island-mvp-tester.profile.json",
    [string] $OutputPath,
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Get-NowMs {
    return [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
}

function Get-ObjectOrEmpty {
    param([object] $Value)

    if ($null -eq $Value) {
        return [ordered]@{}
    }
    return $Value
}

function Get-ObjectPropertyCount {
    param([object] $Value)

    $objectValue = Get-ObjectOrEmpty $Value
    return @($objectValue.PSObject.Properties).Count
}

function ConvertTo-MarkdownReport {
    param([object] $Summary)

    $lines = [System.Collections.Generic.List[string]]::new()
    [void] $lines.Add("# Agent Profile Summary")
    [void] $lines.Add("")
    [void] $lines.Add(('Generated at ms: `{0}`' -f $Summary.generatedAtMs))
    [void] $lines.Add("")
    [void] $lines.Add("| Field | Value |")
    [void] $lines.Add("| --- | --- |")
    [void] $lines.Add(('| Summary id | `{0}` |' -f $Summary.summaryId))
    [void] $lines.Add(('| Agent id | `{0}` |' -f $Summary.agentId))
    [void] $lines.Add(('| Profile version | `{0}` |' -f $Summary.profileVersion))
    [void] $lines.Add(('| Archetype | `{0}` |' -f $Summary.identitySummary.archetype))
    [void] $lines.Add(("| Main goal | {0} |" -f $Summary.identitySummary.mainGoal))
    [void] $lines.Add(('| Class preference | `{0}` |' -f $Summary.identitySummary.classPreference))
    [void] $lines.Add(('| Economy role | `{0}` |' -f $Summary.identitySummary.economyRole))
    [void] $lines.Add("")

    [void] $lines.Add("## LLM Notes")
    [void] $lines.Add("")
    foreach ($note in @($Summary.llmNotes)) {
        [void] $lines.Add("- $note")
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Hard Constraints")
    [void] $lines.Add("")
    [void] $lines.Add('```json')
    [void] $lines.Add(($Summary.hardConstraints | ConvertTo-Json -Depth 8))
    [void] $lines.Add('```')
    [void] $lines.Add("")

    [void] $lines.Add("## Plan Preferences")
    [void] $lines.Add("")
    [void] $lines.Add('```json')
    [void] $lines.Add(($Summary.planPreferences | ConvertTo-Json -Depth 8))
    [void] $lines.Add('```')
    [void] $lines.Add("")

    [void] $lines.Add("## Notes")
    [void] $lines.Add("")
    [void] $lines.Add("- This is an offline LLM-safe profile summary prep artifact.")
    [void] $lines.Add("- It does not read live Agent state or mutate server data.")

    return ($lines -join "`n")
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

if (!(Test-Path -LiteralPath $ProfilePath -PathType Leaf)) {
    throw "Profile file not found: $ProfilePath"
}

$profile = Get-Content -LiteralPath $ProfilePath -Raw | ConvertFrom-Json
$templateVersion = if ($profile.metadata -and $null -ne $profile.metadata.templateVersion) {
    [int] $profile.metadata.templateVersion
} else {
    0
}

$llmNotes = [System.Collections.Generic.List[string]]::new()
[void] $llmNotes.Add("Archetype is $($profile.identity.archetype).")
[void] $llmNotes.Add("Main goal: $($profile.identity.mainGoal)")
if (@($profile.planProfile.hardConstraints.forbiddenNpcActions) -contains "shanks.leave-maple-island") {
    [void] $llmNotes.Add("Never use Shanks leave-island travel.")
}
if (@($profile.planProfile.hardConstraints.allowedRegionIds) -contains "maple-island") {
    [void] $llmNotes.Add("Keep decisions inside Maple Island unless an operator changes hard policy.")
}
if ($profile.policy.adaptationMode) {
    [void] $llmNotes.Add("Adaptation mode is $($profile.policy.adaptationMode).")
}

$summary = [ordered]@{
    schemaVersion = 1
    summaryId = "profile-summary:$($profile.metadata.templateId):v$templateVersion"
    agentId = $profile.agentId
    profileVersion = $templateVersion
    generatedAtMs = Get-NowMs
    sourceProfilePath = $ProfilePath
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    llmNoteCount = $llmNotes.Count
    returnedLlmNoteCount = if ($SummaryOnly) { 0 } else { $llmNotes.Count }
    hardConstraintFieldCount = Get-ObjectPropertyCount $profile.planProfile.hardConstraints
    returnedHardConstraintFieldCount = if ($SummaryOnly) { 0 } else { Get-ObjectPropertyCount $profile.planProfile.hardConstraints }
    planPreferenceFieldCount = 6
    returnedPlanPreferenceFieldCount = if ($SummaryOnly) { 0 } else { 6 }
    detailBlocksOmitted = [bool] $SummaryOnly
    identitySummary = [ordered]@{
        displayName = $profile.identity.displayName
        archetype = $profile.identity.archetype
        mainGoal = $profile.identity.mainGoal
        classPreference = $profile.identity.classPreference
        economyRole = $profile.identity.economyRole
    }
    hardConstraints = if ($SummaryOnly) { $null } else { Get-ObjectOrEmpty $profile.planProfile.hardConstraints }
    planPreferences = if ($SummaryOnly) { $null } else { [ordered]@{
        selectionMode = $profile.planProfile.selectionMode
        fixedPlanId = $profile.planProfile.fixedPlanId
        planSetIds = @($profile.planProfile.planSetIds)
        categoryWeights = Get-ObjectOrEmpty $profile.planProfile.categoryWeights
        softPreferences = Get-ObjectOrEmpty $profile.planProfile.softPreferences
        preferredPlanIds = @($profile.preferences.preferredPlanIds)
    } }
    riskPreferences = if ($SummaryOnly) { $null } else { [ordered]@{
        maxDeathRisk = $profile.policy.maxDeathRisk
        riskToleranceTrait = $profile.traits.riskTolerance
        recoveryStyle = $profile.preferences.recoveryStyle
        allowForceTestFallbacks = $profile.policy.allowForceTestFallbacks
    } }
    socialPreferences = if ($SummaryOnly) { $null } else { [ordered]@{
        socialness = $profile.traits.socialness
        allowPlayerTrade = $profile.policy.allowPlayerTrade
        allowedSidetrackTypes = @($profile.preferences.allowedSidetrackTypes)
    } }
    economyPreferences = if ($SummaryOnly) { $null } else { [ordered]@{
        economyRole = $profile.identity.economyRole
        marketInterest = $profile.traits.marketInterest
        minReserveMesos = $profile.policy.minReserveMesos
        maxSinglePurchaseMesos = $profile.policy.maxSinglePurchaseMesos
    } }
    behaviorTraits = if ($SummaryOnly) { $null } else { Get-ObjectOrEmpty $profile.traits }
    buildSummary = if ($SummaryOnly) { $null } else { Get-ObjectOrEmpty $profile.buildIntent }
    llmNotes = if ($SummaryOnly) { $null } else { @($llmNotes) }
}

if ($OutputPath) {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and !(Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }

    if ($Json) {
        $summary | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    } else {
        ConvertTo-MarkdownReport ([pscustomobject] $summary) | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    }

    Write-Host "Agent profile summary written:"
    Write-Host "  $OutputPath"
} elseif ($Json) {
    $summary | ConvertTo-Json -Depth 12
} else {
    ConvertTo-MarkdownReport ([pscustomobject] $summary)
}
