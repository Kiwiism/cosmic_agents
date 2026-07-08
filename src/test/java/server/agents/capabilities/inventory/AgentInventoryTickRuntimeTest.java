package server.agents.capabilities.inventory;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.Trade;
import server.agents.capabilities.trade.AgentManualTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentInventoryTickRuntimeTest {
    @Test
    void shouldCancelUnmanagedAgentTradeWhenManualTimeoutExpires() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        Trade trade = mock(Trade.class);

        when(agent.getId()).thenReturn(99);
        when(agent.getTrade()).thenReturn(trade);

        AgentInventoryTickRuntime.tickManualTrade(entry, agent);
        AgentManualTradeStateRuntime.setTimeoutMs(entry, 1);

        try (MockedStatic<Trade> trades = mockStatic(Trade.class)) {
            AgentInventoryTickRuntime.tickManualTrade(entry, agent);

            trades.verify(() -> Trade.cancelTrade(agent, Trade.TradeResult.NO_RESPONSE));
            assertNull(AgentManualTradeStateRuntime.tradeRef(entry));
            assertEquals(0, AgentManualTradeStateRuntime.timeoutMs(entry));
        }
    }
}
