package server.agents.capabilities.navigation;

import server.agents.runtime.AgentRuntimeEntry;

/** Typed access to the generic vertical traversal transaction state. */
public final class AgentVerticalTraversalStateRuntime {
    private AgentVerticalTraversalStateRuntime() {
    }

    public static boolean active(AgentRuntimeEntry entry) {
        return entry != null && entry.verticalTraversalState().active();
    }

    public static void clear(AgentRuntimeEntry entry) {
        if (entry != null) {
            entry.verticalTraversalState().clear();
        }
    }
}
