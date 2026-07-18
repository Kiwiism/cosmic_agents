package server.agents.capabilities.supplies;

import server.agents.capabilities.contracts.AgentProcurementRequest;
import server.agents.capabilities.contracts.AgentResourceCategory;
import server.agents.capabilities.contracts.AgentSupplyNeed;
import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.EnumMap;
import java.util.Map;

public final class AgentResourcePlanningState {
    public static final AgentCapabilityStateKey<AgentResourcePlanningState> STATE_KEY =
            new AgentCapabilityStateKey<>("supplies.resource-planning", AgentResourcePlanningState.class,
                    AgentResourcePlanningState::new);

    private final Map<AgentResourceCategory, AgentSupplyNeed> needs =
            new EnumMap<>(AgentResourceCategory.class);
    private final Map<AgentResourceCategory, AgentProcurementRequest> procurements =
            new EnumMap<>(AgentResourceCategory.class);

    public synchronized AgentSupplyNeed need(AgentResourceCategory category) {
        return needs.get(category);
    }

    public synchronized AgentProcurementRequest procurement(AgentResourceCategory category) {
        return procurements.get(category);
    }

    synchronized void update(AgentSupplyNeed need, AgentProcurementRequest procurement) {
        needs.put(need.category(), need);
        if (procurement == null) {
            procurements.remove(need.category());
        } else {
            procurements.put(need.category(), procurement);
        }
    }
}
