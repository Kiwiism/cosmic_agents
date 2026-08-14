param(
    [ValidateSet("calibration", "market", "restore")]
    [string] $Mode = "calibration",
    [int] $RosterSize = 200,
    [int] $CalibrationSize = 25,
    [int] $CalibrationMapId = 103000101,
    [int] $FreeMarketMapId = 910000000,
    [string] $Database = "cosmic",
    [string] $MySqlHost = "host.docker.internal",
    [int] $MySqlPort = 3306,
    [string] $MySqlUser = "root",
    [string] $MySqlPassword = $env:COSMIC_TEST_DB_PASSWORD,
    [string] $PopulationFile = ".runtime/agents/economy-population.json",
    [string] $AuditFile = ".runtime/agents/economy-test-fixture-audit.json"
)

$ErrorActionPreference = "Stop"

if ($Database -notmatch '^[A-Za-z0-9_]+$') {
    throw "Database must contain only letters, numbers, and underscores."
}
if ([string]::IsNullOrWhiteSpace($MySqlPassword)) {
    throw "Set COSMIC_TEST_DB_PASSWORD or pass -MySqlPassword."
}
if ($RosterSize -lt 5 -or $CalibrationSize -lt 5 -or $CalibrationSize -gt $RosterSize) {
    throw "RosterSize and CalibrationSize must be at least five, with calibration no larger than roster."
}
if ($CalibrationSize % 5 -ne 0 -or $RosterSize % 5 -ne 0) {
    throw "RosterSize and CalibrationSize must be divisible by five for the configured equal job distribution."
}
if (Get-NetTCPConnection -State Listen -LocalPort 8484 -ErrorAction SilentlyContinue) {
    throw "Stop the Cosmic server before preparing or restoring the offline roster."
}

function Invoke-MySql {
    param([string] $Sql)

    $output = $Sql | docker run --rm -i `
        -e "MYSQL_PWD=$MySqlPassword" mysql:8.4 `
        mysql -h $MySqlHost -P $MySqlPort -u $MySqlUser -N -B
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL command failed with exit code $LASTEXITCODE."
    }
    return @($output)
}

function Write-RuntimeFiles {
    param([double] $Multiplier, [string] $AppliedMode)

    $rows = @(Invoke-MySql "USE ``$Database``; SELECT character_id, character_name, assigned_job, calibration_member FROM economy_test_roster_fixture ORDER BY character_name, character_id;")
    $agents = foreach ($row in $rows) {
        $parts = $row -split "`t"
        [ordered]@{
            characterId = [int] $parts[0]
            name = $parts[1]
            crewId = $null
        }
    }
    $population = [ordered]@{
        enabled = $true
        multiplier = $Multiplier
        agents = @($agents)
    }
    $populationPath = [System.IO.Path]::GetFullPath($PopulationFile)
    $auditPath = [System.IO.Path]::GetFullPath($AuditFile)
    New-Item -ItemType Directory -Force -Path ([System.IO.Path]::GetDirectoryName($populationPath)) | Out-Null
    $population | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $populationPath -Encoding UTF8

    $jobCounts = @{}
    foreach ($row in $rows) {
        $parts = $row -split "`t"
        $key = [string] $parts[2]
        $current = if ($jobCounts.ContainsKey($key)) { [int] $jobCounts[$key] } else { 0 }
        $jobCounts[$key] = 1 + $current
    }
    $audit = [ordered]@{
        schemaVersion = 1
        fixtureType = "TEST_ONLY_EXCLUDED_FROM_ECONOMIC_ORIGIN"
        generatedAt = (Get-Date).ToUniversalTime().ToString("o")
        mode = $AppliedMode
        database = $Database
        rosterSize = $rows.Count
        calibrationSize = $CalibrationSize
        calibrationMapId = $CalibrationMapId
        freeMarketMapId = $FreeMarketMapId
        jobCounts = $jobCounts
        populationMultiplier = $Multiplier
        taggedPermitItemId = 5140000
        permitGiftFrom = "ECONOMY_TEST_FIXTURE"
        backupTable = "economy_test_character_backup"
        rosterTable = "economy_test_roster_fixture"
        populationFile = $populationPath
        notes = @(
            "Jobs, level, AP stats, HP/MP, mesos, and map are a reversible live-test fixture.",
            "PlayerShop permits are tagged test fixtures and must not be counted as organic item creation.",
            "All simulated farm drops still require real live-session calibration and authoritative WZ drop resolution."
        )
    }
    $audit | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $auditPath -Encoding UTF8
    return [pscustomobject]@{ PopulationPath = $populationPath; AuditPath = $auditPath; Count = $rows.Count }
}

if ($Mode -eq "restore") {
    $exists = @(Invoke-MySql "USE ``$Database``; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database' AND table_name='economy_test_character_backup';")
    if ([int] ([string] $exists[0]) -ne 1) {
        throw "No economy test backup table exists."
    }
    Invoke-MySql @"
USE ``$Database``;
START TRANSACTION;
DELETE FROM inventoryitems WHERE itemid = 5140000 AND giftFrom = 'ECONOMY_TEST_FIXTURE';
UPDATE characters c
JOIN economy_test_character_backup b ON b.id = c.id
SET c.level=b.level, c.exp=b.exp, c.str=b.str, c.dex=b.dex, c.luk=b.luk, c.int=b.int,
    c.hp=b.hp, c.mp=b.mp, c.maxhp=b.maxhp, c.maxmp=b.maxmp, c.meso=b.meso,
    c.job=b.job, c.ap=b.ap, c.sp=b.sp, c.map=b.map, c.spawnpoint=b.spawnpoint;
COMMIT;
"@
    $populationPath = [System.IO.Path]::GetFullPath($PopulationFile)
    New-Item -ItemType Directory -Force -Path ([System.IO.Path]::GetDirectoryName($populationPath)) | Out-Null
    [ordered]@{ enabled = $false; multiplier = 0.0; agents = @() } |
        ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $populationPath -Encoding UTF8
    Write-Host "Restored backed-up character fields and removed tagged test permits."
    exit 0
}

