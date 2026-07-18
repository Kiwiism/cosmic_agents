package server.agents.capabilities.supplies;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.contracts.AgentProcurementMethod;
import server.agents.capabilities.contracts.AgentResourceCategory;
import server.agents.capabilities.contracts.AgentSupplyNeed;
import server.agents.capabilities.contracts.AgentSupplyUrgency;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentSupplyPlannerTest {
    @Test
    void criticalNeedPrefersStructuredTransferThenNpcShop() {
        AgentSupplyNeed need = new AgentSupplyNeed(AgentResourceCategory.HP_POTION,
                2, 50, AgentSupplyUrgency.CRITICAL, "grind", 100);
        var plan = AgentSupplyPlanner.plan(need, 10_000, 5_000);

        assertEquals(48, plan.quantity());
        assertEquals(AgentProcurementMethod.COHORT_TRANSFER, plan.permittedMethods().getFirst());
        assertEquals(AgentProcurementMethod.NPC_SHOP, plan.permittedMethods().get(1));
    }
}
