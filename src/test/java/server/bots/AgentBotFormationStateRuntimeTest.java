package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotFormationStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentBotFormationStateRuntimeTest {
    @Test
    void storesFollowOffset() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotFormationStateRuntime.setFollowOffsetX(entry, -60);

        assertEquals(-60, AgentBotFormationStateRuntime.followOffsetX(entry));
    }
}
