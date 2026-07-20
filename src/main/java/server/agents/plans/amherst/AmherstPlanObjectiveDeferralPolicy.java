package server.agents.plans.amherst;

import server.agents.capabilities.runtime.AgentCapabilityResult;

import java.util.List;

/**
 * Optional plan extension for visiting explicitly independent work while a scarce world-resource
 * objective is waiting. The runner remains ordered unless a plan supplies this policy.
 */
public interface AmherstPlanObjectiveDeferralPolicy {
    AmherstPlanObjectiveDeferralPolicy NONE = new AmherstPlanObjectiveDeferralPolicy() {
    };

    default boolean canDefer(AmherstPlanCard card,
                             AmherstPlanObjective blocked,
                             AgentCapabilityResult result) {
        return false;
    }

    /**
     * @param deferralStage one-based stage, advanced only after a previous alternative batch finishes
     */
    default List<AmherstPlanObjective> independentAlternatives(
            AmherstPlanCard card,
            AmherstPlanObjective blocked,
            AmherstPlanProgressSnapshot progress,
            int deferralStage) {
        return List.of();
    }

    /**
     * Number of declared alternative-work stages. A runner records an empty stage as visited so a
     * resumed plan can advance past work that live-state reconciliation already satisfied.
     */
    default int alternativeStageCount(AmherstPlanCard card,
                                      AmherstPlanObjective blocked) {
        return 0;
    }

    /**
     * Allows a plan to enter slow, indefinite world-resource rechecks immediately after all of its
     * alternative-work stages have been attempted.
     */
    default boolean waitForWorldResourceAfterAlternatives(
            AmherstPlanCard card,
            AmherstPlanObjective blocked,
            AgentCapabilityResult result,
            int nextDeferralStage) {
        return false;
    }
}
