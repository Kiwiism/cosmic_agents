package server.agents.integration;

import server.agents.plans.AgentScriptMoveTargetService;
import server.agents.runtime.AgentRuntimeConfig;
import server.bots.BotEntry;

import java.awt.Point;

public final class AgentBotScriptMoveTargetRuntime {
    private AgentBotScriptMoveTargetRuntime() {
    }

    public static boolean isCheapMoveTarget(BotEntry entry,
                                            Point targetPos,
                                            int maxPathCost,
                                            int fallbackRangeX,
                                            int fallbackRangeY) {
        return AgentScriptMoveTargetService.isCheapMoveTarget(
                entry,
                targetPos,
                maxPathCost,
                fallbackRangeX,
                fallbackRangeY,
                AgentRuntimeConfig.cfg.LOOT_RADIUS);
    }
}
