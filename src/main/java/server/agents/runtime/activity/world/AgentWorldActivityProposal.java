package server.agents.runtime.activity.world;

import server.agents.runtime.activity.session.AgentActivityKind;

/** One system's evidence-backed request for primary ownership. */
public record AgentWorldActivityProposal(
        String proposalId,
        AgentActivityKind kind,
        int priority,
        long utility,
        boolean eligible,
        String evidence) {
    public AgentWorldActivityProposal {
        proposalId = proposalId == null ? "" : proposalId.trim();
        evidence = evidence == null ? "" : evidence.trim();
        if (proposalId.isEmpty() || kind == null) {
            throw new IllegalArgumentException("world activity proposal identity is required");
        }
    }
}
