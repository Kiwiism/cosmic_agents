package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;
import server.life.Monster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentGrindTargetStateTest {
    @Test
    void storesTargetAndSearchCooldown() {
        AgentGrindTargetState state = new AgentGrindTargetState();
        Monster target = mock(Monster.class);

        assertNull(state.target());
        assertEquals(0L, state.nextSearchAtMs());

        state.commitTarget(target, 2_000L);
        state.setNextSearchAtMs(2_000L);

        assertSame(target, state.target());
        assertTrue(state.committedTo(target, 1_999L));
        assertFalse(state.committedTo(target, 2_000L));
        assertEquals(2_000L, state.nextSearchAtMs());

        state.clearTarget();
        state.clearNextSearchAtMs();

        assertNull(state.target());
        assertFalse(state.committedTo(target, 1_000L));
        assertEquals(0L, state.nextSearchAtMs());
    }
}
