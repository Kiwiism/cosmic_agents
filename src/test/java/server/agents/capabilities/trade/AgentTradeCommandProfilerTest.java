package server.agents.capabilities.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTradeCommandProfilerTest {
    @Test
    void shouldProfileLegacySlowTradeCategories() {
        assertTrue(AgentTradeCommandProfiler.profileCategory("trash"));
        assertTrue(AgentTradeCommandProfiler.profileCategory("equips"));
        assertTrue(AgentTradeCommandProfiler.profileCategory("equips:reserved:2"));

        assertFalse(AgentTradeCommandProfiler.profileCategory("etc"));
        assertFalse(AgentTradeCommandProfiler.profileCategory("pots"));
        assertFalse(AgentTradeCommandProfiler.profileCategory(null));
    }
}
