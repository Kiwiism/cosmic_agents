package server.agents.capabilities.supplies;

import server.agents.capabilities.contracts.AgentProcurementMethod;
import server.agents.capabilities.contracts.AgentProcurementRequest;
import server.agents.capabilities.contracts.AgentSupplyNeed;

import java.util.List;

/** Pure conversion from observed need to an acquisition request. */
public final class AgentSupplyPlanner {
    private AgentSupplyPlanner() {
    }

    public static AgentProcurementRequest plan(AgentSupplyNeed need, long maximumBudget, long expiresAtMs) {
        if (need == null || need.shortfall() <= 0) {
            throw new IllegalArgumentException("A supply shortfall is required");
        }
        List<AgentProcurementMethod> methods = switch (need.urgency()) {
            case EMPTY, CRITICAL -> List.of(AgentProcurementMethod.COHORT_TRANSFER,
                    AgentProcurementMethod.NPC_SHOP, AgentProcurementMethod.STORAGE);
            case LOW -> List.of(AgentProcurementMethod.NPC_SHOP,
                    AgentProcurementMethod.COHORT_TRANSFER, AgentProcurementMethod.LOOT);
            case HEALTHY -> List.of(AgentProcurementMethod.LOOT, AgentProcurementMethod.NPC_SHOP);
        };
        return new AgentProcurementRequest(
                "supply:" + need.category() + ':' + need.observedAtMs(),
                need.category(), need.shortfall(), maximumBudget, methods, need.urgency(),
                need.objectiveId(), expiresAtMs);
    }
}
