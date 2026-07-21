package server.agents.capabilities.movement;

import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/** Movement-facing seam for the navigation state needed during physical execution. */
public final class AgentMovementNavigationStateRuntime {
    private AgentMovementNavigationStateRuntime() {
    }

    public static boolean graphWarmupFallback(AgentRuntimeEntry entry) {
        return AgentNavigationDebugStateRuntime.graphWarmupFallback(entry);
    }
}
