package server.agents.plans;

/** Stable identity for the transient runner attached to one universal plan step. */
final class AgentPlanAttachmentKey {
    private AgentPlanAttachmentKey() {
    }

    static String current(AgentPlanSessionState session) {
        if (session == null || !session.active() || !session.stepStartedValue()
                || session.planId().isBlank() || session.chainId().isBlank()) {
            return "";
        }
        return "universal:" + session.planId() + ':' + session.chainId()
                + ':' + session.stepIndex();
    }
}
