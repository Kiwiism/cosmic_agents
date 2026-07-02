package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotScriptTaskStateRuntime;
import server.bots.BotEntry;

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
        void clearIfSettled(BotEntry entry, Point agentPosition, Point targetPosition);
    }

    @FunctionalInterface
    public interface LocalOpportunityAttack {
        Result attack(BotEntry entry,
                      Character agent,
                      Point agentPosition,
                      Point currentTargetPosition);
    }

    @FunctionalInterface
    public interface MovementCore {
        void step(BotEntry entry, Point targetPosition, boolean runAiTick);
    }

    public static boolean shouldUseScriptedMoveLocalCombat(BotEntry entry, Point targetPos) {
        return AgentBotScriptTaskStateRuntime.isActiveLocalOpportunityMoveTo(entry, targetPos);
    }

    public static Result tickScriptedMoveCombat(BotEntry entry,
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
