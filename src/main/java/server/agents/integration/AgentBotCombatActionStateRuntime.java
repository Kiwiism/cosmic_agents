package server.agents.integration;

import server.agents.capabilities.movement.AgentMovementStateResetService;

import server.agents.runtime.AgentRuntimeEntry;

public final class AgentBotCombatActionStateRuntime {
    private AgentBotCombatActionStateRuntime() {
    }

    public static void clearActionState(AgentRuntimeEntry entry) {
        AgentBotGrindTargetStateRuntime.clear(entry);
        AgentBotCombatCooldownStateRuntime.clearAttackCooldown(entry);
        AgentBotCombatCooldownStateRuntime.clearMoveWindow(entry);
        AgentMovementStateResetService.clearNavigationState(entry);
        AgentBotMovementBroadcastStateRuntime.invalidate(entry);
    }
}
