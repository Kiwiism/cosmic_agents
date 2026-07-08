param(
    [string] $Id,
    [string] $Category,
    [string] $Status,
    [string] $ExcludeStatus,
    [string] $ImplementationTrack,
    [string] $PackageId,
    [ValidateSet("handoff", "id", "category", "status")]
    [string] $SortBy = "handoff",
    [string] $OutputPath,
    [switch] $FailOnBlocked,
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$handoffPath = Join-Path $repoRoot "tools/pre-reconstruction/Get-PreReconstructionHandoff.ps1"
if (!(Test-Path -LiteralPath $handoffPath -PathType Leaf)) {
    throw "Missing handoff helper: $handoffPath"
}

$handoffOutput = & powershell -ExecutionPolicy Bypass -File $handoffPath -Json -SkipPrepVerifier 2>&1
$handoffExitCode = $LASTEXITCODE
$handoff = $handoffOutput | ConvertFrom-Json

$items = @($handoff.remainingWork)
if (![string]::IsNullOrWhiteSpace($Id)) {
    $items = @($items | Where-Object { $_.id -eq $Id })
}
if (![string]::IsNullOrWhiteSpace($Category)) {
    $items = @($items | Where-Object { $_.category -eq $Category })
}
if (![string]::IsNullOrWhiteSpace($Status)) {
    $items = @($items | Where-Object { $_.status -eq $Status })
}
if (![string]::IsNullOrWhiteSpace($ExcludeStatus)) {
    $items = @($items | Where-Object { $_.status -ne $ExcludeStatus })
}
if (![string]::IsNullOrWhiteSpace($ImplementationTrack)) {
    $items = @($items | Where-Object { $_.implementationTrack -eq $ImplementationTrack })
}
if (![string]::IsNullOrWhiteSpace($PackageId)) {
    $items = @($items | Where-Object { @($_.packageIds) -contains $PackageId })
}

switch ($SortBy) {
    "id" {
        $items = @($items | Sort-Object id)
    }
    "category" {
        $items = @($items | Sort-Object category, id)
    }
    "status" {
        $items = @($items | Sort-Object status, category, id)
    }
}

$statusCounts = [ordered]@{}
$categoryCounts = [ordered]@{}
$trackCounts = [ordered]@{}
$packageCounts = [ordered]@{}
foreach ($item in $items) {
    if (!$statusCounts.Contains($item.status)) {
        $statusCounts[$item.status] = 0
    }
    $statusCounts[$item.status] += 1

    if (!$categoryCounts.Contains($item.category)) {
        $categoryCounts[$item.category] = 0
    }
    $categoryCounts[$item.category] += 1

    if ($item.implementationTrack) {
        if (!$trackCounts.Contains($item.implementationTrack)) {
            $trackCounts[$item.implementationTrack] = 0
        }
        $trackCounts[$item.implementationTrack] += 1
    }

    foreach ($itemPackageId in @($item.packageIds)) {
        if (!$packageCounts.Contains($itemPackageId)) {
            $packageCounts[$itemPackageId] = 0
        }
        $packageCounts[$itemPackageId] += 1
    }
}

$readyAfterReconstructionItems = @(
    $items |
        Where-Object {
            $_.category -eq "ready to implement after reconstruction"
        }
)
$readyAfterReconstructionIds = @($readyAfterReconstructionItems | ForEach-Object { $_.id })
$readyAfterReconstructionTrackIds = @(
    $readyAfterReconstructionItems |
        ForEach-Object { $_.implementationTrack } |
        Where-Object { ![string]::IsNullOrWhiteSpace([string] $_) } |
        Sort-Object -Unique
)
$readyAfterReconstructionPackageIds = @(
    $readyAfterReconstructionItems |
        ForEach-Object { @($_.packageIds) } |
        Where-Object { ![string]::IsNullOrWhiteSpace([string] $_) } |
        Sort-Object -Unique
)

function Get-ItemIdsByCategory {
    param(
        [object[]] $SourceItems,
        [string] $CategoryName
    )

    return @(
        $SourceItems |
            Where-Object { $_.category -eq $CategoryName } |
            ForEach-Object { $_.id }
    )
}

$serverOnlyItemIds = Get-ItemIdsByCategory $items "server-only"
$waitingForSoakEvidenceItemIds = Get-ItemIdsByCategory $items "waiting for soak evidence"
$waitingForAgentRuntimeBoundaryItemIds = Get-ItemIdsByCategory $items "waiting for Agent runtime boundary"
$agentGameplayItemIds = Get-ItemIdsByCategory $items "Agent gameplay"
$agentScalingOptimisationItemIds = Get-ItemIdsByCategory $items "Agent scaling/optimisation"

$report = [ordered]@{
    status = if ($handoff.status -eq "BLOCKED_FOR_SAFE_PREP_COMMIT") { "BLOCKED_FOR_SAFE_PREP_COMMIT" } else { "OK" }
    repoRoot = $repoRoot
    handoffStatus = $handoff.status
    idFilter = if ([string]::IsNullOrWhiteSpace($Id)) { $null } else { $Id }
    categoryFilter = if ([string]::IsNullOrWhiteSpace($Category)) { $null } else { $Category }
    statusFilter = if ([string]::IsNullOrWhiteSpace($Status)) { $null } else { $Status }
    excludeStatusFilter = if ([string]::IsNullOrWhiteSpace($ExcludeStatus)) { $null } else { $ExcludeStatus }
    implementationTrackFilter = if ([string]::IsNullOrWhiteSpace($ImplementationTrack)) { $null } else { $ImplementationTrack }
    packageIdFilter = if ([string]::IsNullOrWhiteSpace($PackageId)) { $null } else { $PackageId }
    sortBy = $SortBy
    count = $items.Count
    remainingWorkCount = $items.Count
    readyCount = if ($statusCounts.Contains("ready")) { $statusCounts["ready"] } else { 0 }
    readyAfterReconstructionCount = $readyAfterReconstructionItems.Count
    readyAfterReconstructionIds = @($readyAfterReconstructionIds)
    readyAfterReconstructionTrackIds = @($readyAfterReconstructionTrackIds)
    readyAfterReconstructionPackageIds = @($readyAfterReconstructionPackageIds)
    serverOnlyItemIds = @($serverOnlyItemIds)
    waitingForSoakEvidenceItemIds = @($waitingForSoakEvidenceItemIds)
    waitingForAgentRuntimeBoundaryItemIds = @($waitingForAgentRuntimeBoundaryItemIds)
    agentGameplayItemIds = @($agentGameplayItemIds)
    agentScalingOptimisationItemIds = @($agentScalingOptimisationItemIds)
    waitingCount = if ($statusCounts.Contains("waiting")) { $statusCounts["waiting"] } else { 0 }
    blockedCount = if ($statusCounts.Contains("blocked")) { $statusCounts["blocked"] } else { 0 }
    clearCount = if ($statusCounts.Contains("clear")) { $statusCounts["clear"] } else { 0 }
    readyWithCautionCount = if ($statusCounts.Contains("ready-with-caution")) { $statusCounts["ready-with-caution"] } else { 0 }
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    returnedItemCount = if ($SummaryOnly) { 0 } else { $items.Count }
    returnedRemainingWorkCount = if ($SummaryOnly) { 0 } else { $items.Count }
    safePrepCommitStatus = $handoff.summary.safePrepCommitStatus
    safePrepCommitBlockers = $handoff.summary.commitBlockers
    safePrepReviewRequired = $handoff.summary.reviewRequired
    safePrepStageReady = $handoff.summary.safeStageReady
    safePrepRecommendedVerificationCommands = @($handoff.summary.recommendedVerificationCommands)
    safePrepRecommendedVerificationCommandCount = $handoff.summary.recommendedVerificationCommandCount
    baselineSoakNextStepIds = @($handoff.summary.baselineSoakNextStepIds)
    baselineSoakRequiredNextStepIds = @($handoff.summary.baselineSoakRequiredNextStepIds)
    baselineSoakNextStepCount = $handoff.summary.baselineSoakNextStepCount
    baselineSoakRequiredNextStepCount = $handoff.summary.baselineSoakRequiredNextStepCount
    baselineSoakNextRequiredCommand = $handoff.summary.baselineSoakNextRequiredCommand
    completionReadyExceptExternalEvidence = $handoff.summary.completionReadyExceptExternalEvidence
    completionProgressEstimatePercent = $handoff.summary.completionProgressEstimatePercent
    gitForbiddenStaged = $handoff.summary.gitForbiddenStaged
    gitForbiddenUnstaged = $handoff.summary.gitForbiddenUnstaged
    gitForbiddenStagedPaths = @($handoff.gitForbiddenStagedPaths)
    gitForbiddenUnstagedPaths = @($handoff.gitForbiddenUnstagedPaths)
    blockerUnstageCommand = $handoff.summary.blockerUnstageCommand
    statusCounts = $statusCounts
    categoryCounts = $categoryCounts
    trackCounts = $trackCounts
    packageCounts = $packageCounts
    items = if ($SummaryOnly) { $null } else { @($items) }
    handoffSummary = if ($SummaryOnly) { $null } else { $handoff.summary }
}

function ConvertTo-MarkdownRemainingWork {
    param([object] $Report)

    $lines = [System.Collections.Generic.List[string]]::new()
    [void] $lines.Add("# Pre-Reconstruction Remaining Work")
    [void] $lines.Add("")
    [void] $lines.Add("| Field | Value |")
    [void] $lines.Add("| --- | --- |")
    [void] $lines.Add("| Status | $($Report.status) |")
    [void] $lines.Add("| Handoff status | $($Report.handoffStatus) |")
    [void] $lines.Add("| Items | $($Report.count) |")
    [void] $lines.Add("| Ready after reconstruction | $($Report.readyAfterReconstructionCount) |")
    [void] $lines.Add("| Safe-prep commit status | $($Report.safePrepCommitStatus) |")
    [void] $lines.Add("| Safe-prep stage ready | $($Report.safePrepStageReady) |")
    [void] $lines.Add("| Safe-prep commit blockers | $($Report.safePrepCommitBlockers) |")
    [void] $lines.Add("| Safe-prep review-required paths | $($Report.safePrepReviewRequired) |")
    [void] $lines.Add("| Safe-prep verification commands | $($Report.safePrepRecommendedVerificationCommandCount) |")
    [void] $lines.Add("| Baseline soak next steps | $($Report.baselineSoakNextStepCount) |")
    [void] $lines.Add("| Baseline soak required next steps | $($Report.baselineSoakRequiredNextStepCount) |")
    [void] $lines.Add("| Completion ready except external evidence | $($Report.completionReadyExceptExternalEvidence) |")
    [void] $lines.Add("| Completion progress estimate | $($Report.completionProgressEstimatePercent)% |")
    [void] $lines.Add("| Git staged forbidden paths | $($Report.gitForbiddenStaged) |")
    [void] $lines.Add("| Git unstaged forbidden paths | $($Report.gitForbiddenUnstaged) |")
    if ($Report.idFilter) {
        [void] $lines.Add("| Id filter | `$($Report.idFilter)` |")
    }
    if ($Report.categoryFilter) {
        [void] $lines.Add("| Category filter | $($Report.categoryFilter) |")
    }
    if ($Report.statusFilter) {
        [void] $lines.Add("| Status filter | $($Report.statusFilter) |")
    }
    if ($Report.excludeStatusFilter) {
        [void] $lines.Add("| Exclude status filter | $($Report.excludeStatusFilter) |")
    }
    if ($Report.implementationTrackFilter) {
        [void] $lines.Add("| Implementation track filter | `$($Report.implementationTrackFilter)` |")
    }
    if ($Report.packageIdFilter) {
        [void] $lines.Add("| Package id filter | `$($Report.packageIdFilter)` |")
    }
    [void] $lines.Add("")

    if (@($Report.readyAfterReconstructionIds).Count -gt 0) {
        [void] $lines.Add("## Ready After Reconstruction")
        [void] $lines.Add("")
        [void] $lines.Add("- Item ids: $(@($Report.readyAfterReconstructionIds) -join ', ')")
        [void] $lines.Add("- Tracks: $(@($Report.readyAfterReconstructionTrackIds) -join ', ')")
        [void] $lines.Add("- Packages: $(@($Report.readyAfterReconstructionPackageIds) -join ', ')")
        [void] $lines.Add("")
    }

    [void] $lines.Add("## Goal Category Item Ids")
    [void] $lines.Add("")
    [void] $lines.Add("- Server-only: $(@($Report.serverOnlyItemIds) -join ', ')")
    [void] $lines.Add("- Waiting for soak evidence: $(@($Report.waitingForSoakEvidenceItemIds) -join ', ')")
    [void] $lines.Add("- Waiting for Agent runtime boundary: $(@($Report.waitingForAgentRuntimeBoundaryItemIds) -join ', ')")
    [void] $lines.Add("- Agent gameplay: $(@($Report.agentGameplayItemIds) -join ', ')")
    [void] $lines.Add("- Agent scaling/optimisation: $(@($Report.agentScalingOptimisationItemIds) -join ', ')")
    [void] $lines.Add("")

    if (![string]::IsNullOrWhiteSpace([string] $Report.blockerUnstageCommand)) {
        [void] $lines.Add("## Blocker Unstage Command")
        [void] $lines.Add("")
        [void] $lines.Add('```powershell')
        [void] $lines.Add($Report.blockerUnstageCommand)
        [void] $lines.Add('```')
        [void] $lines.Add("")
    }

    if (![string]::IsNullOrWhiteSpace([string] $Report.baselineSoakNextRequiredCommand)) {
        [void] $lines.Add("## Baseline Soak Next Required Command")
        [void] $lines.Add("")
        [void] $lines.Add('```powershell')
        [void] $lines.Add($Report.baselineSoakNextRequiredCommand)
        [void] $lines.Add('```')
        [void] $lines.Add("")
    }

    if ($Report.statusCounts.Keys.Count -gt 0) {
        [void] $lines.Add("## Status Counts")
        [void] $lines.Add("")
        foreach ($key in $Report.statusCounts.Keys) {
            [void] $lines.Add("- `$($key)`: $($Report.statusCounts[$key])")
        }
        [void] $lines.Add("")
    }

    if ($Report.categoryCounts.Keys.Count -gt 0) {
        [void] $lines.Add("## Category Counts")
        [void] $lines.Add("")
        foreach ($key in $Report.categoryCounts.Keys) {
            [void] $lines.Add("- $($key): $($Report.categoryCounts[$key])")
        }
        [void] $lines.Add("")
    }

    if ($Report.trackCounts.Keys.Count -gt 0) {
        [void] $lines.Add("## Implementation Track Counts")
        [void] $lines.Add("")
        foreach ($key in $Report.trackCounts.Keys) {
            [void] $lines.Add("- $($key): $($Report.trackCounts[$key])")
        }
        [void] $lines.Add("")
    }

    if ($Report.packageCounts.Keys.Count -gt 0) {
        [void] $lines.Add("## Package Counts")
        [void] $lines.Add("")
        foreach ($key in $Report.packageCounts.Keys) {
            [void] $lines.Add("- $($key): $($Report.packageCounts[$key])")
        }
        [void] $lines.Add("")
    }

    if (!$SummaryOnly) {
        [void] $lines.Add("## Items")
        [void] $lines.Add("")
        [void] $lines.Add("| Id | Category | Status | Track | Packages | Title | Evidence | Next Action |")
        [void] $lines.Add("| --- | --- | --- | --- | --- | --- | --- | --- |")
        foreach ($item in @($Report.items)) {
            [void] $lines.Add(("| `{0}` | {1} | {2} | `{3}` | {4} | {5} | {6} | {7} |" -f $item.id, $item.category, $item.status, $item.implementationTrack, (@($item.packageIds) -join ", "), $item.title, $item.evidence, $item.nextAction))
        }
    }

    return ($lines -join "`n")
}

if (![string]::IsNullOrWhiteSpace($OutputPath)) {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and !(Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }

    if ($Json) {
        $report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    } else {
        ConvertTo-MarkdownRemainingWork ([pscustomobject] $report) | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    }

    Write-Host "Pre-reconstruction remaining work written:"
    Write-Host "  $OutputPath"
} elseif ($Json) {
    $report | ConvertTo-Json -Depth 12
} else {
    Write-Host "Pre-reconstruction remaining work: $($report.status)"
    Write-Host "Handoff status: $($report.handoffStatus)"
    Write-Host "Items: $($report.count)"
    Write-Host "Safe-prep commit status: $($report.safePrepCommitStatus)"
    Write-Host "Safe-prep commit blockers: $($report.safePrepCommitBlockers)"
    Write-Host "Safe-prep review-required paths: $($report.safePrepReviewRequired)"
    Write-Host "Git staged forbidden paths: $($report.gitForbiddenStaged)"
    Write-Host "Git unstaged forbidden paths: $($report.gitForbiddenUnstaged)"

    if (![string]::IsNullOrWhiteSpace([string] $report.blockerUnstageCommand)) {
        Write-Host ""
        Write-Host "Blocker unstage command:"
        Write-Host $report.blockerUnstageCommand
    }

    if ($statusCounts.Count -gt 0) {
        Write-Host ""
        Write-Host "Status counts:"
        foreach ($key in $statusCounts.Keys) {
            Write-Host ("- {0}: {1}" -f $key, $statusCounts[$key])
        }
    }

    if ($categoryCounts.Count -gt 0) {
        Write-Host ""
        Write-Host "Category counts:"
        foreach ($key in $categoryCounts.Keys) {
            Write-Host ("- {0}: {1}" -f $key, $categoryCounts[$key])
        }
    }

    if ($trackCounts.Count -gt 0) {
        Write-Host ""
        Write-Host "Implementation track counts:"
        foreach ($key in $trackCounts.Keys) {
            Write-Host ("- {0}: {1}" -f $key, $trackCounts[$key])
        }
    }

    if ($packageCounts.Count -gt 0) {
        Write-Host ""
        Write-Host "Package counts:"
        foreach ($key in $packageCounts.Keys) {
            Write-Host ("- {0}: {1}" -f $key, $packageCounts[$key])
        }
    }

    if (!$SummaryOnly) {
        Write-Host ""
        foreach ($item in $items) {
            Write-Host ("[{0}] {1} ({2})" -f $item.status, $item.id, $item.category)
            Write-Host ("  Track: {0}" -f $item.implementationTrack)
            Write-Host ("  Packages: {0}" -f (@($item.packageIds) -join ", "))
            Write-Host ("  {0}" -f $item.title)
            Write-Host ("  Evidence: {0}" -f $item.evidence)
            Write-Host ("  Next: {0}" -f $item.nextAction)
        }
    }
}

if ($FailOnBlocked -and $handoffExitCode -ne 0) {
    exit $handoffExitCode
}
