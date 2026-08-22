package server.agents.runtime.activity.control;

import server.agents.runtime.activity.world.AgentWorldContext;
import server.agents.runtime.activity.world.AgentWorldShadowEvaluator;
import server.agents.runtime.activity.world.AgentWorldShadowReport;

/** Pure scheduler sampling step with no activity-control or persistence dependency. */
public final class AgentWorldDirectorObserveTickService {
    private AgentWorldDirectorObserveTickService() { }

    public static AgentWorldShadowReport tick(
            AgentWorldDirectorObserveState state,
            AgentWorldShadowEvaluator evaluator,
            AgentWorldContext context,
            long nowMs) {
        if (state == null || evaluator == null || context == null || !state.due(nowMs)) {
            return null;
        }
        AgentWorldShadowReport report = evaluator.evaluate(context);
        state.sampled(report, nowMs);
        return report;
    }
}
