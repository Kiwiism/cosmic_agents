param(
    [Parameter(Mandatory = $true)]
    [string] $RunPath,

    [Parameter(Mandatory = $true)]
    [ValidateSet("serverhealth", "scale-health", "slow-operations", "startup", "shutdown")]
    [string] $Target,

    [string] $Text,

    [string] $InputPath,

    [switch] $FromClipboard,

    [string] $Timestamp,

    [switch] $DryRun,

    [switch] $SummaryOnly,

    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Get-SampleText {
    if (-not [string]::IsNullOrWhiteSpace($InputPath)) {
        return Get-Content -LiteralPath $InputPath -Raw
    }

    if ($FromClipboard) {
        return Get-Clipboard -Raw
    }

    if (-not [string]::IsNullOrWhiteSpace($Text)) {
        return $Text
    }

    if (-not [Console]::IsInputRedirected) {
        throw "Provide sample text with -Text, -InputPath, -FromClipboard, or pipeline input."
    }

    return [Console]::In.ReadToEnd()
}

$targetFiles = @{
    "serverhealth" = "serverhealth-5min-samples.log"
    "scale-health" = "scale-health.log"
    "slow-operations" = "slow-operations.log"
    "startup" = "startup.log"
    "shutdown" = "shutdown.log"
}

$resolvedRunPath = Resolve-Path -LiteralPath $RunPath -ErrorAction Stop
$targetFile = $targetFiles[$Target]
$targetPath = Join-Path $resolvedRunPath $targetFile

if (-not (Test-Path -LiteralPath $targetPath)) {
    throw "Target file $targetFile was not found in $($resolvedRunPath.Path). Create the evidence package first."
}

if ([string]::IsNullOrWhiteSpace($Timestamp)) {
    $Timestamp = (Get-Date).ToString("o")
}

$sampleText = Get-SampleText
if ([string]::IsNullOrWhiteSpace($sampleText)) {
    throw "Sample text is empty."
}

$sampleCountBefore = @(
    Select-String -LiteralPath $targetPath -Pattern '^# sample:' -ErrorAction SilentlyContinue
).Count

$entry = @"

# sample: $Timestamp
$sampleText
"@

if (!$DryRun) {
    Add-Content -LiteralPath $targetPath -Value $entry -Encoding UTF8
}

$sampleCount = if ($DryRun) {
    $sampleCountBefore + 1
} else {
    @(
        Select-String -LiteralPath $targetPath -Pattern '^# sample:' -ErrorAction SilentlyContinue
    ).Count
}

$report = [ordered]@{
    status = if ($DryRun) { "DRY_RUN" } else { "OK" }
    runPath = $resolvedRunPath.Path
    target = $Target
    targetPath = $targetPath
    timestamp = $Timestamp
    dryRun = [bool] $DryRun
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = $false
    appended = -not [bool] $DryRun
    appendedCharacterCount = $sampleText.Length
    sampleCountBefore = $sampleCountBefore
    sampleCount = $sampleCount
}

if ($Json) {
    $report | ConvertTo-Json -Depth 4
    return
}

if ($DryRun) {
    Write-Host "Dry run only. No $Target sample was appended to:"
} else {
    Write-Host "Appended $Target sample to:"
}
Write-Host "  $targetPath"
Write-Host "Samples before: $sampleCountBefore"
Write-Host "Samples after: $sampleCount"
