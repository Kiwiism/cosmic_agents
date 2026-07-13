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
            return currentMode;
        }
        if (requestedMode == AgentSimulationMode.PRESENTATION
                && currentMode == AgentSimulationMode.BACKGROUND_ABSTRACT
                && !outcomeReconciler.reconcile(entry)) {
            return currentMode;
        }
        if (requestedMode == AgentSimulationMode.PRESENTATION
                && currentMode != AgentSimulationMode.PRESENTATION
                && !materializationService.materialize(entry)) {
            return currentMode;
        }
        entry.simulationState().transitionTo(requestedMode, nowMs);
        return requestedMode;
    }
}
