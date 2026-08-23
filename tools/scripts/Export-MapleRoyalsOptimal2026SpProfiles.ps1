param(
    [string]$OutputPath = "src/main/resources/agents/profiles/mapleroyals-optimal-2026-sp-build-profiles.json"
)

$ErrorActionPreference = "Stop"
$profiles = [System.Collections.Generic.List[object]]::new()

function S([int]$level, [int]$skill, [int]$points) {
    [ordered]@{ minimumLevel = $level; skillId = $skill; points = $points }
}

function P([string]$name, [string]$family, [int]$job, [int]$start, [int]$entry,
           [int]$through, [hashtable]$inherited, [object[]]$segments, [int[]]$dump = @()) {
    $normalizedInherited = [ordered]@{}
    foreach ($pair in $inherited.GetEnumerator()) {
        $normalizedInherited["$($pair.Key)"] = $pair.Value
    }
    $profiles.Add([ordered]@{
        profileId = "mapleroyals-optimal-2026-$name"
        profileVersion = 1
        jobFamily = $family
        inheritedSkillLevels = $normalizedInherited
        supportedThroughLevel = $through
        exactJobId = $job
        startingLevel = $start
        entrySp = $entry
        segments = $segments
        dumpSkillIds = $dump
    })
}

$warrior1 = @{1000000=5;1000001=10;1001004=20;1001005=20;1000002=3;1001003=3}
$magician1 = @{2001004=1;2000000=16;2000001=10;2001005=20;2001002=20}
$bowman1 = @{3001004=1;3001005=20;3000000=3;3000002=8;3000001=20;3001003=9}
$rogueAssassin1 = @{4001344=20;4000000=10;4000001=8;4001002=3;4001003=20}
$rogueBandit1 = @{4001334=10;4000000=20;4001002=3;4001003=20;4000001=8}
$pirateBrawler1 = @{5001001=20;5001002=20;5000000=20;5001005=1}
$pirateGunslinger1 = @{5001003=20;5001002=20;5001005=10;5000000=11}

P "warrior-first-job" WARRIOR 100 10 1 30 @{} @(
    S 10 1000000 5; S 10 1000001 10; S 10 1001004 1; S 10 1001005 20;
    S 10 1001004 19; S 10 1000002 3; S 10 1001003 3)
P "magician-first-job" MAGICIAN 200 8 1 30 @{} @(
    S 8 2001004 1; S 8 2000000 5; S 8 2000001 10; S 8 2001005 20;
    S 8 2000000 11; S 8 2001002 20)
P "bowman-first-job" BOWMAN 300 10 1 30 @{} @(
    S 10 3001004 1; S 10 3001005 1; S 10 3000000 3; S 10 3000002 8;
    S 10 3000001 20; S 10 3001005 19; S 10 3001003 9)
P "rogue-assassin-first-job" THIEF 400 10 1 30 @{} @(
    S 10 4001344 1; S 10 4000000 3; S 10 4000001 8; S 10 4001344 19;
    S 10 4000000 7; S 10 4001002 3; S 10 4001003 20)
P "rogue-bandit-first-job" THIEF 400 10 1 30 @{} @(
    S 10 4001334 10; S 10 4000000 20; S 10 4001002 3; S 10 4001003 20;
    S 10 4000001 8)
P "pirate-brawler-first-job" PIRATE 500 10 1 30 @{} @(
    S 10 5001001 1; S 10 5001002 20; S 10 5001001 19; S 10 5000000 20;
    S 10 5001005 1)
P "pirate-gunslinger-first-job" PIRATE 500 10 1 30 @{} @(
    S 10 5001003 1; S 10 5001002 1; S 10 5001003 19; S 10 5001002 19;
    S 10 5001005 10; S 10 5000000 11)

$fighter2 = $warrior1 + @{1100000=20;1101004=20;1101006=20;1101007=30;1100002=30}
$page2 = $warrior1 + @{1200000=20;1201004=20;1201006=20;1201007=30;1200002=30}
$spearman2 = $warrior1 + @{1300000=20;1300001=20;1301004=20;1301005=20;1301006=11;1301007=30}
$fighter2[1001003] = 4
$page2[1001003] = 4

