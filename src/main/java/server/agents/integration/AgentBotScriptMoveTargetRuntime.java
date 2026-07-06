package server.agents.integration;

import server.agents.plans.AgentScriptMoveTargetService;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

public final class AgentBotScriptMoveTargetRuntime {
    private AgentBotScriptMoveTargetRuntime() {
    }

    public static boolean isCheapMoveTarget(AgentRuntimeEntry entry,
                                            Point targetPos,
                                            int maxPathCost,
                                            int fallbackRangeX,
                                            int fallbackRangeY) {
        return AgentScriptMoveTargetService.isCheapMoveTarget(
                entry,
                targetPos,
                maxPathCost,
                fallbackRangeX,
                fallbackRangeY);
    }
}
