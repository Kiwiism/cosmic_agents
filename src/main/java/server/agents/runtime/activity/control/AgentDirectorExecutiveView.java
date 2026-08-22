package server.agents.runtime.activity.control;

import server.agents.runtime.activity.world.AgentWorldContext;
import server.agents.runtime.activity.world.AgentWorldDirectiveEnvelope;
import server.agents.runtime.activity.world.AgentWorldDirectorSession;
import server.agents.runtime.activity.outcome.AgentActivityOutcomeEnvelope;
import server.agents.runtime.journey.AgentJourneyEvent;

import java.util.List;

/** Complete backend projection consumed by a panel, WASM client, in-game command, or LLM. */
public record AgentDirectorExecutiveView(
        String contextRevision,
        AgentWorldContext context,
        AgentDirectorResourceSnapshot resources,
        AgentDirectorEnergySnapshot energy,
        AgentDirectorProfileSnapshot profile,
        AgentWorldDirectorSession directorSession,
        AgentDirectorActivityProjection activity,
        List<AgentDirectorAction> actions,
        List<AgentWorldDirectiveEnvelope> directives,
        List<AgentActivityOutcomeEnvelope> pendingActivityOutcomes,
        List<AgentJourneyEvent> recentJourney) {
    public AgentDirectorExecutiveView {
        contextRevision = contextRevision == null ? "" : contextRevision.trim();
        actions = List.copyOf(actions == null ? List.of() : actions);
        directives = List.copyOf(directives == null ? List.of() : directives);
        pendingActivityOutcomes = List.copyOf(
                pendingActivityOutcomes == null ? List.of() : pendingActivityOutcomes);
        recentJourney = List.copyOf(recentJourney == null ? List.of() : recentJourney);
        if (contextRevision.isEmpty() || context == null || resources == null
                || energy == null || profile == null
                || directorSession == null || activity == null) {
            throw new IllegalArgumentException("complete Director executive view is required");
        }
    }
}