P "fighter" WARRIOR 110 30 1 70 $warrior1 @(
    S 30 1100000 5; S 30 1101004 6; S 30 1100000 14; S 30 1101004 10;
    S 30 1101006 20; S 30 1100000 1; S 30 1101007 30; S 30 1100002 30;
    S 30 1101004 4; S 30 1001003 1)
P "page" WARRIOR 120 30 1 70 $warrior1 @(
    S 30 1200000 5; S 30 1201004 6; S 30 1200000 14; S 30 1201004 14;
    S 30 1200000 1; S 30 1201006 3; S 30 1201007 30; S 30 1200002 30;
    S 30 1201006 17; S 30 1001003 1)
P "spearman" WARRIOR 130 30 1 70 $warrior1 @(
    S 30 1300000 5; S 30 1301004 6; S 30 1300000 14; S 30 1301004 14;
    S 30 1300000 1; S 30 1301006 3; S 30 1301007 30; S 30 1300001 20;
    S 30 1301005 20; S 30 1301006 8)

$fpWizard2 = $magician1 + @{2101002=20;2101004=30;2101005=30;2100000=20;2101001=20;2101003=1}
$ilWizard2 = $magician1 + @{2201002=20;2201004=30;2201005=30;2200000=20;2201001=20;2201003=1}
$cleric2 = $magician1 + @{2301001=20;2301002=30;2300000=20;2301003=20;2301004=20;2301005=11}
P "fp-wizard" MAGICIAN 210 30 1 70 $magician1 @(
    S 30 2101002 1; S 30 2101004 30; S 30 2101002 19; S 30 2101005 30;
    S 30 2100000 20; S 30 2101001 20; S 30 2101003 1)
P "il-wizard" MAGICIAN 220 30 1 70 $magician1 @(
    S 30 2201002 1; S 30 2201004 1; S 30 2201005 30; S 30 2200000 1;
    S 30 2201002 19; S 30 2201004 29; S 30 2200000 19; S 30 2201001 20;
    S 30 2201003 1)
P "cleric" MAGICIAN 230 30 1 70 $magician1 @(
    S 30 2301001 1; S 30 2301002 30; S 30 2300000 1; S 30 2301001 19;
    S 30 2300000 19; S 30 2301003 20; S 30 2301004 20; S 30 2301005 11)

$hunter2 = $bowman1 + @{3101005=30;3100000=20;3101002=20;3101003=20;3101004=20}
$crossbow2 = $bowman1 + @{3201005=30;3200000=20;3201002=20;3201003=20;3201004=20}
$hunter2[3001003] = 20
$crossbow2[3001003] = 20
P "hunter" BOWMAN 310 30 1 70 $bowman1 @(
    S 30 3101005 1; S 30 3100000 5; S 30 3101002 6; S 30 3100000 14;
    S 30 3101005 29; S 30 3101002 14; S 30 3101003 20; S 30 3101004 7;
    S 30 3001003 11; S 30 3100000 1; S 30 3101004 13)
P "crossbowman" BOWMAN 320 30 1 70 $bowman1 @(
    S 30 3201005 1; S 30 3200000 5; S 30 3201002 6; S 30 3200000 14;
    S 30 3201005 29; S 30 3201002 14; S 30 3201003 20; S 30 3201004 7;
    S 30 3001003 11; S 30 3200000 1; S 30 3201004 13)

$assassin2 = $rogueAssassin1 + @{4100000=20;4100001=30;4101003=20;4101004=20;4100002=3;4101005=28}
$bandit2 = $rogueBandit1 + @{4200000=20;4201002=20;4201005=30;4201003=20;4200001=20;4201004=11}
P "assassin" THIEF 410 30 1 70 $rogueAssassin1 @(
    S 30 4100000 3; S 30 4100001 30; S 30 4100000 2; S 30 4101003 6;
    S 30 4101004 6; S 30 4101003 14; S 30 4101004 14; S 30 4100002 3;
    S 30 4101005 28; S 30 4100000 15)
P "bandit" THIEF 420 30 1 70 $rogueBandit1 @(
    S 30 4200000 19; S 30 4201002 6; S 30 4201005 30; S 30 4201003 6;
    S 30 4201002 14; S 30 4201003 14; S 30 4200001 20; S 30 4201004 11;
    S 30 4200000 1)

