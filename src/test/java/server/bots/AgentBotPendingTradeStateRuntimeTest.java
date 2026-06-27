package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotPendingTradeStateRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotPendingTradeStateRuntimeTest {
    @Test
    void adaptsPendingTradeActiveGuard() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotPendingTradeStateRuntime.hasActiveSequence(entry));
        assertTrue(AgentBotPendingTradeStateRuntime.isIdle(entry));

        entry.pendingTradeCategory = "trash";

        assertTrue(AgentBotPendingTradeStateRuntime.hasActiveSequence(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.isIdle(entry));
    }
}
