param(
    [string] $SourceStringRoot = "wz/String.wz",

    [Parameter(Mandatory = $true)]
    [string] $RoundTripStringRoot,

    [string] $OutputPath = "tmp/string-cleanup/client-roundtrip-validation.json",

    [string[]] $FileNames = @('Cash.img.xml', 'Consume.img.xml', 'Eqp.img.xml', 'Etc.img.xml', 'Ins.img.xml', 'Skill.img.xml')
)

$ErrorActionPreference = "Stop"

function Get-CanonicalRows {
    param([System.Xml.XmlDocument] $Document)

    $canonical = foreach ($node in @($Document.SelectNodes('//*'))) {
        $segments = [System.Collections.Generic.List[string]]::new()
        $current = $node
        while ($null -ne $current -and $current.NodeType -eq [System.Xml.XmlNodeType]::Element) {
            $name = if ($current.Attributes['name']) { $current.Attributes['name'].Value } else { '' }
            $segments.Insert(0, "$($current.Name)[$name]")
            $current = $current.ParentNode
        }
        $attributes = foreach ($attributeName in @('value', 'x', 'y')) {
            if ($node.Attributes[$attributeName]) {
                "$attributeName=$($node.Attributes[$attributeName].Value)"
            }
        }
        "/$($segments -join '/')|$($attributes -join '|')"
    }
    return [string[]] $canonical
}

$sourceRoot = (Resolve-Path -LiteralPath $SourceStringRoot).Path
$roundTripRoot = (Resolve-Path -LiteralPath $RoundTripStringRoot).Path
$rows = [System.Collections.Generic.List[object]]::new()

foreach ($fileName in $FileNames) {
    $source = Get-Item -LiteralPath (Join-Path $sourceRoot $fileName)
    $roundTripPath = Join-Path $roundTripRoot $source.Name
    if (!(Test-Path -LiteralPath $roundTripPath -PathType Leaf)) {
        [void] $rows.Add([pscustomobject]@{ file = $source.Name; sourceNodes = 0; roundTripNodes = 0; differences = 1; error = 'missing round-trip XML' })
        continue
    }

    [xml] $sourceDocument = Get-Content -Raw -LiteralPath $source.FullName
    [xml] $roundTripDocument = Get-Content -Raw -LiteralPath $roundTripPath
    [string[]] $sourceRows = Get-CanonicalRows $sourceDocument
    [string[]] $roundTripRows = Get-CanonicalRows $roundTripDocument
    [Array]::Sort($sourceRows, [System.StringComparer]::Ordinal)
    [Array]::Sort($roundTripRows, [System.StringComparer]::Ordinal)
    $differenceCount = [Math]::Abs($sourceRows.Count - $roundTripRows.Count)
    $differenceExamples = [System.Collections.Generic.List[string]]::new()
    $sharedCount = [Math]::Min($sourceRows.Count, $roundTripRows.Count)
    for ($index = 0; $index -lt $sharedCount; $index++) {
        if ($sourceRows[$index] -cne $roundTripRows[$index]) {
            $differenceCount++
            if ($differenceExamples.Count -lt 5) {
                [void] $differenceExamples.Add("source=$($sourceRows[$index]) || roundTrip=$($roundTripRows[$index])")
            }
        }
    }

    [void] $rows.Add([pscustomobject]@{
        file = $source.Name
        sourceNodes = $sourceRows.Count
        roundTripNodes = $roundTripRows.Count
        differences = $differenceCount
        error = if ($differenceExamples.Count -gt 0) { $differenceExamples -join "`n" } else { $null }
    })
}

$failed = @($rows | Where-Object { $_.differences -gt 0 -or $null -ne $_.error })
$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString('o')
    status = if ($failed.Count -eq 0) { 'PASS' } else { 'FAIL' }
    sourceStringRoot = $sourceRoot
    roundTripStringRoot = $roundTripRoot
    files = $rows.Count
    failedFiles = $failed.Count
    sourceNodes = ($rows | Measure-Object sourceNodes -Sum).Sum
    roundTripNodes = ($rows | Measure-Object roundTripNodes -Sum).Sum
    differences = ($rows | Measure-Object differences -Sum).Sum
    rows = @($rows)
}

$outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outputFullPath) | Out-Null
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $outputFullPath -Encoding UTF8
$report | ConvertTo-Json -Depth 6

if ($failed.Count -gt 0) { exit 1 }
