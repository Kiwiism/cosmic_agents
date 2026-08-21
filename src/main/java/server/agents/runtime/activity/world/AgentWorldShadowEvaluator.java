package server.agents.runtime.activity.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure shadow orchestration. It has no admission, exit, transfer, or scheduler dependency. */
public final class AgentWorldShadowEvaluator {
    private final AgentWorldDirector director;
    private final List<AgentWorldProposalProvider> providers;

    public AgentWorldShadowEvaluator(
            AgentWorldDirector director, List<AgentWorldProposalProvider> providers) {
        if (director == null || providers == null || providers.isEmpty()
                || providers.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Director and proposal providers are required");
        }
        this.director = director;
        this.providers = List.copyOf(providers);
    }

    public static AgentWorldShadowEvaluator baseline() {
        return new AgentWorldShadowEvaluator(
                new AgentWorldDirector(25L), List.of(new AgentWorldBaselineProposalProvider()));
    }

    public AgentWorldShadowReport evaluate(AgentWorldContext context) {
        AgentWorldMilestoneSnapshot milestones = AgentWorldMilestoneEvaluator.evaluate(context);
        List<AgentWorldActivityIntent> intents = new ArrayList<>();
        providers.forEach(provider -> intents.addAll(provider.propose(context, milestones)));
        validateUniqueIds(intents);
        AgentWorldActivityDecision decision = director.select(
                context.currentActivityKind(),
                intents.stream().map(AgentWorldActivityIntent::proposal).toList());
        return new AgentWorldShadowReport(context, milestones, intents, decision);
    }

    private static void validateUniqueIds(List<AgentWorldActivityIntent> intents) {
        Set<String> ids = new HashSet<>();
        for (AgentWorldActivityIntent intent : intents) {
            if (intent == null || !ids.add(intent.proposal().proposalId())) {
                throw new IllegalArgumentException("shadow proposal ids must be globally unique");
            }
        }
    }
}
