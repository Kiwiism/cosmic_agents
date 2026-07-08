package server.agents.plans;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

public final class AgentScriptMoveTargetRuntime {
    private AgentScriptMoveTargetRuntime() {
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
