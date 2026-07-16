package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentMapleIslandTravelRuntime;
import server.agents.capabilities.navigation.AgentMapleIslandTravelSettings;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOptionalTravelHopPolicyTest {
    @Test
    void allowsProbeVerifiedForwardHopThatLandsInSameRegion() {
        MapleMap map = flatMap(910009071);
        Foothold ground = map.getFootholds().getAllFootholds().getFirst();
        AgentNavigationGraphService.rebuildGraph(map, AgentMovementProfile.base());
        AgentRuntimeEntry entry = entry(map, 41);
        AgentMapleIslandTravelRuntime.configure(entry, new AgentMapleIslandTravelSettings(
                19L, false, 1.0d, true, 1.0d, 1_000L, 3_000L));
        Point from = new Point(100, 100);
        int stepX = AgentMovementKinematicsService.walkStep(map, AgentMovementProfile.base());

        assertTrue(AgentOptionalTravelHopPolicy.shouldHop(
                entry, ground, from, new Point(900, 100), stepX, 1_000L));
        assertFalse(AgentOptionalTravelHopPolicy.shouldHop(
                entry, ground, from, new Point(900, 100), stepX, 2_000L));
    }

    @Test
    void neverOverridesRequiredNonWalkNavigationEdge() {
        MapleMap map = flatMap(910009072);
        Foothold ground = map.getFootholds().getAllFootholds().getFirst();
        AgentRuntimeEntry entry = entry(map, 42);
        AgentMapleIslandTravelRuntime.configure(entry, new AgentMapleIslandTravelSettings(
                20L, false, 1.0d, true, 1.0d, 1_000L, 3_000L));
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(100, 100), new Point(200, 60), 10, 0, 0, 0, 0, 200));

        assertFalse(AgentOptionalTravelHopPolicy.shouldHop(
                entry, ground, new Point(100, 100), new Point(900, 100), 6, 1_000L));
    }

    private static MapleMap flatMap(int id) {
        MapleMap map = new MapleMap(id, 0, 0, id, 1.0f);
        Foothold ground = new Foothold(new Point(0, 100), new Point(1_000, 100), 1);
        FootholdTree tree = new FootholdTree(new Point(-2_000, -2_000), new Point(2_000, 2_000));
        tree.insert(ground);
        map.setFootholds(tree);
        return map;
    }

    private static AgentRuntimeEntry entry(MapleMap map, int id) {
        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(id);
        when(bot.getMap()).thenReturn(map);
        when(bot.getPosition()).thenReturn(new Point(100, 100));
        when(bot.getTotalMoveSpeedStat()).thenReturn(100);
        when(bot.getTotalJumpStat()).thenReturn(100);
        return new AgentRuntimeEntry(bot, null, null);
    }
}
