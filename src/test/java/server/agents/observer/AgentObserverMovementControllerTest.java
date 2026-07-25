package server.agents.observer;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentObserverMovementControllerTest {
    @Test
    void observationOrderStartsNearMapCenterAndSpreadsAcrossAvailableSpots() {
        assertEquals(5, AgentObserverMovementController.observationIndex(0, 10));

        Set<Integer> firstFour = new HashSet<>();
        for (int visit = 0; visit < 4; visit++) {
            firstFour.add(AgentObserverMovementController.observationIndex(visit, 10));
        }

        assertEquals(4, firstFour.size());
        assertTrue(firstFour.stream().anyMatch(index -> index < 3));
        assertTrue(firstFour.stream().anyMatch(index -> index > 6));
    }
}
