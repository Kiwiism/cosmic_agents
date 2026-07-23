package server.agents.capabilities.townlife;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTownLifeActivitySequenceStateTest {
    @Test
    void advancesThroughBoundedHumanReadablePhases() {
        AgentTownLifeActivitySequenceState state = new AgentTownLifeActivitySequenceState();

        state.start(1_000L, 11_000L);

        assertEquals(AgentTownLifeActivitySequenceState.Phase.ORIENT, state.phase());
        assertEquals(AgentTownLifeActivitySequenceState.Phase.OPENING, state.advance(1_800L));
        assertEquals(AgentTownLifeActivitySequenceState.Phase.PERFORMING, state.advance(3_000L));
        assertEquals(AgentTownLifeActivitySequenceState.Phase.REACTION, state.advance(9_100L));
        assertEquals(AgentTownLifeActivitySequenceState.Phase.CLOSING, state.advance(10_300L));
        assertEquals(AgentTownLifeActivitySequenceState.Phase.COMPLETE, state.advance(11_000L));
    }

    @Test
    void tracksOnePerformanceStartPerActivity() {
        AgentTownLifeActivitySequenceState state = new AgentTownLifeActivitySequenceState();
        state.start(0L, 5_000L);

        assertFalse(state.performanceStarted());
        state.markPerformanceStarted();
        assertTrue(state.performanceStarted());
        state.clear();
        assertFalse(state.performanceStarted());
    }
}
