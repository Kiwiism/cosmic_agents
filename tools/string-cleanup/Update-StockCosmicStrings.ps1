param(
    [Parameter(Mandatory = $true)]
    [string] $StockStringRoot,

    [Parameter(Mandatory = $true)]
    [string] $MapleRootStringRoot,

    [string] $ItemWzRoot = "wz/Item.wz/Consume",
    [string] $OutputStringRoot = "wz/String.wz",
    [string] $ReportDir = "tmp/string-cleanup"
)

$ErrorActionPreference = "Stop"

function Resolve-RequiredDirectory {
    param([string] $Path, [string] $Label)

    if (!(Test-Path -LiteralPath $Path -PathType Container)) {
        throw "$Label does not exist: $Path"
    }
    return (Resolve-Path -LiteralPath $Path).Path
}

function Read-XmlDocument {
    param([string] $Path)

    $document = [System.Xml.XmlDocument]::new()
    $document.PreserveWhitespace = $false
    $document.Load($Path)
    return $document
}

function Write-XmlDocument {
    param([System.Xml.XmlDocument] $Document, [string] $Path)

    $settings = [System.Xml.XmlWriterSettings]::new()
    $settings.Encoding = [System.Text.UTF8Encoding]::new($false)
    $settings.Indent = $true
    $settings.IndentChars = "  "
    $settings.NewLineChars = "`n"
    $settings.NewLineHandling = [System.Xml.NewLineHandling]::None
    $settings.OmitXmlDeclaration = $false

    $writer = [System.Xml.XmlWriter]::Create($Path, $settings)
    try {
        $Document.Save($writer)
    } finally {
        $writer.Dispose()
    }
}

function Get-DirectStringNode {
    param([System.Xml.XmlElement] $Entry, [string] $Field)
    return $Entry.SelectSingleNode("./string[@name='$Field']")
}

function Get-DirectStringValue {
    param([System.Xml.XmlElement] $Entry, [string] $Field)
    $node = Get-DirectStringNode $Entry $Field
    if ($null -eq $node) { return $null }
    return [string] $node.GetAttribute("value")
}

function Set-DirectStringValue {
    param(
        [System.Xml.XmlElement] $Entry,
        [string] $Field,
        [string] $Value
    )

    $node = Get-DirectStringNode $Entry $Field
    if ($null -eq $node) { return $false }
    if ([string] $node.GetAttribute("value") -ceq $Value) { return $false }
    $node.SetAttribute("value", $Value)
    return $true
}

function Normalize-LineFormatting {
    param([string] $Value)

    if ($null -eq $Value) { return $null }

    $result = $Value
    $result = $result -creplace '\\R\\N', '\r\n'
    $result = $result -creplace '\\R', '\r'
    $result = $result -creplace '\\N', '\n'
    $result = $result -replace '\\r\\n', '\n'
    $result = $result -replace '[ \t]+\\n', '\n'
    $result = $result -replace '\\n[ \t]+', '\n'
    $result = $result -replace '[ \t]{2,}', ' '
    return $result.Trim()
}

