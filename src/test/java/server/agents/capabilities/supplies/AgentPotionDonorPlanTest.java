package server.agents.capabilities.supplies;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPotionDonorPlanTest {
    @Test
    void preservesDonorEntryAndDonationQuantity() {
        AgentPotionDonorPlan<?> plan = new AgentPotionDonorPlan<>(null, 90);

        assertNull(plan.entry());
        assertEquals(90, plan.count());
        assertEquals(30, plan.donationQty());
    }

    @Test
    void qualifiesOnlyAboveLegacyLowPotionThresholdMultiplier() {
        int threshold = AgentRuntimeConfig.cfg.POT_LOW_WARN * 3;

        assertFalse(new AgentPotionDonorPlan<>(null, threshold).qualifies());
        assertTrue(new AgentPotionDonorPlan<>(null, threshold + 1).qualifies());
    }
}
