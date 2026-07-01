package server.agents.capabilities.trade;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy;
import server.agents.capabilities.trade.AgentMesoTradeService.Action;
import server.agents.capabilities.trade.AgentMesoTradeService.MesoTradeStartDecision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AgentMesoTradeServiceTest {
    @Test
    void missingOwnerRepliesWithLegacyMessage() {
        MesoTradeStartDecision decision = AgentMesoTradeService.decideStart("mesos", false, false, false, 1000);

        assertEquals(Action.REPLY, decision.action());
        assertEquals(AgentDialogueCatalog.tradeOwnerNotFoundReply(), decision.reply());
    }

    @Test
    void botBusyRepliesWithLegacyMessage() {
        MesoTradeStartDecision decision = AgentMesoTradeService.decideStart("mesos", true, true, false, 1000);

        assertEquals(Action.REPLY, decision.action());
        assertEquals(AgentDialogueCatalog.tradeBotBusyReply(), decision.reply());
    }

    @Test
    void ownerBusyRepliesWithLegacyMessage() {
        MesoTradeStartDecision decision = AgentMesoTradeService.decideStart("mesos", true, false, true, 1000);

        assertEquals(Action.REPLY, decision.action());
        assertEquals(AgentDialogueCatalog.tradeOwnerBusyReply(), decision.reply());
    }

    @Test
    void noMesosRepliesWithNoItemsMessage() {
        MesoTradeStartDecision decision = AgentMesoTradeService.decideStart("mesos", true, false, false, 0);

        assertEquals(Action.REPLY, decision.action());
        assertFalse(decision.reply().isBlank());
    }

    @Test
    void invalidRequestedMesosRepliesWithLegacyMessage() {
        MesoTradeStartDecision decision = AgentMesoTradeService.decideStart("mesos:nope", true, false, false, 1000);

        assertEquals(Action.REPLY, decision.action());
        assertEquals(AgentDialogueCatalog.tradeMesoInvalidReply(), decision.reply());
    }

    @Test
    void insufficientMesosRepliesWithLegacyMessage() {
        MesoTradeStartDecision decision = AgentMesoTradeService.decideStart("mesos:5000", true, false, false, 1000);

        assertEquals(Action.REPLY, decision.action());
        assertEquals(AgentInventoryTradePolicy.notEnoughMesosReply(5000, 1000), decision.reply());
    }

    @Test
    void validRequestedMesosStartsTradeForRequestedAmount() {
        MesoTradeStartDecision decision = AgentMesoTradeService.decideStart("mesos:500", true, false, false, 1000);

        assertEquals(Action.START_TRADE, decision.action());
        assertEquals(500, decision.mesos());
        assertNull(decision.reply());
    }

    @Test
    void allMesosStartsTradeForCurrentAmount() {
        MesoTradeStartDecision decision = AgentMesoTradeService.decideStart("mesos", true, false, false, 1000);

        assertEquals(Action.START_TRADE, decision.action());
        assertEquals(1000, decision.mesos());
        assertNull(decision.reply());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routesMesoReplyDecisionToCallbacks() {
        java.util.function.IntConsumer start = mock(java.util.function.IntConsumer.class);
        java.util.function.Consumer<String> reply = mock(java.util.function.Consumer.class);

        AgentMesoTradeService.routeStart(MesoTradeStartDecision.reply("no mesos"), start, reply);

        verify(reply).accept("no mesos");
        verify(start, never()).accept(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routesMesoStartDecisionToCallbacks() {
        java.util.function.IntConsumer start = mock(java.util.function.IntConsumer.class);
        java.util.function.Consumer<String> reply = mock(java.util.function.Consumer.class);

        AgentMesoTradeService.routeStart(MesoTradeStartDecision.startTrade(1234), start, reply);

        verify(start).accept(1234);
        verify(reply, never()).accept(org.mockito.ArgumentMatchers.anyString());
    }
}
