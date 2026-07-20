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
}
