package server.agents.runtime.simulation;

import server.agents.runtime.AgentRuntimeEntry;

public final class AgentSimulationTransitionService {
    private final AgentMaterializationService materializationService;
    private final AgentBackgroundOutcomeReconciler outcomeReconciler;

    public AgentSimulationTransitionService(AgentMaterializationService materializationService,
                                            AgentBackgroundOutcomeReconciler outcomeReconciler) {
        if (materializationService == null || outcomeReconciler == null) {
            throw new IllegalArgumentException("Agent simulation transition dependencies are required");
        }
        this.materializationService = materializationService;
        this.outcomeReconciler = outcomeReconciler;
    }

    public AgentSimulationMode transition(AgentRuntimeEntry entry,
                                          AgentSimulationMode requestedMode,
                                          long nowMs) {
        if (entry == null || requestedMode == null) {
            throw new IllegalArgumentException("Agent runtime entry and simulation mode are required");
        }
        AgentSimulationMode currentMode = entry.simulationState().mode();
        if (currentMode == requestedMode) {
            if (currentMode == AgentSimulationMode.BACKGROUND_ABSTRACT) {
                entry.simulationState().backgroundOutcomes().begin(
                        entry.simulationState().abstractExecutionScope(),
                        nowMs);
            }
            entry.simulationState().recordTransitionAttempt(
                    currentMode,
                    requestedMode,
                    AgentSimulationTransitionEvidence.Outcome.ALREADY_IN_MODE,
                    nowMs);
            return currentMode;
        }
        if (currentMode == AgentSimulationMode.BACKGROUND_ABSTRACT
                && requestedMode != AgentSimulationMode.BACKGROUND_ABSTRACT
                && !outcomeReconciler.reconcile(entry)) {
            entry.simulationState().recordTransitionAttempt(
                    currentMode,
                    requestedMode,
                    AgentSimulationTransitionEvidence.Outcome.OUTCOME_RECONCILIATION_FAILED,
                    nowMs);
            return currentMode;
        }
        if (currentMode != AgentSimulationMode.PRESENTATION
                && requestedMode != AgentSimulationMode.BACKGROUND_ABSTRACT
                && !materializationService.materialize(entry)) {
            entry.simulationState().recordTransitionAttempt(
                    currentMode,
                    requestedMode,
                    AgentSimulationTransitionEvidence.Outcome.MATERIALIZATION_FAILED,
                    nowMs);
            return currentMode;
        }
        entry.simulationState().transitionTo(requestedMode, nowMs);
        if (requestedMode == AgentSimulationMode.BACKGROUND_ABSTRACT) {
            entry.simulationState().backgroundOutcomes().begin(
                    entry.simulationState().abstractExecutionScope(),
                    nowMs);
        }
        entry.simulationState().recordTransitionAttempt(
                currentMode,
                requestedMode,
                AgentSimulationTransitionEvidence.Outcome.APPLIED,
                nowMs);
        return requestedMode;
    }
}
