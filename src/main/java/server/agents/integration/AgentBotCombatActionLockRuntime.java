package server.agents.integration;

import server.bots.BotEntry;
import server.bots.BotMovementManager;

public final class AgentBotCombatActionLockRuntime {
    private AgentBotCombatActionLockRuntime() {
    }

    public static void tickActionLock(BotEntry entry) {
        if (AgentBotCombatCooldownStateRuntime.hasAttackCooldown(entry)) {
            AgentBotCombatCooldownStateRuntime.tickAttackCooldown(entry, BotMovementManager::tickDown);
        } else if (AgentBotCombatCooldownStateRuntime.hasMoveWindow(entry)) {
            AgentBotCombatCooldownStateRuntime.tickMoveWindow(entry, BotMovementManager::tickDown);
        }
    }
}
