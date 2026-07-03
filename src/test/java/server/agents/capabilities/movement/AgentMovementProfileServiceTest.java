package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentMovementProfileServiceTest {
    @Test
    void refreshMovementProfileReturnsFalseWhenProfileIsUnchanged() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotMovementStateRuntime.setMovementProfile(entry, AgentMovementProfile.base());

        assertFalse(AgentMovementProfileService.refreshMovementProfile(entry));
    }
}
