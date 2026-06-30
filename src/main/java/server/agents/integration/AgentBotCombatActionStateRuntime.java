package server.agents.integration;

import server.bots.BotEntry;
import server.bots.BotMovementManager;

public final class AgentBotCombatActionStateRuntime {
    private AgentBotCombatActionStateRuntime() {
    }

    public static void clearActionState(BotEntry entry) {
        AgentBotGrindTargetStateRuntime.clear(entry);
        AgentBotCombatCooldownStateRuntime.clearAttackCooldown(entry);
        AgentBotCombatCooldownStateRuntime.clearMoveWindow(entry);
        BotMovementManager.clearNavigationState(entry);
        AgentBotMovementBroadcastStateRuntime.invalidate(entry);
    }
}
