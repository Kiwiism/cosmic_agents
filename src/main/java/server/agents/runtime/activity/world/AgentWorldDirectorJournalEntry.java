package server.agents.runtime.activity.world;

import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.List;
import java.util.Map;

/** Replayable evidence from one command-driven shadow sample. */
public record AgentWorldDirectorJournalEntry(
        int schemaVersion,
        int agentId,
        long capturedAtMs,
        long contextSequence,
        AgentActivityKind actualActivityKind,
        String actualControllerId,
        String selectedProposalId,
        AgentActivityKind selectedKind,
        String decisionEvidence,
        List<AgentWorldActivityProposal> proposals,
        Map<AgentWorldMilestone, AgentWorldMilestoneStatus> milestones) {

    public AgentWorldDirectorJournalEntry {
        if (schemaVersion != 1 || agentId <= 0 || capturedAtMs < 0L || contextSequence <= 0L) {
            throw new IllegalArgumentException("valid shadow journal evidence is required");
        }
        actualControllerId = normalize(actualControllerId);
        selectedProposalId = normalize(selectedProposalId);
        decisionEvidence = normalize(decisionEvidence);
        proposals = List.copyOf(proposals == null ? List.of() : proposals);
        milestones = Map.copyOf(milestones == null ? Map.of() : milestones);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