function Normalize-CanonicalTerms {
    param(
        [string] $Value,
        [switch] $NameField,
        [switch] $ScrollName,
        [switch] $ScrollText
    )

    if ($null -eq $Value) { return $null }

    $result = Normalize-LineFormatting $Value

    # Safe spelling corrections observed in the stock String.wz dump.
    $result = $result -replace '(?i)Marshmellow', 'Marshmallow'
    $result = $result -replace '(?i)one-haned', 'one-handed'
    $result = $result -replace '(?i)Donky', 'Donkey'
    $result = $result -replace '(?i)expect for', 'except for'
    $result = $result -replace '(?i)experiemented', 'experimented'
    $result = $result -replace '(?i)canve', 'can be'
    $result = $result -replace '(?i)skillt', 'skill'
    $result = $result -replace '(?i)chili power', 'chili powder'
    $result = $result -replace '(?i)made of up', 'made of'
    $result = $result -replace '(?i)a thousands-of-years-old', 'a thousand-year-old'

    # Canonical equipment and stat vocabulary.
    $result = $result -replace '(?i)Knucklers', 'Knuckle weapons'
    $result = $result -replace '(?i)Knuckler', 'Knuckle'
    $result = $result -replace '(?i)\bMonsterbook\b', 'Monster Book'
    $result = $result -replace '(?i)Pole[- ]Arms', 'Polearms'
    $result = $result -replace '(?i)Pole[- ]Arm', 'Polearm'
    if ($ScrollText) {
        $result = $result -replace '(?i)Magic Att\.', 'Magic ATT'
        $result = $result -replace '(?i)W\. Att\.?', 'Weapon ATT'
        $result = $result -replace '(?i)M\. Att\.?', 'Magic ATT'
        $result = $result -replace '(?i)Weapon Attack\b', 'Weapon ATT'
        $result = $result -replace '(?i)Magic Attack\b', 'Magic ATT'
        $result = $result -replace '(?i)Weapon\s*&\s*Magic ATT', 'Weapon ATT & Magic ATT'
        $result = $result -replace '(?i)Attack\s*&\s*Magic ATT', 'Weapon ATT & Magic ATT'
        $result = $result -replace '(?i)weapon and Magic ATT', 'Weapon ATT and Magic ATT'
    } else {
        $result = $result -replace '(?i)Magic Att\.', 'Magic Attack'
        $result = $result -replace '(?i)W\. Att\.?', 'Weapon Attack'
        $result = $result -replace '(?i)M\. Att\.?', 'Magic Attack'
        $result = $result -creplace '\bWeapon ATT\b', 'Weapon Attack'
        $result = $result -creplace '\bMagic ATT\b', 'Magic Attack'
        $result = $result -creplace '\bATT\b', 'Attack'
    }
    $result = $result -replace '(?i)Weapon Defense\b', 'Weapon DEF'
    $result = $result -replace '(?i)Magic Defense\b', 'Magic DEF'
    $result = $result -replace '(?i)Weapon Def\.', 'Weapon DEF'
    $result = $result -replace '(?i)Magic Def\.', 'Magic DEF'
    $result = $result -replace '(?i)Weapon Def\b', 'Weapon DEF'
    $result = $result -replace '(?i)Magic Def\b', 'Magic DEF'
    $result = $result -replace '(?i)Pet Equip\.', 'Pet Equipment'
    $result = $result -replace '(?i)MaxHP', 'Max HP'
    $result = $result -replace '(?i)MaxMP', 'Max MP'
    $result = $result -replace '(?i)\[4yrAnniv\]\s*', '[4th Anniversary] '
    $result = $result -replace '(?i)\[4th Anniversary\]\s*', '[4th Anniversary] '
    $result = $result -replace '(?i)\[Master Level\s*:\s*', '[Master Level: '
    $result = $result -replace '(?i)Required Skill\s*:', 'Required Skill:'
    $result = $result -replace '(?i)Success rate\s*:\s*', 'Success rate: '
    $result = $result -replace '(?i)Gun\.This', 'Gun. This'

    if ($NameField) {
        $result = $result -replace '(?i)\bOne-handed\b', 'One-Handed'
        $result = $result -replace '(?i)\bTwo-handed\b', 'Two-Handed'
        $result = $result -replace '(?i)\bOverall\s+for\b', 'Overall Armor for'
        $result = $result -replace '(?i)\bPet Equip\b', 'Pet Equipment'
        $result = $result -replace '(?i)\bKnuckles\b', 'Knuckle'
        $result = $result -replace '(?i)\bBW\b', 'Blunt Weapon'
        $result = $result -replace '(?i)^Dark scroll\b', 'Dark Scroll'
    } else {
        $result = $result -replace '(?i)\bone-handed\b', 'one-handed'
        $result = $result -replace '(?i)\btwo-handed\b', 'two-handed'
        $result = $result -creplace '\bPolearms\b', 'polearms'
    }

    if ($ScrollName) {
        $result = $result -replace '(?i)\bfor Attack\b', 'for ATT'
        $result = $result -replace '(?i)Magic Attacks\b', 'Magic ATT'
        $result = $result -replace '(?i)Attacks\b', 'for ATT'
        $result = $result -replace '(?i)Magic Attack\b', 'Magic ATT'
        $result = $result -replace '(?i)Weapon Attack\b', 'ATT'
    }

    foreach ($stat in @('Accuracy', 'Avoidability', 'Speed', 'Jump')) {
        $result = $result -replace "(?i)\b$stat(?=\s*\+)", $stat
    }
    if ($ScrollText) {
        $result = $result -replace '(?i)Attack(?=\s*\+)', 'Weapon ATT'
    }
    $result = $result -replace '(?i)(Weapon ATT|Magic ATT|Weapon Attack|Magic Attack|Attack|Weapon DEF|Magic DEF|STR|DEX|INT|LUK|ATT|DEF|HP|MP|Max HP|Max MP|Accuracy|Avoidability|Speed|Jump)\s*\+\s*', '$1 +'
    $result = $result -replace '\+\s+(\d)', '+$1'
    $result = $result -replace '(?i)destroyed in a (\d+)% rate', 'destroyed at a $1% rate'
    $result = $result -replace '[ \t]{2,}', ' '
    return $result.Trim()
}

