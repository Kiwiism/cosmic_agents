package server.agents.runtime;

import org.junit.jupiter.api.Test;
import server.bots.BotManager;

import static org.junit.jupiter.api.Assertions.assertSame;

class AgentRuntimeConfigTest {
    @Test
    void botManagerConfigIsCompatibilityAliasForAgentRuntimeConfig() {
        assertSame(AgentRuntimeConfig.cfg, BotManager.cfg);
    }
}
