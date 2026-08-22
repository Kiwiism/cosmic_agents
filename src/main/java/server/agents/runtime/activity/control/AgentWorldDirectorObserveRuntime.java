package server.agents.runtime.activity.control;

import client.Character;
import server.agents.integration.cosmic.CosmicAgentWorldContextFactory;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.world.AgentWorldShadowEvaluator;

/** Cosmic capture adapter invoked by the central Agent scheduler. */
public final class AgentWorldDirectorObserveRuntime {
    private static final AgentWorldShadowEvaluator EVALUATOR =
            AgentWorldShadowEvaluator.baseline();

    private AgentWorldDirectorObserveRuntime() { }

    public static void tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) return;
        AgentWorldDirectorObserveState state = entry.capabilityStates()
                .find(AgentWorldDirectorObserveState.STATE_KEY).orElse(null);
        if (state == null || !state.due(nowMs)) return;
        try {
            AgentWorldDirectorObserveTickService.tick(state, EVALUATOR,
                    CosmicAgentWorldContextFactory.capture(entry, agent, nowMs), nowMs);
        } catch (RuntimeException failure) {
            state.failed("Observe sampling failed: " + failure.getMessage(), nowMs);
        }
    }
}
