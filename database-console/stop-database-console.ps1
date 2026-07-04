$runtime = Join-Path $PSScriptRoot ".runtime"

foreach ($name in "api", "web") {
    $pidFile = Join-Path $runtime "$name.pid"
    if (Test-Path -LiteralPath $pidFile) {
        $processId = [int](Get-Content -LiteralPath $pidFile)
        Stop-Process -Id $processId -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $pidFile -ErrorAction SilentlyContinue
    }
}

Write-Host "Cosmic Database Console processes are stopped."
