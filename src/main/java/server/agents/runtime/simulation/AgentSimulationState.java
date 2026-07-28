package server.agents.runtime.simulation;

public final class AgentSimulationState {
    private volatile AgentSimulationMode mode = AgentSimulationMode.PRESENTATION;
    private volatile long modeSinceMs;
    private volatile long transitionCount;
    private volatile AgentSimulationTransitionEvidence lastTransitionEvidence;

    public AgentSimulationMode mode() {
        return mode;
    }

    public long modeSinceMs() {
        return modeSinceMs;
    }

    public long transitionCount() {
        return transitionCount;
    }

    public AgentSimulationTransitionEvidence lastTransitionEvidence() {
        return lastTransitionEvidence;
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

    void recordTransitionAttempt(AgentSimulationMode previousMode,
                                 AgentSimulationMode requestedMode,
                                 AgentSimulationTransitionEvidence.Outcome outcome,
                                 long nowMs) {
        lastTransitionEvidence = new AgentSimulationTransitionEvidence(
                previousMode,
                requestedMode,
                mode,
                outcome,
                Math.max(0L, nowMs),
                transitionCount);
    }
}