$brawler2 = $pirateBrawler1 + @{5100000=10;5100001=20;5101006=20;5101002=20;5101004=20;5101005=10;5101007=10;5101003=1}
$gunslinger2 = $pirateGunslinger1 + @{5201001=20;5200000=20;5201003=20;5201005=10;5201006=20;5201004=20;5201002=1}
$brawler2[5001005] = 10
$brawler2[5001003] = 1
$gunslinger2[5000000] = 20
$gunslinger2[5001001] = 1
P "brawler" PIRATE 510 30 1 70 $pirateBrawler1 @(
    S 30 5100000 10; S 30 5100001 1; S 30 5101002 1; S 30 5101004 1;
    S 30 5100001 4; S 30 5101006 6; S 30 5100001 14; S 30 5101006 14;
    S 30 5101004 19; S 30 5001005 9; S 30 5101002 19; S 30 5001003 1;
    S 30 5101005 10; S 30 5100001 1; S 30 5101007 10; S 30 5101003 1)
P "gunslinger" PIRATE 520 30 1 70 $pirateGunslinger1 @(
    S 30 5201001 1; S 30 5200000 5; S 30 5201003 6; S 30 5200000 14;
    S 30 5201001 19; S 30 5201005 5; S 30 5201006 20; S 30 5201005 5;
    S 30 5201003 14; S 30 5201004 20; S 30 5000000 9; S 30 5200000 1;
    S 30 5201002 1; S 30 5001001 1)

$crusader3 = $fighter2 + @{1111002=30;1111003=30;1111005=30;1111008=30;1111007=20;1110000=11}
$whiteKnight3 = $page2 + @{1211007=30;1211005=30;1211003=30;1211009=20;1211002=30;1210000=11}
$dragonKnightHybrid3 = $spearman2 + @{1311004=30;1311003=1;1311001=30;1311008=20;1311005=3;1311006=30;1310000=20;1311007=17}
$dragonKnightSpear3 = $spearman2 + @{1311003=13;1311001=30;1311008=20;1311005=18;1311006=30;1310000=20;1311007=20}
P "crusader" WARRIOR 111 70 1 120 $fighter2 @(
    S 70 1111002 30; S 70 1111003 30; S 70 1111005 30; S 70 1111008 30;
    S 70 1111007 20; S 70 1110000 11)
P "white-knight" WARRIOR 121 70 1 120 $page2 @(
    S 70 1211007 30; S 70 1211005 30; S 70 1211003 30; S 70 1211002 3;
    S 70 1211009 20; S 70 1210000 11; S 70 1211002 27)
P "dragon-knight-hybrid" WARRIOR 131 70 1 120 $spearman2 @(
    S 70 1311004 30; S 70 1311003 1; S 70 1311001 15; S 90 1311001 15;
    S 90 1311008 20; S 90 1311005 3; S 90 1311006 30; S 90 1310000 20;
    S 90 1311007 17)
P "dragon-knight-spear" WARRIOR 131 70 1 120 $spearman2 @(
    S 70 1311003 1; S 70 1311001 15; S 80 1311001 15; S 80 1311008 20;
    S 80 1311005 3; S 80 1311006 30; S 80 1311003 12; S 80 1310000 20;
    S 80 1311007 20; S 80 1311005 15)

$fpMage3 = $fpWizard2 + @{2111002=30;2111003=30;2110001=30;2111004=20;2111005=20;2110000=20;2111006=1}
$ilMage3 = $ilWizard2 + @{2211002=30;2210001=30;2211005=20;2211004=20;2210000=20;2211003=30;2211006=1}
$priest3 = $cleric2 + @{2311004=30;2311001=20;2311003=30;2311002=20;2311005=30;2310000=6;2311006=15}
P "fp-mage" MAGICIAN 211 70 1 120 $fpWizard2 @(
    S 70 2111002 1; S 70 2111003 30; S 70 2111002 27; S 70 2110001 3;
    S 70 2111004 1; S 70 2111005 11; S 70 2111004 19; S 70 2111002 2;
    S 70 2110001 27; S 70 2111005 9; S 70 2110000 20; S 70 2111006 1)
P "il-mage" MAGICIAN 221 70 1 120 $ilWizard2 @(
    S 70 2211002 30; S 70 2210001 3; S 70 2211005 11; S 70 2210001 27;
    S 70 2211004 1; S 70 2211005 9; S 70 2211004 19; S 70 2210000 1;
    S 70 2211003 30; S 70 2210000 19; S 70 2211006 1)
