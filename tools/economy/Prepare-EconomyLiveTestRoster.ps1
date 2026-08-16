param(
    [ValidateSet("calibration", "market", "restore")]
    [string] $Mode = "calibration",
    [int] $RosterSize = 200,
    [int] $CalibrationSize = 25,
    [int] $WarriorCount = 37,
    [int] $MagicianCount = 46,
    [int] $BowmanCount = 48,
    [int] $ThiefCount = 33,
    [int] $PirateCount = 36,
    [switch] $PreserveInventory,
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
if ($CalibrationSize % 5 -ne 0) {
    throw "CalibrationSize must be divisible by five for the equal live-calibration cohort."
}
$configuredJobCounts = @($WarriorCount, $MagicianCount, $BowmanCount, $ThiefCount, $PirateCount)
$calibrationPerJob = [int] ($CalibrationSize / 5)
if (($configuredJobCounts | Measure-Object -Sum).Sum -ne $RosterSize) {
    throw "WarriorCount + MagicianCount + BowmanCount + ThiefCount + PirateCount must equal RosterSize."
}
if (($configuredJobCounts | Measure-Object -Minimum).Minimum -lt $calibrationPerJob) {
    throw "Every job count must provide at least $calibrationPerJob calibration characters."
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
        inventoryBackupTable = "economy_test_inventory_backup"
        inventoryEquipmentBackupTable = "economy_test_inventoryequipment_backup"
        skillBackupTable = "economy_test_skill_backup"
        rosterTable = "economy_test_roster_fixture"
        populationFile = $populationPath
        notes = @(
            "Jobs, level, AP stats, HP/MP, mesos, and map are a reversible live-test fixture.",
            "PlayerShop permits are tagged test fixtures and must not be counted as organic item creation.",
            "Calibration weapons are tagged and removed before market mode.",
            "Tagged HP/MP potion, ammunition, and permit runway are explicit test-only initial holdings; reports must exclude them from organic item creation.",
            $(if ($PreserveInventory) { "Existing inventory was intentionally imported as an explicit initial endowment." } else { "The roster began with no inherited inventory; only tagged starter runway was injected." }),
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
    $skillBackupExists = @(Invoke-MySql "USE ``$Database``; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database' AND table_name='economy_test_skill_backup';")
    $restoreSkillsSql = if ([int] ([string] $skillBackupExists[0]) -eq 1) {
@"
DELETE s FROM skills s
JOIN economy_test_roster_fixture r ON r.character_id = s.characterid;
INSERT INTO skills (id, skillid, characterid, skilllevel, masterlevel, expiration)
SELECT id, skillid, characterid, skilllevel, masterlevel, expiration
FROM economy_test_skill_backup;
"@
    } else {
        ""
    }
    Invoke-MySql @"
USE ``$Database``;
CREATE TABLE IF NOT EXISTS economy_test_item_position_backup (
    inventoryitemid INT UNSIGNED NOT NULL PRIMARY KEY,
    character_id INT NOT NULL,
    original_position INT NOT NULL,
    backed_up_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
START TRANSACTION;
DELETE ie FROM inventoryequipment ie
JOIN inventoryitems ii ON ii.inventoryitemid = ie.inventoryitemid
JOIN economy_test_roster_fixture r ON r.character_id = ii.characterid;
DELETE i FROM inventoryitems i
JOIN economy_test_roster_fixture r ON r.character_id = i.characterid;
INSERT INTO inventoryitems SELECT * FROM economy_test_inventory_backup;
INSERT INTO inventoryequipment SELECT * FROM economy_test_inventoryequipment_backup;
UPDATE characters c
JOIN economy_test_character_backup b ON b.id = c.id
SET c.level=b.level, c.exp=b.exp, c.str=b.str, c.dex=b.dex, c.luk=b.luk, c.int=b.int,
    c.hp=b.hp, c.mp=b.mp, c.maxhp=b.maxhp, c.maxmp=b.maxmp, c.meso=b.meso,
    c.job=b.job, c.ap=b.ap, c.sp=b.sp, c.map=b.map, c.spawnpoint=b.spawnpoint;
$restoreSkillsSql
COMMIT;
"@
    $populationPath = [System.IO.Path]::GetFullPath($PopulationFile)
    New-Item -ItemType Directory -Force -Path ([System.IO.Path]::GetDirectoryName($populationPath)) | Out-Null
    [ordered]@{ enabled = $false; multiplier = 0.0; agents = @() } |
        ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $populationPath -Encoding UTF8
    Write-Host "Restored backed-up character fields, skills, and complete inventory; removed all test-period holdings."
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
CREATE TABLE economy_test_item_position_backup (
    inventoryitemid INT UNSIGNED NOT NULL PRIMARY KEY,
    character_id INT NOT NULL,
    original_position INT NOT NULL,
    backed_up_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE economy_test_skill_backup LIKE skills;
INSERT INTO economy_test_skill_backup
SELECT s.* FROM skills s JOIN economy_test_roster_fixture r ON r.character_id = s.characterid;
CREATE TABLE economy_test_inventory_backup LIKE inventoryitems;
INSERT INTO economy_test_inventory_backup
SELECT i.* FROM inventoryitems i JOIN economy_test_roster_fixture r ON r.character_id = i.characterid;
CREATE TABLE economy_test_inventoryequipment_backup LIKE inventoryequipment;
INSERT INTO economy_test_inventoryequipment_backup
SELECT ie.* FROM inventoryequipment ie
JOIN inventoryitems i ON i.inventoryitemid = ie.inventoryitemid
JOIN economy_test_roster_fixture r ON r.character_id = i.characterid;
CREATE TABLE economy_test_fixture_state (
    id TINYINT NOT NULL PRIMARY KEY,
    inventory_backup_completed BOOLEAN NOT NULL,
    backed_up_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO economy_test_fixture_state (id, inventory_backup_completed) VALUES (1, TRUE);
"@
}

$rosterCount = @(Invoke-MySql "USE ``$Database``; SELECT COUNT(*) FROM economy_test_roster_fixture;")
if ([int] ([string] $rosterCount[0]) -ne $RosterSize) {
    throw "Existing fixture has $($rosterCount[0]) characters, expected $RosterSize. Restore/drop it before changing size."
}

$warriorBoundary = $CalibrationSize + ($WarriorCount - $calibrationPerJob)
$magicianBoundary = $warriorBoundary + ($MagicianCount - $calibrationPerJob)
$bowmanBoundary = $magicianBoundary + ($BowmanCount - $calibrationPerJob)
$thiefBoundary = $bowmanBoundary + ($ThiefCount - $calibrationPerJob)
Invoke-MySql @"
USE ``$Database``;
UPDATE economy_test_roster_fixture
SET calibration_member = roster_index <= $CalibrationSize,
    assigned_job = CASE
        WHEN roster_index <= $CalibrationSize THEN
            CASE MOD(roster_index - 1, 5)
                WHEN 0 THEN 100 WHEN 1 THEN 200 WHEN 2 THEN 300 WHEN 3 THEN 400 ELSE 500 END
        WHEN roster_index <= $warriorBoundary THEN 100
        WHEN roster_index <= $magicianBoundary THEN 200
        WHEN roster_index <= $bowmanBoundary THEN 300
        WHEN roster_index <= $thiefBoundary THEN 400
        ELSE 500
    END;
"@

$skillBackupExists = @(Invoke-MySql "USE ``$Database``; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database' AND table_name='economy_test_skill_backup';")
if ([int] ([string] $skillBackupExists[0]) -eq 0) {
    Invoke-MySql @"
USE ``$Database``;
CREATE TABLE economy_test_skill_backup LIKE skills;
INSERT INTO economy_test_skill_backup
SELECT s.* FROM skills s JOIN economy_test_roster_fixture r ON r.character_id = s.characterid;
"@
}

Invoke-MySql @"
USE ``$Database``;
CREATE TABLE IF NOT EXISTS economy_test_item_position_backup (
    inventoryitemid INT UNSIGNED NOT NULL PRIMARY KEY,
    character_id INT NOT NULL,
    original_position INT NOT NULL,
    backed_up_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS economy_test_inventory_backup LIKE inventoryitems;
CREATE TABLE IF NOT EXISTS economy_test_inventoryequipment_backup LIKE inventoryequipment;
CREATE TABLE IF NOT EXISTS economy_test_fixture_state (
    id TINYINT NOT NULL PRIMARY KEY,
    inventory_backup_completed BOOLEAN NOT NULL,
    backed_up_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
START TRANSACTION;
INSERT INTO economy_test_inventory_backup
SELECT i.* FROM inventoryitems i
JOIN economy_test_roster_fixture r ON r.character_id = i.characterid
WHERE NOT EXISTS (SELECT 1 FROM economy_test_fixture_state WHERE id = 1 AND inventory_backup_completed = TRUE);
INSERT INTO economy_test_inventoryequipment_backup
SELECT ie.* FROM inventoryequipment ie
JOIN inventoryitems i ON i.inventoryitemid = ie.inventoryitemid
JOIN economy_test_roster_fixture r ON r.character_id = i.characterid
WHERE NOT EXISTS (SELECT 1 FROM economy_test_fixture_state WHERE id = 1 AND inventory_backup_completed = TRUE);
INSERT INTO economy_test_fixture_state (id, inventory_backup_completed)
VALUES (1, TRUE)
ON DUPLICATE KEY UPDATE inventory_backup_completed = TRUE;
COMMIT;
"@

$inventoryBaselineSql = if ($PreserveInventory) {
@"
DELETE ie FROM inventoryequipment ie
JOIN inventoryitems ii ON ii.inventoryitemid = ie.inventoryitemid
WHERE ii.giftFrom = 'ECONOMY_TEST_FIXTURE'
  AND ii.characterid IN (SELECT character_id FROM economy_test_roster_fixture);
DELETE FROM inventoryitems
WHERE giftFrom = 'ECONOMY_TEST_FIXTURE'
  AND characterid IN (SELECT character_id FROM economy_test_roster_fixture);
"@
} else {
@"
DELETE ie FROM inventoryequipment ie
JOIN inventoryitems ii ON ii.inventoryitemid = ie.inventoryitemid
JOIN economy_test_roster_fixture r ON r.character_id = ii.characterid;
DELETE i FROM inventoryitems i
JOIN economy_test_roster_fixture r ON r.character_id = i.characterid;
"@
}

$targetMapSql = if ($Mode -eq "calibration") {
    "CASE WHEN r.calibration_member THEN $CalibrationMapId ELSE $FreeMarketMapId END"
} else {
    [string] $FreeMarketMapId
}
$calibrationSupplySql = if ($Mode -eq "calibration") {
@"
CREATE TEMPORARY TABLE economy_test_next_equip_slot AS
SELECT r.character_id, COALESCE(MAX(CASE WHEN i.inventorytype = 1 AND i.position > 0 THEN i.position END), 0) AS base_position
FROM economy_test_roster_fixture r
LEFT JOIN inventoryitems i ON i.characterid = r.character_id
WHERE r.calibration_member = 1
GROUP BY r.character_id;
INSERT IGNORE INTO economy_test_item_position_backup (inventoryitemid, character_id, original_position)
SELECT i.inventoryitemid, i.characterid, i.position
FROM inventoryitems i
JOIN economy_test_roster_fixture r ON r.character_id = i.characterid AND r.calibration_member = 1
WHERE i.inventorytype = -1 AND i.position = -11 AND i.giftFrom <> 'ECONOMY_TEST_FIXTURE';
UPDATE inventoryitems i
JOIN economy_test_item_position_backup b ON b.inventoryitemid = i.inventoryitemid
JOIN economy_test_next_equip_slot s ON s.character_id = i.characterid
SET i.inventorytype = 1, i.position = s.base_position + 1
WHERE b.original_position = -11;
INSERT INTO inventoryitems
    (type, characterid, accountid, itemid, inventorytype, position, quantity, owner, petid, flag, expiration, giftFrom)
SELECT 1, r.character_id, NULL,
       CASE r.assigned_job
           WHEN 100 THEN 1302000 WHEN 200 THEN 1372005 WHEN 300 THEN 1452002
           WHEN 400 THEN 1332007 ELSE 1482000 END,
       -1, -11, 1, '', -1, 0, -1, 'ECONOMY_TEST_FIXTURE'
FROM economy_test_roster_fixture r
WHERE r.calibration_member = 1;
INSERT INTO inventoryequipment
    (inventoryitemid, upgradeslots, level, ``str``, dex, ``int``, luk, hp, mp, watk, matk,
     wdef, mdef, acc, avoid, hands, speed, jump, locked, vicious, itemlevel, itemexp, ringid)
SELECT i.inventoryitemid, 7, 0, 0, 0, 0, 0, 0, 0,
       CASE i.itemid WHEN 1302000 THEN 17 WHEN 1372005 THEN 15 WHEN 1452002 THEN 25
                    WHEN 1332007 THEN 22 WHEN 1482000 THEN 18 END,
       CASE i.itemid WHEN 1372005 THEN 23 ELSE 0 END,
       0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, -1
FROM inventoryitems i
JOIN economy_test_roster_fixture r ON r.character_id = i.characterid AND r.calibration_member = 1
WHERE i.inventorytype = -1 AND i.position = -11 AND i.giftFrom = 'ECONOMY_TEST_FIXTURE';
DROP TEMPORARY TABLE economy_test_next_equip_slot;
CREATE TEMPORARY TABLE economy_test_next_use_slot AS
SELECT r.character_id, COALESCE(MAX(CASE WHEN i.inventorytype = 2 THEN i.position END), 0) AS base_position
FROM economy_test_roster_fixture r
LEFT JOIN inventoryitems i ON i.characterid = r.character_id
WHERE r.calibration_member = 1
GROUP BY r.character_id;
INSERT INTO inventoryitems
    (type, characterid, accountid, itemid, inventorytype, position, quantity, owner, petid, flag, expiration, giftFrom)
SELECT 1, s.character_id, NULL, 2000002, 2, s.base_position + 1, 100, '', -1, 0, -1, 'ECONOMY_TEST_FIXTURE'
FROM economy_test_next_use_slot s;
INSERT INTO inventoryitems
    (type, characterid, accountid, itemid, inventorytype, position, quantity, owner, petid, flag, expiration, giftFrom)
SELECT 1, s.character_id, NULL, 2000003, 2, s.base_position + 2, 100, '', -1, 0, -1, 'ECONOMY_TEST_FIXTURE'
FROM economy_test_next_use_slot s;
INSERT INTO inventoryitems
    (type, characterid, accountid, itemid, inventorytype, position, quantity, owner, petid, flag, expiration, giftFrom)
SELECT 1, s.character_id, NULL, 2060000, 2, s.base_position + 3, 1000, '', -1, 0, -1, 'ECONOMY_TEST_FIXTURE'
FROM economy_test_next_use_slot s
JOIN economy_test_roster_fixture r ON r.character_id = s.character_id AND r.assigned_job = 300;
INSERT INTO inventoryitems
    (type, characterid, accountid, itemid, inventorytype, position, quantity, owner, petid, flag, expiration, giftFrom)
SELECT 1, s.character_id, NULL, 2330000, 2, s.base_position + 3, 600, '', -1, 0, -1, 'ECONOMY_TEST_FIXTURE'
FROM economy_test_next_use_slot s
JOIN economy_test_roster_fixture r ON r.character_id = s.character_id AND r.assigned_job = 500;
DROP TEMPORARY TABLE economy_test_next_use_slot;
"@
} else {
@"
INSERT INTO inventoryitems
    (type, characterid, accountid, itemid, inventorytype, position, quantity, owner, petid, flag, expiration, giftFrom)
SELECT 1, r.character_id, NULL,
       CASE r.assigned_job
           WHEN 100 THEN 1302000 WHEN 200 THEN 1372005 WHEN 300 THEN 1452002
           WHEN 400 THEN 1332007 ELSE 1482000 END,
       -1, -11, 1, '', -1, 0, -1, 'ECONOMY_TEST_FIXTURE'
FROM economy_test_roster_fixture r;
INSERT INTO inventoryequipment
    (inventoryitemid, upgradeslots, level, ``str``, dex, ``int``, luk, hp, mp, watk, matk,
     wdef, mdef, acc, avoid, hands, speed, jump, locked, vicious, itemlevel, itemexp, ringid)
SELECT i.inventoryitemid, 7, 0, 0, 0, 0, 0, 0, 0,
       CASE i.itemid WHEN 1302000 THEN 17 WHEN 1372005 THEN 15 WHEN 1452002 THEN 25
                    WHEN 1332007 THEN 22 WHEN 1482000 THEN 18 END,
       CASE i.itemid WHEN 1372005 THEN 23 ELSE 0 END,
       0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, -1
FROM inventoryitems i
JOIN economy_test_roster_fixture r ON r.character_id = i.characterid
WHERE i.inventorytype = -1 AND i.position = -11 AND i.giftFrom = 'ECONOMY_TEST_FIXTURE';
CREATE TEMPORARY TABLE economy_test_next_market_use_slot AS
SELECT r.character_id, COALESCE(MAX(CASE WHEN i.inventorytype = 2 THEN i.position END), 0) AS base_position
FROM economy_test_roster_fixture r
LEFT JOIN inventoryitems i ON i.characterid = r.character_id
GROUP BY r.character_id;
INSERT INTO inventoryitems
    (type, characterid, accountid, itemid, inventorytype, position, quantity, owner, petid, flag, expiration, giftFrom)
SELECT 1, s.character_id, NULL, 2000000, 2, s.base_position + 1, 100, '', -1, 0, -1, 'ECONOMY_TEST_FIXTURE'
FROM economy_test_next_market_use_slot s;
INSERT INTO inventoryitems
    (type, characterid, accountid, itemid, inventorytype, position, quantity, owner, petid, flag, expiration, giftFrom)
SELECT 1, s.character_id, NULL, 2000003, 2, s.base_position + 2, 100, '', -1, 0, -1, 'ECONOMY_TEST_FIXTURE'
FROM economy_test_next_market_use_slot s;
INSERT INTO inventoryitems
    (type, characterid, accountid, itemid, inventorytype, position, quantity, owner, petid, flag, expiration, giftFrom)
SELECT 1, s.character_id, NULL, 2060000, 2, s.base_position + 3, 1000, '', -1, 0, -1, 'ECONOMY_TEST_FIXTURE'
FROM economy_test_next_market_use_slot s
JOIN economy_test_roster_fixture r ON r.character_id = s.character_id AND r.assigned_job = 300;
INSERT INTO inventoryitems
    (type, characterid, accountid, itemid, inventorytype, position, quantity, owner, petid, flag, expiration, giftFrom)
SELECT 1, s.character_id, NULL, 2070000, 2, s.base_position + 3, 500, '', -1, 0, -1, 'ECONOMY_TEST_FIXTURE'
FROM economy_test_next_market_use_slot s
JOIN economy_test_roster_fixture r ON r.character_id = s.character_id AND r.assigned_job = 400;
INSERT INTO inventoryitems
    (type, characterid, accountid, itemid, inventorytype, position, quantity, owner, petid, flag, expiration, giftFrom)
SELECT 1, s.character_id, NULL, 2330000, 2, s.base_position + 3, 600, '', -1, 0, -1, 'ECONOMY_TEST_FIXTURE'
FROM economy_test_next_market_use_slot s
JOIN economy_test_roster_fixture r ON r.character_id = s.character_id AND r.assigned_job = 500;
DROP TEMPORARY TABLE economy_test_next_market_use_slot;
"@
}

$skillFixtureSql = @"
DELETE s FROM skills s
JOIN economy_test_roster_fixture r ON r.character_id = s.characterid;
INSERT INTO skills (skillid, characterid, skilllevel, masterlevel, expiration)
SELECT 1000000, character_id, 5, 0, -1 FROM economy_test_roster_fixture WHERE assigned_job = 100
UNION ALL SELECT 1000001, character_id, 10, 0, -1 FROM economy_test_roster_fixture WHERE assigned_job = 100
UNION ALL SELECT 1001004, character_id, 1, 0, -1 FROM economy_test_roster_fixture WHERE assigned_job = 100
UNION ALL SELECT 2000000, character_id, 5, 0, -1 FROM economy_test_roster_fixture WHERE assigned_job = 200
UNION ALL SELECT 2000001, character_id, 10, 0, -1 FROM economy_test_roster_fixture WHERE assigned_job = 200
UNION ALL SELECT 2001004, character_id, 1, 0, -1 FROM economy_test_roster_fixture WHERE assigned_job = 200
UNION ALL SELECT 2001005, character_id, 6, 0, -1 FROM economy_test_roster_fixture WHERE assigned_job = 200
UNION ALL SELECT 3000000, character_id, 3, 0, -1 FROM economy_test_roster_fixture WHERE assigned_job = 300
UNION ALL SELECT 3000001, character_id, 4, 0, -1 FROM economy_test_roster_fixture WHERE assigned_job = 300
UNION ALL SELECT 3000002, character_id, 8, 0, -1 FROM economy_test_roster_fixture WHERE assigned_job = 300
UNION ALL SELECT 3001004, character_id, 1, 0, -1 FROM economy_test_roster_fixture WHERE assigned_job = 300
UNION ALL SELECT 4000000, character_id, 5, 0, -1 FROM economy_test_roster_fixture WHERE assigned_job = 400
UNION ALL SELECT 4001334, character_id, 11, 0, -1 FROM economy_test_roster_fixture WHERE assigned_job = 400
UNION ALL SELECT 5000000, character_id, 5, 0, -1 FROM economy_test_roster_fixture WHERE assigned_job = 500
UNION ALL SELECT 5001001, character_id, 11, 0, -1 FROM economy_test_roster_fixture WHERE assigned_job = 500;
"@

Invoke-MySql @"
USE ``$Database``;
START TRANSACTION;
$inventoryBaselineSql
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
$skillFixtureSql
UPDATE inventoryitems
SET inventorytype = 5, position = 1
WHERE itemid = 5140000 AND giftFrom = 'ECONOMY_TEST_FIXTURE'
  AND characterid IN (SELECT character_id FROM economy_test_roster_fixture);
INSERT INTO inventoryitems
    (type, characterid, accountid, itemid, inventorytype, position, quantity, owner, petid, flag, expiration, giftFrom)
SELECT 1, r.character_id, NULL, 5140000, 5, 1, 1, '', -1, 0, -1, 'ECONOMY_TEST_FIXTURE'
FROM economy_test_roster_fixture r
WHERE NOT EXISTS (
    SELECT 1 FROM inventoryitems i WHERE i.characterid = r.character_id AND i.itemid = 5140000
);
$calibrationSupplySql
COMMIT;
"@

$multiplier = if ($Mode -eq "calibration") { $CalibrationSize / [double] $RosterSize } else { 1.0 }
$written = Write-RuntimeFiles -Multiplier $multiplier -AppliedMode $Mode
Write-Host "Prepared $($written.Count) deterministic agent-only characters in $Mode mode."
Write-Host "Population: $($written.PopulationPath)"
Write-Host "Audit: $($written.AuditPath)"
