package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDegenerateAttackStateRuntimeTest {
    @Test
    void adaptsDegenerateAttackLatch() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentDegenerateAttackStateRuntime.degenAttackDone(entry));

        AgentDegenerateAttackStateRuntime.markDegenAttackDone(entry);

        assertTrue(AgentDegenerateAttackStateRuntime.degenAttackDone(entry));

        AgentDegenerateAttackStateRuntime.clear(entry);

        assertFalse(AgentDegenerateAttackStateRuntime.degenAttackDone(entry));
    }
}
