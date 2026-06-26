package server.agents.capabilities.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class AgentSupplyRequestOutcomeFlowTest {
    @Test
    void returnsNoReplyWhenPotionShareFoundDonor() {
        assertNull(AgentSupplyRequestOutcomeFlow.potionShareReply(false, true, "out of %s"));
    }

    @Test
    void formatsPotionShortageReplyByPotionType() {
        assertEquals(
                "out of hp",
                AgentSupplyRequestOutcomeFlow.potionShareReply(true, true, "out of %s"));
        assertEquals(
                "out of mp",
                AgentSupplyRequestOutcomeFlow.potionShareReply(true, false, "out of %s"));
    }

    @Test
    void passesThroughAmmoNotNeededReply() {
        assertEquals("no ammo needed", AgentSupplyRequestOutcomeFlow.ammoNotNeededReply("no ammo needed"));
    }

    @Test
    void returnsAmmoShortageReplyOnlyWhenNoDonorExists() {
        assertEquals("no arrows", AgentSupplyRequestOutcomeFlow.ammoShareReply(true, "no arrows"));
        assertNull(AgentSupplyRequestOutcomeFlow.ammoShareReply(false, "no arrows"));
    }

    @Test
    void selectsPotionShortageReplyFromLegacyCatalog() {
        String reply = AgentSupplyRequestOutcomeFlow.potionShareReply(true, true);
        List<String> possibleReplies = AgentDialogueCatalog.ownerPotShortageReplies().stream()
                .map(template -> AgentDialogueReportFormatter.ownerPotShortageReply(template, "hp"))
                .toList();

        assertTrue(possibleReplies.contains(reply));
        assertNull(AgentSupplyRequestOutcomeFlow.potionShareReply(false, true));
    }

    @Test
    void selectsAmmoRepliesFromLegacyCatalog() {
        assertTrue(AgentDialogueCatalog.ammoNotNeededReplies()
                .contains(AgentSupplyRequestOutcomeFlow.ammoNotNeededReply()));
        assertTrue(AgentDialogueCatalog.ownerAmmoShortageReplies()
                .contains(AgentSupplyRequestOutcomeFlow.ammoShareReply(true)));
        assertNull(AgentSupplyRequestOutcomeFlow.ammoShareReply(false));
    }
}
