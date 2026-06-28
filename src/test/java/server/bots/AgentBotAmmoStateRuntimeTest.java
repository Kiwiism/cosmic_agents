package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotAmmoStateRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotAmmoStateRuntimeTest {
    @Test
    void adaptsAmmoShareRequestedState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotAmmoStateRuntime.ammoShareRequested(entry));

        AgentBotAmmoStateRuntime.setAmmoShareRequested(entry, true);
        assertTrue(AgentBotAmmoStateRuntime.ammoShareRequested(entry));

        AgentBotAmmoStateRuntime.clearAmmoShareRequested(entry);
        assertFalse(AgentBotAmmoStateRuntime.ammoShareRequested(entry));
    }

    @Test
    void adaptsNoAmmoState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotAmmoStateRuntime.noAmmo(entry));
        assertFalse(AgentBotAmmoStateRuntime.ammoWarnSent(entry));

        AgentBotAmmoStateRuntime.setNoAmmo(entry, true);
        AgentBotAmmoStateRuntime.setAmmoWarnSent(entry, true);

        assertTrue(AgentBotAmmoStateRuntime.noAmmo(entry));
        assertTrue(AgentBotAmmoStateRuntime.ammoWarnSent(entry));

        AgentBotAmmoStateRuntime.clearAmmoWarningState(entry);

        assertFalse(AgentBotAmmoStateRuntime.noAmmo(entry));
        assertFalse(AgentBotAmmoStateRuntime.ammoWarnSent(entry));
    }
}
