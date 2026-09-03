package server.agents.capabilities.movement;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Optional map-local virtual walls for activities that must retain headless Agents. */
public final class AgentHorizontalBoundaryState {
    public static final AgentCapabilityStateKey<AgentHorizontalBoundaryState> STATE_KEY =
            new AgentCapabilityStateKey<>("movement.horizontal-boundary",
                    AgentHorizontalBoundaryState.class, AgentHorizontalBoundaryState::new);

    private int mapId = -1;
    private int minX;
    private int maxX;

    public synchronized void set(int mapId, int minX, int maxX) {
        if (mapId < 0 || minX > maxX) {
            throw new IllegalArgumentException("Valid map-local horizontal bounds are required");
        }
        this.mapId = mapId;
        this.minX = minX;
        this.maxX = maxX;
    }

    public synchronized int clampX(int currentMapId, int x) {
        return currentMapId == mapId ? Math.clamp(x, minX, maxX) : x;
    }

    public synchronized void clear() {
        mapId = -1;
        minX = 0;
        maxX = 0;
    }
}
