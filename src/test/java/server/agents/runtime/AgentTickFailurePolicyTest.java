package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotTickFailureStateRuntime;
import server.bots.BotEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentTickFailurePolicyTest {
    @Test
    void escalatesFromWarningToIdleToDisableWithinWindow() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);

        AgentTickFailurePolicy.Decision first = AgentTickFailurePolicy.recordFailure(entry, 1_000L);
        AgentTickFailurePolicy.Decision second = AgentTickFailurePolicy.recordFailure(entry, 2_000L);
        AgentTickFailurePolicy.Decision third = AgentTickFailurePolicy.recordFailure(entry, 3_000L);

        assertEquals(1, first.failureCount());
        assertFalse(first.forceIdle());
        assertFalse(first.disableAgent());
        assertEquals(2, second.failureCount());
        assertTrue(second.forceIdle());
        assertFalse(second.disableAgent());
        assertEquals(3, third.failureCount());
        assertFalse(third.forceIdle());
        assertTrue(third.disableAgent());
    }

    @Test
    void resetsFailures() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        AgentTickFailurePolicy.recordFailure(entry, 1_000L);

        AgentTickFailurePolicy.resetFailures(entry);

        assertFalse(AgentBotTickFailureStateRuntime.hasFailures(entry));
    }
}
