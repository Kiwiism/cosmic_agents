package server.agents.capabilities.navigation;

import server.agents.runtime.AgentRuntimeEntry;

@FunctionalInterface
public interface AgentPortalRoutePolicy {
    AgentPortalRoutePolicy DIRECT = (entry, sourceMapId, destinationMapId) -> AgentPortalRoutePlan.DIRECT;

    AgentPortalRoutePlan plan(AgentRuntimeEntry entry, int sourceMapId, int destinationMapId);
}
