package server.agents.runtime.simulation;

/** Bounded explanation for the most recent simulation-tier transition attempt. */
public record AgentSimulationTransitionEvidence(
        AgentSimulationMode previousMode,
        AgentSimulationMode requestedMode,
        AgentSimulationMode resultingMode,
        Outcome outcome,
        long attemptedAtMs,
        long transitionCount) {

    public enum Outcome {
        APPLIED,
        ALREADY_IN_MODE,
        OUTCOME_RECONCILIATION_FAILED,
        MATERIALIZATION_FAILED
    }

    public AgentSimulationTransitionEvidence {
        if (previousMode == null || requestedMode == null || resultingMode == null
                || outcome == null || attemptedAtMs < 0 || transitionCount < 0) {
            throw new IllegalArgumentException("Valid simulation transition evidence is required");
        }
    }
}
