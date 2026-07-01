package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.Trade;
import server.agents.integration.AgentBotManualTradeStateRuntime;
import server.bots.BotEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentManualTradeServiceTest {
    @Test
    void beginsManualTradeStateForNewTradeWindow() {
        Character bot = mock(Character.class);
        Trade trade = mock(Trade.class);
        BotEntry entry = new BotEntry(bot, null, null);

        boolean cancelled = AgentManualTradeService.beginOrTickTimeout(entry, bot, trade, 60_000, value -> value);

        assertFalse(cancelled);
        assertSame(trade, AgentBotManualTradeStateRuntime.tradeRef(entry));
        assertEquals(60_000, AgentBotManualTradeStateRuntime.timeoutMs(entry));
    }

    @Test
    void defaultManualTradeTimeoutMatchesLegacyDuration() {
        Character bot = mock(Character.class);
        Trade trade = mock(Trade.class);
        BotEntry entry = new BotEntry(bot, null, null);

        boolean cancelled = AgentManualTradeService.beginOrTickTimeout(entry, bot, trade, value -> value);

        assertFalse(cancelled);
        assertSame(trade, AgentBotManualTradeStateRuntime.tradeRef(entry));
        assertEquals(60_000, AgentBotManualTradeStateRuntime.timeoutMs(entry));
    }

    @Test
    void cancelsAndClearsManualTradeWhenTimeoutExpires() {
        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(88);
        Trade trade = mock(Trade.class);
        BotEntry entry = new BotEntry(bot, null, null);
        AgentBotManualTradeStateRuntime.beginTrade(entry, trade, 100);

        try (MockedStatic<Trade> trades = mockStatic(Trade.class)) {
            boolean cancelled = AgentManualTradeService.beginOrTickTimeout(entry, bot, trade, 60_000, value -> 0);

            assertTrue(cancelled);
            trades.verify(() -> Trade.cancelTrade(bot, Trade.TradeResult.NO_RESPONSE));
            assertNull(AgentBotManualTradeStateRuntime.tradeRef(entry));
            assertEquals(0, AgentBotManualTradeStateRuntime.timeoutMs(entry));
        }
    }

    @Test
    void sendsManualTradeGreetingOnlyOnceUntilCleared() {
        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(99);
        Trade trade = mock(Trade.class);

        AgentManualTradeService.clearGreeting(bot);
        AgentManualTradeService.sendGreetingOnce(bot, trade, () -> "hi");
        AgentManualTradeService.sendGreetingOnce(bot, trade, () -> "hi again");

        verify(trade, times(1)).chat("hi");

        AgentManualTradeService.clearGreeting(bot);
        AgentManualTradeService.sendGreetingOnce(bot, trade, () -> "hi after clear");

        verify(trade).chat("hi after clear");
    }

    @Test
    void waitsBeforeAcceptingManualTradeInvite() {
        Character bot = mock(Character.class);
        Character inviter = mock(Character.class);
        Trade trade = mock(Trade.class);
        BotEntry entry = new BotEntry(bot, null, null);
        when(trade.getNumber()).thenReturn((byte) 1);

        Trade result = AgentManualTradeService.acceptInviteWhenReady(entry, bot, inviter, trade, 600, value -> 100);

        assertSame(trade, result);
        assertEquals(100, AgentBotManualTradeStateRuntime.acceptDelayMs(entry));
    }

    @Test
    void acceptsManualTradeInviteAfterDelayExpires() {
        Character bot = mock(Character.class);
        Character inviter = mock(Character.class);
        Trade trade = mock(Trade.class);
        Trade joinedTrade = mock(Trade.class);
        BotEntry entry = new BotEntry(bot, null, null);
        when(trade.getNumber()).thenReturn((byte) 1);
        when(bot.getTrade()).thenReturn(joinedTrade);
        when(joinedTrade.isFullTrade()).thenReturn(true);

        try (MockedStatic<Trade> trades = mockStatic(Trade.class)) {
            Trade result = AgentManualTradeService.acceptInviteWhenReady(entry, bot, inviter, trade, 600, value -> 0);

            trades.verify(() -> Trade.visitTrade(bot, inviter));
            assertSame(joinedTrade, result);
        }
    }
}
