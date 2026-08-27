var minPlayers = 1;
var maxPlayers = 3;
var timeLimit = 20; //20 minutes
var eventTimer = 1000 * 60 * timeLimit;
var exitMap = 106021400;
var eventMap = 106021500;
var recruitMap = 106021400;
var bossMobIds = [3300005, 3300006, 3300007];
var bossSpawnX = 0;
var bossSpawnY = -68;

function init() {}

function getEligibleParty(party) {
    var eligible = [];
    var hasLeader = false;
    var expectedProgress = null;
    var expectedMode = null;
    var mismatchedProgress = false;
    var partyList = party.toArray();

    for (var i = 0; i < party.size(); i++) {
        var partyCharacter = partyList[i];
        var player = partyCharacter.getPlayer();
        if (player == null || partyCharacter.getMapId() != recruitMap) {
            continue;
        }
        var mode = player.getQuestStatus(2330) == 1 ? "story"
                : player.getQuestStatus(2336) == 2 ? "farm" : "";
        if (mode == "") continue;
        if (expectedMode == null) expectedMode = mode;
        else if (expectedMode != mode) mismatchedProgress = true;
        var api = player.getClient().getAbstractPlayerInteraction();
        if (mode == "story") {
            var progress = Math.min(1, api.getQuestProgressInt(2330, 3300005)) + ":"
                    + Math.min(1, api.getQuestProgressInt(2330, 3300006)) + ":"
                    + Math.min(1, api.getQuestProgressInt(2330, 3300007));
            if (expectedProgress == null) {
                expectedProgress = progress;
            } else if (expectedProgress != progress) {
                mismatchedProgress = true;
            }
        }
        if (partyCharacter.isLeader()) hasLeader = true;
        eligible.push(partyCharacter);
    }

    if (!hasLeader || mismatchedProgress || eligible.length < minPlayers
            || eligible.length > maxPlayers || eligible.length != party.size()) {
        eligible = [];
    }
    return Java.to(eligible, Java.type('net.server.world.PartyCharacter[]'));
}

function setup(difficulty, lobbyId) {
    var eim = em.newInstance("KingPepe_" + lobbyId);
    var yetiMap = eim.getInstanceMap(eventMap);
    yetiMap.resetFully();
    yetiMap.allowSummonState(false);

    var bossMobId = bossMobIds[Math.floor(Math.random() * bossMobIds.length)];
    eim.setProperty("bossMobId", bossMobId);
    const LifeFactory = Java.type('server.life.LifeFactory');
    const Point = Java.type('java.awt.Point');
    yetiMap.spawnMonsterOnGroundBelow(
        LifeFactory.getMonster(bossMobId), new Point(bossSpawnX, bossSpawnY));

    eim.startEventTimer(eventTimer);
    return eim;
}

function afterSetup(eim) {}

function playerEntry(eim, player) {
    var yetiMap = eim.getMapInstance(eventMap);
    player.changeMap(yetiMap, yetiMap.getPortal(1));
}

function scheduledTimeout(eim) {
    var party = eim.getPlayers();

    for (var i = 0; i < party.size(); i++) {
        playerExit(eim, party.get(i));
    }

    eim.dispose();
}

function playerDead(eim, player) {}

function playerDisconnected(eim, player) {
    if (eim.isEventTeamLackingNow(true, minPlayers, player)) {
        eim.unregisterPlayer(player);
        end(eim);
    } else {
        eim.unregisterPlayer(player);
    }
}

function monsterValue(eim, mobId) {
    return -1;
}

function end(eim) {
    var party = eim.getPlayers();
    for (var i = 0; i < party.size(); i++) {
        playerExit(eim, party.get(i));
    }
    eim.dispose();
}

function leftParty(eim, player) {}

function disbandParty(eim) {}

function playerUnregistered(eim, player) {}

function playerExit(eim, player) {
    eim.unregisterPlayer(player);
    player.changeMap(exitMap, 2);
}

function changedMap(eim, chr, mapid) {
    if (mapid != eventMap) {
        if (eim.isEventTeamLackingNow(true, minPlayers, chr)) {
            eim.unregisterPlayer(chr);
            end(eim);
        } else {
            eim.unregisterPlayer(chr);
        }
    }
}

function cancelSchedule() {}

function dispose() {}

function clearPQ(eim) {}

function monsterKilled(mob, eim) {}

function allMonstersDead(eim) {}

// ---------- FILLER FUNCTIONS ----------

function changedLeader(eim, leader) {}

