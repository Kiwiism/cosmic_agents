package server.agents.runtime.activity.world;

import java.util.List;

/** One side-effect-free selection result. */
public record AgentWorldShadowReport(
        AgentWorldContext context,
        AgentWorldMilestoneSnapshot milestones,
        List<AgentWorldActivityIntent> intents,
        AgentWorldActivityDecision decision) {

    public AgentWorldShadowReport {
        if (context == null || milestones == null || intents == null || decision == null) {
            throw new IllegalArgumentException("complete shadow evidence is required");
        }
        intents = List.copyOf(intents);
    }

    public AgentWorldDirectorJournalEntry journalEntry() {
        return new AgentWorldDirectorJournalEntry(
                1, context.agentId(), context.capturedAtMs(), context.sequence(),
                context.currentActivityKind(), context.currentControllerId(),
                decision.proposalId(), decision.kind(), decision.evidence(),
                intents.stream().map(AgentWorldActivityIntent::proposal).toList(),
                milestones.statuses());
    }
}
