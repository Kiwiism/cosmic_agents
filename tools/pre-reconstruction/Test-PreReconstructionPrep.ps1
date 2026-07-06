param(
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

function Get-GitOutput {
    param([string[]] $Arguments)

    $output = & git @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed: $output"
    }

    return @($output)
}

function Test-PathEntry {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [string] $Path,
        [string] $Description
    )

    if (Test-Path -LiteralPath $Path) {
        Add-Check $Checks "artifact:$Path" "PASS" "Found $Description at $Path."
    } else {
        Add-Check $Checks "artifact:$Path" "FAIL" "Missing $Description at $Path."
    }
}

function Invoke-BaselineEvidenceVerifier {
    param([string] $RunPath)

    $verifierPath = Join-Path $repoRoot "tools/soak/Test-BaselineSoakEvidencePackage.ps1"
    $json = & powershell -ExecutionPolicy Bypass -File $verifierPath -RunPath $RunPath -Json 2>&1
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        return [ordered]@{
            status = "FAIL"
            failCount = 1
            warnCount = 0
            message = ($json -join "`n")
        }
    }

    return ($json | ConvertFrom-Json)
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()

$requiredArtifacts = @(
    @{ path = "docs/agents/PRE_RECONSTRUCTION_SAFE_PREP_STATUS.md"; description = "safe-prep status map" },
    @{ path = "docs/agents/PRE_RECONSTRUCTION_COMPLETION_AUDIT.md"; description = "completion/evidence audit" },
    @{ path = "docs/agents/PRE_RECONSTRUCTION_BASELINE_SOAK_RUNBOOK.md"; description = "baseline soak runbook" },
    @{ path = "docs/agents/PRE_RECONSTRUCTION_GOAL_PROMPT.md"; description = "reusable goal prompt" },
    @{ path = "docs/agents/PACKAGE_REGISTRY.md"; description = "Agent package registry" },
    @{ path = "docs/agents/POST_RECONSTRUCTION_AGENT_PLATFORM_SPECIFICATION.md"; description = "post-reconstruction platform specification" },
    @{ path = "docs/agents/MAPLE_ISLAND_MVP_HANDOFF.md"; description = "Maple Island MVP handoff" },
    @{ path = "docs/agents/MAPLE_ISLAND_MVP_SEQUENCE.md"; description = "Maple Island MVP quest sequence" },
    @{ path = "docs/agents/plans/maple-island-mvp.plan.json"; description = "Maple Island MVP plan card" },
    @{ path = "docs/agents/catalog-platform/CATALOG_PLATFORM_ARCHITECTURE.md"; description = "catalog platform architecture" },
    @{ path = "docs/agents/catalog-platform/CATALOG_BUNDLE_SPEC.md"; description = "catalog bundle specification" },
    @{ path = "docs/agents/catalog-platform/CATALOG_QUERY_API.md"; description = "catalog query API" },
    @{ path = "docs/agents/plan-runtime/PLAN_RUNTIME_DESIGN_SPECIFICATION.md"; description = "plan runtime design specification" },
    @{ path = "docs/agents/plan-runtime/PLAN_RUNTIME_TECHNICAL_SPECIFICATION.md"; description = "plan runtime technical specification" },
    @{ path = "docs/agents/capability-runtime/CAPABILITY_RUNTIME_DESIGN_SPECIFICATION.md"; description = "capability runtime design specification" },
    @{ path = "docs/agents/capability-runtime/CAPABILITY_RUNTIME_TECHNICAL_SPECIFICATION.md"; description = "capability runtime technical specification" },
    @{ path = "docs/agents/npc-quest-capability/NPC_QUEST_CAPABILITY_DESIGN_SPECIFICATION.md"; description = "NPC quest capability design specification" },
    @{ path = "docs/agents/npc-quest-capability/NPC_QUEST_CAPABILITY_TECHNICAL_SPECIFICATION.md"; description = "NPC quest capability technical specification" },
    @{ path = "docs/agents/profile-platform/AGENT_PROFILE_SYSTEM_DESIGN_SPECIFICATION.md"; description = "Agent profile design specification" },
    @{ path = "docs/agents/profile-platform/AGENT_PROFILE_SYSTEM_TECHNICAL_SPECIFICATION.md"; description = "Agent profile technical specification" },
    @{ path = "docs/agents/llm-autonomy/ECONOMY_DESIGN_SPECIFICATION.md"; description = "economy design specification" },
    @{ path = "docs/agents/llm-autonomy/ECONOMY_TECHNICAL_IMPLEMENTATION_SPECIFICATION.md"; description = "economy technical specification" },
    @{ path = "docs/agents/simulation-tier-runtime/AGENT_SIMULATION_TIER_DESIGN_SPECIFICATION.md"; description = "simulation tier design specification" },
    @{ path = "docs/agents/simulation-tier-runtime/AGENT_SIMULATION_TIER_TECHNICAL_SPECIFICATION.md"; description = "simulation tier technical specification" },
    @{ path = "docs/agents/server-adapter/SERVER_ADAPTER_CONTRACT.md"; description = "server adapter contract" },
    @{ path = "docs/agents/server-adapter/MINIMAL_COSMIC_EDIT_INSTALL_TARGET.md"; description = "minimal Cosmic edit target" },
    @{ path = "docs/agents/server-adapter/PORTABLE_INSTALLER_TECHNICAL_SPECIFICATION.md"; description = "portable installer technical specification" },
    @{ path = "docs/consoles/DATABASE_CONSOLE_INFORMATION_ARCHITECTURE.md"; description = "Database Console information architecture" },
    @{ path = "docs/consoles/DATABASE_CONSOLE_UI_DESIGN.md"; description = "Database Console UI design" },
    @{ path = "docs/consoles/SERVER_CONSOLE_SCOPE.md"; description = "Server Console scope" },
    @{ path = "docs/NUTNNUT_OVER_COSMIC_REVIEW.md"; description = "NuTNNuT-over-Cosmic review" },
    @{ path = "docs/COSMIC_REVERT_REVIEW.md"; description = "Cosmic revert review" },
    @{ path = "docs/SERVER_SCALE_TODO.md"; description = "server scale TODO" },
    @{ path = "docs/SERVER_PLAYER_SCALE_IMPLEMENTATION_PLAN.md"; description = "server player-scale implementation plan" },
    @{ path = "tools/soak/New-BaselineSoakEvidencePackage.ps1"; description = "baseline soak evidence scaffold" },
    @{ path = "tools/soak/Test-BaselineSoakEvidencePackage.ps1"; description = "baseline soak evidence verifier" },
    @{ path = "tools/soak/Add-BaselineSoakSample.ps1"; description = "baseline soak sample appender" },
    @{ path = "tools/soak/Update-BaselineSoakSummary.ps1"; description = "baseline soak summary updater" },
    @{ path = "tools/soak/New-BaselineSoakAuditEntry.ps1"; description = "baseline soak audit-entry generator" }
)

