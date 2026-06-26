package server.agents.capabilities.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

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
}
