package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentScriptTaskStateRuntime;

import java.awt.Point;

public final class AgentScriptedMoveCombatTickService {
    private AgentScriptedMoveCombatTickService() {
    }

    public record Result(boolean consumedTick, Point targetPos) {
    }

    public record Hooks(ActionMoveWindowCleanup actionMoveWindowCleanup,
                        LocalOpportunityAttack localOpportunityAttack,
                        MovementCore movementCore) {
    }

    @FunctionalInterface
    public interface ActionMoveWindowCleanup {
        void clearIfSettled(AgentRuntimeEntry entry, Point agentPosition, Point targetPosition);
    }

    @FunctionalInterface
    public interface LocalOpportunityAttack {
        Result attack(AgentRuntimeEntry entry,
                      Character agent,
                      Point agentPosition,
                      Point currentTargetPosition);
    }

    @FunctionalInterface
    public interface MovementCore {
        void step(AgentRuntimeEntry entry, Point targetPosition, boolean runAiTick);
    }

    public static boolean shouldUseScriptedMoveLocalCombat(AgentRuntimeEntry entry, Point targetPos) {
        return AgentScriptTaskStateRuntime.isActiveLocalOpportunityMoveTo(entry, targetPos);
    }

    public static Result tickScriptedMoveCombat(AgentRuntimeEntry entry,
                                                Character agent,
                                                Point agentPosition,
                                                Point currentTargetPosition,
                                                boolean runAiTick,
                                                Hooks hooks) {
        if (!runAiTick || !shouldUseScriptedMoveLocalCombat(entry, currentTargetPosition)) {
            return new Result(false, currentTargetPosition);
        }

        hooks.actionMoveWindowCleanup().clearIfSettled(entry, agentPosition, currentTargetPosition);
        Result attackResult = hooks.localOpportunityAttack().attack(entry, agent, agentPosition, currentTargetPosition);
        if (attackResult.consumedTick()) {
            return attackResult;
        }

        hooks.movementCore().step(entry, attackResult.targetPos(), runAiTick);
        return new Result(true, attackResult.targetPos());
    }
}
