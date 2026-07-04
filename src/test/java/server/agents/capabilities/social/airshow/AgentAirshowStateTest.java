package server.agents.capabilities.social.airshow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAirshowStateTest {
    @Test
    void storesAirshowLifecycleAndTrailTimestamp() {
        AgentAirshowState state = new AgentAirshowState();

        assertFalse(state.active());
        assertEquals(0L, state.lastTrailAtMs());

        state.setActive(true);
        state.setLastTrailAtMs(1_500L);

        assertTrue(state.active());
        assertEquals(1_500L, state.lastTrailAtMs());
    }
}
