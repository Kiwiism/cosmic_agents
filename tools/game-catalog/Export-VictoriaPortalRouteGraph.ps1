param(
    [string]$PortalGraphPath = "tmp/agent-llm-catalog/generated_portal_graph.json",
    [string]$OutputPath = "src/main/resources/agents/catalogs/victoria-portal-route-graph.json"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $PortalGraphPath)) {
    throw "Portal graph not found: $PortalGraphPath"
}

$source = Get-Content -Raw -LiteralPath $PortalGraphPath | ConvertFrom-Json
$edges = @($source |
    Where-Object {
        $_.fromMapId -ge 100000000 -and $_.fromMapId -lt 130000000 -and
        $_.toMapId -ge 100000000 -and $_.toMapId -lt 130000000 -and
        $_.fromMapId -ne $_.toMapId -and
        [string]::IsNullOrWhiteSpace($_.script)
    } |
    ForEach-Object {
        [pscustomobject]@{
            fromMapId = [int]$_.fromMapId
            toMapId = [int]$_.toMapId
        }
    } |
    Sort-Object fromMapId, toMapId -Unique)

$catalog = [ordered]@{
    schemaVersion = 1
    catalogId = "victoria-standard-portal-route-graph-v1"
    sourcePath = $PortalGraphPath.Replace('\', '/')
    sourceSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $PortalGraphPath).Hash.ToLowerInvariant()
    edges = $edges
}

$parent = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Force -Path $parent | Out-Null
$catalog | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 -LiteralPath $OutputPath

Write-Host "Exported $($edges.Count) standard Victoria portal edges to $OutputPath"
