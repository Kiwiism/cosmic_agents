param(
    [Parameter(Mandatory = $true)]
    [string] $StockStringRoot,

    [string] $StringRoot = "wz/String.wz",
    [string] $ItemWzRoot = "wz/Item.wz/Consume",
    [string] $OutputPath = "tmp/string-cleanup/validation.json"
)

$ErrorActionPreference = "Stop"

function Read-XmlDocument([string] $Path) {
    $document = [System.Xml.XmlDocument]::new()
    $document.PreserveWhitespace = $false
    $document.Load($Path)
    return $document
}

function Get-Ids([System.Xml.XmlDocument] $Document) {
    return @($Document.SelectNodes('//imgdir[translate(@name,"0123456789","")=""]') |
        ForEach-Object { [string] $_.GetAttribute('name') } |
        Sort-Object -Unique)
}

function Get-Field([System.Xml.XmlElement] $Entry, [string] $Field) {
    $node = $Entry.SelectSingleNode("./string[@name='$Field']")
    if ($null -eq $node) { return $null }
    return [string] $node.GetAttribute('value')
}

function Get-ItemInt([System.Xml.XmlDocument] $Document, [string] $ItemId, [string] $Field) {
    $padded = $ItemId.PadLeft(8, '0')
    $node = $Document.SelectSingleNode("/imgdir/imgdir[@name='$padded']/imgdir[@name='info']/*[@name='$Field' and (@value)]")
    if ($null -eq $node) { return $null }
    $parsed = 0
    if (![int]::TryParse($node.GetAttribute('value'), [ref] $parsed)) { return $null }
    return $parsed
}

function Test-IsExplorerSkill([long] $SkillId) {
    $jobId = [int] [Math]::Floor($SkillId / 10000)
    return $jobId -in @(
        0,
        100, 110, 111, 112, 120, 121, 122, 130, 131, 132,
        200, 210, 211, 212, 220, 221, 222, 230, 231, 232,
        300, 310, 311, 312, 320, 321, 322,
        400, 410, 411, 412, 420, 421, 422,
        500, 510, 511, 512, 520, 521, 522
    )
}

function Get-NumericAncestorId([System.Xml.XmlNode] $Node) {
    $current = $Node.ParentNode
    while ($null -ne $current) {
        if ($current -is [System.Xml.XmlElement]) {
            $candidate = $current.GetAttribute('name')
            if ($candidate -match '^\d+$') { return $candidate }
        }
        $current = $current.ParentNode
    }
    return $null
}

$stockRoot = (Resolve-Path -LiteralPath $StockStringRoot).Path
$stringRoot = (Resolve-Path -LiteralPath $StringRoot).Path
$itemRoot = (Resolve-Path -LiteralPath $ItemWzRoot).Path
$findings = [System.Collections.Generic.List[object]]::new()

function Add-Finding([string] $Severity, [string] $Code, [string] $File, [string] $Id, [string] $Detail) {
    $findings.Add([pscustomobject]@{ severity = $Severity; code = $Code; file = $File; id = $Id; detail = $Detail })
}

foreach ($stockFile in Get-ChildItem -LiteralPath $stockRoot -File -Filter '*.xml' | Sort-Object Name) {
    $outputFile = Join-Path $stringRoot $stockFile.Name
    if (!(Test-Path -LiteralPath $outputFile -PathType Leaf)) {
        Add-Finding 'error' 'missing-output-file' $stockFile.Name '' $outputFile
        continue
    }

    try {
        $stockDocument = Read-XmlDocument $stockFile.FullName
        $outputDocument = Read-XmlDocument $outputFile
    } catch {
        Add-Finding 'error' 'xml-parse-failed' $stockFile.Name '' $_.Exception.Message
        continue
    }

    $stockIds = @(Get-Ids $stockDocument)
    $outputIds = @(Get-Ids $outputDocument)
    $difference = @(Compare-Object $stockIds $outputIds)
    if ($difference.Count -gt 0) {
        Add-Finding 'error' 'id-set-changed' $stockFile.Name '' (($difference | ConvertTo-Json -Compress) -join '')
    }

    foreach ($parent in @($outputDocument.SelectNodes('//imgdir'))) {
        foreach ($group in @($parent.SelectNodes('./imgdir') | Group-Object { $_.GetAttribute('name') } | Where-Object Count -gt 1)) {
            Add-Finding 'error' 'duplicate-sibling-id' $stockFile.Name $group.Name "parent=$($parent.GetAttribute('name')); count=$($group.Count)"
        }
    }

    foreach ($string in @($outputDocument.SelectNodes('//string'))) {
        $value = [string] $string.GetAttribute('value')
        $ancestorId = Get-NumericAncestorId $string
        $isScrollText = $stockFile.Name -eq 'Consume.img.xml' -and $null -ne $ancestorId -and $ancestorId.StartsWith('204')
        if ($value -cmatch '\\R|\\N') {
            Add-Finding 'error' 'uppercase-newline-escape' $stockFile.Name '' $value
        }
        if (!$isScrollText -and $value -cmatch '\bATT\b') {
            Add-Finding 'error' 'att-outside-scroll' $stockFile.Name $ancestorId $value
        }

        $enforcePresentation = $stockFile.Name -in @('Cash.img.xml', 'Consume.img.xml', 'Eqp.img.xml', 'Etc.img.xml', 'Ins.img.xml')
        if ($stockFile.Name -eq 'Skill.img.xml') {
            $skillId = 0L
            $enforcePresentation = $null -ne $ancestorId -and [long]::TryParse($ancestorId, [ref] $skillId) -and (Test-IsExplorerSkill $skillId)
        }
        if (!$enforcePresentation) { continue }

        if ($value -match '(?i)Marshmellow|one-haned|Donky|expect for|experiemented|canve|skillt|Knuckler|Pole-Arm|Pet Equip\.' -or
            $value -cmatch 'Magic Att\.') {
            Add-Finding 'error' 'forbidden-spelling-or-term' $stockFile.Name '' $value
        }
        if ($value -ne $value.Trim()) {
            Add-Finding 'error' 'leading-or-trailing-space' $stockFile.Name '' $value
        }
        if ($value -match '[ \t]+\\n|\\n[ \t]+') {
            Add-Finding 'error' 'newline-adjacent-space' $stockFile.Name '' $value
        }
        $lastColor = $value.LastIndexOf('#c', [System.StringComparison]::Ordinal)
        if ($lastColor -ge 0 -and $value.IndexOf('#', $lastColor + 2) -lt 0) {
            Add-Finding 'error' 'unclosed-color-token' $stockFile.Name '' $value
        }
    }
}

