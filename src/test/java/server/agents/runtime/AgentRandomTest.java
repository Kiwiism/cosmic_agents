package server.agents.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRandomTest {
    @Test
    void randMsUsesInclusiveLowerExclusiveUpperRange() {
        for (int i = 0; i < 100; i++) {
            long value = AgentRandom.randMs(5, 8);

            assertTrue(value >= 5);
            assertTrue(value < 8);
        }
    }

    @Test
    void randMsReturnsLowerBoundWhenOnlyOneValueIsPossible() {
        assertEquals(5, AgentRandom.randMs(5, 6));
    }

    @Test
    void randMsPreservesLegacyInvalidRangeFailure() {
        assertThrows(IllegalArgumentException.class, () -> AgentRandom.randMs(5, 5));
    }
}
