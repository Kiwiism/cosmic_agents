package server.agents.capabilities.supplies;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAmmoSupplyStateTest {
    @Test
    void storesAmmoShareAndWarningFlags() {
        AgentAmmoSupplyState state = new AgentAmmoSupplyState();

        assertFalse(state.shareRequested());
        assertFalse(state.noAmmo());
        assertFalse(state.warnSent());

        state.setShareRequested(true);
        state.setNoAmmo(true);
        state.setWarnSent(true);

        assertTrue(state.shareRequested());
        assertTrue(state.noAmmo());
        assertTrue(state.warnSent());

        state.clearWarningState();

        assertTrue(state.shareRequested());
        assertFalse(state.noAmmo());
        assertFalse(state.warnSent());
    }
}
