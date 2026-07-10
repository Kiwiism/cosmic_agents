package server.agents.capabilities.combat;

import server.agents.capabilities.movement.AgentPositionService;
import server.agents.capabilities.movement.AgentMovementTargetMaintenanceService;
import client.Character;
import server.agents.capabilities.movement.AgentFarmAnchorStateRuntime;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned anchored farm/sentry tick shell.
 */
public final class AgentAnchoredFarmTickService {
    @FunctionalInterface
    public interface LocalOpportunityAttack {
        LocalOpportunityResult tryAttack(AgentRuntimeEntry entry,
                                         Character agent,
                                         Point agentPosition,
                                         Point movementTargetPosition,
                                         Point moveWindowReferencePosition,
                                         boolean allowCombatMovement,
                                         boolean allowJumpTowardTarget);
    }

    @FunctionalInterface
    public interface IdleTick {
        void tick(AgentRuntimeEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface GroundIdleTick {
        void tick(AgentRuntimeEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface MovementCore {
        void step(AgentRuntimeEntry entry, Point targetPosition, boolean runAiTick);
    }

    public record LocalOpportunityResult(boolean consumedTick, Point targetPosition) {
    }

    public record AnchoredFarmHooks(LocalOpportunityAttack localOpportunityAttack,
                                    IdleTick idleTick,
                                    GroundIdleTick groundIdleTick,
                                    MovementCore movementCore) {
    }

    private AgentAnchoredFarmTickService() {
    }

    public static void tickAnchoredFarm(AgentRuntimeEntry entry,
                                        Character agent,
                                        Point agentPosition,
                                        boolean runAiTick,
                                        AnchoredFarmHooks hooks) {
        if (!AgentFarmAnchorStateRuntime.isFarmAnchorInMap(entry, agent.getMapId())) {
            AgentMovementTargetMaintenanceService.clearFarmAnchorOnMapChange(entry, agent);
            hooks.idleTick().tick(entry, agent);
            return;
        }

        Point anchor = AgentFarmAnchorStateRuntime.farmAnchor(entry);
        if (runAiTick) {
            LocalOpportunityResult attackResult = hooks.localOpportunityAttack().tryAttack(
                    entry, agent, agentPosition, anchor, anchor, false, false);
            if (attackResult.consumedTick()) {
                return;
            }
        }

        if (AgentPositionService.isNear(agentPosition, anchor, 8)
                && !AgentMovementStateRuntime.inAir(entry)
                && !AgentMovementStateRuntime.climbing(entry)) {
            AgentMoveTargetStateRuntime.clearMoveTarget(entry);
            hooks.groundIdleTick().tick(entry, agent);
            return;
        }

        AgentMoveTargetStateRuntime.setPreciseMoveTarget(entry, anchor);
        hooks.movementCore().step(entry, anchor, runAiTick);
    }
}
