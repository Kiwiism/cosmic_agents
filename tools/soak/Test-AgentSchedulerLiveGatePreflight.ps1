param(
    [string] $ExpectedBranch = "feature/agent-central-scheduler-runtime",
    [ValidateSet("legacy", "central-sequential", "central-sharded")]
    [string] $SchedulerMode = "central-sharded",
    [string] $ExpectedDatabaseName,
    [string] $RuntimeOutputRoot = (Join-Path $env:TEMP "cosmic-agent-scheduler-live-gate"),
    [int[]] $ServerPorts = @(8484, 7575, 7576, 7577, 8787),
    [ValidateRange(0, 2000)]
    [int] $MinimumTargetAgents = 0,
    [switch] $AllowConfigOverride,
    [switch] $AllowClientLaunchAfterServer,
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Add-Check {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [Parameter(Mandatory = $true)] [string] $Id,
        [Parameter(Mandatory = $true)] [string] $Status,
        [Parameter(Mandatory = $true)] [string] $Message
    )

    $Checks.Add([ordered]@{
        id = $Id
        status = $Status
        message = $Message
    }) | Out-Null
}

function Get-ConfiguredDatabaseName {
    param([string] $ConfigPath)

    $configText = Get-Content -LiteralPath $ConfigPath -Raw
    $urlMatch = [regex]::Match($configText, '(?im)^\s*DB_URL_FORMAT\s*:\s*["''](?<url>[^"'']+)["'']')
    if (!$urlMatch.Success) {
        return $null
    }
    $databaseMatch = [regex]::Match($urlMatch.Groups['url'].Value, '/(?<database>[^/?]+)(?:\?|$)')
    if (!$databaseMatch.Success) {
        return $null
    }
    return $databaseMatch.Groups['database'].Value
}

