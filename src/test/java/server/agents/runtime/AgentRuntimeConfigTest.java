package server.agents.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeConfigTest {
    @Test
    void agentRuntimeConfigExposesRuntimeTickSettings() {
        assertTrue(AgentRuntimeConfig.cfg.AI_TICK_MS > 0);
    }
}
