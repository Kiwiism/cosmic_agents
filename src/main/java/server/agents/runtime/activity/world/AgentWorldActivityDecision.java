package server.agents.runtime.activity.world;

import server.agents.runtime.activity.session.AgentActivityKind;

/** Deterministic World Director output; callers perform admission and handoff separately. */
public record AgentWorldActivityDecision(
        AgentActivityKind kind,
        String proposalId,
        boolean switchRequired,
        String evidence) {
    public static AgentWorldActivityDecision idle() {
        return new AgentWorldActivityDecision(null, "", false, "no eligible activity proposal");
    }
}
