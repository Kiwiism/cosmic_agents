package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotPotionStateRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotPotionStateRuntimeTest {
    @Test
    void adaptsPotionShareRequestedStateByPotionType() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotPotionStateRuntime.setPotShareRequested(entry, true, true);
        AgentBotPotionStateRuntime.setPotShareRequested(entry, false, true);

        assertTrue(AgentBotPotionStateRuntime.potShareRequested(entry, true));
        assertTrue(AgentBotPotionStateRuntime.potShareRequested(entry, false));

        AgentBotPotionStateRuntime.clearPotShareRequested(entry, true);
        assertFalse(AgentBotPotionStateRuntime.potShareRequested(entry, true));
        assertTrue(AgentBotPotionStateRuntime.potShareRequested(entry, false));

        AgentBotPotionStateRuntime.clearAllPotShareRequests(entry);
        assertFalse(AgentBotPotionStateRuntime.potShareRequested(entry, true));
        assertFalse(AgentBotPotionStateRuntime.potShareRequested(entry, false));
    }
}
