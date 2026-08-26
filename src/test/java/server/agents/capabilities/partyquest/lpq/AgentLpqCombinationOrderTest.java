package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentLpqCombinationOrderTest {
    @Test
    void defaultsToTheJmsAttemptOrder() {
        List<List<Integer>> order = AgentLpqCombinationOrder.fiveOfNine();

        assertEquals(126, order.size());
        assertEquals(126, new HashSet<>(order).size());
        assertEquals(List.of(1, 3, 6, 7, 4), order.getFirst());
        assertEquals(List.of(1, 3, 6, 7, 8), order.get(1));
        assertEquals(List.of(1, 3, 6, 7, 2), order.get(2));
        assertEquals(List.of(1, 3, 6, 7, 5), order.get(3));
        assertEquals(List.of(1, 3, 6, 7, 9), order.get(4));
        assertEquals(List.of(1, 3, 6, 4, 8), order.get(5));
        assertEquals(List.of(4, 8, 2, 5, 9), order.getLast());
    }

    @Test
    void supportsTheGmsAttemptOrder() {
        List<List<Integer>> order = AgentLpqCombinationOrder.fiveOfNine(
                AgentLpqCombinationOrder.Method.GMS);

        assertEquals(126, order.size());
        assertEquals(126, new HashSet<>(order).size());
        assertEquals(List.of(1, 2, 3, 4, 5), order.getFirst());
        assertEquals(List.of(1, 2, 3, 4, 6), order.get(1));
        assertEquals(List.of(1, 2, 3, 4, 9), order.get(4));
        assertEquals(List.of(1, 2, 3, 5, 6), order.get(5));
        assertEquals(List.of(5, 6, 7, 8, 9), order.getLast());
    }

    @Test
    void validatesTheConfiguredMethod() {
        assertEquals(AgentLpqCombinationOrder.Method.JMS,
                AgentLpqCombinationOrder.parseMethod("jms"));
        assertEquals(AgentLpqCombinationOrder.Method.GMS,
                AgentLpqCombinationOrder.parseMethod("GMS"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentLpqCombinationOrder.parseMethod("gray"));
    }
}
