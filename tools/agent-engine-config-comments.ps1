param(
    [string]$Path = "agent-engine.yaml"
)

$ErrorActionPreference = "Stop"

$unitTokens = @(
    "MS", "PX", "PERCENT", "RATIO", "FACTOR", "MULTIPLIER", "WEIGHT"
)

$wordReplacements = @{
    "acc" = "accuracy"
    "ack" = "acknowledgement"
    "afk" = "away-from-keyboard"
    "aggro" = "aggression"
    "aoe" = "area-of-effect"
    "ap" = "ability point"
    "auto" = "automatic"
    "buff" = "buff"
    "cd" = "cooldown"
    "cpu" = "CPU"
    "crit" = "critical hit"
    "dps" = "damage-per-second"
    "exp" = "experience"
    "fh" = "foothold"
    "gm" = "game master"
    "hp" = "health"
    "hspeed" = "horizontal speed"
    "id" = "identifier"
    "ign" = "character name"
    "ingress" = "incoming work"
    "jvm" = "JVM"
    "kpq" = "Kerning Party Quest"
    "los" = "line-of-sight"
    "max" = "maximum"
    "min" = "minimum"
    "mob" = "monster"
    "mp" = "mana"
    "npc" = "NPC"
    "nx" = "NX"
    "pio" = "Pio"
    "px" = "pixels"
    "rng" = "random selection"
    "sp" = "skill point"
    "ttl" = "time-to-live"
    "vforce" = "vertical force"
    "x" = "horizontal"
    "y" = "vertical"
}

function Convert-CamelCaseToWords([string]$Text) {
    $words = $Text -replace '^Agent', ''
    $words = $words -creplace '([a-z0-9])([A-Z])', '$1 $2'
    $words = $words -creplace '([A-Z]+)([A-Z][a-z])', '$1 $2'
    return $words.ToLowerInvariant()
}

function Convert-SettingToWords([string]$Name) {
    $tokens = $Name.Split('_') | Where-Object {
        $_ -and $_ -notin $unitTokens -and $_ -notin @("AGENT", "DEFAULT")
    }
    $words = foreach ($token in $tokens) {
        $lower = $token.ToLowerInvariant()
        if ($wordReplacements.ContainsKey($lower)) {
            $wordReplacements[$lower]
        } else {
            $lower
        }
    }
    return ($words -join ' ')
}

