package server.agents.runtime;

import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.maps.MapleMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
