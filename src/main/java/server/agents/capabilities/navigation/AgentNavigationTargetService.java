package server.agents.capabilities.navigation;

import server.bots.BotEntry;
import server.bots.BotNavigationManager;

import java.awt.Point;

/**
 * Agent-owned seam for live navigation target resolution while the resolver body migrates.
 */
public final class AgentNavigationTargetService {
    private AgentNavigationTargetService() {
    }

    public record NavigationDirective(Point targetPos, boolean consumedTick) {
    }

    public static NavigationDirective resolveTarget(BotEntry entry, Point rawTargetPos, boolean runAiTick) {
        BotNavigationManager.NavigationDirective directive =
                BotNavigationManager.resolveTarget(entry, rawTargetPos, runAiTick);
        return new NavigationDirective(directive.targetPos, directive.consumedTick);
    }

    public static boolean tryExecuteCommittedEdgeAfterGroundMovement(BotEntry entry, Point rawTargetPos) {
        return BotNavigationManager.tryExecuteCommittedEdgeAfterGroundMovement(entry, rawTargetPos);
    }
}
