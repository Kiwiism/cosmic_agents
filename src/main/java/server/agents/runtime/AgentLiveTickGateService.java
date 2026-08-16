package server.agents.runtime;

import client.Character;
import server.agents.runtime.commerce.AgentCommerceSessionRegistryRuntime;

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
                        CompatibilityInterludeTick compatibilityInterludeTick,
                        ObjectiveSupervisionTick objectiveSupervisionTick,
                        ForegroundTravelCombatTick foregroundTravelCombatTick,
                        ActivityHostTick activityHostTick,
                        TradeWindowTick tradeWindowTick,
                        IdleModeTick idleModeTick,
                        RecoveryTick recoveryTick,
                        TrackedMapChangeTick trackedMapChangeTick) {
        public Hooks(CommonTickSystems commonTickSystems,
                     CompatibilityInterludeTick compatibilityInterludeTick,
                     ObjectiveSupervisionTick objectiveSupervisionTick,
                     ActivityHostTick activityHostTick,
                     TradeWindowTick tradeWindowTick,
                     IdleModeTick idleModeTick,
                     RecoveryTick recoveryTick,
                     TrackedMapChangeTick trackedMapChangeTick) {
            this(commonTickSystems, compatibilityInterludeTick, objectiveSupervisionTick,
                    (entry, agent, targetPosition, runAiTick) -> false,
                    activityHostTick, tradeWindowTick, idleModeTick, recoveryTick,
                    trackedMapChangeTick);
        }

        public Hooks(CommonTickSystems commonTickSystems,
                     ObjectiveSupervisionTick objectiveSupervisionTick,
                     ActivityHostTick activityHostTick,
                     TradeWindowTick tradeWindowTick,
                     IdleModeTick idleModeTick,
                     RecoveryTick recoveryTick,
                     TrackedMapChangeTick trackedMapChangeTick) {
            this(commonTickSystems, (entry, agent, runAiTick) -> false, objectiveSupervisionTick,
                    (entry, agent, targetPosition, runAiTick) -> false,
                    activityHostTick, tradeWindowTick, idleModeTick, recoveryTick, trackedMapChangeTick);
        }
    }

    @FunctionalInterface
    public interface CompatibilityInterludeTick {
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
    public interface ForegroundTravelCombatTick {
        boolean tick(AgentRuntimeEntry entry,
                     Character agent,
                     Point targetPosition,
                     boolean runAiTick);
    }

    @FunctionalInterface
    public interface ActivityHostTick {
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
        if (AgentCommerceSessionRegistryRuntime.active(context.agent().getId())) {
            return hooks.activityHostTick().tick(context.entry(), context.agent());
        }
        if (AgentCommerceControlRuntime.claimed(context.agent().getId())) {
            // Commerce ownership suppresses every ordinary decision gate, but a
            // navigation capability deliberately returns a non-consuming tick
            // after installing its MOVE_TO target. Let that one tick reach the
            // existing movement phase so the character still walks physically.
            return AgentCommerceControlRuntime.withAttribution(context.agent().getId(),
                    () -> hooks.activityHostTick().tick(context.entry(), context.agent()));
        }
        if (hooks.trackedMapChangeTick().tick(context.entry(), context.agent())) {
            return true;
        }
        // A seated character must not reach passive/common systems: those include physics,
        // facing, fidget and loot ticks that can overwrite the chair pose after a successful
        // sit. The active capability still gets one chance to verify/finish the sit command.
        if (context.agent().getChair() >= 0) {
            if (hooks.compatibilityInterludeTick().tick(
                    context.entry(), context.agent(), context.runAiTick())) {
                return true;
            }
            hooks.activityHostTick().tick(context.entry(), context.agent());
            return true;
        }
        if (hooks.commonTickSystems().run(context.entry(), context.agent(), context.leader(), context.runAiTick())) {
            return true;
        }
        if (hooks.objectiveSupervisionTick().tick(context.entry(), context.agent())) {
            // Maintenance can replace the foreground mode with route-aware movement (for
            // example, an emergency shop visit). Skip the suspended plan and later gates,
            // but let the caller run the normal capability/movement phase for that mode.
            return false;
        }
        if (hooks.compatibilityInterludeTick().tick(
                context.entry(), context.agent(), context.runAiTick())) {
            return true;
        }
        if (hooks.foregroundTravelCombatTick().tick(
                context.entry(), context.agent(), context.targetPosition(), context.runAiTick())) {
            return true;
        }
        if (hooks.activityHostTick().tick(context.entry(), context.agent())) {
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
