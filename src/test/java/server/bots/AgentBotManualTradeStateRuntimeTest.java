package server.bots;

import org.junit.jupiter.api.Test;
import server.Trade;
import server.agents.integration.AgentBotManualTradeStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class AgentBotManualTradeStateRuntimeTest {
    @Test
    void adaptsManualTradeInviteState() {
        BotEntry entry = new BotEntry(null, null, null);
        Trade trade = mock(Trade.class);

        AgentBotManualTradeStateRuntime.beginTrade(entry, trade, 10_000);

        assertSame(trade, AgentBotManualTradeStateRuntime.tradeRef(entry));
        assertEquals(0, AgentBotManualTradeStateRuntime.acceptDelayMs(entry));
        assertEquals(10_000, AgentBotManualTradeStateRuntime.timeoutMs(entry));

        AgentBotManualTradeStateRuntime.ensureAcceptDelay(entry, 600);
        assertEquals(600, AgentBotManualTradeStateRuntime.acceptDelayMs(entry));
        AgentBotManualTradeStateRuntime.ensureAcceptDelay(entry, 900);
        assertEquals(600, AgentBotManualTradeStateRuntime.acceptDelayMs(entry));

        AgentBotManualTradeStateRuntime.setAcceptDelayMs(entry, 0);
        AgentBotManualTradeStateRuntime.setTimeoutMs(entry, 0);
        AgentBotManualTradeStateRuntime.clear(entry);

        assertNull(AgentBotManualTradeStateRuntime.tradeRef(entry));
        assertEquals(0, AgentBotManualTradeStateRuntime.acceptDelayMs(entry));
        assertEquals(0, AgentBotManualTradeStateRuntime.timeoutMs(entry));
    }
}
