package server.agents.integration;

import server.agents.capabilities.movement.AgentMovementTimers;

import server.bots.BotEntry;

public final class AgentBotCombatActionLockRuntime {
    private AgentBotCombatActionLockRuntime() {
    }

    public static void tickActionLock(BotEntry entry) {
        if (AgentBotCombatCooldownStateRuntime.hasAttackCooldown(entry)) {
            AgentBotCombatCooldownStateRuntime.tickAttackCooldown(entry, AgentMovementTimers::tickDown);
        } else if (AgentBotCombatCooldownStateRuntime.hasMoveWindow(entry)) {
            AgentBotCombatCooldownStateRuntime.tickMoveWindow(entry, AgentMovementTimers::tickDown);
        }
    }
}
