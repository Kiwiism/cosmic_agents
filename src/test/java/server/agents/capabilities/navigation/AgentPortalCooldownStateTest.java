package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPortalCooldownStateTest {
    @Test
    void gatesPortalUseUntilConfiguredDeadline() {
        AgentPortalCooldownState state = new AgentPortalCooldownState();

        assertEquals(0L, state.useCooldownUntilMs());
        assertFalse(state.onCooldown(1_000L));

        state.setUseCooldownUntilMs(1_500L);

        assertEquals(1_500L, state.useCooldownUntilMs());
        assertTrue(state.onCooldown(1_000L));
        assertFalse(state.onCooldown(1_500L));
    }
}