$tableExists = @(Invoke-MySql "USE ``$Database``; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database' AND table_name='economy_test_roster_fixture';")
if ([int] ([string] $tableExists[0]) -eq 0) {
    Invoke-MySql @"
USE ``$Database``;
CREATE TABLE economy_test_roster_fixture (
    roster_index INT NOT NULL PRIMARY KEY,
    character_id INT NOT NULL UNIQUE,
    character_name VARCHAR(13) NOT NULL,
    assigned_job INT NOT NULL,
    calibration_member BOOLEAN NOT NULL,
    prepared_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO economy_test_roster_fixture
    (roster_index, character_id, character_name, assigned_job, calibration_member)
SELECT ranked.roster_index, ranked.id, ranked.name,
       CASE MOD(ranked.roster_index - 1, 5)
           WHEN 0 THEN 100 WHEN 1 THEN 200 WHEN 2 THEN 300 WHEN 3 THEN 400 ELSE 500 END,
       ranked.roster_index <= $CalibrationSize
FROM (
    SELECT ROW_NUMBER() OVER (ORDER BY selected.name, selected.id) AS roster_index,
           selected.id, selected.name
    FROM (
        SELECT c.id, c.name
        FROM characters c
        JOIN accounts a ON a.id = c.accountid
        WHERE c.world = 0 AND c.gm = 0 AND a.banned = 1
          AND a.banreason = 'Agent-only backing account'
        ORDER BY c.name, c.id
        LIMIT $RosterSize
    ) selected
) ranked;
ALTER TABLE economy_test_roster_fixture
    ADD CONSTRAINT economy_test_roster_character_fk FOREIGN KEY (character_id) REFERENCES characters(id);
CREATE TABLE economy_test_character_backup LIKE characters;
INSERT INTO economy_test_character_backup
SELECT c.* FROM characters c JOIN economy_test_roster_fixture r ON r.character_id = c.id;
"@
}

$rosterCount = @(Invoke-MySql "USE ``$Database``; SELECT COUNT(*) FROM economy_test_roster_fixture;")
if ([int] ([string] $rosterCount[0]) -ne $RosterSize) {
    throw "Existing fixture has $($rosterCount[0]) characters, expected $RosterSize. Restore/drop it before changing size."
}

$targetMapSql = if ($Mode -eq "calibration") {
    "CASE WHEN r.calibration_member THEN $CalibrationMapId ELSE $FreeMarketMapId END"
} else {
    [string] $FreeMarketMapId
}

Invoke-MySql @"
USE ``$Database``;
START TRANSACTION;
UPDATE characters c
JOIN economy_test_roster_fixture r ON r.character_id = c.id
SET c.level = 15, c.exp = 0, c.ap = 0, c.sp = '0,0,0,0,0,0,0,0,0,0', c.meso = 0,
    c.job = r.assigned_job,
    c.str = CASE r.assigned_job WHEN 100 THEN 60 WHEN 300 THEN 20 WHEN 500 THEN 40 ELSE 4 END,
    c.dex = CASE r.assigned_job WHEN 100 THEN 20 WHEN 300 THEN 60 WHEN 400 THEN 24 WHEN 500 THEN 40 ELSE 4 END,
    c.int = CASE r.assigned_job WHEN 200 THEN 68 ELSE 4 END,
    c.luk = CASE r.assigned_job WHEN 200 THEN 12 WHEN 400 THEN 56 ELSE 4 END,
    c.maxhp = CASE r.assigned_job WHEN 100 THEN 650 WHEN 500 THEN 500 ELSE 400 END,
    c.hp = CASE r.assigned_job WHEN 100 THEN 650 WHEN 500 THEN 500 ELSE 400 END,
    c.maxmp = CASE r.assigned_job WHEN 200 THEN 650 ELSE 250 END,
    c.mp = CASE r.assigned_job WHEN 200 THEN 650 ELSE 250 END,
    c.map = $targetMapSql, c.spawnpoint = 0;
INSERT INTO inventoryitems
    (type, characterid, accountid, itemid, inventorytype, position, quantity, owner, petid, flag, expiration, giftFrom)
SELECT 1, r.character_id, NULL, 5140000, 6, 1, 1, '', -1, 0, -1, 'ECONOMY_TEST_FIXTURE'
FROM economy_test_roster_fixture r
WHERE NOT EXISTS (
    SELECT 1 FROM inventoryitems i WHERE i.characterid = r.character_id AND i.itemid = 5140000
);
COMMIT;
"@

$multiplier = if ($Mode -eq "calibration") { $CalibrationSize / [double] $RosterSize } else { 1.0 }
$written = Write-RuntimeFiles -Multiplier $multiplier -AppliedMode $Mode
Write-Host "Prepared $($written.Count) deterministic agent-only characters in $Mode mode."
Write-Host "Population: $($written.PopulationPath)"
Write-Host "Audit: $($written.AuditPath)"
