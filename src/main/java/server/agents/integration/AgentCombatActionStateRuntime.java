package server.agents.integration;

import server.agents.capabilities.movement.AgentMovementStateResetService;

import server.agents.runtime.AgentRuntimeEntry;

public final class AgentCombatActionStateRuntime {
    private AgentCombatActionStateRuntime() {
    }

    public static void clearActionState(AgentRuntimeEntry entry) {
        AgentGrindTargetStateRuntime.clear(entry);
        AgentCombatCooldownStateRuntime.clearAttackCooldown(entry);
        AgentCombatCooldownStateRuntime.clearMoveWindow(entry);
        AgentMovementStateResetService.clearNavigationState(entry);
        AgentMovementBroadcastStateRuntime.invalidate(entry);
    }
}
