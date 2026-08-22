package server.agents.runtime.activity.control.chat;

import server.agents.runtime.activity.control.proposal.AgentDirectorProposal;

public record AgentDirectorChatResult(
        String reply,
        AgentDirectorProposal proposal,
        String provider,
        long latencyMs) {
    public AgentDirectorChatResult {
        reply = reply == null ? "" : reply.trim();
        provider = provider == null ? "" : provider.trim();
        if (reply.isEmpty() || latencyMs < 0L) {
            throw new IllegalArgumentException("complete Director chat result is required");
        }
    }
}
