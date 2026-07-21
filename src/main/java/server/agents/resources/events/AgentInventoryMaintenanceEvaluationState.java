package server.agents.resources.events;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.LinkedHashMap;
import java.util.Map;

/** Next-tick inventory-maintenance signals projected from capacity events. */
public final class AgentInventoryMaintenanceEvaluationState {
    public static final AgentCapabilityStateKey<AgentInventoryMaintenanceEvaluationState> STATE_KEY =
            new AgentCapabilityStateKey<>("inventory.maintenance-evaluation",
                    AgentInventoryMaintenanceEvaluationState.class,
                    AgentInventoryMaintenanceEvaluationState::new);

    private final Map<String, AgentInventoryThresholdChangedEvent> pending = new LinkedHashMap<>();

    public synchronized void project(AgentInventoryThresholdChangedEvent event) {
        if ("FULL".equals(event.threshold())) {
            pending.put(event.inventoryType(), event);
        } else {
            pending.remove(event.inventoryType());
        }
    }

    public synchronized AgentInventoryThresholdChangedEvent next() {
        return pending.values().stream()
                .max(java.util.Comparator.comparingLong(
                        AgentInventoryThresholdChangedEvent::occurredAtMs))
                .orElse(null);
    }

    public synchronized void resolve(String inventoryType) {
        pending.remove(inventoryType);
    }
}