P "priest" MAGICIAN 231 70 1 120 $cleric2 @(
    S 70 2311004 1; S 70 2311001 3; S 70 2311003 30; S 70 2311002 4;
    S 70 2311004 29; S 70 2311005 30; S 70 2311002 16; S 70 2311001 17;
    S 70 2310000 6; S 70 2311006 15)

$ranger3 = $hunter2 + @{3111006=30;3110001=5;3111004=30;3111002=20;3111003=30;3110000=7;3111005=29}
$sniper3 = $crossbow2 + @{3211006=30;3211003=30;3210001=6;3211004=30;3211002=20;3210000=20;3211005=15}
P "ranger" BOWMAN 311 70 1 120 $hunter2 @(
    S 70 3111006 1; S 70 3110001 5; S 70 3111004 30; S 70 3111006 29;
    S 70 3111002 6; S 70 3111003 30; S 70 3111002 14; S 70 3110000 7;
    S 70 3111005 29)
P "sniper" BOWMAN 321 70 1 120 $crossbow2 @(
    S 70 3211006 1; S 70 3211003 1; S 70 3210001 5; S 70 3211004 30;
    S 70 3211006 29; S 70 3211002 6; S 70 3211003 20; S 70 3211002 14;
    S 70 3210000 20; S 70 3211003 9; S 70 3211005 15; S 70 3210001 1)

$hermit3 = $assassin2 + @{4111005=30;4111002=30;4111006=20;4111003=20;4110000=20;4111001=20}
$chiefBandit3 = $bandit2 + @{4211006=30;4211002=30;4211001=21;4211005=20;4211004=30;4211003=1}
$hermit3[4101005] = 30
$hermit3[4000000] = 19
$chiefBandit3[4201004] = 30
P "hermit" THIEF 411 70 1 120 $assassin2 @(
    S 70 4111005 1; S 70 4111002 30; S 70 4111005 4; S 70 4111006 20;
    S 70 4111005 25; S 70 4111003 20; S 70 4110000 20; S 70 4111001 20;
    S 70 4101005 2; S 70 4000000 9)
P "chief-bandit" THIEF 421 70 1 120 $bandit2 @(
    S 70 4211006 1; S 70 4211002 1; S 70 4211001 3; S 70 4211005 1;
    S 70 4211006 29; S 70 4211004 30; S 70 4211002 29; S 70 4211005 19;
    S 70 4201004 19; S 70 4211001 18; S 70 4211003 1)

$marauder3 = $brawler2 + @{5111005=20;5111006=21;5110001=40;5111002=30;5111004=20;5110000=20}
$outlaw3 = $gunslinger2 + @{5211005=26;5210000=20;5211004=30;5211002=15;5211001=30;5211006=30}
P "marauder" PIRATE 511 70 1 120 $brawler2 @(
    S 70 5111005 1; S 70 5111006 1; S 70 5110001 1; S 70 5111002 1;
    S 70 5111004 1; S 70 5110001 39; S 70 5111002 29; S 70 5111006 20;
    S 70 5111004 19; S 70 5110000 20; S 70 5111005 19)
P "outlaw" PIRATE 521 70 1 120 $gunslinger2 @(
    S 70 5211005 1; S 74 5210000 20; S 74 5211004 6; S 74 5211005 6;
    S 74 5211004 6; S 74 5211005 6; S 74 5211004 6; S 74 5211005 6;
    S 74 5211004 6; S 74 5211005 6; S 74 5211004 6; S 74 5211005 1;
    S 74 5211002 1; S 74 5211001 30; S 74 5211002 14; S 74 5211006 30)

P "hero" WARRIOR 112 120 3 200 $crusader3 @(
    S 120 1120003 1; S 120 1121006 1; S 120 1121008 30; S 120 1120003 29;
    S 120 1121002 30; S 120 1121001 30; S 120 1121011 1; S 120 1121010 30;
    S 120 1121000 19; S 120 1121006 27; S 120 1121011 4; S 120 1120004 30;
    S 120 1121000 1) @(1110000,1120005)
