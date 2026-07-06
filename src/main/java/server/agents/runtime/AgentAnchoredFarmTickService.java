package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;

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
        if (!AgentBotFarmAnchorStateRuntime.isFarmAnchorInMap(entry, agent.getMapId())) {
            AgentTickStateMaintenanceService.clearFarmAnchorOnMapChange(entry, agent);
            hooks.idleTick().tick(entry, agent);
            return;
        }

        Point anchor = AgentBotFarmAnchorStateRuntime.farmAnchor(entry);
        if (runAiTick) {
            LocalOpportunityResult attackResult = hooks.localOpportunityAttack().tryAttack(
                    entry, agent, agentPosition, anchor, anchor, false, false);
            if (attackResult.consumedTick()) {
                return;
            }
        }

        if (AgentPositionService.isNear(agentPosition, anchor, 8)
                && !AgentBotMovementStateRuntime.inAir(entry)
                && !AgentBotMovementStateRuntime.climbing(entry)) {
            AgentBotMoveTargetStateRuntime.clearMoveTarget(entry);
            hooks.groundIdleTick().tick(entry, agent);
            return;
        }

        AgentBotMoveTargetStateRuntime.setPreciseMoveTarget(entry, anchor);
        hooks.movementCore().step(entry, anchor, runAiTick);
    }
}
