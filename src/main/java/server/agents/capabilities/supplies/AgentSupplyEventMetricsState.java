package server.agents.capabilities.supplies;

import server.agents.capabilities.contracts.AgentSupplyUrgency;
import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.EnumMap;
import java.util.Map;

/** Session-local supply event counters for diagnostics and future read models. */
public final class AgentSupplyEventMetricsState {
    public static final AgentCapabilityStateKey<AgentSupplyEventMetricsState> STATE_KEY =
            new AgentCapabilityStateKey<>("supplies.event-metrics", AgentSupplyEventMetricsState.class,
                    AgentSupplyEventMetricsState::new);

    private final Map<AgentSupplyUrgency, Long> transitions = new EnumMap<>(AgentSupplyUrgency.class);
    private AgentSupplyThresholdChangedEvent lastEvent;

    public synchronized void record(AgentSupplyThresholdChangedEvent event) {
        transitions.merge(event.urgency(), 1L, Long::sum);
        lastEvent = event;
    }

    public synchronized long transitionsTo(AgentSupplyUrgency urgency) {
        return transitions.getOrDefault(urgency, 0L);
    }

    public synchronized AgentSupplyThresholdChangedEvent lastEvent() {
        return lastEvent;
    }
}
