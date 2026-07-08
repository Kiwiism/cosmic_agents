package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.Trade;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.integration.AgentBotInventoryRuntime;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentTradeConfirmWaitServiceTest {
    @Test
    void partnerConfirmedCompletesMarksDoneAndClearsTimer() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        Trade trade = mock(Trade.class);
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean reset = new AtomicBoolean(false);
        AgentBotPendingTradeStateRuntime.setTimerMs(entry, 500);
        when(trade.isPartnerConfirmed()).thenReturn(true);

        boolean handled = AgentTradeConfirmWaitService.tickWaitingForConfirmation(
                entry,
                agent,
                trade,
                100,
                () -> null,
                recipient -> false,
                () -> completed.set(true),
                () -> reset.set(true));

        assertTrue(handled);
        assertTrue(completed.get());
        assertFalse(reset.get());
        assertTrue(AgentBotPendingTradeStateRuntime.botDone(entry));
        assertEquals(0, AgentBotPendingTradeStateRuntime.timerMs(entry));
    }

    @Test
    void botRecipientCompletesWithoutPartnerConfirmation() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        Character recipient = mock(Character.class);
        Trade trade = mock(Trade.class);
        AtomicBoolean completed = new AtomicBoolean(false);

        AgentTradeConfirmWaitService.tickWaitingForConfirmation(
                entry,
                agent,
                trade,
                100,
                () -> recipient,
                found -> true,
                () -> completed.set(true),
                () -> {});

        assertTrue(completed.get());
        assertTrue(AgentBotPendingTradeStateRuntime.botDone(entry));
        assertEquals(0, AgentBotPendingTradeStateRuntime.timerMs(entry));
    }

    @Test
    void belowTimeoutOnlyAddsTimer() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        Trade trade = mock(Trade.class);
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean reset = new AtomicBoolean(false);

        try (MockedStatic<AgentBotInventoryRuntime> replies = mockStatic(AgentBotInventoryRuntime.class);
             MockedStatic<Trade> tradeStatic = mockStatic(Trade.class)) {
            AgentTradeConfirmWaitService.tickWaitingForConfirmation(
                    entry,
                    agent,
                    trade,
                    100,
                    () -> null,
                    recipient -> false,
                    () -> completed.set(true),
                    () -> reset.set(true));

            assertEquals(100, AgentBotPendingTradeStateRuntime.timerMs(entry));
            assertFalse(completed.get());
            assertFalse(reset.get());
            replies.verifyNoInteractions();
            tradeStatic.verifyNoInteractions();
        }
    }

    @Test
    void overTimeoutRepliesCancelsAndResets() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        Trade trade = mock(Trade.class);
        AtomicBoolean reset = new AtomicBoolean(false);
        AgentBotPendingTradeStateRuntime.setTimerMs(entry, 59_950);

        try (MockedStatic<AgentBotInventoryRuntime> replies = mockStatic(AgentBotInventoryRuntime.class);
             MockedStatic<Trade> tradeStatic = mockStatic(Trade.class)) {
            AgentTradeConfirmWaitService.tickWaitingForConfirmation(
                    entry,
                    agent,
                    trade,
                    100,
                    () -> null,
                    recipient -> false,
                    () -> {},
                    () -> reset.set(true));

            assertEquals(60_050, AgentBotPendingTradeStateRuntime.timerMs(entry));
            assertTrue(reset.get());
            replies.verify(() -> AgentBotInventoryRuntime.replyNow(
                    entry,
                    AgentDialogueCatalog.tradeConfirmTimeoutReply()));
            tradeStatic.verify(() -> Trade.cancelTrade(agent, Trade.TradeResult.NO_RESPONSE));
        }
    }
}
