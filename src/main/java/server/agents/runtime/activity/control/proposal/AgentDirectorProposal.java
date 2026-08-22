package server.agents.runtime.activity.control.proposal;

import java.util.List;
import java.util.Map;

/** Durable, approval-gated high-level decision. It never carries executable code. */
public record AgentDirectorProposal(
        int schemaVersion,
        String proposalId,
        int agentId,
        AgentDirectorProposalSource source,
        String contextRevision,
        String actionId,
        String label,
        String rationale,
        Map<String, String> evidence,
        List<String> alternativeActionIds,
        int expectedEnergyDelta,
        long createdAtMs,
        long expiresAtMs,
        AgentDirectorProposalStatus status,
        long resolvedAtMs,
        String resolution,
        String directiveId) {

    public AgentDirectorProposal {
        proposalId = text(proposalId);
        contextRevision = text(contextRevision);
        actionId = text(actionId);
        label = text(label);
        rationale = text(rationale);
        resolution = text(resolution);
        directiveId = text(directiveId);
        evidence = Map.copyOf(evidence == null ? Map.of() : evidence);
        alternativeActionIds = List.copyOf(
                alternativeActionIds == null ? List.of() : alternativeActionIds);
        if (schemaVersion != 1 || proposalId.isEmpty() || agentId <= 0 || source == null
                || contextRevision.isEmpty() || actionId.isEmpty() || label.isEmpty()
                || rationale.isEmpty() || createdAtMs < 0L || expiresAtMs <= createdAtMs
                || status == null || resolvedAtMs < 0L) {
            throw new IllegalArgumentException("complete Director proposal is required");
        }
    }

    public boolean expiredAt(long nowMs) {
        return status == AgentDirectorProposalStatus.PENDING && nowMs >= expiresAtMs;
    }

    public AgentDirectorProposal resolve(
            AgentDirectorProposalStatus next, String reason, String executedDirectiveId,
            long nowMs) {
        if (next == null || next == AgentDirectorProposalStatus.PENDING
                || status != AgentDirectorProposalStatus.PENDING || nowMs < createdAtMs) {
            throw new IllegalStateException("only a pending proposal can be resolved");
        }
        return new AgentDirectorProposal(schemaVersion, proposalId, agentId, source,
                contextRevision, actionId, label, rationale, evidence, alternativeActionIds,
                expectedEnergyDelta, createdAtMs, expiresAtMs, next, nowMs, reason,
                executedDirectiveId);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
