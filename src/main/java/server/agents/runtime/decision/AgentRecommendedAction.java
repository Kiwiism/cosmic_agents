package server.agents.runtime.decision;

import server.agents.runtime.activity.session.AgentActivityKind;

/** Advisory actions. Execution remains owned by activity sessions and the handoff coordinator. */
public enum AgentRecommendedAction {
    CONTINUE(null),
    RETRY_LOCAL(null),
    REPLAN_CURRENT(null),
    SUSPEND(null),
    RESUPPLY(null),
    REQUEST_TOWN_LIFE(AgentActivityKind.TOWN_LIFE),
    REQUEST_HUNTING(AgentActivityKind.HUNTING),
    REQUEST_QUESTING(AgentActivityKind.QUESTING),
    REQUEST_COMMERCE(AgentActivityKind.COMMERCE),
    REQUEST_PARTY_QUEST(AgentActivityKind.PARTY_QUEST),
    ABANDON_OBJECTIVE(null),
    SAFE_FALLBACK(null);

    private final AgentActivityKind targetKind;

    AgentRecommendedAction(AgentActivityKind targetKind) {
        this.targetKind = targetKind;
    }

    public AgentActivityKind targetKind() {
        return targetKind;
    }

    public boolean requestsActivity() {
        return targetKind != null;
    }
}
