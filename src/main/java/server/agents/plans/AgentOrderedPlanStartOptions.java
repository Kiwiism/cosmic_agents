package server.agents.plans;

import server.agents.plans.amherst.AmherstPlanExecutionMode;
import server.agents.plans.amherst.AmherstPlanObserver;

public record AgentOrderedPlanStartOptions(
        AmherstPlanExecutionMode mode,
        AmherstPlanObserver observer,
        long initialObjectiveDelayMs) {

    public static AgentOrderedPlanStartOptions automatic(AmherstPlanObserver observer) {
        return automatic(observer, 0L);
    }

    public static AgentOrderedPlanStartOptions automatic(
            AmherstPlanObserver observer, long initialObjectiveDelayMs) {
        return new AgentOrderedPlanStartOptions(
                AmherstPlanExecutionMode.AUTO, observer, initialObjectiveDelayMs);
    }

    public static AgentOrderedPlanStartOptions manual(AmherstPlanObserver observer) {
        return new AgentOrderedPlanStartOptions(AmherstPlanExecutionMode.MANUAL, observer, 0L);
    }

    public AgentOrderedPlanStartOptions {
        mode = mode == null ? AmherstPlanExecutionMode.AUTO : mode;
        observer = observer == null ? AmherstPlanObserver.NONE : observer;
        initialObjectiveDelayMs = Math.max(0L, initialObjectiveDelayMs);
    }
}
