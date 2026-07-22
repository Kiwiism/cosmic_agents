package server.agents.runtime;

import client.Character;

import java.awt.Point;

public final class AgentLiveTickGateService {
    private AgentLiveTickGateService() {
    }

    public record Context(AgentRuntimeEntry entry,
                          Character agent,
                          Character leader,
                          Character followAnchor,
                          Point targetPosition,
                          boolean runAiTick) {
    }

    public record Hooks(CommonTickSystems commonTickSystems,
                        PlanExecutionGateTick planExecutionGateTick,
                        ObjectiveSupervisionTick objectiveSupervisionTick,
                        ActiveCapabilityTick activeCapabilityTick,
                        TradeWindowTick tradeWindowTick,
                        IdleModeTick idleModeTick,
                        RecoveryTick recoveryTick,
                        TrackedMapChangeTick trackedMapChangeTick) {
        public Hooks(CommonTickSystems commonTickSystems,
                     ObjectiveSupervisionTick objectiveSupervisionTick,
                     ActiveCapabilityTick activeCapabilityTick,
                     TradeWindowTick tradeWindowTick,
                     IdleModeTick idleModeTick,
                     RecoveryTick recoveryTick,
                     TrackedMapChangeTick trackedMapChangeTick) {
            this(commonTickSystems, (entry, agent, runAiTick) -> false, objectiveSupervisionTick,
                    activeCapabilityTick, tradeWindowTick, idleModeTick, recoveryTick, trackedMapChangeTick);
        }
    }

    @FunctionalInterface
    public interface PlanExecutionGateTick {
        boolean tick(AgentRuntimeEntry entry, Character agent, boolean runAiTick);
    }

    @FunctionalInterface
    public interface CommonTickSystems {
        boolean run(AgentRuntimeEntry entry, Character agent, Character leader, boolean runAiTick);
    }

    @FunctionalInterface
    public interface ObjectiveSupervisionTick {
        boolean tick(AgentRuntimeEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface ActiveCapabilityTick {
        boolean tick(AgentRuntimeEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface TradeWindowTick {
        boolean tick(AgentRuntimeEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface IdleModeTick {
        boolean tick(AgentRuntimeEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface RecoveryTick {
        boolean tick(AgentRuntimeEntry entry, Character agent, Character followAnchor, Point targetPosition);
    }

    @FunctionalInterface
    public interface TrackedMapChangeTick {
        boolean tick(AgentRuntimeEntry entry, Character agent);
    }

    public static boolean tickLiveGates(Context context, Hooks hooks) {
        if (hooks.trackedMapChangeTick().tick(context.entry(), context.agent())) {
            return true;
        }
        // A seated character must not reach passive/common systems: those include physics,
        // facing, fidget and loot ticks that can overwrite the chair pose after a successful
        // sit. The active capability still gets one chance to verify/finish the sit command.
        if (context.agent().getChair() >= 0) {
            if (hooks.planExecutionGateTick().tick(
                    context.entry(), context.agent(), context.runAiTick())) {
                return true;
            }
            hooks.activeCapabilityTick().tick(context.entry(), context.agent());
            return true;
        }
        if (hooks.objectiveSupervisionTick().tick(context.entry(), context.agent())) {
            return true;
        }
        if (hooks.commonTickSystems().run(context.entry(), context.agent(), context.leader(), context.runAiTick())) {
            return true;
        }
        if (hooks.planExecutionGateTick().tick(context.entry(), context.agent(), context.runAiTick())) {
            return true;
        }
        if (hooks.activeCapabilityTick().tick(context.entry(), context.agent())) {
            return true;
        }
        if (hooks.tradeWindowTick().tick(context.entry(), context.agent())) {
            return true;
        }
        if (hooks.idleModeTick().tick(context.entry(), context.agent())) {
            return true;
        }
        return hooks.recoveryTick().tick(
                context.entry(),
                context.agent(),
                context.followAnchor(),
                context.targetPosition());
    }
}
