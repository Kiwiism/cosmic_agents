package server.agents.economy.market;

import org.junit.jupiter.api.Test;
import server.agents.economy.scenario.NamedRandomStreams;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoomVisitPlannerTest {
    @Test
    void plansOnlyInsideConfiguredPhysicalRoomManifest() {
        RoomVisitPlanner planner = new RoomVisitPlanner(910000005, 910000007);

        var rooms = planner.plan(3, 3, new NamedRandomStreams(41));

        assertEquals(Set.of(910000005, 910000006, 910000007), Set.copyOf(rooms));
    }

    @Test
    void rejectsTripLargerThanConfiguredPhysicalRoomManifest() {
        RoomVisitPlanner planner = new RoomVisitPlanner(910000001, 910000001);

        assertThrows(IllegalArgumentException.class,
                () -> planner.plan(1, 2, new NamedRandomStreams(41)));
    }
}
