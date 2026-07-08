package server.agents.capabilities.supplies;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAmmoStateRuntimeTest {
    @Test
    void adaptsAmmoShareRequestedState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentAmmoStateRuntime.ammoShareRequested(entry));

        AgentAmmoStateRuntime.setAmmoShareRequested(entry, true);
        assertTrue(AgentAmmoStateRuntime.ammoShareRequested(entry));

        AgentAmmoStateRuntime.clearAmmoShareRequested(entry);
        assertFalse(AgentAmmoStateRuntime.ammoShareRequested(entry));
    }

    @Test
    void adaptsNoAmmoState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentAmmoStateRuntime.noAmmo(entry));
        assertFalse(AgentAmmoStateRuntime.ammoWarnSent(entry));

        AgentAmmoStateRuntime.setNoAmmo(entry, true);
        AgentAmmoStateRuntime.setAmmoWarnSent(entry, true);

        assertTrue(AgentAmmoStateRuntime.noAmmo(entry));
        assertTrue(AgentAmmoStateRuntime.ammoWarnSent(entry));

        AgentAmmoStateRuntime.clearAmmoWarningState(entry);

        assertFalse(AgentAmmoStateRuntime.noAmmo(entry));
        assertFalse(AgentAmmoStateRuntime.ammoWarnSent(entry));
    }
}
