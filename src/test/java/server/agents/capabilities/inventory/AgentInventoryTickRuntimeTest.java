package server.agents.capabilities.inventory;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.Trade;
import server.agents.integration.AgentBotManualTradeStateRuntime;
import server.bots.BotEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentInventoryTickRuntimeTest {
    @Test
    void shouldCancelUnmanagedAgentTradeWhenManualTimeoutExpires() {
        Character agent = mock(Character.class);
        BotEntry entry = new BotEntry(agent, null, null);
        Trade trade = mock(Trade.class);

        when(agent.getId()).thenReturn(99);
        when(agent.getTrade()).thenReturn(trade);

        AgentInventoryTickRuntime.tickManualTrade(entry, agent);
        AgentBotManualTradeStateRuntime.setTimeoutMs(entry, 1);

        try (MockedStatic<Trade> trades = mockStatic(Trade.class)) {
            AgentInventoryTickRuntime.tickManualTrade(entry, agent);

            trades.verify(() -> Trade.cancelTrade(agent, Trade.TradeResult.NO_RESPONSE));
            assertNull(AgentBotManualTradeStateRuntime.tradeRef(entry));
            assertEquals(0, AgentBotManualTradeStateRuntime.timeoutMs(entry));
        }
    }
}
