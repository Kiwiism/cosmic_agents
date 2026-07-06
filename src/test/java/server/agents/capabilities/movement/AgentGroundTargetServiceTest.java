package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentGroundTargetServiceTest {
    @Test
    void clampsGrindingTargetAwayFromCurrentRegionEdge() {
        MapleMap map = newMap();
        Foothold foothold = new Foothold(new Point(0, 100), new Point(200, 100), 1);
        FootholdTree footholds = footholds();
        footholds.insert(foothold);
        map.setFootholds(footholds);
        AgentNavigationGraphService.rebuildGraph(map);

        AgentRuntimeEntry entry = grindingEntry(new Point(100, 100), map);

        Point adjusted = AgentGroundTargetService.adjustGrindingTargetPosition(entry, foothold, new Point(190, 100));

        assertEquals(new Point(160, 100), adjusted);
    }

    @Test
    void leavesGrindingTargetWhenTargetIsDifferentRegion() {
        MapleMap map = newMap();
        FootholdTree footholds = footholds();
        Foothold leftFoothold = new Foothold(new Point(-200, 100), new Point(0, 100), 1);
        Foothold rightFoothold = new Foothold(new Point(1, 100), new Point(200, 100), 2);
        footholds.insert(leftFoothold);
        footholds.insert(rightFoothold);
        map.setFootholds(footholds);
        AgentNavigationGraphService.rebuildGraph(map);

        AgentRuntimeEntry entry = grindingEntry(new Point(-100, 100), map);
        Point target = new Point(190, 100);

        Point adjusted = AgentGroundTargetService.adjustGrindingTargetPosition(entry, leftFoothold, target);

        assertEquals(target, adjusted);
    }

    private static AgentRuntimeEntry grindingEntry(Point position, MapleMap map) {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(position);
        when(agent.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentBotModeStateRuntime.setGrinding(entry, true);
        return entry;
    }

    private static MapleMap newMap() {
        return new MapleMap(910000007, 0, 0, 910000007, 1.0f);
    }

    private static FootholdTree footholds() {
        return new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
    }
}
