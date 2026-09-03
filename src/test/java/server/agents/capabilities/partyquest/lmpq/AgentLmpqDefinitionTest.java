package server.agents.capabilities.partyquest.lmpq;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLmpqDefinitionTest {
    @Test
    void declaresTheAuthoredDirectedPortalGraph() {
        List<List<Integer>> expected = List.of(
                List.of(5, 8, 13), List.of(6, 9, 14), List.of(7, 10, 15),
                List.of(8, 11, 1), List.of(9, 12, 2), List.of(10, 13, 3),
                List.of(11, 14, 4), List.of(12, 15, 5), List.of(13, 16, 6),
                List.of(14, 2, 7), List.of(15, 3, 8), List.of(1, 4, 9),
                List.of(2, 5, 10), List.of(3, 6, 11), List.of(4, 7, 12));
        for (int room = 1; room <= 15; room++) {
            assertEquals(expected.get(room - 1), AgentLmpqDefinition.edges(room).stream()
                    .map(AgentLmpqDefinition.Edge::destinationRoom).toList());
        }
        assertEquals(16, AgentLmpqDefinition.edges(9).get(1).destinationRoom());
        assertEquals(9, AgentLmpqDefinition.edges(16).getFirst().destinationRoom());
        assertEquals(2, AgentLmpqDefinition.nextPortalId(1, 16));
        assertEquals(3, AgentLmpqDefinition.nextPortalId(9, 16));
        assertEquals(4, AgentLmpqDefinition.nextPortalId(12, 16));
    }

    @Test
    void everyRandomStartReachesPierreWithinFivePortals() {
        for (int room = 1; room <= 15; room++) {
            int distance = AgentLmpqDefinition.distance(room, AgentLmpqDefinition.CLEAR_ROOM);
            assertTrue(distance >= 1 && distance <= 5, "room " + room + " distance=" + distance);
        }
        assertEquals(100, AgentLmpqDefinition.yieldPriority(6));
        assertEquals(80, AgentLmpqDefinition.yieldPriority(7));
    }
}
