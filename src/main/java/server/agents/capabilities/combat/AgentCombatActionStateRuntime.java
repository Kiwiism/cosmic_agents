package server.agents.capabilities.combat;

import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;
import server.agents.capabilities.movement.AgentMovementBroadcastStateRuntime;
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
