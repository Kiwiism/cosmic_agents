package server.agents.capabilities.movement;

import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.integration.AgentBotCombatCooldownStateRuntime;
import server.agents.integration.AgentBotGrindSearchStateRuntime;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.integration.AgentBotMovementBroadcastStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotOwnerMotionStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;

/**
 * Agent-owned movement reset and transient-state cleanup.
 */
public final class AgentMovementStateResetService {
    private AgentMovementStateResetService() {
    }

    public static void resetEntryState(BotEntry entry) {
        BotPhysicsEngine.resetMotion(entry, AgentBotRuntimeIdentityRuntime.bot(entry).getPosition());
        clearTransientState(entry);
    }

    public static void resetEntryStateAfterTeleport(BotEntry entry) {
        clearTransientState(entry);
    }

    public static void clearTransientState(BotEntry entry) {
        AgentBotGrindTargetStateRuntime.clear(entry);
        AgentBotGrindSearchStateRuntime.clear(entry);
        AgentBotCombatCooldownStateRuntime.clearAttackCooldown(entry);
        AgentBotNavigationDebugStateRuntime.clearGraphWarmupFallback(entry);
        AgentBotOwnerMotionStateRuntime.clearObservedOwnerStep(entry);
        AgentFidgetService.clear(entry);
        clearNavigationState(entry);
        AgentBotMovementBroadcastStateRuntime.invalidate(entry);
    }

    public static void clearNavigationState(BotEntry entry) {
        AgentBotNavigationDebugStateRuntime.clearActiveNavigationEdge(entry);
        AgentBotNavigationDebugStateRuntime.clearNavJumpLaunch(entry);
        AgentBotNavigationDebugStateRuntime.clearNavTarget(entry);
    }
}
