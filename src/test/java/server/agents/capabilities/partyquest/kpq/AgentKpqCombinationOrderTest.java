package server.agents.capabilities.partyquest.kpq;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentKpqCombinationOrderTest {
    @Test
    void visitsEveryCombinationOnceAndMovesOnlyOneMember() {
        for (int positions = 4; positions <= 6; positions++) {
            List<List<Integer>> order = AgentKpqCombinationOrder.forPositionCount(positions);
            int expected = switch (positions) {
                case 4 -> 4;
                case 5 -> 10;
                default -> 20;
            };
            assertEquals(expected, order.size());
            assertEquals(expected, new HashSet<>(order).size());
            assertEquals(List.of(1, 2, 3), order.getFirst());
            for (int attempt = 1; attempt < order.size(); attempt++) {
                assertTrue(AgentKpqCombinationOrder.oneMover(
                        order.get(attempt - 1), order.get(attempt)));
            }
        }
    }
}
