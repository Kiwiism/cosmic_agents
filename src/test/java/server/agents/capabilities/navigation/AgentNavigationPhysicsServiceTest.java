package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.maps.MapleMap;
import server.maps.Rope;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationPhysicsServiceTest {
    @Test
    void firstClimbableYMatchesLegacyTopClamp() {
        Rope rope = new Rope(100, 40, 160, false);
        Rope ladder = new Rope(100, 40, 160, true);

        assertEquals(41, AgentNavigationPhysicsService.firstClimbableY(rope));
        assertEquals(41, AgentNavigationPhysicsService.firstClimbableY(ladder));
    }

    @Test
    void walkableEndpointStepPreservesLegacyGapPolicy() {
        assertTrue(AgentNavigationPhysicsService.isWalkableEndpointStep(0, -1));
        assertTrue(AgentNavigationPhysicsService.isWalkableEndpointStep(12, 0));
        assertFalse(AgentNavigationPhysicsService.isWalkableEndpointStep(13, 0));
    }

    @Test
    void buildWalkRegionLookupLifecycleAcceptsNullClear() {
        AgentNavigationPhysicsService.setBuildWalkRegionLookup(null, null, null, null);
        AgentNavigationPhysicsService.clearBuildWalkRegionLookup();
    }

    @Test
    void buildWalkRegionLookupLifecycleIsAgentOwned() {
        MapleMap map = new MapleMap(910000034, 0, 0, 910000034, 1.0f);

        AgentNavigationPhysicsService.setBuildWalkRegionLookup(map, Map.of(), Map.of(), Map.of());

        AgentNavigationWalkRegionLookupService.WalkRegionLookup lookup =
                AgentNavigationWalkRegionLookupService.resolveWalkRegionLookup(map);
        assertEquals(map.getId(), lookup.mapId());
        assertTrue(lookup.regionsById().isEmpty());

        AgentNavigationPhysicsService.clearBuildWalkRegionLookup();

        assertNull(AgentNavigationWalkRegionLookupService.resolveWalkRegionLookup(map));
    }
}
