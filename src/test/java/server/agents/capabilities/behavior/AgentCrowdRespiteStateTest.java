package server.agents.capabilities.behavior;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCrowdRespiteStateTest {
    @Test
    void completedRespiteHasAnActiveWindowBeforeItCanRestart() {
        AgentCrowdRespiteState state = new AgentCrowdRespiteState();
        state.start(1_000L, 2_000L, new Point(10, 20), true);

        assertTrue(state.active());
        state.finish(7_000L);

        assertFalse(state.active());
        assertFalse(state.eligible(6_999L));
        assertTrue(state.eligible(7_000L));
    }
}
