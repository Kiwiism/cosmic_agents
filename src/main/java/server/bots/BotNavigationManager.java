package server.bots;

import server.agents.capabilities.navigation.AgentNavigationTargetService;

import java.awt.*;

public final class BotNavigationManager {
    public static final class NavigationDirective {
        public final Point targetPos;
        public final boolean consumedTick;

        NavigationDirective(Point targetPos, boolean consumedTick) {
            this.targetPos = targetPos;
            this.consumedTick = consumedTick;
        }
    }

    public static NavigationDirective resolveTarget(BotEntry entry, Point rawTargetPos, boolean runAiTick) {
        AgentNavigationTargetService.NavigationDirective directive =
                AgentNavigationTargetService.resolveTarget(entry, rawTargetPos, runAiTick);
        return new NavigationDirective(directive.targetPos(), directive.consumedTick());
    }

    public static boolean tryExecuteCommittedEdgeAfterGroundMovement(BotEntry entry, Point rawTargetPos) {
        return AgentNavigationTargetService.tryExecuteCommittedEdgeAfterGroundMovement(entry, rawTargetPos);
    }
}
