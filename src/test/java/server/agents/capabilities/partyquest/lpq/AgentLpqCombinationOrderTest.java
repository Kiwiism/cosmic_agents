package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLpqCombinationOrderTest {
    @Test
    void coversAllFiveOfNineFormationsWithOneMoverPerAttempt() {
        List<List<Integer>> order = AgentLpqCombinationOrder.fiveOfNine();

        assertEquals(126, order.size());
        assertEquals(126, new HashSet<>(order).size());
        assertEquals(List.of(1, 2, 3, 4, 5), order.getFirst());
        for (int index = 1; index < order.size(); index++) {
            assertTrue(AgentLpqCombinationOrder.oneMover(
                    order.get(index - 1), order.get(index)), "attempt " + index);
        }
    }
}
