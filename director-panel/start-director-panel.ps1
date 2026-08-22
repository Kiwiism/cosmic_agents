$ErrorActionPreference = "Stop"
$panelRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$token = [Environment]::GetEnvironmentVariable("COSMIC_DIRECTOR_TOKEN", "User")
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "COSMIC_DIRECTOR_TOKEN is not configured for this Windows user."
}
$env:COSMIC_DIRECTOR_TOKEN = $token
$port = [Environment]::GetEnvironmentVariable("COSMIC_DIRECTOR_PORT", "User")
if ([string]::IsNullOrWhiteSpace($port)) { $port = "8790" }
$env:DIRECTOR_BRIDGE_URL = "http://127.0.0.1:" + $port
Set-Location -LiteralPath $panelRoot
npm run dev
