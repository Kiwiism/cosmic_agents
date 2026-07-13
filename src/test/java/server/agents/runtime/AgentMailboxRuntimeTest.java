package server.agents.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMailboxRuntimeTest {
    @AfterEach
    void clearProperties() {
        System.clearProperty("agents.mailbox.enabled");
        System.clearProperty("agents.scheduler.mode");
        System.clearProperty("agents.scheduler.central.enabled");
    }

    @Test
    void legacyModeKeepsMailboxOwnershipDisabledByDefault() {
        assertFalse(AgentMailboxRuntime.enabled());
    }

    @Test
    void centralModesRequireMailboxOwnership() {
        System.setProperty("agents.scheduler.mode", "central-sequential");
        assertTrue(AgentMailboxRuntime.enabled());

        System.setProperty("agents.scheduler.mode", "central-sharded");
        assertTrue(AgentMailboxRuntime.enabled());
    }

    @Test
    void legacyModeCanOptIntoMailboxOwnership() {
        System.setProperty("agents.mailbox.enabled", "true");
        assertTrue(AgentMailboxRuntime.enabled());
    }

    @Test
    void centralDispatchDefersMutationUntilMailboxDrain() {
        System.setProperty("agents.scheduler.mode", "central-sequential");
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AtomicInteger mutations = new AtomicInteger();

        AgentMailboxRuntime.dispatch(entry, ignored -> mutations.incrementAndGet());

        assertEquals(0, mutations.get());
        assertEquals(1, entry.actionMailbox().drain(entry, 1));
        assertEquals(1, mutations.get());
    }
}
