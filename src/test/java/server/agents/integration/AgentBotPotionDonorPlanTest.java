package server.agents.integration;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotPotionDonorPlanTest {
    @Test
    void preservesDonorEntryAndDonationQuantity() {
        AgentBotPotionDonorPlan plan = new AgentBotPotionDonorPlan(null, 90);

        assertNull(plan.entry());
        assertEquals(90, plan.count());
        assertEquals(30, plan.donationQty());
    }

    @Test
    void qualifiesOnlyAboveLegacyLowPotionThresholdMultiplier() {
        int threshold = AgentRuntimeConfig.cfg.POT_LOW_WARN * 3;

        assertFalse(new AgentBotPotionDonorPlan(null, threshold).qualifies());
        assertTrue(new AgentBotPotionDonorPlan(null, threshold + 1).qualifies());
    }
}