P "paladin" WARRIOR 122 120 3 200 $whiteKnight3 @(
    S 120 1221007 1; S 120 1221011 1; S 120 1220010 10; S 120 1221009 30;
    S 120 1221002 30; S 120 1221001 30; S 120 1221012 1; S 120 1221011 29;
    S 120 1221000 19; S 120 1221003 20; S 120 1221007 27; S 120 1221012 4;
    S 120 1220005 30; S 120 1221000 1) @(1210000,1220006)
P "dark-knight-hybrid" WARRIOR 132 120 3 200 $dragonKnightHybrid3 @(
    S 120 1321003 1; S 120 1321007 1; S 120 1320006 30; S 120 1321002 30;
    S 120 1321007 9; S 120 1321001 30; S 120 1320005 30; S 120 1321010 1;
    S 120 1321000 19; S 120 1311007 3; S 120 1320009 21; S 120 1321003 27;
    S 120 1311003 29; S 120 1321010 4; S 120 1321000 1) @(1320008)
P "dark-knight-spear" WARRIOR 132 120 3 200 $dragonKnightSpear3 @(
    S 120 1321003 1; S 120 1321007 1; S 120 1320006 30; S 120 1321002 30;
    S 120 1321007 9; S 120 1321001 30; S 120 1320005 30; S 120 1321010 1;
    S 120 1321000 19; S 120 1320009 21; S 120 1321003 27; S 120 1311003 17;
    S 120 1321010 4; S 120 1320009 4; S 120 1321000 1) @(1320008)

P "fp-arch-mage" MAGICIAN 212 120 3 200 $fpMage3 @(
    S 120 2121006 1; S 120 2121007 1; S 120 2121000 19; S 120 2121007 29;
    S 120 2121006 29; S 120 2121003 5; S 120 2121005 30; S 120 2121004 30;
    S 120 2121008 1; S 120 2121003 25; S 120 2121001 30; S 120 2121008 4;
    S 120 2121002 30; S 120 2121000 1) @(2101003,2001003)
P "il-arch-mage" MAGICIAN 222 120 3 200 $ilMage3 @(
    S 120 2221007 1; S 120 2221006 1; S 120 2221000 19; S 120 2221007 29;
    S 120 2221006 29; S 120 2221003 5; S 120 2221005 30; S 120 2221003 25;
    S 120 2221008 1; S 120 2221004 30; S 120 2221001 30; S 120 2221008 4;
    S 120 2221002 30; S 120 2221000 1) @(2201003,2001003)
P "bishop" MAGICIAN 232 120 3 200 $priest3 @(
    S 120 2321008 1; S 120 2321000 19; S 120 2321006 1; S 120 2321008 29;
    S 120 2321003 30; S 120 2321005 30; S 120 2321009 1; S 120 2321007 30;
    S 120 2310000 14; S 120 2321009 4; S 120 2321004 30; S 120 2321006 9;
    S 120 2321001 30; S 120 2321000 1) @(2321002)

P "bowmaster" BOWMAN 312 120 3 200 $ranger3 @(
    S 120 3121004 1; S 120 3120005 1; S 120 3121003 1; S 120 3121002 30;
    S 120 3121004 29; S 120 3121006 1; S 120 3120005 25; S 120 3121000 19;
    S 120 3121009 1; S 120 3120005 4; S 120 3121007 30; S 120 3121006 29;
    S 120 3121009 4; S 120 3121003 20; S 120 3121008 30; S 120 3121000 1) @(3110001,3110000)
P "marksman" BOWMAN 322 120 3 200 $sniper3 @(
    S 120 3221007 1; S 120 3221005 1; S 120 3220004 1; S 120 3221002 30;
    S 120 3221003 1; S 120 3221007 29; S 120 3220004 29; S 120 3221008 1;
    S 120 3221001 30; S 120 3221000 19; S 120 3221006 30; S 120 3221005 29;
    S 120 3221008 4; S 120 3221003 20; S 120 3221000 1) @(3210001,3211005)

P "night-lord" THIEF 412 120 3 200 $hermit3 @(
    S 120 4121007 1; S 120 4121006 1; S 120 4121007 29; S 120 4121000 19;
    S 120 4120002 10; S 120 4121003 1; S 120 4120002 20; S 120 4121006 29;
    S 120 4121009 5; S 120 4121008 30; S 120 4121003 29; S 120 4120005 30;
    S 120 4121000 1) @(4100002,4111004)
