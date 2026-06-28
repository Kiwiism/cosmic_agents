package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotDegenerateAttackStateRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotDegenerateAttackStateRuntimeTest {
    @Test
    void adaptsDegenerateAttackLatch() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotDegenerateAttackStateRuntime.degenAttackDone(entry));

        AgentBotDegenerateAttackStateRuntime.markDegenAttackDone(entry);

        assertTrue(AgentBotDegenerateAttackStateRuntime.degenAttackDone(entry));

        AgentBotDegenerateAttackStateRuntime.clear(entry);

        assertFalse(AgentBotDegenerateAttackStateRuntime.degenAttackDone(entry));
    }
}
