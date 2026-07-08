package server.agents.capabilities.trade;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.Trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class AgentManualTradeStateRuntimeTest {
    @Test
    void adaptsManualTradeInviteState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Trade trade = mock(Trade.class);

        AgentManualTradeStateRuntime.beginTrade(entry, trade, 10_000);

        assertSame(trade, AgentManualTradeStateRuntime.tradeRef(entry));
        assertEquals(0, AgentManualTradeStateRuntime.acceptDelayMs(entry));
        assertEquals(10_000, AgentManualTradeStateRuntime.timeoutMs(entry));

        AgentManualTradeStateRuntime.ensureAcceptDelay(entry, 600);
        assertEquals(600, AgentManualTradeStateRuntime.acceptDelayMs(entry));
        AgentManualTradeStateRuntime.ensureAcceptDelay(entry, 900);
        assertEquals(600, AgentManualTradeStateRuntime.acceptDelayMs(entry));

        AgentManualTradeStateRuntime.setAcceptDelayMs(entry, 0);
        AgentManualTradeStateRuntime.setTimeoutMs(entry, 0);
        AgentManualTradeStateRuntime.clear(entry);

        assertNull(AgentManualTradeStateRuntime.tradeRef(entry));
        assertEquals(0, AgentManualTradeStateRuntime.acceptDelayMs(entry));
        assertEquals(0, AgentManualTradeStateRuntime.timeoutMs(entry));
    }
}
