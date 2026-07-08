package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.Trade;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.inventory.AgentInventoryRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentTradeInviteWaitServiceTest {
    @Test
    void belowTimeoutOnlyAddsTimer() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        AtomicBoolean reset = new AtomicBoolean(false);

        try (MockedStatic<AgentInventoryRuntime> replies = mockStatic(AgentInventoryRuntime.class);
             MockedStatic<Trade> trade = mockStatic(Trade.class)) {
            AgentTradeInviteWaitService.tickWaitingForAccept(entry, agent, 100, () -> reset.set(true));

            assertEquals(100, AgentPendingTradeStateRuntime.timerMs(entry));
            assertFalse(reset.get());
            replies.verifyNoInteractions();
            trade.verifyNoInteractions();
        }
    }

    @Test
    void overTimeoutRepliesCancelsAndResets() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        AtomicBoolean reset = new AtomicBoolean(false);
        AgentPendingTradeStateRuntime.setTimerMs(entry, 29_950);

        try (MockedStatic<AgentInventoryRuntime> replies = mockStatic(AgentInventoryRuntime.class);
             MockedStatic<Trade> trade = mockStatic(Trade.class)) {
            AgentTradeInviteWaitService.tickWaitingForAccept(entry, agent, 100, () -> reset.set(true));

            assertEquals(30_050, AgentPendingTradeStateRuntime.timerMs(entry));
            assertTrue(reset.get());
            replies.verify(() -> AgentInventoryRuntime.replyNow(
                    entry,
                    AgentDialogueCatalog.tradeRequestTimeoutReply()));
            trade.verify(() -> Trade.cancelTrade(agent, Trade.TradeResult.NO_RESPONSE));
        }
    }
}
