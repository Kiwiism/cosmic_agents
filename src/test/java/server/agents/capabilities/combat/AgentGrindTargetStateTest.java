package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;
import server.life.Monster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class AgentGrindTargetStateTest {
    @Test
    void storesTargetAndSearchCooldown() {
        AgentGrindTargetState state = new AgentGrindTargetState();
        Monster target = mock(Monster.class);

        assertNull(state.target());
        assertEquals(0L, state.nextSearchAtMs());

        state.setTarget(target);
        state.setNextSearchAtMs(2_000L);

        assertSame(target, state.target());
        assertEquals(2_000L, state.nextSearchAtMs());

        state.clearTarget();
        state.clearNextSearchAtMs();

        assertNull(state.target());
        assertEquals(0L, state.nextSearchAtMs());
    }
}
