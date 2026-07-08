package server.agents.integration;

import server.agents.capabilities.movement.AgentMovementTimers;

import server.agents.runtime.AgentRuntimeEntry;

public final class AgentCombatActionLockRuntime {
    private AgentCombatActionLockRuntime() {
    }

    public static void tickActionLock(AgentRuntimeEntry entry) {
        if (AgentCombatCooldownStateRuntime.hasAttackCooldown(entry)) {
            AgentCombatCooldownStateRuntime.tickAttackCooldown(entry, AgentMovementTimers::tickDown);
        } else if (AgentCombatCooldownStateRuntime.hasMoveWindow(entry)) {
            AgentCombatCooldownStateRuntime.tickMoveWindow(entry, AgentMovementTimers::tickDown);
        }
    }
}
