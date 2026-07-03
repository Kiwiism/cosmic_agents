package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.maps.Rope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
