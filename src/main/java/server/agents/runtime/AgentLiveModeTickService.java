package server.agents.runtime;

import client.Character;
import server.bots.BotEntry;

import java.awt.Point;

public final class AgentLiveModeTickService {
    private AgentLiveModeTickService() {
    }

    public record Context(BotEntry entry,
                          Character agent,
                          Point agentPosition,
                          Point targetPosition,
                          Point followTargetPosition,
                          Character followAnchor,
                          boolean runAiTick,
                          long nowMs) {
    }

    public record Result(Point targetPosition) {
    }

    public record PhaseResult(boolean consumedTick, Point targetPosition) {
        public static PhaseResult fallThrough(Point targetPosition) {
            return new PhaseResult(false, targetPosition);
        }
    }

    public record Hooks(ShopVisitTick shopVisitTick,
                        FollowOpportunityTick followOpportunityTick,
                        FollowIdleFastPath followIdleFastPath,
                        ScriptedMoveCombatTick scriptedMoveCombatTick,
                        AnchoredFarmTick anchoredFarmTick,
                        GrindModeTick grindModeTick,
                        FinalMovementTail finalMovementTail) {
    }

    @FunctionalInterface
    public interface ShopVisitTick {
        PhaseResult tick(BotEntry entry, Character agent, boolean runAiTick);
    }

    @FunctionalInterface
    public interface FollowOpportunityTick {
        PhaseResult tick(BotEntry entry,
                         Character agent,
                         Point agentPosition,
                         Point targetPosition,
                         Point followTargetPosition,
                         Character followAnchor,
                         boolean runAiTick);
    }

    @FunctionalInterface
    public interface FollowIdleFastPath {
        boolean tick(BotEntry entry, Character agent, Point targetPosition, long nowMs);
    }

    @FunctionalInterface
    public interface ScriptedMoveCombatTick {
        PhaseResult tick(BotEntry entry,
                         Character agent,
                         Point agentPosition,
                         Point targetPosition,
                         boolean runAiTick);
    }

    @FunctionalInterface
    public interface AnchoredFarmTick {
        boolean tick(BotEntry entry, Character agent, Point agentPosition, boolean runAiTick);
    }

    @FunctionalInterface
    public interface GrindModeTick {
        PhaseResult tick(BotEntry entry,
                         Character agent,
                         Point agentPosition,
                         Point targetPosition,
                         boolean runAiTick);
    }

    @FunctionalInterface
    public interface FinalMovementTail {
        void step(BotEntry entry, Point targetPosition, boolean runAiTick);
    }

    public static Result tickLiveModes(Context context, Hooks hooks) {
        Point targetPosition = context.targetPosition();

        PhaseResult shopVisitResult = hooks.shopVisitTick().tick(context.entry(), context.agent(), context.runAiTick());
        if (shopVisitResult.consumedTick()) {
            return new Result(targetPosition);
        }
        if (shopVisitResult.targetPosition() != null) {
            targetPosition = shopVisitResult.targetPosition();
        }

        PhaseResult followOpportunity = hooks.followOpportunityTick().tick(
                context.entry(),
                context.agent(),
                context.agentPosition(),
                targetPosition,
                context.followTargetPosition(),
                context.followAnchor(),
                context.runAiTick());
        targetPosition = followOpportunity.targetPosition();
        if (followOpportunity.consumedTick()) {
            return new Result(targetPosition);
        }

        if (hooks.followIdleFastPath().tick(context.entry(), context.agent(), targetPosition, context.nowMs())) {
            return new Result(targetPosition);
        }

        PhaseResult scriptedMoveCombat = hooks.scriptedMoveCombatTick().tick(
                context.entry(),
                context.agent(),
                context.agentPosition(),
                targetPosition,
                context.runAiTick());
        if (scriptedMoveCombat.consumedTick()) {
            return new Result(targetPosition);
        }

        if (hooks.anchoredFarmTick().tick(
                context.entry(),
                context.agent(),
                context.agentPosition(),
                context.runAiTick())) {
            return new Result(targetPosition);
        }

        PhaseResult grindDispatch = hooks.grindModeTick().tick(
                context.entry(),
                context.agent(),
                context.agentPosition(),
                targetPosition,
                context.runAiTick());
        if (grindDispatch.consumedTick()) {
            return new Result(targetPosition);
        }
        targetPosition = grindDispatch.targetPosition();

        hooks.finalMovementTail().step(context.entry(), targetPosition, context.runAiTick());
        return new Result(targetPosition);
    }
}
