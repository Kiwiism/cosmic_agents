param(
    [Parameter(Mandatory = $true)]
    [string] $OldCatalogDir,

    [Parameter(Mandatory = $true)]
    [string] $NewCatalogDir,

    [string] $OutputPath,

    [switch] $SummaryOnly,

    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Resolve-ExistingDirectory {
    param(
        [string] $Path,
        [string] $Label
    )

    if (!(Test-Path -LiteralPath $Path -PathType Container)) {
        throw "$Label catalog directory was not found: $Path"
    }

    return (Resolve-Path -LiteralPath $Path).Path
}

function Get-FileHashValue {
    param([string] $Path)

    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $null
    }

    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
}

function Read-JsonValue {
    param([string] $Path)

    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $null
    }

    try {
        return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    } catch {
        return [pscustomobject] @{
            parseError = $_.Exception.Message
        }
    }
}

function Get-ArrayCount {
    param([object] $Value)

    if ($null -eq $Value) {
        return 0
    }
    if ($Value.PSObject.Properties.Name -contains "parseError") {
        return $null
    }
    if ($Value -is [System.Array]) {
        return $Value.Count
    }
    return 1
}

function Get-ManifestCounts {
    param([string] $Dir)

    $path = Join-Path $Dir "generated_catalog_manifest.json"
    $manifest = Read-JsonValue $path
    if ($null -eq $manifest -or $manifest.PSObject.Properties.Name -contains "parseError" -or $null -eq $manifest.counts) {
        return @{}
    }

    $counts = @{}
    foreach ($property in $manifest.counts.PSObject.Properties) {
        $counts[$property.Name] = $property.Value
    }
    return $counts
}

function Get-MapleIslandQuestIds {
    param([string] $Dir)

    $path = Join-Path $Dir "generated_maple_island_mvp_catalog.json"
    $catalog = Read-JsonValue $path
    if ($null -eq $catalog -or $catalog.PSObject.Properties.Name -contains "parseError" -or $null -eq $catalog.quests) {
        return @()
    }

    return @($catalog.quests | ForEach-Object { [int] $_.questId } | Sort-Object -Unique)
}

function Compare-ScalarMap {
    param(
        [hashtable] $OldMap,
        [hashtable] $NewMap
    )

    $rows = New-Object System.Collections.Generic.List[object]
    $keys = @($OldMap.Keys + $NewMap.Keys | Sort-Object -Unique)
    foreach ($key in $keys) {
        $oldValue = if ($OldMap.ContainsKey($key)) { $OldMap[$key] } else { $null }
        $newValue = if ($NewMap.ContainsKey($key)) { $NewMap[$key] } else { $null }
        if ("$oldValue" -eq "$newValue") {
            continue
        }

        [void] $rows.Add([pscustomobject] @{
            key = $key
            old = $oldValue
            new = $newValue
            delta = if ($null -ne $oldValue -and $null -ne $newValue -and "$oldValue" -match "^-?\d+$" -and "$newValue" -match "^-?\d+$") {
                [int64] $newValue - [int64] $oldValue
            } else {
                $null
            }
        })
    }
    return @($rows.ToArray())
}