function Close-TrailingColorToken {
    param([string] $Value)

    if ([string]::IsNullOrEmpty($Value)) { return $Value }
    $lastOpen = $Value.LastIndexOf('#c', [System.StringComparison]::Ordinal)
    if ($lastOpen -lt 0) { return $Value }
    $closing = $Value.IndexOf('#', $lastOpen + 2)
    if ($closing -lt 0) { return $Value + '#' }
    return $Value
}

function Get-ItemInfoNode {
    param([System.Xml.XmlDocument] $Document, [string] $ItemId)
    $padded = $ItemId.PadLeft(8, '0')
    return $Document.SelectSingleNode("/imgdir/imgdir[@name='$padded']/imgdir[@name='info']")
}

function Get-IntValue {
    param([System.Xml.XmlElement] $Parent, [string] $Field)
    if ($null -eq $Parent) { return $null }
    $node = $Parent.SelectSingleNode("./*[@name='$Field' and (@value)]")
    if ($null -eq $node) { return $null }
    $parsed = 0
    if (![int]::TryParse($node.GetAttribute("value"), [ref] $parsed)) { return $null }
    return $parsed
}

function Get-EntryMap {
    param([System.Xml.XmlDocument] $Document)
    $map = @{}
    foreach ($node in @($Document.SelectNodes('//imgdir[translate(@name,"0123456789","")=""]'))) {
        $map[[string] $node.GetAttribute("name")] = $node
    }
    return $map
}