function Get-GitStatusPath {
    param([string] $StatusLine)

    if ([string]::IsNullOrWhiteSpace($StatusLine) -or $StatusLine.Length -lt 4) {
        return $null
    }
    return $StatusLine.Substring(3).Trim()
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot
$checks = [System.Collections.Generic.List[object]]::new()

$branch = (& git branch --show-current 2>&1).Trim()
if ($LASTEXITCODE -ne 0) {
    Add-Check $checks "git:branch" "FAIL" "Unable to read the current branch."
} elseif ($branch -eq $ExpectedBranch) {
    Add-Check $checks "git:branch" "PASS" "Current branch is $branch."
} else {
    Add-Check $checks "git:branch" "FAIL" "Current branch is $branch; expected $ExpectedBranch."
}

$gitStatus = @(& git status --porcelain 2>&1)
$singlePendingPath = if ($gitStatus.Count -eq 1) {
    Get-GitStatusPath $gitStatus[0]
} else {
    $null
}
if ($LASTEXITCODE -ne 0) {
    Add-Check $checks "git:clean" "FAIL" "Unable to read worktree status."
} elseif ($gitStatus.Count -eq 0) {
    Add-Check $checks "git:clean" "PASS" "Worktree is clean."
} elseif ($AllowConfigOverride -and $singlePendingPath -eq "config.yaml") {
    Add-Check $checks "git:config-override" "PASS" "Only the explicit local config.yaml soak override is pending."
} else {
    Add-Check $checks "git:clean" "FAIL" "Worktree has $($gitStatus.Count) pending path(s)."
}

$wzPath = Join-Path $repoRoot "wz"
$wzTarget = $null
if (!(Test-Path -LiteralPath $wzPath -PathType Container)) {
    Add-Check $checks "wz:junction" "FAIL" "WZ directory is missing."
} else {
    $wzItem = Get-Item -LiteralPath $wzPath
    $wzTarget = [string] $wzItem.Target
    if (($wzItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) {
        Add-Check $checks "wz:junction" "FAIL" "WZ path is not the expected shared directory junction."
    } elseif ([string]::IsNullOrWhiteSpace($wzTarget)) {
        Add-Check $checks "wz:junction" "FAIL" "WZ junction target could not be resolved."
    } else {
        Add-Check $checks "wz:junction" "PASS" "WZ junction target is $wzTarget; treat it as read-only."
    }
}

$artifactPath = Join-Path $repoRoot "target\Cosmic.jar"
if (Test-Path -LiteralPath $artifactPath -PathType Leaf) {
    Add-Check $checks "build:artifact" "PASS" "Found packaged server artifact target/Cosmic.jar."
} else {
    Add-Check $checks "build:artifact" "FAIL" "Missing target/Cosmic.jar; run mvnw.cmd -q -DskipTests package."
}

$configPath = Join-Path $repoRoot "config.yaml"
$databaseName = $null
if (!(Test-Path -LiteralPath $configPath -PathType Leaf)) {
    Add-Check $checks "database:config" "FAIL" "config.yaml is missing."
} else {
    $databaseName = Get-ConfiguredDatabaseName $configPath
    if ([string]::IsNullOrWhiteSpace($databaseName)) {
        Add-Check $checks "database:config" "FAIL" "Could not determine the configured database name."
    } elseif ($databaseName -eq "cosmic") {
        Add-Check $checks "database:disposable" "FAIL" "Configured database is cosmic, not a disposable soak database."
    } elseif ([string]::IsNullOrWhiteSpace($ExpectedDatabaseName)) {
        Add-Check $checks "database:expected-name" "FAIL" "Pass -ExpectedDatabaseName to pin the disposable soak database."
    } elseif ($databaseName -ne $ExpectedDatabaseName) {
        Add-Check $checks "database:expected-name" "FAIL" "Configured database is $databaseName; expected $ExpectedDatabaseName."
    } else {
        Add-Check $checks "database:disposable" "PASS" "Configured database matches the explicitly pinned soak database $databaseName."
    }
}

$resolvedRuntimeRoot = [IO.Path]::GetFullPath($RuntimeOutputRoot)
$repoPrefix = $repoRoot.TrimEnd('\') + '\'
if ($resolvedRuntimeRoot.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    Add-Check $checks "runtime:external-root" "FAIL" "Runtime output root must be outside the worktree: $resolvedRuntimeRoot."
} else {
    Add-Check $checks "runtime:external-root" "PASS" "Runtime output is redirected outside the worktree: $resolvedRuntimeRoot."
}

$listeners = @(
    Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -in $ServerPorts }
)
if ($listeners.Count -eq 0) {
    Add-Check $checks "ports:free" "PASS" "Login, channel, and diagnostics ports are free."
} else {
    $busyPorts = @($listeners | Select-Object -ExpandProperty LocalPort -Unique | Sort-Object)
    Add-Check $checks "ports:free" "FAIL" "Required port(s) already listening: $($busyPorts -join ', ')."
}

$clients = @(Get-Process -Name "MapleStory" -ErrorAction SilentlyContinue)
if ($clients.Count -gt 0) {
    Add-Check $checks "client:available" "PASS" "Found $($clients.Count) MapleStory client process(es)."
} elseif ($AllowClientLaunchAfterServer) {
    Add-Check $checks "client:launch-after-server" "PASS" "No client is running; explicit launch-after-server mode is enabled."
} else {
    Add-Check $checks "client:available" "FAIL" "No MapleStory client process is running for visible parity validation."
}

$populationFile = Join-Path $resolvedRuntimeRoot "population.json"
$populationManagedAgents = $null
$populationConfiguredTarget = $null
if ($MinimumTargetAgents -gt 0) {
    if (!(Test-Path -LiteralPath $populationFile -PathType Leaf)) {
        Add-Check $checks "population:file" "FAIL" "Population file is required for a $MinimumTargetAgents-Agent stage: $populationFile."
    } else {
        try {
            $population = Get-Content -LiteralPath $populationFile -Raw | ConvertFrom-Json
            $requiredProperties = @("enabled", "multiplier", "agents")
            $missingProperties = @(
                $requiredProperties |
                    Where-Object { $population.PSObject.Properties.Name -notcontains $_ }
            )
            if ($missingProperties.Count -gt 0) {
                Add-Check $checks "population:shape" "FAIL" "Population file is missing: $($missingProperties -join ', ')."
            } else {
                $records = @($population.agents)
                $invalidRecords = @(
                    $records |
                        Where-Object {
                            $null -eq $_ -or
                            $_.PSObject.Properties.Name -notcontains "characterId" -or
                            $_.PSObject.Properties.Name -notcontains "name" -or
                            [int] $_.characterId -le 0 -or
                            [string]::IsNullOrWhiteSpace([string] $_.name)
                        }
                )
                if ($invalidRecords.Count -gt 0) {
                    Add-Check $checks "population:shape" "FAIL" "Population file contains $($invalidRecords.Count) invalid Agent record(s)."
                } else {
                    Add-Check $checks "population:shape" "PASS" "Population file contains structurally valid Agent records."
                }

                $duplicateIds = @(
                    $records |
                        Group-Object -Property characterId |
                        Where-Object { $_.Count -gt 1 }
                )
                $duplicateNames = @(
                    $records |
                        ForEach-Object { ([string] $_.name).ToLowerInvariant() } |
                        Group-Object |
                        Where-Object { $_.Count -gt 1 }
                )
                if ($duplicateIds.Count -gt 0 -or $duplicateNames.Count -gt 0) {
                    Add-Check $checks "population:unique" "FAIL" "Population file has duplicate character ids or case-insensitive names."
                } else {
                    Add-Check $checks "population:unique" "PASS" "Population character ids and names are unique."
                }

                $populationManagedAgents = $records.Count
                $multiplier = [double] $population.multiplier
                $validMultiplier = ![double]::IsNaN($multiplier) -and
                        ![double]::IsInfinity($multiplier) -and
                        $multiplier -ge 0.0 -and
                        $multiplier -le 100.0
                if ($population.enabled -isnot [bool] -or !$population.enabled) {
                    Add-Check $checks "population:target" "FAIL" "Population scheduling must be enabled for a populated soak stage."
                } elseif (!$validMultiplier) {
                    Add-Check $checks "population:target" "FAIL" "Population multiplier must be finite and between 0 and 100."
                } else {
                    $populationConfiguredTarget = [int] [Math]::Min(
                        $populationManagedAgents,
                        [Math]::Floor($populationManagedAgents * $multiplier))
                    if ($populationConfiguredTarget -lt $MinimumTargetAgents) {
                        Add-Check $checks "population:target" "FAIL" "Population target is $populationConfiguredTarget; stage requires at least $MinimumTargetAgents."
                    } else {
                        Add-Check $checks "population:target" "PASS" "Population target $populationConfiguredTarget satisfies the $MinimumTargetAgents-Agent stage."
                    }
                }
            }
        } catch {
            Add-Check $checks "population:parse" "FAIL" "Population file could not be parsed: $($_.Exception.Message)"
        }
    }
}
$navigationCache = Join-Path $resolvedRuntimeRoot "navigation-cache"
$jvmArguments = @(
    "-Dagents.scheduler.mode=$SchedulerMode",
    "-Dagents.scheduler.shardCount=4",
    "-Dagents.scheduler.baseTickMs=50",
    "-Dagents.scheduler.simulation.enabled=false",
    "-Dagents.scheduler.simulation.backgroundAbstract.enabled=false",
    "-Dagents.scheduler.tickSlicing.enabled=false",
    "-Dagents.scheduler.loadShedding.enabled=false",
    "-Dagents.scheduler.logSlowTicks=true",
    "-Dagents.navigation.cacheDir=$navigationCache"
)

$failCount = @($checks | Where-Object { $_.status -eq "FAIL" }).Count
$warnCount = @($checks | Where-Object { $_.status -eq "WARN" }).Count
$overall = if ($failCount -gt 0) {
    "FAIL"
} elseif ($warnCount -gt 0) {
    "INCOMPLETE"
} else {
    "PASS"
}

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToString("o")
    status = $overall
    repoRoot = $repoRoot
    branch = $branch
    schedulerMode = $SchedulerMode
    databaseName = $databaseName
    runtimeOutputRoot = $resolvedRuntimeRoot
    populationFile = $populationFile
    minimumTargetAgents = $MinimumTargetAgents
    populationManagedAgents = $populationManagedAgents
    populationConfiguredTarget = $populationConfiguredTarget
    wzTarget = $wzTarget
    failCount = $failCount
    warnCount = $warnCount
    checkCount = @($checks).Count
    passCount = @($checks | Where-Object { $_.status -eq "PASS" }).Count
    failureIds = @($checks | Where-Object { $_.status -eq "FAIL" } | ForEach-Object { $_.id })
    warningIds = @($checks | Where-Object { $_.status -eq "WARN" } | ForEach-Object { $_.id })
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    returnedCheckCount = if ($SummaryOnly) { 0 } else { @($checks).Count }
    checks = if ($SummaryOnly) { $null } else { @($checks) }
    environment = [ordered]@{
        COSMIC_AGENT_POPULATION_FILE = $populationFile
    }
    jvmArguments = $jvmArguments
}

if ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    Write-Host "Agent scheduler live-gate preflight: $overall"
    Write-Host "Failures: $failCount  Warnings: $warnCount"
    Write-Host ""
    if ($SummaryOnly) {
        Write-Host "Detailed check rows omitted."
    } else {
        foreach ($check in $checks) {
            Write-Host ("[{0}] {1} - {2}" -f $check.status, $check.id, $check.message)
        }
    }
    Write-Host ""
    Write-Host "Set COSMIC_AGENT_POPULATION_FILE=$populationFile"
    Write-Host "JVM arguments:"
    foreach ($argument in $jvmArguments) {
        Write-Host "  $argument"
    }
}

if ($failCount -gt 0) {
    exit 1
}
