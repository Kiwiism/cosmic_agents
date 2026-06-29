package server.agents.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentBotAmmoDonorPlanTest {
    @Test
    void preservesAmmoDonorSelectionData() {
        AgentBotAmmoDonorPlan plan = new AgentBotAmmoDonorPlan(null, 1200, false, 400);

        assertNull(plan.entry());
        assertEquals(1200, plan.matchingAmmoCount());
        assertFalse(plan.donorNeedsSameAmmo());
        assertEquals(400, plan.donationQty());
    }
}
