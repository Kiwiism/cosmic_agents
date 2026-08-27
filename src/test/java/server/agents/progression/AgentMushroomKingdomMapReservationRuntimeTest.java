package server.agents.progression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentMushroomKingdomMapReservationRuntimeTest {
    private final List<AgentMushroomKingdomCatalog.HuntMap> maps = List.of(
            new AgentMushroomKingdomCatalog.HuntMap(1, "best", 3),
            new AgentMushroomKingdomCatalog.HuntMap(2, "second", 2));

    @AfterEach
    void clearReservations() {
        AgentMushroomKingdomMapReservationRuntime.clear();
    }

    @Test
    void pendingTravelReservationsAtomicallyConsumeMapCapacity() {
        Map<Integer, Integer> assignments = new HashMap<>();

        for (int agentId = 1; agentId <= 5; agentId++) {
            var selected = reserve(agentId, Map.of(), 1_000L).map();
            assignments.merge(selected.mapId(), 1, Integer::sum);
        }

        assertEquals(Map.of(1, 3, 2, 2), assignments);
        assertEquals(5, AgentMushroomKingdomMapReservationRuntime.reservationCount());
    }

    @Test
    void reservationExpiresWhenTravelDoesNotFinishInTime() {
        assertEquals(1, reserve(1, Map.of(), 1_000L).map().mapId());

        var afterExpiry = AgentMushroomKingdomMapReservationRuntime.selectAndReserve(
                2, 0, 1, maps, Map.of(), 1_101L, 100L).orElseThrow();

        assertEquals(1, afterExpiry.map().mapId());
        assertEquals(1, AgentMushroomKingdomMapReservationRuntime.reservationCount());
    }

    @Test
    void reservationIsReleasedAsSoonAsAnArrivedAgentLeaves() {
        reserve(1, Map.of(), 1_000L);
        assertEquals(AgentMushroomKingdomMapReservationRuntime.LeaseState.OCCUPYING,
                AgentMushroomKingdomMapReservationRuntime.maintain(
                        1, 1, 1, 1_010L, 100L));

        assertEquals(AgentMushroomKingdomMapReservationRuntime.LeaseState.LEFT,
                AgentMushroomKingdomMapReservationRuntime.maintain(
                        1, 1, 99, 1_020L, 100L));
        assertEquals(0, AgentMushroomKingdomMapReservationRuntime.reservationCount());
    }

    private AgentMushroomKingdomHuntMapSelector.Selection reserve(
            int agentId, Map<Integer, Integer> liveOccupancy, long nowMs) {
        return AgentMushroomKingdomMapReservationRuntime.selectAndReserve(
                agentId, 0, 1, maps, liveOccupancy, nowMs, 100L).orElseThrow();
    }
}
