package server.agents.runtime.simulation;

public final class AgentSimulationState {
    private volatile AgentSimulationMode mode = AgentSimulationMode.PRESENTATION;
    private volatile long modeSinceMs;
    private volatile long transitionCount;

    public AgentSimulationMode mode() {
        return mode;
    }

    public long modeSinceMs() {
        return modeSinceMs;
    }

    public long transitionCount() {
        return transitionCount;
    }

    public boolean transitionTo(AgentSimulationMode nextMode, long nowMs) {
        if (nextMode == null) {
            throw new IllegalArgumentException("Agent simulation mode is required");
        }
        if (mode == nextMode) {
            return false;
        }
        mode = nextMode;
        modeSinceMs = Math.max(0L, nowMs);
        transitionCount++;
        return true;
    }
}
