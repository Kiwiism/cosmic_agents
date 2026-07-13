package server.agents.runtime.simulation;

import server.agents.runtime.AgentRuntimeEntry;

@FunctionalInterface
public interface AgentBackgroundOutcomeReconciler {
    boolean reconcile(AgentRuntimeEntry entry);

    static AgentBackgroundOutcomeReconciler noPendingOutcomes() {
        return entry -> true;
    }
}
