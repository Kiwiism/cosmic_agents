param(
    [Parameter(Mandatory = $true)]
    [string] $RunPath,

    [string] $Pattern,

    [switch] $All,

    [switch] $Uncheck,

    [switch] $List
)

$ErrorActionPreference = "Stop"

function Get-ChecklistItems {
    param([string[]] $Lines)

    $items = @()
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i] -match '^\s*-\s+\[(?<mark>[ xX])\]\s+(?<text>.+)$') {
            $mark = $Matches.mark
            $text = $Matches.text
            $items += [ordered]@{
                lineNumber = $i + 1
                checked = $mark -match '[xX]'
                text = $text
            }
        }
    }

    return $items
}

$resolvedRunPath = Resolve-Path -LiteralPath $RunPath -ErrorAction Stop
$checklistPath = Join-Path $resolvedRunPath "evidence-checklist.md"

if (-not (Test-Path -LiteralPath $checklistPath)) {
    throw "Missing evidence checklist: $checklistPath"
}

$lines = @(Get-Content -LiteralPath $checklistPath)

if ($List) {
    $items = Get-ChecklistItems $lines
    foreach ($item in $items) {
        $mark = if ($item.checked) { "x" } else { " " }
        Write-Host ("[{0}] {1}. {2}" -f $mark, $item.lineNumber, $item.text)
    }
    return
}

if (-not $All -and [string]::IsNullOrWhiteSpace($Pattern)) {
    throw "Provide -Pattern, -All, or -List."
}

$changed = 0
$matchedCount = 0
$targetMark = if ($Uncheck) { " " } else { "x" }

for ($i = 0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]
    $match = [regex]::Match($line, '^(?<prefix>\s*-\s+)\[(?<mark>[ xX])\](?<space>\s+)(?<text>.+)$')
    if (-not $match.Success) {
        continue
    }

    $itemText = $match.Groups["text"].Value
    if (-not $All -and $itemText -notmatch $Pattern) {
        continue
    }

    $matchedCount++
    $currentMark = $match.Groups["mark"].Value
    if ($currentMark -ne $targetMark) {
        $lines[$i] = $match.Groups["prefix"].Value + "[$targetMark]" + $match.Groups["space"].Value + $itemText
        $changed++
    }
}

if ($matchedCount -eq 0) {
    throw "No checklist item matched."
}

Set-Content -LiteralPath $checklistPath -Value $lines -Encoding UTF8

$action = if ($Uncheck) { "unchecked" } else { "checked" }
Write-Host "Checklist items matched: $matchedCount"
Write-Host "Checklist items $action`: $changed"
Write-Host "Checklist: $checklistPath"
