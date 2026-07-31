package server.agents.runtime;

import server.agents.policy.behavior.AgentBehaviorCapability;
import server.agents.policy.behavior.AgentBehaviorMode;
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

    /** Assigns one stable capability implementation to an Agent for a bounded canary rollout. */
    public static AgentBehaviorRoute assignStableCanary(
            AgentRuntimeEntry entry,
            AgentBehaviorCapability capability,
            String legacyVersion,
            String candidateVersion,
            int rolloutPercent) {
        if (entry == null || capability == null
                || legacyVersion == null || legacyVersion.isBlank()
                || candidateVersion == null || candidateVersion.isBlank()
                || rolloutPercent < 0 || rolloutPercent > 100) {
            throw new IllegalArgumentException("Complete behavior rollout inputs are required");
        }
        int agentId = entry.bot() == null ? 0 : entry.bot().getId();
        boolean candidate = Math.floorMod(31 * agentId + capability.name().hashCode(), 100)
                < rolloutPercent;
        AgentBehaviorRoute route = candidate
                ? AgentBehaviorRoute.reconstructed(capability, candidateVersion)
                : new AgentBehaviorRoute(
                        capability, AgentBehaviorMode.LEGACY,
                        legacyVersion, "");
        assign(entry, route);
        return route;
    }
}
