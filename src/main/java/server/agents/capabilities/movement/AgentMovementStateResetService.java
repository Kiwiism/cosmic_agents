package server.agents.capabilities.movement;

import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.capabilities.combat.AgentCombatCooldownStateRuntime;
import server.agents.integration.AgentGrindSearchStateRuntime;
import server.agents.integration.AgentGrindTargetStateRuntime;
import server.agents.integration.AgentNavigationDebugStateRuntime;
import server.agents.runtime.AgentOwnerMotionStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned movement reset and transient-state cleanup.
 */
public final class AgentMovementStateResetService {
    private AgentMovementStateResetService() {
    }

    public static void resetEntryState(AgentRuntimeEntry entry) {
        AgentMovementPoseService.resetMotion(entry, AgentRuntimeIdentityRuntime.bot(entry).getPosition());
        clearTransientState(entry);
    }

    public static void resetEntryStateAfterTeleport(AgentRuntimeEntry entry) {
        clearTransientState(entry);
    }

    public static void clearTransientState(AgentRuntimeEntry entry) {
        AgentGrindTargetStateRuntime.clear(entry);
        AgentGrindSearchStateRuntime.clear(entry);
        AgentCombatCooldownStateRuntime.clearAttackCooldown(entry);
        AgentNavigationDebugStateRuntime.clearGraphWarmupFallback(entry);
        AgentOwnerMotionStateRuntime.clearObservedOwnerStep(entry);
        AgentFidgetService.clear(entry);
        clearNavigationState(entry);
        AgentMovementBroadcastStateRuntime.invalidate(entry);
    }

    public static void clearNavigationState(AgentRuntimeEntry entry) {
        AgentNavigationDebugStateRuntime.clearActiveNavigationEdge(entry);
        AgentNavigationDebugStateRuntime.clearNavJumpLaunch(entry);
        AgentNavigationDebugStateRuntime.clearNavTarget(entry);
    }
}
