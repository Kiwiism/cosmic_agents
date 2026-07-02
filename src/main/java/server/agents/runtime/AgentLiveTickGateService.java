package server.agents.runtime;

import client.Character;
import server.bots.BotEntry;

import java.awt.Point;

public final class AgentLiveTickGateService {
    private AgentLiveTickGateService() {
    }

    public record Context(BotEntry entry,
                          Character agent,
                          Character leader,
                          Character followAnchor,
                          Point targetPosition,
                          boolean runAiTick) {
    }

    public record Hooks(CommonTickSystems commonTickSystems,
                        TradeWindowTick tradeWindowTick,
                        IdleModeTick idleModeTick,
                        RecoveryTick recoveryTick,
                        TrackedMapChangeTick trackedMapChangeTick) {
    }

    @FunctionalInterface
    public interface CommonTickSystems {
        boolean run(BotEntry entry, Character agent, Character leader, boolean runAiTick);
    }

    @FunctionalInterface
    public interface TradeWindowTick {
        boolean tick(BotEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface IdleModeTick {
        boolean tick(BotEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface RecoveryTick {
        boolean tick(BotEntry entry, Character agent, Character followAnchor, Point targetPosition);
    }

    @FunctionalInterface
    public interface TrackedMapChangeTick {
        boolean tick(BotEntry entry, Character agent);
    }

    public static boolean tickLiveGates(Context context, Hooks hooks) {
        if (hooks.commonTickSystems().run(context.entry(), context.agent(), context.leader(), context.runAiTick())) {
            return true;
        }
        if (hooks.tradeWindowTick().tick(context.entry(), context.agent())) {
            return true;
        }
        if (hooks.idleModeTick().tick(context.entry(), context.agent())) {
            return true;
        }
        if (hooks.recoveryTick().tick(
                context.entry(),
                context.agent(),
                context.followAnchor(),
                context.targetPosition())) {
            return true;
        }
        return hooks.trackedMapChangeTick().tick(context.entry(), context.agent());
    }
}
