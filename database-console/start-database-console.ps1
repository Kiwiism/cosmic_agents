$ErrorActionPreference = "Stop"

$databaseConsoleRoot = $PSScriptRoot
$projectRoot = Split-Path $databaseConsoleRoot -Parent
$envFile = Join-Path $databaseConsoleRoot ".env"
$nodeCommand = Get-Command node -ErrorAction SilentlyContinue
$npmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
$logRoot = Join-Path $databaseConsoleRoot ".runtime"

function Stop-ListeningProcesses {
    param(
        [int]$Port,
        [string]$CommandHint = $null,
        [string]$ProcessNameHint = $null
    )

    try {
        $listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        if (-not $listeners) { return }
        $pids = $listeners | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($pid in $pids) {
            $process = Get-CimInstance Win32_Process -Filter "ProcessId=$pid" -ErrorAction SilentlyContinue
            if (-not $process) { continue }
            $commandMatch = (-not [string]::IsNullOrWhiteSpace($CommandHint) -and $process.CommandLine -like "*$CommandHint*")
            $processMatch = (-not [string]::IsNullOrWhiteSpace($ProcessNameHint) -and $process.Name -like "*$ProcessNameHint*")
            if ($commandMatch -or $processMatch) {
                Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
            }
        }
    } catch {
        # ignore transient socket/process lookup failures during startup cleanup
    }
}

Stop-ListeningProcesses -Port 8081 -CommandHint "cosmic-database-console" -ProcessNameHint "java"
Stop-ListeningProcesses -Port 3000 -CommandHint "database-console\\web" -ProcessNameHint "node.exe"

if (-not $nodeCommand) {
    $bundledNode = Join-Path $env:USERPROFILE ".cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe"
    if (Test-Path -LiteralPath $bundledNode) {
        $nodeExecutable = $bundledNode
    } else {
        throw "Node.js 22+ is required to build and run the Database Console web application."
    }
} else {
    $nodeExecutable = $nodeCommand.Source
}

if (-not (Test-Path -LiteralPath $envFile)) {
    Copy-Item -LiteralPath (Join-Path $databaseConsoleRoot ".env.example") -Destination $envFile
    throw "Created database-console/.env. Add the MySQL password, then run this script again."
}

Get-Content -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#")) {
        $parts = $line.Split("=", 2)
        if ($parts.Count -eq 2) {
            [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1], "Process")
        }
    }
}

if ([string]::IsNullOrWhiteSpace($env:DATABASE_CONSOLE_DB_PASSWORD) -or
    [string]::IsNullOrWhiteSpace($env:GAME_DB_PASSWORD)) {
    throw "DATABASE_CONSOLE_DB_PASSWORD and GAME_DB_PASSWORD must be set in database-console/.env."
}

& (Join-Path $projectRoot "mvnw.cmd") -q -f (Join-Path $databaseConsoleRoot "api/pom.xml") package
if ($LASTEXITCODE -ne 0) {
    throw "Database Console API build failed."
}

Push-Location (Join-Path $databaseConsoleRoot "web")
try {
    if ($npmCommand) {
        & $npmCommand.Source install
        if ($LASTEXITCODE -ne 0) {
            throw "Database Console web dependency installation failed."
        }
        & $npmCommand.Source run build
    } elseif (Test-Path -LiteralPath "node_modules\next\dist\bin\next") {
        & $nodeExecutable "node_modules\next\dist\bin\next" build
    } else {
        throw "npm is unavailable and database-console/web/node_modules has not been installed."
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Database Console web build failed."
    }
} finally {
    Pop-Location
}

$logRoot = Join-Path $databaseConsoleRoot ".runtime"
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null

$apiJar = Join-Path $databaseConsoleRoot "api/target/cosmic-database-console-api-0.1.0-SNAPSHOT.jar"
$api = Start-Process -FilePath "java" `
    -ArgumentList @("-jar", "`"$apiJar`"") `
    -WorkingDirectory (Join-Path $databaseConsoleRoot "api") -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $logRoot "api.log") `
    -RedirectStandardError (Join-Path $logRoot "api-error.log")

$webRoot = Join-Path $databaseConsoleRoot "web"
$standaloneRoot = Join-Path $webRoot ".next/standalone"
Copy-Item -Path (Join-Path $webRoot ".next/static") -Destination (Join-Path $standaloneRoot ".next") `
    -Recurse -Force

$webServer = Join-Path $standaloneRoot "server.js"
$web = Start-Process -FilePath $nodeExecutable -ArgumentList "`"$webServer`"" `
    -WorkingDirectory $webRoot -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $logRoot "web.log") `
    -RedirectStandardError (Join-Path $logRoot "web-error.log")

Set-Content -LiteralPath (Join-Path $logRoot "api.pid") -Value $api.Id
Set-Content -LiteralPath (Join-Path $logRoot "web.pid") -Value $web.Id

Write-Host "Cosmic Database Console is starting at http://localhost:3000"
Write-Host "Runtime logs are stored in database-console/.runtime."
