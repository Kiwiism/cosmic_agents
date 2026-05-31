$ErrorActionPreference = 'SilentlyContinue'
$in = [Console]::In.ReadToEnd()
# Fire only when the tool input actually reaches into a wz/*.wz/ tree (raw WZ access).
if ($in -match '\.wz[\\/]') {
    $ctx = 'WZ data access detected. Follow the wz-data skill (.claude/skills/wz-data/SKILL.md) before parsing wz/*.img.xml: the files are one giant line (regex-slice, do not Read whole), keys like range/mobCount/damage are per-level (read the specific level/N block), single-target skills often omit range/lt/rb, and prefer loaded Skill/StatEffect objects over reimplementing isBuff.'
    $out = @{ hookSpecificOutput = @{ hookEventName = 'PreToolUse'; additionalContext = $ctx } } | ConvertTo-Json -Compress
    Write-Output $out
}
exit 0
