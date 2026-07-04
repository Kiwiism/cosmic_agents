package server.agents.capabilities.social;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentScrollReactionStateTest {
    @Test
    void ownsCooldownLoadAndScrollerStreakState() {
        AgentScrollReactionState state = new AgentScrollReactionState();

        state.setRecentLoad(2.5);
        state.setLastObservedAtMs(100L);
        state.setNextReactionAtMs(200L);
        state.setNextStreakPruneAtMs(300L);

        AgentScrollReactionState.StreakState streak = new AgentScrollReactionState.StreakState();
        streak.streak = 2;
        streak.lastWasSuccess = true;
        streak.lastOutcomeAtMs = 150L;
        state.streaksByScroller().put(10, streak);

        assertEquals(2.5, state.recentLoad());
        assertEquals(100L, state.lastObservedAtMs());
        assertEquals(200L, state.nextReactionAtMs());
        assertEquals(300L, state.nextStreakPruneAtMs());
        assertEquals(2, state.streaksByScroller().get(10).streak);
        assertTrue(state.streaksByScroller().get(10).lastWasSuccess);
        assertFalse(state.streaksByScroller().containsKey(11));
    }
}