function Test-IsExplorerSkill {
    param([long] $SkillId)

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

$stockRoot = Resolve-RequiredDirectory $StockStringRoot "Stock String.wz root"
$mapleRoot = Resolve-RequiredDirectory $MapleRootStringRoot "MapleRoot String.wz root"
$itemRoot = Resolve-RequiredDirectory $ItemWzRoot "Item.wz Consume root"
$outputRoot = [System.IO.Path]::GetFullPath($OutputStringRoot)
$reportRoot = [System.IO.Path]::GetFullPath($ReportDir)

$targetFiles = @(
    'Cash.img.xml',
    'Consume.img.xml',
    'Eqp.img.xml',
    'Etc.img.xml',
    'Ins.img.xml',
    'Skill.img.xml'
)

$stockFiles = @(Get-ChildItem -LiteralPath $stockRoot -File -Filter '*.xml' | Sort-Object Name)
if ($stockFiles.Count -eq 0) {
    throw "No stock String.wz XML files were found in $stockRoot"
}
foreach ($required in $targetFiles) {
    if (!(Test-Path -LiteralPath (Join-Path $stockRoot $required) -PathType Leaf)) {
        throw "Stock String.wz is missing required file: $required"
    }
    if (!(Test-Path -LiteralPath (Join-Path $mapleRoot $required) -PathType Leaf)) {
        throw "MapleRoot String.wz is missing reference file: $required"
    }
}

New-Item -ItemType Directory -Force -Path $outputRoot, $reportRoot | Out-Null

# The requested baseline operation is explicit: every server String.wz XML file is
# replaced with the stock Cosmic copy before scoped text changes are applied.
foreach ($source in $stockFiles) {
    Copy-Item -LiteralPath $source.FullName -Destination (Join-Path $outputRoot $source.Name) -Force
}

$changes = [System.Collections.Generic.List[object]]::new()
$warnings = [System.Collections.Generic.List[object]]::new()

function Record-Change {
    param([string] $File, [string] $Id, [string] $Field, [string] $Before, [string] $After, [string] $Reason)
    if ($Before -ceq $After) { return }
    $changes.Add([pscustomobject]@{
        file = $File
        id = $Id
        field = $Field
        before = $Before
        after = $After
        reason = $Reason
    })
}

function Normalize-EntryStrings {
    param(
        [string] $File,
        [System.Xml.XmlElement] $Entry,
        [switch] $AllStringFields,
        [switch] $ScrollName,
        [switch] $ScrollText
    )

    $nodes = if ($AllStringFields) { @($Entry.SelectNodes('./string')) } else { @($Entry.SelectNodes('./string[@name="name" or @name="desc"]')) }
    foreach ($node in $nodes) {
        $field = [string] $node.GetAttribute('name')
        $before = [string] $node.GetAttribute('value')
        $after = Normalize-CanonicalTerms $before -NameField:($field -eq 'name') -ScrollName:($ScrollName -and $field -eq 'name') -ScrollText:$ScrollText
        $after = Close-TrailingColorToken $after
        if ($before -cne $after) {
            $node.SetAttribute('value', $after)
            Record-Change $File $Entry.GetAttribute('name') $field $before $after 'spelling-and-format'
        }
    }
}

# Fix malformed uppercase newline escapes everywhere without otherwise rewriting
# dialogue, map lore, NPC text, or EULA prose.
foreach ($file in $stockFiles) {
    $path = Join-Path $outputRoot $file.Name
    $document = Read-XmlDocument $path
    $changed = $false
    foreach ($node in @($document.SelectNodes('//string'))) {
        $before = [string] $node.GetAttribute('value')
        $after = $before -creplace '\\R\\N', '\r\n'
        $after = $after -creplace '\\R', '\r'
        $after = $after -creplace '\\N', '\n'
        if ($before -cne $after) {
            $node.SetAttribute('value', $after)
            $changed = $true
            Record-Change $file.Name '' $node.GetAttribute('name') $before $after 'newline-escape-case'
        }
    }
    if ($changed) { Write-XmlDocument $document $path }
}

$consumeItemDocument = Read-XmlDocument (Join-Path $itemRoot '0204.img.xml')
$masteryItemDocument = Read-XmlDocument (Join-Path $itemRoot '0229.img.xml')

foreach ($fileName in $targetFiles) {
    $outputPath = Join-Path $outputRoot $fileName
    $document = Read-XmlDocument $outputPath
    $referenceDocument = Read-XmlDocument (Join-Path $mapleRoot $fileName)
    $referenceEntries = Get-EntryMap $referenceDocument

    foreach ($entry in @($document.SelectNodes('//imgdir[translate(@name,"0123456789","")=""]'))) {
        $id = [string] $entry.GetAttribute('name')

        if ($fileName -eq 'Skill.img.xml') {
            $parsedSkillId = 0L
            if (![long]::TryParse($id, [ref] $parsedSkillId) -or !(Test-IsExplorerSkill $parsedSkillId)) {
                continue
            }
            Normalize-EntryStrings $fileName $entry -AllStringFields
            continue
        }

        $isScroll = $fileName -eq 'Consume.img.xml' -and $id.StartsWith('204')
        Normalize-EntryStrings $fileName $entry -AllStringFields:($fileName -eq 'Eqp.img.xml') -ScrollName:$isScroll -ScrollText:$isScroll

        if ($fileName -eq 'Eqp.img.xml') {
            $name = Get-DirectStringValue $entry 'name'
            if ($name -eq 'MISSING NAME' -and $referenceEntries.ContainsKey($id)) {
                $referenceName = Get-DirectStringValue $referenceEntries[$id] 'name'
                if (![string]::IsNullOrWhiteSpace($referenceName) -and $referenceName -notlike 'MISSING*') {
                    $replacement = Normalize-CanonicalTerms $referenceName -NameField
                    if (Set-DirectStringValue $entry 'name' $replacement) {
                        Record-Change $fileName $id 'name' $name $replacement 'mapleroot-missing-name-reference'
                    }
                }
            }
        }

        if ($fileName -ne 'Consume.img.xml') { continue }

        if ($id.StartsWith('204')) {
            $info = Get-ItemInfoNode $consumeItemDocument $id
            $success = Get-IntValue $info 'success'
            if ($null -eq $success) {
                $warnings.Add([pscustomobject]@{ code = 'scroll-missing-success'; file = $fileName; id = $id; detail = 'No Item.wz success value' })
                continue
            }

            $stockName = Get-DirectStringValue $entry 'name'
            $referenceName = if ($referenceEntries.ContainsKey($id)) { Get-DirectStringValue $referenceEntries[$id] 'name' } else { $null }
            $baseName = if (![string]::IsNullOrWhiteSpace($referenceName) -and $referenceName -notlike 'MISSING*') { $referenceName } else { $stockName }
            $cleanName = Normalize-CanonicalTerms $baseName -NameField -ScrollName -ScrollText
            $cleanName = $cleanName -replace '(?i)(?:\s+|^)(\d{1,3})%(?=\s|$)', ' '
            $cleanName = ($cleanName -replace '[ \t]{2,}', ' ').Trim()
            $cleanName = "$cleanName $success%"
            if (Set-DirectStringValue $entry 'name' $cleanName) {
                Record-Change $fileName $id 'name' $stockName $cleanName 'mapleroot-scroll-format-with-wz-rate'
            }

            $description = Get-DirectStringValue $entry 'desc'
            if ($null -ne $description) {
                $cleanDescription = Normalize-CanonicalTerms $description -ScrollText
                if ($cleanDescription -match '(?i)Success rate\s*:\s*\d+%') {
                    $cleanDescription = $cleanDescription -replace '(?i)Success rate\s*:\s*\d+%', "Success rate: $success%"
                } else {
                    $cleanDescription = "$cleanDescription\nSuccess rate: $success%"
                }
                $cleanDescription = Close-TrailingColorToken $cleanDescription
                if (Set-DirectStringValue $entry 'desc' $cleanDescription) {
                    Record-Change $fileName $id 'desc' $description $cleanDescription 'scroll-format-with-wz-rate'
                }
            }
        } elseif ($id.StartsWith('229')) {
            $info = Get-ItemInfoNode $masteryItemDocument $id
            $success = Get-IntValue $info 'success'
            $masterLevel = Get-IntValue $info 'masterLevel'
            $requiredLevel = Get-IntValue $info 'reqSkillLevel'
            if ($null -eq $success -or $null -eq $masterLevel) {
                $warnings.Add([pscustomobject]@{ code = 'mastery-missing-item-data'; file = $fileName; id = $id; detail = 'Missing success or masterLevel' })
                continue
            }

            $stockName = Get-DirectStringValue $entry 'name'
            $referenceName = if ($referenceEntries.ContainsKey($id)) { Get-DirectStringValue $referenceEntries[$id] 'name' } else { $null }
            $baseName = if (![string]::IsNullOrWhiteSpace($referenceName) -and $referenceName -notlike 'MISSING*') { $referenceName } else { $stockName }
            $cleanName = Normalize-CanonicalTerms $baseName -NameField
            $cleanName = ($cleanName -replace '\s+(20|30)$', '').Trim()
            $cleanName = "$cleanName $masterLevel"
            if (Set-DirectStringValue $entry 'name' $cleanName) {
                Record-Change $fileName $id 'name' $stockName $cleanName 'mapleroot-mastery-format-with-wz-level'
            }

            $description = Get-DirectStringValue $entry 'desc'
            if ($null -ne $description) {
                $skillMatch = [regex]::Match($description, '#c([^#]+)#')
                $jobMatch = [regex]::Match($description, '(?i)Job:\s*([^\\]+?)(?:\\n|$)')
                if ($skillMatch.Success) {
                    $skillName = $skillMatch.Groups[1].Value.Trim()
                    $job = if ($jobMatch.Success) { $jobMatch.Groups[1].Value.Trim() } else { 'See skill requirements' }
                    $requirement = if ($null -ne $requiredLevel) { "\nRequirement: Skill level above $requiredLevel" } else { '' }
                    $cleanDescription = "At a $success% success rate, it raises the Master Level of #c$skillName# to $masterLevel.\nJob: $job$requirement"
                    if (Set-DirectStringValue $entry 'desc' $cleanDescription) {
                        Record-Change $fileName $id 'desc' $description $cleanDescription 'mastery-format-with-wz-values'
                    }
                } else {
                    $warnings.Add([pscustomobject]@{ code = 'mastery-skill-name-unparsed'; file = $fileName; id = $id; detail = $description })
                }
            }
        }
    }

    if ($fileName -eq 'Consume.img.xml') {
        foreach ($nullNode in @($document.SelectNodes('//null'))) {
            $parent = $nullNode.ParentNode
            $id = if ($parent -is [System.Xml.XmlElement]) { $parent.GetAttribute('name') } else { '' }
            $before = [string] $nullNode.GetAttribute('name')
            [void] $parent.RemoveChild($nullNode)
            Record-Change $fileName $id '<null>' $before '' 'remove-stray-null-node'
        }
    }

    Write-XmlDocument $document $outputPath
}

$placeholderRows = [System.Collections.Generic.List[object]]::new()
foreach ($fileName in @('Cash.img.xml', 'Consume.img.xml', 'Eqp.img.xml', 'Etc.img.xml', 'Ins.img.xml')) {
    $document = Read-XmlDocument (Join-Path $outputRoot $fileName)
    foreach ($entry in @($document.SelectNodes('//imgdir[string[@name="name" and starts-with(@value,"MISSING NAME")]]'))) {
        $placeholderRows.Add([pscustomobject]@{ file = $fileName; id = [string] $entry.GetAttribute('name') })
    }
}

$changesPath = Join-Path $reportRoot 'string-changes.csv'
$placeholdersPath = Join-Path $reportRoot 'unresolved-placeholders.csv'
$warningsPath = Join-Path $reportRoot 'warnings.json'
$summaryPath = Join-Path $reportRoot 'summary.json'

$changes | Export-Csv -LiteralPath $changesPath -NoTypeInformation -Encoding UTF8
$placeholderRows | Export-Csv -LiteralPath $placeholdersPath -NoTypeInformation -Encoding UTF8
$warnings | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $warningsPath -Encoding UTF8

$fileHashes = foreach ($file in Get-ChildItem -LiteralPath $outputRoot -File -Filter '*.xml' | Sort-Object Name) {
    [ordered]@{
        file = $file.Name
        sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $file.FullName).Hash.ToLowerInvariant()
        bytes = $file.Length
    }
}

$summary = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString('o')
    stockStringRoot = $stockRoot
    mapleRootReference = $mapleRoot
    itemWzRoot = $itemRoot
    outputStringRoot = $outputRoot
    changedFields = $changes.Count
    changedByFile = @($changes | Group-Object file | Sort-Object Name | ForEach-Object { [ordered]@{ file = $_.Name; changes = $_.Count } })
    changedByReason = @($changes | Group-Object reason | Sort-Object Name | ForEach-Object { [ordered]@{ reason = $_.Name; changes = $_.Count } })
    unresolvedPlaceholders = $placeholderRows.Count
    warnings = $warnings.Count
    files = @($fileHashes)
    reports = [ordered]@{
        changes = $changesPath
        unresolvedPlaceholders = $placeholdersPath
        warnings = $warningsPath
    }
}
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding UTF8

Write-Output ($summary | ConvertTo-Json -Depth 8)
