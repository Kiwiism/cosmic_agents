package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRelaxerSpotCatalogTest {
    @AfterEach
    void clearReservations() {
        AgentRelaxerSpotReservationRuntime.clear();
    }

    @Test
    void catalogsExposeFixedUniqueMapPositionsAndSouthperryHalves() {
        List<AgentRelaxerSpotCatalog.Spot> amherst = spots(AgentRelaxerSpotCatalog.Pool.AMHERST);
        List<AgentRelaxerSpotCatalog.Spot> nearPio = spots(
                AgentRelaxerSpotCatalog.Pool.AMHERST_NEAR_PIO);
        List<AgentRelaxerSpotCatalog.Spot> left = spots(AgentRelaxerSpotCatalog.Pool.SOUTHPERRY_LEFT);
        List<AgentRelaxerSpotCatalog.Spot> right = spots(AgentRelaxerSpotCatalog.Pool.SOUTHPERRY_RIGHT);
        List<AgentRelaxerSpotCatalog.Spot> all = spots(AgentRelaxerSpotCatalog.Pool.SOUTHPERRY_ALL);
        List<AgentRelaxerSpotCatalog.Spot> faceHoles = spots(
                AgentRelaxerSpotCatalog.Pool.SOUTHPERRY_FACE_HOLES);

        assertEquals(117, amherst.size());
        assertEquals(12, nearPio.size());
        assertEquals(107, left.size());
        assertEquals(100, right.size());
        assertEquals(207, all.size());
        assertEquals(Set.of(
                        new AgentRelaxerSpotCatalog.Spot(2000000, 2432, 287),
                        new AgentRelaxerSpotCatalog.Spot(2000000, 2545, 287)),
                Set.copyOf(faceHoles));
        assertEquals(amherst.size(), new HashSet<>(amherst).size());
        assertTrue(nearPio.stream().allMatch(spot -> spot.mapId() == 1000000
                && spot.y() == 274 && spot.x() >= 250 && spot.x() <= 690
                && Math.abs(spot.x() - 547) >= 75));
        assertEquals(all.size(), new HashSet<>(all).size());
        assertTrue(left.stream().allMatch(spot -> spot.mapId() == AgentRelaxerSpotCatalog.SOUTHPERRY_MAP_ID
                && spot.x() < AgentRelaxerSpotCatalog.SOUTHPERRY_MIDPOINT_X));
        assertTrue(right.stream().allMatch(spot -> spot.mapId() == AgentRelaxerSpotCatalog.SOUTHPERRY_MAP_ID
                && spot.x() >= AgentRelaxerSpotCatalog.SOUTHPERRY_MIDPOINT_X));
        assertTrue(all.stream().noneMatch(spot ->
                (spot.y() == -256 && spot.x() >= 2605 && spot.x() <= 2708)
                        || (spot.y() == -196 && spot.x() >= 2941 && spot.x() <= 3124)));
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

    @Test
    void controlledReservationStartsAtSeededIndexThenSkipsOccupiedSpot() {
        var pool = AgentRelaxerSpotCatalog.Pool.SOUTHPERRY_ALL;
        List<AgentRelaxerSpotCatalog.Spot> candidates = spots(pool);
        Character first = mock(Character.class);
        Character second = mock(Character.class);
        when(first.getId()).thenReturn(1);
        when(second.getId()).thenReturn(2);

        assertEquals(candidates.get(17),
                AgentRelaxerSpotReservationRuntime.reserveFromIndex(first, pool, 17).orElseThrow());
        assertEquals(candidates.get(18),
                AgentRelaxerSpotReservationRuntime.reserveFromIndex(second, pool, 17).orElseThrow());
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
