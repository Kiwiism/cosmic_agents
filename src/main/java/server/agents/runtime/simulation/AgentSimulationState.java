package server.agents.runtime.simulation;

public final class AgentSimulationState {
    private volatile AgentSimulationMode mode = AgentSimulationMode.PRESENTATION;
    private volatile AgentAbstractExecutionScope abstractExecutionScope =
            AgentAbstractExecutionScope.NONE;
    private volatile long modeSinceMs;
    private volatile long transitionCount;
    private volatile AgentSimulationTransitionEvidence lastTransitionEvidence;
    private final AgentBackgroundOutcomeLedger backgroundOutcomes =
            new AgentBackgroundOutcomeLedger();

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

    public AgentAbstractExecutionScope abstractExecutionScope() {
        return abstractExecutionScope;
    }

    public AgentBackgroundOutcomeLedger backgroundOutcomes() {
        return backgroundOutcomes;
    }

    public void allowAbstractExecution(AgentAbstractExecutionScope scope) {
        if (scope == null || scope == AgentAbstractExecutionScope.NONE) {
            throw new IllegalArgumentException("An abstract execution scope is required");
        }
        abstractExecutionScope = scope;
    }

    public void clearAbstractExecution(AgentAbstractExecutionScope scope) {
        if (scope != null && abstractExecutionScope == scope) {
            abstractExecutionScope = AgentAbstractExecutionScope.NONE;
        }
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