$consumeDocument = Read-XmlDocument (Join-Path $stringRoot 'Consume.img.xml')
$scrollDocument = Read-XmlDocument (Join-Path $itemRoot '0204.img.xml')
$masteryDocument = Read-XmlDocument (Join-Path $itemRoot '0229.img.xml')

foreach ($entry in @($consumeDocument.SelectNodes('/imgdir/imgdir[starts-with(@name,"204")]'))) {
    $id = [string] $entry.GetAttribute('name')
    $name = Get-Field $entry 'name'
    if ($name -match '(?i)\bfor Attack\b') {
        Add-Finding 'error' 'noncanonical-scroll-attack-name' 'Consume.img.xml' $id $name
    }
    $desc = Get-Field $entry 'desc'
    if ($null -ne $desc -and $desc -match '(?i)\b(?:weapon|magic) attack\b') {
        Add-Finding 'error' 'noncanonical-scroll-attack-description' 'Consume.img.xml' $id $desc
    }
    $success = Get-ItemInt $scrollDocument $id 'success'
    if ($null -eq $success) { continue }
    if ($name -notmatch "(?<!\d)$success%$") {
        Add-Finding 'error' 'scroll-name-rate-mismatch' 'Consume.img.xml' $id $name
    }
    if ($null -ne $desc -and $desc -notmatch "(?i)Success rate: $success%") {
        Add-Finding 'error' 'scroll-description-rate-mismatch' 'Consume.img.xml' $id $desc
    }
}

foreach ($entry in @($consumeDocument.SelectNodes('/imgdir/imgdir[starts-with(@name,"229")]'))) {
    $id = [string] $entry.GetAttribute('name')
    $success = Get-ItemInt $masteryDocument $id 'success'
    $masterLevel = Get-ItemInt $masteryDocument $id 'masterLevel'
    if ($null -eq $success -or $null -eq $masterLevel) { continue }
    $name = Get-Field $entry 'name'
    $desc = Get-Field $entry 'desc'
    if ($name -notmatch " $masterLevel$") {
        Add-Finding 'error' 'mastery-name-level-mismatch' 'Consume.img.xml' $id $name
    }
    if ($desc -notmatch "(?i)$success% success rate" -or $desc -notmatch "(?i)to $masterLevel\.") {
        Add-Finding 'error' 'mastery-description-value-mismatch' 'Consume.img.xml' $id $desc
    }
}

if (@($consumeDocument.SelectNodes('//null')).Count -gt 0) {
    Add-Finding 'error' 'stray-null-node' 'Consume.img.xml' '' "count=$(@($consumeDocument.SelectNodes('//null')).Count)"
}

foreach ($fileName in @('Cash.img.xml', 'Consume.img.xml', 'Eqp.img.xml', 'Etc.img.xml', 'Ins.img.xml')) {
    $document = Read-XmlDocument (Join-Path $stringRoot $fileName)
    foreach ($entry in @($document.SelectNodes('//imgdir[string[@name="name" and starts-with(@value,"MISSING NAME")]]'))) {
        Add-Finding 'warning' 'unresolved-placeholder' $fileName $entry.GetAttribute('name') 'MISSING NAME remains in stock data'
    }
}

$outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outputFullPath) | Out-Null
$errors = @($findings | Where-Object severity -eq 'error')
$warnings = @($findings | Where-Object severity -eq 'warning')
$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString('o')
    status = if ($errors.Count -eq 0) { 'PASS' } else { 'FAIL' }
    stockStringRoot = $stockRoot
    stringRoot = $stringRoot
    errors = $errors.Count
    warnings = $warnings.Count
    findings = @($findings)
}
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $outputFullPath -Encoding UTF8
$report | ConvertTo-Json -Depth 8

if ($errors.Count -gt 0) { exit 1 }
