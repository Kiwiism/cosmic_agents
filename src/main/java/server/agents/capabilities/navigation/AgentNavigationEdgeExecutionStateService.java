package server.agents.capabilities.navigation;

import server.agents.integration.AgentNavigationDebugStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned state writes used while executing a committed navigation edge.
 */
public final class AgentNavigationEdgeExecutionStateService {
    private AgentNavigationEdgeExecutionStateService() {
    }

    public static void setEdgeExecutionTarget(AgentRuntimeEntry entry, AgentNavigationGraph.Edge edge) {
        AgentNavigationDebugStateRuntime.setNavWaypoint(entry, edge.endPoint, false);
    }
}