P "shadower" THIEF 422 120 3 200 $chiefBandit3 @(
    S 120 4221007 1; S 120 4221006 1; S 120 4221001 30; S 120 4221007 29;
    S 120 4221006 29; S 120 4220002 30; S 120 4221008 5; S 120 4221000 19;
    S 120 4221003 30; S 120 4220005 30; S 120 4211001 9; S 120 4221000 1) @(4001002,4221004)

P "buccaneer" PIRATE 512 120 3 200 $marauder3 @(
    S 120 5121007 1; S 120 5121001 1; S 120 5121010 1; S 120 5121003 1;
    S 120 5121004 1; S 120 5121009 11; S 120 5121007 29; S 120 5121001 29;
    S 120 5121004 29; S 120 5121003 19; S 120 5121008 1; S 120 5121005 30;
    S 120 5121000 19; S 120 5121010 29; S 120 5121009 9; S 120 5121008 4;
    S 120 5121000 1) @(5121002)
P "corsair" PIRATE 522 120 3 200 $outlaw3 @(
    S 120 5221004 1; S 120 5220001 1; S 120 5221003 1; S 120 5220002 1;
    S 120 5221006 1; S 120 5221007 30; S 120 5220011 20; S 120 5220002 10;
    S 120 5221004 29; S 120 5221010 1; S 120 5221006 9; S 120 5220002 9;
    S 120 5221000 19; S 120 5221009 1; S 120 5221008 30; S 120 5220001 29;
    S 120 5211005 4; S 120 5221003 29; S 120 5221010 4; S 120 5221000 1) @(5211002,5221009)

$usedSkillIds = [System.Collections.Generic.HashSet[int]]::new()
foreach ($profile in $profiles) {
    foreach ($property in $profile.inheritedSkillLevels.GetEnumerator()) {
        [void]$usedSkillIds.Add([int]$property.Key)
    }
    foreach ($segment in $profile.segments) { [void]$usedSkillIds.Add([int]$segment.skillId) }
    foreach ($skillId in $profile.dumpSkillIds) { [void]$usedSkillIds.Add([int]$skillId) }
}

$legacy = Get-Content -LiteralPath 'src/main/resources/agents/profiles/sp-build-profiles.json' -Raw | ConvertFrom-Json
$legacySkillIds = [System.Collections.Generic.HashSet[int]]::new()
foreach ($property in $legacy.skills.PSObject.Properties) { [void]$legacySkillIds.Add([int]$property.Name) }

[xml]$strings = Get-Content -LiteralPath 'wz/String.wz/Skill.img.xml' -Raw
$nameById = @{}
foreach ($node in $strings.DocumentElement.SelectNodes('./imgdir')) {
    $name = $node.SelectSingleNode('./string[@name="name"]')
    if ($name) { $nameById[[int]$node.name] = $name.value }
}

$skills = [ordered]@{}
foreach ($skillId in ($usedSkillIds | Sort-Object)) {
    if ($legacySkillIds.Contains($skillId)) { continue }
    $prefix = [math]::Floor($skillId / 10000)
    [xml]$skillFile = Get-Content -LiteralPath "wz/Skill.wz/$prefix.img.xml" -Raw
    $node = $skillFile.SelectSingleNode("/imgdir/imgdir[@name='skill']/imgdir[@name='$skillId']")
    if (-not $node) { throw "Skill $skillId is missing from local WZ" }
    $requirements = @()
    foreach ($requirement in $node.SelectNodes('./imgdir[@name="req"]/*')) {
        $requirements += [ordered]@{ skillId = [int]$requirement.Name; level = [int]$requirement.value }
    }
    $skills["$skillId"] = [ordered]@{
        name = $nameById[$skillId]
        maxLevel = $node.SelectNodes('./imgdir[@name="level"]/imgdir').Count
        requirements = $requirements
    }
}

$catalog = [ordered]@{ schemaVersion = 2; skills = $skills; profiles = $profiles }
$json = $catalog | ConvertTo-Json -Depth 20
$resolved = Join-Path (Get-Location) $OutputPath
[IO.File]::WriteAllText($resolved, $json + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
Write-Host "Wrote $($profiles.Count) profiles and $($skills.Count) new skill definitions to $resolved"