function Get-OwnerLabel([string]$Owner) {
    if ($Owner -eq "AgentEngineConfig") {
        return "the Agent engine deployment configuration"
    }
    return "the `"$(Convert-CamelCaseToWords (($Owner -split '\.')[-1]))`" component"
}

function Get-ValueKind([string]$RawValue) {
    $value = $RawValue.Trim().Trim("'").Trim('"')
    if ($value -in @("true", "false")) {
        return "boolean"
    }
    $number = 0.0
    if ([double]::TryParse(
            $value,
            [System.Globalization.NumberStyles]::Float,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [ref]$number)) {
        if ($value -match '[.]') {
            return "decimal number"
        }
        return "integer"
    }
    return "text"
}

function Get-UnitAndDomain([string]$Name, [string]$RawValue) {
    $value = $RawValue.Trim().Trim("'").Trim('"')
    if ((Get-ValueKind $RawValue) -eq "boolean") {
        return "Domain: true or false"
    }
    if ($Name -match '(?:^|_)MS(?:_|$)') {
        return "Unit: milliseconds; use 0 only where the owning policy explicitly treats it as immediate or disabled"
    }
    if ($Name -match '(?:^|_)NS(?:_|$)') {
        return "Unit: nanoseconds; this is normally a profiling threshold rather than a gameplay duration"
    }
    if ($Name -match 'PXS2') {
        return "Unit: MapleStory map pixels per second squared"
    }
    if ($Name -match 'PXS') {
        return "Unit: MapleStory map pixels per second"
    }
    if ($Name -match 'BYTES') {
        return "Unit: bytes"
    }
    if ($Name -match '(?:^|_)PX(?:_|$)' -or $Name -match '(?:DISTANCE|RANGE|INSET|HEIGHT|WIDTH|RADIUS|ARRIVAL_X|ARRIVAL_Y|SEEK_RANGE|AHEAD)(?:_|$)') {
        return "Unit: MapleStory map pixels; horizontal and vertical values use the server map coordinate system"
    }
    if ($Name -match 'PERCENT') {
        return "Domain: percentage from 0 to 100 inclusive"
    }
    if ($Name -match 'RATIO') {
        return "Domain: decimal ratio; 0.0 means none and 1.0 means the full reference amount unless the owning policy documents a wider range"
    }
    if ($Name -match '(?:CHANCE|PROBABILITY)') {
        $number = 0.0
        if ([double]::TryParse(
                $value,
                [System.Globalization.NumberStyles]::Float,
                [System.Globalization.CultureInfo]::InvariantCulture,
                [ref]$number) -and $number -le 1.0) {
            return "Domain: probability from 0.0 to 1.0 inclusive"
        }
        return "Domain: percentage chance from 0 to 100 inclusive"
    }
    if ($Name -match '(?:WEIGHT|BONUS|PENALTY|SCORE|COST|FACTOR|MULTIPLIER)') {
        return "Domain: non-negative policy coefficient; it is meaningful relative to the other coefficients used by the same decision"
    }
    if ($Name -match '(?:COUNT|LIMIT|CAP|MAX|MIN|ATTEMPTS|RETRIES|STEPS|LEVEL|SIZE|TOTAL|RESERVE|BUDGET|THRESHOLD)') {
        return "Domain: non-negative count, capacity, or threshold unless the owning component documents a sentinel"
    }
    if ($Name -match '(?:ENABLED|DEBUG|LOGGING|TRACE|STRESS)') {
        return "Domain: true or false"
    }
    return "Domain: component-specific policy value; startup validation rejects missing, blank, or malformed values"
}

function Get-PurposeDescription([string]$Name) {
    $subject = Convert-SettingToWords $Name
    if ($Name -match 'ENABLED$') {
        return "Turns $subject on or off. Disable it to remove that Agent behavior without changing unrelated capabilities."
    }
    if ($Name -match '(?:MODE|POLICY)$') {
        return "Chooses how $subject works. Change it when selecting a different operating strategy rather than merely making the current strategy faster or slower."
    }
    if ($Name -match '(?:NAMES|PLAYER_NAMES)$') {
        return "Lists the character names used for $subject. Separate multiple names with commas; an empty value grants the role to nobody."
    }
    if ($Name -match 'NAME$') {
        return "Selects the character name used for $subject. The named Agent is the dedicated participant for that guarded showcase flow."
    }
    if ($Name -match 'TIMEOUT') {
        return "Sets how long the engine tolerates $subject before treating it as stalled, expired, or failed."
    }
    if ($Name -match '(?:INTERVAL|CADENCE|TICK)') {
        return "Sets how often $subject is evaluated or published. Lower values react more quickly but create more scheduler and packet work."
    }
    if ($Name -match '(?:TTL|_MS$)') {
        return "Sets the time allowance for $subject. Larger values keep the related state or wait active longer; smaller values make it expire or advance sooner."
    }
    if ($Name -match '(?:DELAY|WARMUP|COOLDOWN|HOLD|DWELL|DURATION|WINDOW|RETAIN)') {
        return "Controls the waiting or active time for $subject. Higher values make the state last longer; lower values make transitions happen sooner."
    }
    if ($Name -match '(?:CHANCE|PROBABILITY|PERCENT)') {
        return "Controls how much or how often $subject applies. Increase it to make the behavior more common or more influential."
    }
    if ($Name -match 'RATIO') {
        return "Sets the proportion used for $subject. Values nearer 1.0 require or represent more of the reference amount; values nearer 0.0 require less."
    }
    if ($Name -match 'DELTA') {
        return "Sets how much $subject changes after the named event. A larger delta makes the Agent's adaptive state react more strongly to each occurrence."
    }
    if ($Name -match 'WEIGHT') {
        return "Controls the relative preference for $subject. A larger weight makes it more likely when competing choices are otherwise available."
    }
    if ($Name -match 'PENALTY') {
        return "Controls how strongly $subject is discouraged. A larger penalty makes the associated choice less attractive."
    }
    if ($Name -match 'BONUS') {
        return "Controls how strongly $subject is favored. A larger bonus makes the associated choice more attractive."
    }
    if ($Name -match '(?:FACTOR|MULTIPLIER)') {
        return "Scales the effect of $subject. Values above the neutral value amplify it, while smaller values reduce its influence."
    }
    if ($Name -match 'DIVISOR') {
        return "Controls how strongly $subject is scaled down. A larger divisor softens the trait or score adjustment; a smaller divisor makes it more sensitive."
    }
    if ($Name -match '(?:BASE|INITIAL|CENTER)') {
        return "Sets the baseline for $subject before situational bonuses, penalties, or runtime adaptation are applied."
    }
    if ($Name -match '(?:PXS2|PXS|SPEED|FORCE|ACCEL|FRICTION|GRAVITY|VELOCITY|GROUNDSLIP)') {
        return "Controls the simulated movement strength for $subject. Changing it alters how quickly or sharply the entity moves."
    }
    if ($Name -match '(?:DISTANCE|RANGE|INSET|HEIGHT|WIDTH|RADIUS|ARRIVAL|AHEAD)') {
        return "Sets the spatial allowance for $subject. Larger values permit action across a wider or farther area."
    }
    if ($Name -match '(?:_PX$|_X$|_Y$|_DIST$|SPACING|GAP|TOLERANCE|MARGIN|TOP|BOTTOM|THICKNESS|OFFSET|EXTRA)') {
        return "Sets a map-space position, tolerance, or separation for $subject. Larger values allow more room before the owning movement or navigation rule reacts."
    }
    if ($Name -match '(?:MAX|LIMIT|CAP|TOTAL|BUDGET|RESERVE)') {
        return "Caps or reserves resources for $subject. Raising it permits more work or state, but can increase CPU, memory, or visible crowding."
    }
    if ($Name -match '(?:MIN|THRESHOLD)') {
        return "Sets the trigger or lower bound for $subject. The behavior begins or becomes eligible when this boundary is reached."
    }
    if ($Name -match '(?:THRESH|WARN|STOP|READY_DEPTH|PRESSURE_CYCLES|RECOVERY_CYCLES)') {
        return "Sets the trigger point for $subject. Crossing this boundary causes the owning policy to warn, stop, shed load, or enter recovery."
    }
    if ($Name -match '(?:ATTEMPTS|RETRIES)') {
        return "Sets how many times $subject may be attempted before the engine gives up or escalates recovery."
    }
    if ($Name -match '(?:COUNT|COMPONENTS|SLOTS|COLUMNS|SIZE|BYTES|PAGE|LEFTOVERS)') {
        return "Sets the amount or capacity used for $subject. Higher values process, retain, or arrange more entries in one operation."
    }
    if ($Name -match '(?:FIXED_HP|STAGE)') {
        return "Sets the fixed fallback or lifecycle position for $subject. The owning policy uses it when dynamic state cannot supply a more specific value."
    }
    if ($Name -match 'STAGGER') {
        return "Sets the separation applied to $subject so multiple Agents do not begin or repeat the same action in lockstep."
    }
    if ($Name -match '(?:COMMAND|MODE)') {
        return "Selects the internal command or operating value used for $subject. Change it only when the corresponding runtime or packet behavior is understood."
    }
    if ($Name -match '^(?:ENABLE_|RETURN_)') {
        return "Turns $subject on or off for the owning capability while leaving the rest of the Agent lifecycle unchanged."
    }
    if ($Name -match '(?:LOGGING|DEBUG|TRACE|DIAGNOSTIC|STRESS)') {
        return "Controls diagnostic behavior for $subject. Enable it for investigation or load testing and normally leave it off in routine production."
    }
    return "Controls $subject in the Agent engine. Change it only when tuning that named behavior or policy."
}

function Get-TechnicalDescription(
    [string]$Owner,
    [string]$Name,
    [string]$RawValue
) {
    $kind = Get-ValueKind $RawValue
    $article = if ($kind -eq "integer") { "an" } else { "a" }
    $ownerLabel = Get-OwnerLabel $Owner
    $domain = Get-UnitAndDomain $Name $RawValue
    $effect = if ($Name -match '(?:MIN|MAX)' -and $Name -match 'MS') {
        "Paired minimum/maximum values define an inclusive random or validation range and must not be inverted."
    } elseif ($Name -match 'ENABLED$') {
        "The disabled path must bypass only this feature and preserve the generic Agent lifecycle."
    } elseif ($Name -match 'DELTA') {
        "The owning adaptation state adds or subtracts this magnitude per event and clamps the result to its configured state bounds."
    } elseif ($Name -match 'DIVISOR') {
        "The owning score calculation divides by this value, so it must remain greater than zero."
    } elseif ($Name -match '(?:WEIGHT|PENALTY|BONUS|SCORE|COST)') {
        "The value participates in ranking or weighted selection and is not an absolute probability by itself."
    } elseif ($Name -match '(?:INTERVAL|CADENCE|TICK)') {
        "It governs recurring work, so lowering it increases invocation frequency and operational load."
    } elseif ($Name -match '(?:TIMEOUT|STALL|RECOVERY)') {
        "It participates in bounded failure handling and should remain long enough for the normal operation to complete."
    } else {
        "It is loaded once at server startup; changing the YAML requires a restart."
    }
    return "$ownerLabel consumes this as $article $kind. $domain. $effect"
}

$inputLines = Get-Content -LiteralPath $Path
$output = [System.Collections.Generic.List[string]]::new()

foreach ($line in $inputLines) {
    if ($line -match '^    # (?:Layman|Purpose|Technical):') {
        continue
    }
    if ($line -match '^    AGENT_([A-Z0-9_]+):\s*(.*)$') {
        $name = "AGENT_$($matches[1])"
        $value = $matches[2]
        $output.Add("    # Purpose: $(Get-PurposeDescription $name)")
        $output.Add("    # Technical: $(Get-TechnicalDescription 'AgentEngineConfig' $name $value)")
    } elseif ($line -match '^    (server\.agents\.(.+)\.([A-Z][A-Z0-9_]+)):\s*(.*)$') {
        $owner = "server.agents.$($matches[2])"
        $name = $matches[3]
        $value = $matches[4]
        $output.Add("    # Purpose: $(Get-PurposeDescription $name)")
        $output.Add("    # Technical: $(Get-TechnicalDescription $owner $name $value)")
    }
    $output.Add($line)
}

$temporaryPath = "$Path.tmp"
[System.IO.File]::WriteAllLines(
    (Join-Path (Get-Location) $temporaryPath),
    $output,
    [System.Text.UTF8Encoding]::new($false)
)
Move-Item -Force -LiteralPath $temporaryPath -Destination $Path
