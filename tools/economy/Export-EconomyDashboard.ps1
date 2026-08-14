param(
    [string] $RunId,
    [string] $OutputPath = "economy-dashboard/data/latest.json",
    [string] $Container = "economy-database-economy-postgres-1",
    [string] $Database = "cosmic_economy",
    [string] $User = "cosmic_economy"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RunId)) {
    $RunId = (& docker exec $Container psql -U $User -d $Database -Atc "SELECT run_id FROM simulation_run ORDER BY created_at DESC LIMIT 1;").Trim()
}
$parsed = [guid]::Empty
if (-not [guid]::TryParse($RunId, [ref] $parsed)) { throw "RunId must be a UUID." }
$RunId = $parsed.ToString()

function Invoke-EconomyQuery([string] $Sql) {
    $lines = @(& docker exec $Container psql -U $User -d $Database --csv -c $Sql)
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL query failed." }
    $rows = if ($lines.Count -le 1) { @() } else { @($lines | ConvertFrom-Csv) }
    return ,$rows
}

function Read-ItemNames([System.Collections.Generic.HashSet[int]] $Wanted) {
    $result = [ordered]@{}
    $files = @("Consume.img.xml", "Eqp.img.xml", "Etc.img.xml", "Ins.img.xml", "Cash.img.xml")
    foreach ($file in $files) {
        $path = Join-Path "wz/String.wz" $file
        if (-not (Test-Path -LiteralPath $path)) { continue }
        [xml] $document = Get-Content -Raw -LiteralPath $path
        foreach ($node in $document.SelectNodes("//imgdir[string[@name='name']]")) {
            $id = 0
            if (-not [int]::TryParse([string] $node.name, [ref] $id) -or -not $Wanted.Contains($id)) { continue }
            $name = $node.SelectSingleNode("string[@name='name']")
            if ($null -ne $name -and -not $result.Contains([string] $id)) {
                $result[[string] $id] = [string] $name.value
            }
        }
    }
    return $result
}

