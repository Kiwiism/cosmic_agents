$runtime = Join-Path $PSScriptRoot ".runtime"

function Stop-ByPidFile {
    param([string]$Name)
    $pidFile = Join-Path $runtime "$Name.pid"
    if (-not (Test-Path -LiteralPath $pidFile)) {
        return
    }
    try {
        $processId = [int](Get-Content -LiteralPath $pidFile -ErrorAction Stop)
        Stop-Process -Id $processId -ErrorAction SilentlyContinue
    } catch {
        # pid file may be stale or malformed; continue
    } finally {
        Remove-Item -LiteralPath $pidFile -ErrorAction SilentlyContinue
    }
}

function Stop-MatchingProcesses {
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
        # ignore transient races
    }
}

Stop-ByPidFile -Name "api"
Stop-ByPidFile -Name "web"

Stop-MatchingProcesses -Port 8081 -CommandHint "cosmic-database-console" -ProcessNameHint "java"
Stop-MatchingProcesses -Port 3000 -CommandHint "database-console\\web" -ProcessNameHint "node.exe"

foreach ($name in "api", "web") {
    Write-Host "Checking old $name process..."
}

Write-Host "Cosmic Database Console processes are stopped."
