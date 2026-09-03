package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;

public final class AgentHorizontalBoundaryStateRuntime {
    private AgentHorizontalBoundaryStateRuntime() {
    }

    public static void set(AgentRuntimeEntry entry, int mapId, int minX, int maxX) {
        if (entry == null) return;
        entry.capabilityStates().require(AgentHorizontalBoundaryState.STATE_KEY)
                .set(mapId, minX, maxX);
    }

    public static int clampX(AgentRuntimeEntry entry, int mapId, int x) {
        if (entry == null) return x;
        return entry.capabilityStates().find(AgentHorizontalBoundaryState.STATE_KEY)
                .map(state -> state.clampX(mapId, x))
                .orElse(x);
    }

    public static void clear(AgentRuntimeEntry entry) {
        if (entry == null) return;
        entry.capabilityStates().remove(AgentHorizontalBoundaryState.STATE_KEY)
                .ifPresent(AgentHorizontalBoundaryState::clear);
    }
}
