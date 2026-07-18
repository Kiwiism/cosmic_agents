package server.agents.policy.behavior;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.EnumMap;
import java.util.Map;

/** Session-local behavior selection; route lookup never mutates game state. */
public final class AgentBehaviorRouteTable {
    public static final AgentCapabilityStateKey<AgentBehaviorRouteTable> STATE_KEY =
            new AgentCapabilityStateKey<>("policy.behavior-routes", AgentBehaviorRouteTable.class,
                    AgentBehaviorRouteTable::new);

    private final Map<AgentBehaviorCapability, AgentBehaviorRoute> routes =
            new EnumMap<>(AgentBehaviorCapability.class);

    public synchronized void assign(AgentBehaviorRoute route) {
        if (route == null) {
            throw new IllegalArgumentException("Behavior route is required");
        }
        routes.put(route.capability(), route);
    }

    public synchronized AgentBehaviorRoute resolve(AgentBehaviorCapability capability) {
        AgentBehaviorRoute assigned = routes.get(capability);
        return assigned != null
                ? assigned
                : AgentBehaviorRoute.legacy(capability);
    }

    public synchronized Map<AgentBehaviorCapability, AgentBehaviorRoute> snapshot() {
        return Map.copyOf(routes);
    }
}