function ConvertTo-MarkdownReport {
    param([object] $Report)

    $lines = New-Object System.Collections.Generic.List[string]
    [void] $lines.Add("# Agent/LLM Catalog Diff Report")
    [void] $lines.Add("")
    [void] $lines.Add("Generated: $($Report.generatedAt)")
    [void] $lines.Add("")
    [void] $lines.Add("| Field | Value |")
    [void] $lines.Add("| --- | --- |")
    [void] $lines.Add(("| Old catalog | `{0}` |" -f $Report.oldCatalogDir))
    [void] $lines.Add(("| New catalog | `{0}` |" -f $Report.newCatalogDir))
    [void] $lines.Add("| Added files | $($Report.summary.addedFiles) |")
    [void] $lines.Add("| Removed files | $($Report.summary.removedFiles) |")
    [void] $lines.Add("| Changed files | $($Report.summary.changedFiles) |")
    [void] $lines.Add("| Manifest count changes | $($Report.summary.manifestCountChanges) |")
    [void] $lines.Add("| Maple Island MVP quest additions | $($Report.summary.mapleIslandQuestAdditions) |")
    [void] $lines.Add("| Maple Island MVP quest removals | $($Report.summary.mapleIslandQuestRemovals) |")
    [void] $lines.Add("")

    [void] $lines.Add("## File Changes")
    [void] $lines.Add("")
    [void] $lines.Add("| File | Status | Old Rows | New Rows | Old Bytes | New Bytes |")
    [void] $lines.Add("| --- | --- | ---: | ---: | ---: | ---: |")
    foreach ($file in @($Report.files | Where-Object { $_.status -ne "unchanged" })) {
        [void] $lines.Add("| $($file.name) | $($file.status) | $($file.oldRows) | $($file.newRows) | $($file.oldBytes) | $($file.newBytes) |")
    }
    if (@($Report.files | Where-Object { $_.status -ne "unchanged" }).Count -eq 0) {
        [void] $lines.Add("| none | unchanged | - | - | - | - |")
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Manifest Count Changes")
    [void] $lines.Add("")
    [void] $lines.Add("| Count | Old | New | Delta |")
    [void] $lines.Add("| --- | ---: | ---: | ---: |")
    foreach ($count in @($Report.manifestCountChanges)) {
        [void] $lines.Add("| $($count.key) | $($count.old) | $($count.new) | $($count.delta) |")
    }
    if (@($Report.manifestCountChanges).Count -eq 0) {
        [void] $lines.Add("| none | - | - | - |")
    }
    [void] $lines.Add("")

    [void] $lines.Add("## Maple Island MVP Quest Changes")
    [void] $lines.Add("")
    [void] $lines.Add("- Added quest IDs: $(if (@($Report.mapleIsland.addedQuestIds).Count -gt 0) { @($Report.mapleIsland.addedQuestIds) -join ', ' } else { 'none' })")
    [void] $lines.Add("- Removed quest IDs: $(if (@($Report.mapleIsland.removedQuestIds).Count -gt 0) { @($Report.mapleIsland.removedQuestIds) -join ', ' } else { 'none' })")
    [void] $lines.Add("")
    [void] $lines.Add("## Notes")
    [void] $lines.Add("")
    [void] $lines.Add("- This report compares generated offline catalogs only.")
    [void] $lines.Add("- It does not modify runtime code, Agent behavior, BotClient behavior, or config.")
    [void] $lines.Add("- Treat quest, map, and item diffs as review prompts before refreshing runtime bundles.")

    return ($lines -join "`n")
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$oldDir = Resolve-ExistingDirectory $OldCatalogDir "Old"
$newDir = Resolve-ExistingDirectory $NewCatalogDir "New"

$oldFiles = @{}
foreach ($file in Get-ChildItem -LiteralPath $oldDir -File) {
    $oldFiles[$file.Name] = $file
}

$newFiles = @{}
foreach ($file in Get-ChildItem -LiteralPath $newDir -File) {
    $newFiles[$file.Name] = $file
}

$fileRows = New-Object System.Collections.Generic.List[object]
$allFileNames = @($oldFiles.Keys + $newFiles.Keys | Sort-Object -Unique)
foreach ($name in $allFileNames) {
    $oldFile = if ($oldFiles.ContainsKey($name)) { $oldFiles[$name] } else { $null }
    $newFile = if ($newFiles.ContainsKey($name)) { $newFiles[$name] } else { $null }
    $oldPath = if ($oldFile) { $oldFile.FullName } else { $null }
    $newPath = if ($newFile) { $newFile.FullName } else { $null }
    $oldHash = if ($oldPath) { Get-FileHashValue $oldPath } else { $null }
    $newHash = if ($newPath) { Get-FileHashValue $newPath } else { $null }
    $status = if ($null -eq $oldFile) {
        "added"
    } elseif ($null -eq $newFile) {
        "removed"
    } elseif ($oldHash -ne $newHash) {
        "changed"
    } else {
        "unchanged"
    }

    $oldRows = if ($oldPath -and $name.EndsWith(".json")) { Get-ArrayCount (Read-JsonValue $oldPath) } else { $null }
    $newRows = if ($newPath -and $name.EndsWith(".json")) { Get-ArrayCount (Read-JsonValue $newPath) } else { $null }

    [void] $fileRows.Add([pscustomobject] @{
        name = $name
        status = $status
        oldRows = $oldRows
        newRows = $newRows
        oldBytes = if ($oldFile) { $oldFile.Length } else { $null }
        newBytes = if ($newFile) { $newFile.Length } else { $null }
        oldSha256 = $oldHash
        newSha256 = $newHash
    })
}

$oldCounts = Get-ManifestCounts $oldDir
$newCounts = Get-ManifestCounts $newDir
$manifestCountChanges = @(
    Compare-ScalarMap $oldCounts $newCounts |
        Where-Object { $null -ne $_ -and $_.PSObject.Properties.Name -contains "key" }
)

$oldMapleQuests = @(Get-MapleIslandQuestIds $oldDir)
$newMapleQuests = @(Get-MapleIslandQuestIds $newDir)
$addedMapleQuests = @($newMapleQuests | Where-Object { $oldMapleQuests -notcontains $_ } | Sort-Object)
$removedMapleQuests = @($oldMapleQuests | Where-Object { $newMapleQuests -notcontains $_ } | Sort-Object)
$fileArray = @($fileRows.ToArray())

$report = [ordered] @{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    repoRoot = $repoRoot
    oldCatalogDir = $oldDir
    newCatalogDir = $newDir
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    fileCount = $fileArray.Count
    returnedFileCount = if ($SummaryOnly) { 0 } else { $fileArray.Count }
    manifestCountChangeCount = @($manifestCountChanges).Count
    returnedManifestCountChangeCount = if ($SummaryOnly) { 0 } else { @($manifestCountChanges).Count }
    oldMapleIslandQuestCount = @($oldMapleQuests).Count
    newMapleIslandQuestCount = @($newMapleQuests).Count
    returnedMapleIslandQuestListCount = if ($SummaryOnly) { 0 } else { 2 }
    summary = [ordered] @{
        addedFiles = @($fileArray | Where-Object { $_.status -eq "added" }).Count
        removedFiles = @($fileArray | Where-Object { $_.status -eq "removed" }).Count
        changedFiles = @($fileArray | Where-Object { $_.status -eq "changed" }).Count
        manifestCountChanges = @($manifestCountChanges).Count
        mapleIslandQuestAdditions = @($addedMapleQuests).Count
        mapleIslandQuestRemovals = @($removedMapleQuests).Count
    }
    files = if ($SummaryOnly) { $null } else { @($fileArray) }
    manifestCountChanges = if ($SummaryOnly) { $null } else { @($manifestCountChanges) }
    mapleIsland = [ordered] @{
        oldQuestIds = if ($SummaryOnly) { $null } else { @($oldMapleQuests) }
        newQuestIds = if ($SummaryOnly) { $null } else { @($newMapleQuests) }
        addedQuestIds = @($addedMapleQuests)
        removedQuestIds = @($removedMapleQuests)
    }
}

if ($OutputPath) {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and !(Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }

    if ($Json) {
        $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    } else {
        ConvertTo-MarkdownReport ([pscustomobject] $report) | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    }

    Write-Host "Agent/LLM catalog diff written:"
    Write-Host "  $OutputPath"
} elseif ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    ConvertTo-MarkdownReport ([pscustomobject] $report)
}
