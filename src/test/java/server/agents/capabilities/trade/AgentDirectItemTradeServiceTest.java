package server.agents.capabilities.trade;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.trade.AgentDirectItemTradeService.Action;
import server.agents.capabilities.trade.AgentDirectItemTradeService.DirectItemTradeDecision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AgentDirectItemTradeServiceTest {
    @Test
    void missingRecipientRepliesWithLegacyMessage() {
        DirectItemTradeDecision decision = AgentDirectItemTradeService.decideStart(false, true, false, false);

        assertEquals(Action.REPLY, decision.action());
        assertEquals(AgentDialogueCatalog.tradeRecipientNotFoundReply(), decision.reply());
    }

    @Test
    void missingItemRepliesWithLegacyMessage() {
        DirectItemTradeDecision decision = AgentDirectItemTradeService.decideStart(true, false, false, false);

        assertEquals(Action.REPLY, decision.action());
        assertEquals(AgentDialogueCatalog.tradeItemMissingReply(), decision.reply());
    }

    @Test
    void busyTradeRetriesWithoutReply() {
        DirectItemTradeDecision agentBusy = AgentDirectItemTradeService.decideStart(true, true, true, false);
        DirectItemTradeDecision recipientBusy = AgentDirectItemTradeService.decideStart(true, true, false, true);

        assertEquals(Action.RETRY, agentBusy.action());
        assertNull(agentBusy.reply());
        assertEquals(Action.RETRY, recipientBusy.action());
        assertNull(recipientBusy.reply());
    }

    @Test
    void availableTradeStartsWithoutReply() {
        DirectItemTradeDecision decision = AgentDirectItemTradeService.decideStart(true, true, false, false);

        assertEquals(Action.START_TRADE, decision.action());
        assertNull(decision.reply());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routesDirectTradeStartDecisionToCallbacks() {
        Runnable start = mock(Runnable.class);
        Runnable retry = mock(Runnable.class);
        java.util.function.Consumer<String> reply = mock(java.util.function.Consumer.class);

        AgentDirectItemTradeService.routeStart(DirectItemTradeDecision.startTrade(), start, retry, reply);

        verify(start).run();
        verify(retry, never()).run();
        verify(reply, never()).accept(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routesDirectTradeRetryDecisionToCallbacks() {
        Runnable start = mock(Runnable.class);
        Runnable retry = mock(Runnable.class);
        java.util.function.Consumer<String> reply = mock(java.util.function.Consumer.class);

        AgentDirectItemTradeService.routeStart(DirectItemTradeDecision.retry(), start, retry, reply);

        verify(retry).run();
        verify(start, never()).run();
        verify(reply, never()).accept(org.mockito.ArgumentMatchers.anyString());
    }
}
