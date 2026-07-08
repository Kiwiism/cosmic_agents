param(
    [switch] $SummaryOnly,
    [switch] $Json
)

$ErrorActionPreference = "Stop"

function Add-Check {
    param(
        [System.Collections.Generic.List[object]] $Checks,
        [string] $Id,
        [string] $Status,
        [string] $Message
    )

    [void] $Checks.Add([ordered]@{
        id = $Id
        status = $Status
        message = $Message
    })
}

$repoRootText = (& git rev-parse --show-toplevel 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse --show-toplevel failed: $repoRootText"
}

$repoRoot = (Get-Item -LiteralPath $repoRootText.Trim()).FullName
Set-Location -LiteralPath $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()
$serverPath = "src/main/java/net/server/Server.java"
$bridgePath = "src/main/java/net/server/admin/DatabaseConsoleBridgeServer.java"

if (!(Test-Path -LiteralPath $serverPath -PathType Leaf)) {
    Add-Check $checks "bridge:server-source" "FAIL" "Missing $serverPath."
} else {
    $serverText = Get-Content -LiteralPath $serverPath -Raw
    if ($serverText -match 'COSMIC_DATABASE_CONSOLE_BRIDGE_ENABLED') {
        Add-Check $checks "bridge:env-name" "PASS" "Server declares the Database Console bridge control environment variable."
    } else {
        Add-Check $checks "bridge:env-name" "FAIL" "Server does not declare COSMIC_DATABASE_CONSOLE_BRIDGE_ENABLED."
    }

    if ($serverText -match 'if\s*\(\s*!isDatabaseConsoleBridgeEnabled\s*\(\s*\)\s*\)') {
        Add-Check $checks "bridge:disable-gate" "PASS" "Server checks the bridge control flag before starting the bridge."
    } else {
        Add-Check $checks "bridge:disable-gate" "FAIL" "Server does not gate bridge startup behind isDatabaseConsoleBridgeEnabled()."
    }

    if ($serverText -match 'return\s+true;' -and $serverText -match '"false"\.equalsIgnoreCase\(value\)' -and $serverText -match '"0"\.equals\(value\)' -and $serverText -match '"no"\.equalsIgnoreCase\(value\)') {
        Add-Check $checks "bridge:default-enabled-explicit-disable" "PASS" "Bridge is default-enabled for local admin use and accepts explicit false/0/no disable values."
    } else {
        Add-Check $checks "bridge:default-enabled-explicit-disable" "FAIL" "Bridge default/disable policy does not match the current local-admin model."
    }
}

if (!(Test-Path -LiteralPath $bridgePath -PathType Leaf)) {
    Add-Check $checks "bridge:bridge-source" "FAIL" "Missing $bridgePath."
} else {
    $bridgeText = Get-Content -LiteralPath $bridgePath -Raw
    if ($bridgeText -match 'new InetSocketAddress\("127\.0\.0\.1", port\)') {
        Add-Check $checks "bridge:loopback-only" "PASS" "Bridge binds to loopback only."
    } else {
        Add-Check $checks "bridge:loopback-only" "FAIL" "Bridge is not constrained to 127.0.0.1."
    }

    if ($bridgeText -match 'Authorization' -and $bridgeText -match 'Bearer ') {
        Add-Check $checks "bridge:token-required" "PASS" "Bridge requires a bearer token."
    } else {
        Add-Check $checks "bridge:token-required" "FAIL" "Bridge does not require a bearer token."
    }
}

$failCount = @($checks | Where-Object { $_.status -eq "FAIL" }).Count
$passCount = @($checks | Where-Object { $_.status -eq "PASS" }).Count
$status = if ($failCount -gt 0) { "FAIL" } else { "PASS" }

$report = [ordered]@{
    status = $status
    passCount = $passCount
    failCount = $failCount
    failureIds = @($checks | Where-Object { $_.status -eq "FAIL" } | ForEach-Object { $_.id })
    summaryOnly = [bool] $SummaryOnly
    rowsOmitted = [bool] $SummaryOnly
    returnedCheckCount = if ($SummaryOnly) { 0 } else { @($checks).Count }
    checks = if ($SummaryOnly) { $null } else { @($checks) }
}

if ($Json) {
    $report | ConvertTo-Json -Depth 8
} else {
    Write-Host "Database Console bridge default-state verifier: $status"
    Write-Host "Passes: $passCount  Failures: $failCount"
    if (!$SummaryOnly) {
        foreach ($check in $checks) {
            Write-Host ("[{0}] {1} - {2}" -f $check.status, $check.id, $check.message)
        }
    }
}

if ($failCount -gt 0) {
    exit 1
}
