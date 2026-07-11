package server.agents.capabilities.navigation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentNavigationSharedGroundTest {
    @Test
    void rejectsOnlyLandingOnExactSourceSurface() {
        AgentNavigationGraph.Region source = region(1, foothold(1, 0, 100, 100, 100));

        assertTrue(AgentNavigationGraphService.isPhantomSharedGroundLanding(
                source, new Point(50, 100)));
        assertFalse(AgentNavigationGraphService.isPhantomSharedGroundLanding(
                source, new Point(50, 99)));
        assertFalse(AgentNavigationGraphService.isPhantomSharedGroundLanding(
                source, new Point(101, 100)));
    }

    @Test
    void preservesLastRegionWhenBothChainsShareExactGround() {
        AgentNavigationGraph.Region first = region(1, foothold(1, 0, 100, 100, 100));
        AgentNavigationGraph.Region second = region(2, foothold(2, 0, 100, 100, 100));
        AgentNavigationGraph graph = graph(first, second);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);

        assertEquals(1, AgentNavigationRegionService.preserveRegionContinuity(
                graph, entry, 1, new Point(25, 100)));
        assertEquals(1, AgentNavigationRegionService.preserveRegionContinuity(
                graph, entry, 2, new Point(50, 100)));
    }

    @Test
    void graphReplacementResetsRememberedRegion() {
        AgentNavigationGraph.Region first = region(1, foothold(1, 0, 100, 100, 100));
        AgentNavigationGraph.Region second = region(2, foothold(2, 0, 100, 100, 100));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentNavigationGraph oldGraph = graph(first, second);
        AgentNavigationGraph replacement = graph(first, second);

        AgentNavigationRegionService.preserveRegionContinuity(
                oldGraph, entry, 1, new Point(25, 100));

        assertEquals(2, AgentNavigationRegionService.preserveRegionContinuity(
                replacement, entry, 2, new Point(50, 100)));
    }

    private static AgentNavigationGraph graph(AgentNavigationGraph.Region... regions) {
        Map<Integer, AgentNavigationGraph.Region> byId = java.util.Arrays.stream(regions)
                .collect(java.util.stream.Collectors.toMap(region -> region.id, region -> region));
        return new AgentNavigationGraph(910000059, 48, AgentMovementProfile.base(),
                List.of(regions), byId, Map.of(), Map.of(), Set.of());
    }

    private static AgentNavigationGraph.Region region(int id, Foothold foothold) {
        return new AgentNavigationGraph.Region(id, List.of(new AgentNavigationGraph.Segment(foothold)));
    }

    private static Foothold foothold(int id, int x1, int y1, int x2, int y2) {
        return new Foothold(new Point(x1, y1), new Point(x2, y2), id);
    }
}
