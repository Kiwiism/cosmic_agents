package server.agents.capabilities.trade;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentDialogueSelector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

class AgentTradeDialogueServiceTest {
    @Test
    void selectsTradeRepliesFromAgentDialogueCatalogPools() {
        try (MockedStatic<AgentDialogueSelector> dialogueSelector = mockStatic(AgentDialogueSelector.class)) {
            dialogueSelector.when(() -> AgentDialogueSelector.randomReply(AgentDialogueCatalog.tradeInvitationReplies()))
                    .thenReturn("invite");
            dialogueSelector.when(() -> AgentDialogueSelector.randomReply(AgentDialogueCatalog.tradeAllDoneReplies()))
                    .thenReturn("done");
            dialogueSelector.when(() -> AgentDialogueSelector.randomReply(AgentDialogueCatalog.tradeThanksReplies()))
                    .thenReturn("thanks");
            dialogueSelector.when(() -> AgentDialogueSelector.randomReply(AgentDialogueCatalog.tradeFreebieReplies()))
                    .thenReturn("freebie");
            dialogueSelector.when(() -> AgentDialogueSelector.randomReply(AgentDialogueCatalog.tradeReservedForOtherReplies()))
                    .thenReturn("other");
            dialogueSelector.when(() -> AgentDialogueSelector.randomReply(AgentDialogueCatalog.tradeReservedForSelfReplies()))
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
