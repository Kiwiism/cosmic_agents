package server.agents.capabilities.trade;

import org.junit.jupiter.api.Test;
import server.Trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class AgentManualTradeStateTest {
    @Test
    void defaultsPreserveLegacyBotEntryValues() {
        AgentManualTradeState state = new AgentManualTradeState();

        assertEquals(0, state.acceptDelayMs());
        assertNull(state.tradeRef());
        assertEquals(0, state.timeoutMs());
    }

    @Test
    void beginsTradeWithNoAcceptDelayAndTimeout() {
        AgentManualTradeState state = new AgentManualTradeState();
        Trade trade = mock(Trade.class);
        state.setAcceptDelayMs(600);

        state.begin(trade, 10_000);

        assertEquals(0, state.acceptDelayMs());
        assertSame(trade, state.tradeRef());
        assertEquals(10_000, state.timeoutMs());
    }

    @Test
    void ensuresAcceptDelayOnlyWhenUnset() {
        AgentManualTradeState state = new AgentManualTradeState();

        state.ensureAcceptDelay(600);
        state.ensureAcceptDelay(900);

        assertEquals(600, state.acceptDelayMs());
    }

    @Test
    void clearsWholeManualTradeTuple() {
        AgentManualTradeState state = new AgentManualTradeState();
        state.begin(mock(Trade.class), 10_000);
        state.setAcceptDelayMs(600);

        state.clear();

        assertEquals(0, state.acceptDelayMs());
        assertNull(state.tradeRef());
        assertEquals(0, state.timeoutMs());
    }
}
