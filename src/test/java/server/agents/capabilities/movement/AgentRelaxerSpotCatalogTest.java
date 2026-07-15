package server.agents.capabilities.movement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRelaxerSpotCatalogTest {
    @AfterEach
    void clearReservations() {
        AgentRelaxerSpotReservationRuntime.clear();
    }

    @Test
    void catalogsExposeFixedUniqueMapPositionsAndSouthperryHalves() {
        List<AgentRelaxerSpotCatalog.Spot> amherst = spots(AgentRelaxerSpotCatalog.Pool.AMHERST);
        List<AgentRelaxerSpotCatalog.Spot> left = spots(AgentRelaxerSpotCatalog.Pool.SOUTHPERRY_LEFT);
        List<AgentRelaxerSpotCatalog.Spot> right = spots(AgentRelaxerSpotCatalog.Pool.SOUTHPERRY_RIGHT);
        List<AgentRelaxerSpotCatalog.Spot> all = spots(AgentRelaxerSpotCatalog.Pool.SOUTHPERRY_ALL);

        assertEquals(117, amherst.size());
        assertEquals(107, left.size());
        assertEquals(113, right.size());
        assertEquals(220, all.size());
        assertEquals(amherst.size(), new HashSet<>(amherst).size());
        assertEquals(all.size(), new HashSet<>(all).size());
        assertTrue(left.stream().allMatch(spot -> spot.mapId() == AgentRelaxerSpotCatalog.SOUTHPERRY_MAP_ID
                && spot.x() < AgentRelaxerSpotCatalog.SOUTHPERRY_MIDPOINT_X));
        assertTrue(right.stream().allMatch(spot -> spot.mapId() == AgentRelaxerSpotCatalog.SOUTHPERRY_MAP_ID
                && spot.x() >= AgentRelaxerSpotCatalog.SOUTHPERRY_MIDPOINT_X));
        assertEquals(Set.copyOf(all), union(left, right));
    }

    @Test
    void rightPoolCanReserveOneHundredAgentsWithoutSharingASpot() {
        Set<AgentRelaxerSpotCatalog.Spot> assigned = new HashSet<>();
        for (int agentId = 1; agentId <= 100; agentId++) {
            assigned.add(AgentRelaxerSpotReservationRuntime.reserveRandom(
                    agentId, AgentRelaxerSpotCatalog.Pool.SOUTHPERRY_RIGHT).orElseThrow());
        }

        assertEquals(100, assigned.size());
        assertEquals(100, AgentRelaxerSpotReservationRuntime.occupiedCount());
    }

    @Test
    void exhaustedPoolWaitsUntilAnAgentReleasesItsSpot() {
        int capacity = spots(AgentRelaxerSpotCatalog.Pool.SOUTHPERRY_RIGHT).size();
        for (int agentId = 1; agentId <= capacity; agentId++) {
            assertTrue(AgentRelaxerSpotReservationRuntime.reserveRandom(
                    agentId, AgentRelaxerSpotCatalog.Pool.SOUTHPERRY_RIGHT).isPresent());
        }
        assertFalse(AgentRelaxerSpotReservationRuntime.reserveRandom(
                capacity + 1, AgentRelaxerSpotCatalog.Pool.SOUTHPERRY_RIGHT).isPresent());

        AgentRelaxerSpotReservationRuntime.release(1);

        assertTrue(AgentRelaxerSpotReservationRuntime.reserveRandom(
                capacity + 1, AgentRelaxerSpotCatalog.Pool.SOUTHPERRY_RIGHT).isPresent());
    }

    private static List<AgentRelaxerSpotCatalog.Spot> spots(AgentRelaxerSpotCatalog.Pool pool) {
        return AgentRelaxerSpotCatalog.spots(pool);
    }

    private static Set<AgentRelaxerSpotCatalog.Spot> union(
            List<AgentRelaxerSpotCatalog.Spot> first,
            List<AgentRelaxerSpotCatalog.Spot> second) {
        Set<AgentRelaxerSpotCatalog.Spot> result = new HashSet<>(first);
        result.addAll(second);
        return result;
    }
}
