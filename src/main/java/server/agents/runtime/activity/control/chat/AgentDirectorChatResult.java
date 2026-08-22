package server.agents.runtime.activity.control.chat;

import server.agents.runtime.activity.control.proposal.AgentDirectorProposal;

import java.util.List;

public record AgentDirectorChatResult(
        String reply,
        AgentDirectorProposal proposal,
        List<AgentDirectorChatRecommendation> recommendations,
        String provider,
        long latencyMs) {
    public AgentDirectorChatResult {
        reply = reply == null ? "" : reply.trim();
        recommendations = List.copyOf(recommendations == null ? List.of() : recommendations);
        provider = provider == null ? "" : provider.trim();
        if (reply.isEmpty() || latencyMs < 0L) {
            throw new IllegalArgumentException("complete Director chat result is required");
        }
    }
}
