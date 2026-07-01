package server.agents.capabilities.trade;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.bots.BotManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

class AgentTradeDialogueServiceTest {
    @Test
    void selectsTradeRepliesFromAgentDialogueCatalogPools() {
        try (MockedStatic<BotManager> botManager = mockStatic(BotManager.class)) {
            botManager.when(() -> BotManager.randomReply(AgentDialogueCatalog.tradeInvitationReplies()))
                    .thenReturn("invite");
            botManager.when(() -> BotManager.randomReply(AgentDialogueCatalog.tradeAllDoneReplies()))
                    .thenReturn("done");
            botManager.when(() -> BotManager.randomReply(AgentDialogueCatalog.tradeThanksReplies()))
                    .thenReturn("thanks");
            botManager.when(() -> BotManager.randomReply(AgentDialogueCatalog.tradeFreebieReplies()))
                    .thenReturn("freebie");
            botManager.when(() -> BotManager.randomReply(AgentDialogueCatalog.tradeReservedForOtherReplies()))
                    .thenReturn("other");
            botManager.when(() -> BotManager.randomReply(AgentDialogueCatalog.tradeReservedForSelfReplies()))
                    .thenReturn("self");

            assertEquals("invite", AgentTradeDialogueService.invitationReply());
            assertEquals("done", AgentTradeDialogueService.allDoneReply());
            assertEquals("thanks", AgentTradeDialogueService.thanksReply());
            assertEquals("freebie", AgentTradeDialogueService.freebieReply());
            assertEquals("other", AgentTradeDialogueService.reservedForOtherReply());
            assertEquals("self", AgentTradeDialogueService.reservedForSelfReply());
            assertEquals("other", AgentTradeDialogueService.equipsGroupMessage("equips:reserved_for_other"));
            assertEquals("self", AgentTradeDialogueService.equipsGroupMessage("equips:reserved_for_self"));
            assertNull(AgentTradeDialogueService.equipsGroupMessage("equips:normal"));
        }
    }
}
