package server.agents.plans.mapleisland;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.navigation.AgentMapleIslandTravelRuntime;
import server.agents.capabilities.navigation.AgentMapleIslandTravelSettings;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSplitRoadRouteServiceTest {
    @Test
    void disabledTravelKeepsTheDirectGroundRoute() {
        AgentRuntimeEntry entry = entry(1);

        AgentSplitRoadRouteService.Plan plan = AgentSplitRoadRouteService.INSTANCE.select(entry);

        assertEquals(AgentSplitRoadRouteService.Variant.GROUND, plan.variant());
        assertFalse(plan.usesInternalPortal());
        assertTrue(plan.waypoints().isEmpty());
    }

    @Test
    void cohortSeedsDistributeAcrossGroundAndUpperRouteFamilies() {
        Set<AgentSplitRoadRouteService.Variant> variants =
                EnumSet.noneOf(AgentSplitRoadRouteService.Variant.class);
        for (int agentId = 1; agentId <= 200; agentId++) {
            AgentRuntimeEntry entry = entry(agentId);
            AgentMapleIslandTravelRuntime.configure(entry, new AgentMapleIslandTravelSettings(
                    77L, true, 1.15d, false, 0.0d, 3_000L, 0L));
            variants.add(AgentSplitRoadRouteService.INSTANCE.select(entry).variant());
        }

        assertEquals(EnumSet.allOf(AgentSplitRoadRouteService.Variant.class), variants);
    }

    private static AgentRuntimeEntry entry(int agentId) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(agentId);
        return new AgentRuntimeEntry(agent, null, null);
    }
}
