package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotOfferRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentBotOfferRuntimeTest {
    @Test
    void recommendedGearActionsReportMissingOwner() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotOfferRuntime.recommendedGearActions(entry, null, null).hasOwner());
    }
}