foreach ($artifact in $requiredArtifacts) {
    Test-PathEntry $checks $artifact.path $artifact.description
}

$stagedForbiddenPaths = @(
    "src/main/java/server/agents",
    "src/main/java/server/bots",
    "src/test/java/server/agents",
    "src/test/java/server/bots",
    "config.yaml",
    "src/main/resources/config.yaml"
)

foreach ($path in $stagedForbiddenPaths) {
    $matches = @(Get-GitOutput @("diff", "--cached", "--name-only", "--", $path))
    if ($matches.Count -eq 0) {
        Add-Check $checks "staged-forbidden:$path" "PASS" "No staged changes under $path."
    } else {
        Add-Check $checks "staged-forbidden:$path" "FAIL" "Forbidden staged changes found under $path."
    }
}

$unstagedForbiddenPaths = @(
    "src/main/java/server/agents",
    "src/main/java/server/bots",
    "config.yaml",
    "src/main/resources/config.yaml"
)

foreach ($path in $unstagedForbiddenPaths) {
    $matches = @(Get-GitOutput @("diff", "--name-only", "--", $path))
    if ($matches.Count -eq 0) {
        Add-Check $checks "unstaged-forbidden:$path" "PASS" "No unstaged changes under $path."
    } else {
        Add-Check $checks "unstaged-forbidden:$path" "WARN" "Unstaged changes found under $path; verify they are intentional."
    }
}

$baselineRoot = "logs/soak/baseline"
if (Test-Path -LiteralPath $baselineRoot) {
    $runFolders = @(Get-ChildItem -LiteralPath $baselineRoot -Directory -ErrorAction SilentlyContinue)
    if ($runFolders.Count -gt 0) {
        Add-Check $checks "soak:baseline-folder" "PASS" "Found $($runFolders.Count) baseline evidence folder(s)."

        $latestRun = $runFolders | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        $baselineReport = Invoke-BaselineEvidenceVerifier $latestRun.FullName

        if ($baselineReport.status -eq "PASS") {
            Add-Check $checks "soak:latest-baseline-evidence" "PASS" "Latest baseline evidence run $($latestRun.Name) verifies as PASS."
        } elseif ($baselineReport.status -eq "FAIL") {
            Add-Check $checks "soak:latest-baseline-evidence" "FAIL" "Latest baseline evidence run $($latestRun.Name) verifies as FAIL."
        } else {
            Add-Check $checks "soak:latest-baseline-evidence" "WARN" "Latest baseline evidence run $($latestRun.Name) is $($baselineReport.status) with $($baselineReport.warnCount) warning(s)."
        }
    } else {
        Add-Check $checks "soak:baseline-folder" "WARN" "Baseline evidence root exists but has no run folders."
    }
} else {
    Add-Check $checks "soak:baseline-folder" "WARN" "No baseline evidence folder exists yet; collect runtime evidence when ready."
}

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
    status = $overall
    repoRoot = $repoRoot
    failCount = $failCount
    warnCount = $warnCount
    checks = @($checks)
}

if ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    Write-Host "Pre-reconstruction prep verification: $overall"
    Write-Host "Repo root: $repoRoot"
    Write-Host "Failures: $failCount  Warnings: $warnCount"
    Write-Host ""

    foreach ($check in $checks) {
        Write-Host ("[{0}] {1} - {2}" -f $check.status, $check.id, $check.message)
    }
}

if ($failCount -gt 0) {
    exit 1
}