$run = @(Invoke-EconomyQuery @"
SELECT run_id, scenario_id, status, logical_started_at, logical_current_at, target_logical_at,
       seed, config_hash, catalog_version, created_at, completed_at, failure_reason
FROM simulation_run WHERE run_id = '$RunId';
"@)
if ($run.Count -lt 1) { throw "Economy run $RunId was not found." }

$data = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    run = $run[0]
    summary = (Invoke-EconomyQuery @"
SELECT
 (SELECT COUNT(*) FROM population_arrival WHERE run_id='$RunId') AS admitted_agents,
 (SELECT COUNT(*) FROM economic_event WHERE run_id='$RunId') AS economic_events,
 (SELECT COUNT(*) FROM economic_transaction WHERE run_id='$RunId') AS transactions,
 (SELECT COALESCE(SUM(gross_mesos),0) FROM economic_transaction WHERE run_id='$RunId') AS traded_mesos,
 (SELECT COUNT(*) FROM decision_journal WHERE run_id='$RunId') AS decisions,
 (SELECT COUNT(*) FROM market_stall WHERE run_id='$RunId') AS stalls,
 (SELECT COUNT(*) FROM market_observation WHERE run_id='$RunId') AS observations,
 (SELECT COUNT(*) FROM activity_session WHERE run_id='$RunId' AND status='COMPLETED') AS completed_activities,
 (SELECT COUNT(*) FROM economy_invariant_violation WHERE run_id='$RunId' AND resolved_at IS NULL) AS open_violations,
 (SELECT COALESCE(SUM(meso),0) FROM agent_state_projection WHERE run_id='$RunId'
    AND (agent_id,logical_time) IN (SELECT agent_id,MAX(logical_time) FROM agent_state_projection
                                   WHERE run_id='$RunId' GROUP BY agent_id)) AS agent_mesos;
"@)[0]
    mesoDaily = Invoke-EconomyQuery "SELECT logical_date,flow_kind,meso_amount,transaction_count FROM meso_flow_daily WHERE run_id='$RunId' ORDER BY logical_date,flow_kind;"
    itemDaily = Invoke-EconomyQuery "SELECT * FROM item_market_daily WHERE run_id='$RunId' ORDER BY logical_date,item_id;"
    transactions = Invoke-EconomyQuery "SELECT transaction_id,transaction_kind,buyer_id,seller_id,item_id,quantity,gross_mesos,tax_mesos,human_counterparty,logical_at,evidence,listing_id FROM economic_transaction WHERE run_id='$RunId' ORDER BY logical_at,transaction_id;"
    lots = Invoke-EconomyQuery "SELECT lot_id,item_id,source_kind,source_identifier,original_quantity,attributes,fingerprint FROM item_lot WHERE run_id='$RunId' ORDER BY item_id,lot_id;"
    stalls = Invoke-EconomyQuery "SELECT stall_id,seller_id,room_map_id,spot_x,opened_at,closed_at,close_reason FROM market_stall WHERE run_id='$RunId' ORDER BY opened_at,stall_id;"
    listings = Invoke-EconomyQuery "SELECT listing_id,stall_id,seller_id,room_map_id,item_id,lot_id,quantity_per_bundle,bundles_initial,bundles_remaining,bundle_price,opened_at,closed_at,close_reason,reprices FROM market_listing WHERE run_id='$RunId' ORDER BY opened_at,listing_id;"
    observations = Invoke-EconomyQuery "SELECT observation_id,agent_id,logical_time,room_map_id,stall_owner_id,item_id,quantity,unit_price,listing_id,observed_state,quantity_per_bundle,bundles,bundle_price,item_fingerprint,item_attributes FROM market_observation WHERE run_id='$RunId' ORDER BY logical_time,observation_id;"
    decisions = Invoke-EconomyQuery "SELECT decision_id,agent_id,logical_time,decision_kind,chosen_action,alternatives,beliefs_used,needs_used,utility_breakdown,random_stream,random_draw FROM decision_journal WHERE run_id='$RunId' ORDER BY logical_time,decision_id;"
    agents = Invoke-EconomyQuery @"
SELECT DISTINCT ON (agent_id) agent_id,logical_time,level,experience,meso,map_id,activity_state,stall_id,needs,beliefs
FROM agent_state_projection WHERE run_id='$RunId' ORDER BY agent_id,logical_time DESC;
"@
    presence = Invoke-EconomyQuery "SELECT logical_at::date AS logical_date,map_id,reason,visible,COUNT(*) AS event_count,COUNT(DISTINCT agent_id) AS unique_agents FROM agent_presence_event WHERE run_id='$RunId' GROUP BY 1,2,3,4 ORDER BY 1,2,3,4;"
    activities = Invoke-EconomyQuery "SELECT activity_id,agent_id,calibration_id,map_id,started_at,due_at,completed_at,status,explicit_work,outcome FROM activity_session WHERE run_id='$RunId' ORDER BY started_at,activity_id;"
    social = Invoke-EconomyQuery "SELECT social_event_id,logical_time,room_map_id,speaker_agent_id,target_agent_id,event_kind,public_text,structured_intent,related_item_id FROM social_event WHERE run_id='$RunId' ORDER BY logical_time,social_event_id;"
    negotiations = Invoke-EconomyQuery "SELECT negotiation_id,buyer_id,seller_id,item_id,opened_at,closed_at,status,transcript,settlement_transaction_id FROM negotiation_session WHERE run_id='$RunId' ORDER BY opened_at,negotiation_id;"
    invariants = Invoke-EconomyQuery "SELECT violation_id,logical_at,invariant_code,severity,related_event_id,evidence,resolved_at FROM economy_invariant_violation WHERE run_id='$RunId' ORDER BY logical_at,violation_id;"
    ingestionFailures = Invoke-EconomyQuery "SELECT * FROM economic_ingestion_failure WHERE run_id='$RunId' ORDER BY failed_at;"
    eventKinds = Invoke-EconomyQuery "SELECT event_kind,COUNT(*) AS event_count FROM economic_event WHERE run_id='$RunId' GROUP BY event_kind ORDER BY event_count DESC,event_kind;"
    decisionKinds = Invoke-EconomyQuery "SELECT decision_kind,COUNT(*) AS decision_count FROM decision_journal WHERE run_id='$RunId' GROUP BY decision_kind ORDER BY decision_count DESC,decision_kind;"
    roomTraffic = Invoke-EconomyQuery "SELECT map_id,COUNT(*) AS visits,COUNT(DISTINCT agent_id) AS unique_agents FROM agent_presence_event WHERE run_id='$RunId' AND visible GROUP BY map_id ORDER BY visits DESC,map_id;"
}

$wanted = [System.Collections.Generic.HashSet[int]]::new()
foreach ($collection in @($data.itemDaily, $data.transactions, $data.lots, $data.listings, $data.observations, $data.social, $data.negotiations)) {
    foreach ($row in $collection) {
        if ($null -ne $row.item_id -and [string] $row.item_id -match '^\d+$') { [void] $wanted.Add([int] $row.item_id) }
        if ($null -ne $row.related_item_id -and [string] $row.related_item_id -match '^\d+$') { [void] $wanted.Add([int] $row.related_item_id) }
    }
}
$data.itemNames = Read-ItemNames $wanted

$absolute = [System.IO.Path]::GetFullPath($OutputPath)
New-Item -ItemType Directory -Force -Path ([System.IO.Path]::GetDirectoryName($absolute)) | Out-Null
$data | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $absolute -Encoding UTF8
Write-Host "Exported economy dashboard data for $RunId to $absolute"
