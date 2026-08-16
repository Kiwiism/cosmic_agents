package server.agents.runtime.activity.world;

import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Chooses among TownLife, Hunting, Questing, and Commerce without executing them. */
public final class AgentWorldDirector {
    private final long currentActivityRetentionUtility;

    public AgentWorldDirector(long currentActivityRetentionUtility) {
        if (currentActivityRetentionUtility < 0L) {
            throw new IllegalArgumentException("retention utility cannot be negative");
        }
        this.currentActivityRetentionUtility = currentActivityRetentionUtility;
    }

    public AgentWorldActivityDecision select(
            AgentActivityKind currentKind,
            Collection<AgentWorldActivityProposal> proposals) {
        if (proposals == null) {
            throw new IllegalArgumentException("world activity proposals are required");
        }
        validateUniqueIds(proposals);
        List<AgentWorldActivityProposal> eligible = proposals.stream()
                .filter(AgentWorldActivityProposal::eligible)
                .toList();
        if (eligible.isEmpty()) {
            return AgentWorldActivityDecision.idle();
        }
        AgentWorldActivityProposal selected = eligible.stream()
                .max(Comparator.comparingInt(AgentWorldActivityProposal::priority)
                        .thenComparingLong(proposal -> proposal.utility()
                                + (proposal.kind() == currentKind
                                ? currentActivityRetentionUtility : 0L))
                        .thenComparing(AgentWorldActivityProposal::proposalId,
                                Comparator.reverseOrder()))
                .orElseThrow();
        return new AgentWorldActivityDecision(selected.kind(), selected.proposalId(),
                currentKind != null && currentKind != selected.kind(), selected.evidence());
    }

    private static void validateUniqueIds(Collection<AgentWorldActivityProposal> proposals) {
        Set<String> ids = new HashSet<>();
        for (AgentWorldActivityProposal proposal : proposals) {
            if (proposal == null || !ids.add(proposal.proposalId())) {
                throw new IllegalArgumentException("world activity proposal ids must be unique");
            }
        }
    }
}
