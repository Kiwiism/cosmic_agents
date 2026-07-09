package server.agents.capabilities.equipment;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.LifeGateway;
import server.life.Monster;
import server.life.MonsterStats;
import server.life.SpawnPoint;
import server.maps.MapleMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMapDamageProfileTest {
    @Test
    void snapshotIncludesSpawnTemplateStatsFromLifeGateway() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        SpawnPoint spawn = mock(SpawnPoint.class);
        Monster monster = mock(Monster.class);
        MonsterStats stats = mock(MonsterStats.class);
        LifeGateway life = mock(LifeGateway.class);
        when(agent.getMap()).thenReturn(map);
        when(map.getAllMonsters()).thenReturn(List.of());
        when(map.getMonsterSpawn()).thenReturn(List.of(spawn));
        when(spawn.getMonsterId()).thenReturn(100100);
        when(spawn.getMobTime()).thenReturn(0);
        when(life.getMonster(100100)).thenReturn(monster);
        when(monster.getStats()).thenReturn(stats);
        when(stats.getPDDamage()).thenReturn(12);
        when(stats.getAvoidability()).thenReturn(34);
        when(stats.getLevel()).thenReturn(56);

        AgentMapDamageProfile profile = AgentMapDamageProfile.snapshot(agent, life);

        assertEquals(new AgentMapDamageProfile(12, 34, 56), profile);
    }
}
