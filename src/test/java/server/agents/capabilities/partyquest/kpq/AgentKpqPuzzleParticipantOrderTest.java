package server.agents.capabilities.partyquest.kpq;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentKpqPuzzleParticipantOrderTest {
    @Test
    void ratiosDoNotNeedToSumToOne() {
        assertArrayEquals(new double[]{3.4d, 3.3d, 3.3d},
                AgentKpqPuzzleParticipantOrder.parseWeights("3.4, 3.3, 3.3"));
    }

    @Test
    void zeroWeightsGiveFirstHumanLeastMovementAndSecondNextLeast() {
        double[] weights = {1.0d, 0.0d, 0.0d};
        int first = AgentKpqPuzzleParticipantOrder.chooseRank(List.of(0, 1, 2), 7L, 10, 0, weights);
        int second = AgentKpqPuzzleParticipantOrder.chooseRank(List.of(1, 2), 7L, 11, 1, weights);
        assertEquals(0, first);
        assertEquals(1, second);
    }

    @Test
    void rejectsAnAllZeroRatio() {
        assertThrows(IllegalStateException.class,
                () -> AgentKpqPuzzleParticipantOrder.parseWeights("0,0,0"));
    }
}
