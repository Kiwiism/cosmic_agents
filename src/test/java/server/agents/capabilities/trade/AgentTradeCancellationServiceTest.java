package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.Trade;
import server.agents.integration.AgentInventoryRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentTradeCancellationServiceTest {
    @Test
    void cancelWithoutActiveTradeRepliesAndResets() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        AtomicBoolean reset = new AtomicBoolean(false);

        try (MockedStatic<AgentInventoryRuntime> replies = mockStatic(AgentInventoryRuntime.class);
             MockedStatic<Trade> trade = mockStatic(Trade.class)) {
            AgentTradeCancellationService.cancelSequence(entry, agent, "stop", () -> reset.set(true));

            replies.verify(() -> AgentInventoryRuntime.replyNow(entry, "stop"));
            trade.verifyNoInteractions();
            assertTrue(reset.get());
        }
    }

    @Test
    void cancelWithActiveTradeCancelsNoResponseThenResets() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        Trade activeTrade = mock(Trade.class);
        AtomicBoolean reset = new AtomicBoolean(false);
        when(agent.getTrade()).thenReturn(activeTrade);

        try (MockedStatic<AgentInventoryRuntime> replies = mockStatic(AgentInventoryRuntime.class);
             MockedStatic<Trade> trade = mockStatic(Trade.class)) {
            AgentTradeCancellationService.cancelSequence(entry, agent, "stop", () -> reset.set(true));

            replies.verify(() -> AgentInventoryRuntime.replyNow(entry, "stop"));
            trade.verify(() -> Trade.cancelTrade(agent, Trade.TradeResult.NO_RESPONSE));
            assertTrue(reset.get());
        }
    }
}
