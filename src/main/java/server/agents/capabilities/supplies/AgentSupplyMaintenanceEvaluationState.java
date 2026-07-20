package server.agents.capabilities.supplies;

import server.agents.capabilities.contracts.AgentResourceCategory;
import server.agents.capabilities.contracts.AgentSupplyUrgency;
import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.EnumMap;
import java.util.Map;

/** Next-tick maintenance signals projected from supply threshold events. */
public final class AgentSupplyMaintenanceEvaluationState {
    public static final AgentCapabilityStateKey<AgentSupplyMaintenanceEvaluationState> STATE_KEY =
            new AgentCapabilityStateKey<>("supplies.maintenance-evaluation",
                    AgentSupplyMaintenanceEvaluationState.class,
                    AgentSupplyMaintenanceEvaluationState::new);

    private final Map<AgentResourceCategory, AgentSupplyThresholdChangedEvent> pending =
            new EnumMap<>(AgentResourceCategory.class);

    public synchronized void project(AgentSupplyThresholdChangedEvent event) {
        if (event.urgency().ordinal() >= AgentSupplyUrgency.CRITICAL.ordinal()) {
            pending.put(event.category(), event);
        } else {
            pending.remove(event.category());
        }
    }

    public synchronized AgentSupplyThresholdChangedEvent next() {
        return pending.values().stream()
                .max(java.util.Comparator
                        .comparingInt((AgentSupplyThresholdChangedEvent event) -> event.urgency().ordinal())
                        .thenComparingLong(AgentSupplyThresholdChangedEvent::occurredAtMs))
                .orElse(null);
    }

    public synchronized void resolve(AgentResourceCategory category) {
        pending.remove(category);
    }
}
