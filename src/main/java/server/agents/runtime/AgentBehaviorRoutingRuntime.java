package server.agents.runtime;

import server.agents.policy.behavior.AgentBehaviorCapability;
import server.agents.policy.behavior.AgentBehaviorRoute;
import server.agents.policy.behavior.AgentBehaviorRouteTable;

public final class AgentBehaviorRoutingRuntime {
    private AgentBehaviorRoutingRuntime() {
    }

    public static void assign(AgentRuntimeEntry entry, AgentBehaviorRoute route) {
        entry.capabilityStates().require(AgentBehaviorRouteTable.STATE_KEY).assign(route);
    }

    public static AgentBehaviorRoute resolve(AgentRuntimeEntry entry, AgentBehaviorCapability capability) {
        return entry.capabilityStates().require(AgentBehaviorRouteTable.STATE_KEY).resolve(capability);
    }
}
