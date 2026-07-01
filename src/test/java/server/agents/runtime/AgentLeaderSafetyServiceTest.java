package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotActivityStateRuntime;
import server.agents.integration.AgentBotBuffStateRuntime;
import server.agents.integration.AgentBotDegenerateAttackStateRuntime;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.plans.AgentTask;
import server.bots.BotEntry;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLeaderSafetyServiceTest {
    @Test
    void doesNotTownWarpWhenMapIsMissing() {
        assertFalse(AgentLeaderSafetyService.shouldTownWarpForInactiveLeader(null));
        assertFalse(AgentLeaderSafetyService.canReturnToDifferentMap(null));
    }

    @Test
    void doesNotTownWarpWhenReturnMapIsMissing() {
        MapleMap map = map(100, null, livingMonster());

        assertFalse(AgentLeaderSafetyService.shouldTownWarpForInactiveLeader(map));
        assertFalse(AgentLeaderSafetyService.canReturnToDifferentMap(map));
    }

    @Test
    void doesNotTownWarpWhenReturnMapIsSameMap() {
        MapleMap map = map(100, null, livingMonster());
        when(map.getReturnMap()).thenReturn(map);

        assertFalse(AgentLeaderSafetyService.shouldTownWarpForInactiveLeader(map));
        assertFalse(AgentLeaderSafetyService.canReturnToDifferentMap(map));
    }

    @Test
    void doesNotTownWarpWhenNoAliveMonstersArePresent() {
        MapleMap returnMap = map(200, null);
        MapleMap map = map(100, returnMap, deadMonster());

        assertFalse(AgentLeaderSafetyService.shouldTownWarpForInactiveLeader(map));
        assertTrue(AgentLeaderSafetyService.canReturnToDifferentMap(map));
    }

    @Test
    void townWarpsOnlyWhenAliveMonsterAndDifferentReturnMapExist() {
        MapleMap returnMap = map(200, null);
        MapleMap map = map(100, returnMap, deadMonster(), livingMonster());

        assertTrue(AgentLeaderSafetyService.shouldTownWarpForInactiveLeader(map));
        assertTrue(AgentLeaderSafetyService.canReturnToDifferentMap(map));
    }

    @Test
    void preparesInactiveIdleWithLegacyResetOrderAndState() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(10, 20), true);
        AgentBotGrindTargetStateRuntime.setTarget(entry, livingMonster());
        AgentBotDegenerateAttackStateRuntime.markDegenAttackDone(entry);
        AgentBotBuffStateRuntime.setEnabled(entry, true);
        entry.addScriptTask(AgentTask.stop());
        AtomicInteger order = new AtomicInteger();

        AgentLeaderSafetyService.prepareInactiveIdle(
                entry,
                () -> {
                    assertEquals(0, order.getAndIncrement());
                    entry.clearScriptTasks();
                },
                () -> assertEquals(1, order.getAndIncrement()),
                () -> assertEquals(2, order.getAndIncrement()));

        assertEquals(3, order.get());
        assertFalse(entry.hasScriptTasks());
        assertFalse(AgentBotMoveTargetStateRuntime.hasMoveTarget(entry));
        assertNull(AgentBotGrindTargetStateRuntime.target(entry));
        assertFalse(AgentBotDegenerateAttackStateRuntime.degenAttackDone(entry));
        assertFalse(AgentBotBuffStateRuntime.enabled(entry));
        assertTrue(AgentBotActivityStateRuntime.ownerAwaySafeMode(entry));
    }

    private static MapleMap map(int id, MapleMap returnMap, Monster... monsters) {
        MapleMap map = mock(MapleMap.class);
        when(map.getId()).thenReturn(id);
        when(map.getReturnMap()).thenReturn(returnMap);
        when(map.getAllMonsters()).thenReturn(List.of(monsters));
        return map;
    }

    private static Monster livingMonster() {
        Monster monster = mock(Monster.class);
        when(monster.isAlive()).thenReturn(true);
        return monster;
    }

    private static Monster deadMonster() {
        Monster monster = mock(Monster.class);
        when(monster.isAlive()).thenReturn(false);
        return monster;
    }
}
