package server.agents.capabilities.movement;

import client.Character;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.shop.AgentShopStateRuntime;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public final class AgentIdlePhysicsService {
    public record PhysicsHooks(Predicate<AgentRuntimeEntry> swimMap,
                               Consumer<AgentRuntimeEntry> swimmingTick,
                               Consumer<AgentRuntimeEntry> airborneTick,
                               ToIntFunction<AgentRuntimeEntry> idleGroundStanceResolver,
                               ToIntFunction<AgentRuntimeEntry> stanceResolver,
                               BiConsumer<AgentRuntimeEntry, Character> idleOnGround,
                               Consumer<AgentRuntimeEntry> movementBroadcaster) {
    }

    private AgentIdlePhysicsService() {
    }

    public static boolean tickIdleEntry(AgentRuntimeEntry entry, Character agent) {
        return tickIdleEntry(entry, agent, defaultHooks());
    }

    public static void tickPhysicsOnly(AgentRuntimeEntry entry, Character agent) {
        tickPhysicsOnly(entry, agent, defaultHooks());
    }

    public static boolean tickIdleEntry(AgentRuntimeEntry entry, Character agent, PhysicsHooks hooks) {
        if (AgentModeStateRuntime.following(entry)
                || AgentModeStateRuntime.grinding(entry)
                || AgentMoveTargetStateRuntime.hasMoveTarget(entry)
                || AgentFarmAnchorStateRuntime.hasFarmAnchor(entry)
                || AgentShopStateRuntime.shopVisitPending(entry)) {
            return false;
        }
        tickPhysicsOnly(entry, agent, hooks);
        return true;
    }

    public static void tickPhysicsOnly(AgentRuntimeEntry entry, Character agent, PhysicsHooks hooks) {
        if (hooks.swimMap().test(entry)
                && AgentMovementStateRuntime.inAir(entry)
                && !AgentMovementStateRuntime.climbing(entry)) {
            hooks.swimmingTick().accept(entry);
        } else if (AgentMovementStateRuntime.inAir(entry)) {
            hooks.airborneTick().accept(entry);
        } else if (!AgentMovementStateRuntime.climbing(entry)) {
            int expectedIdleStance = hooks.idleGroundStanceResolver().applyAsInt(entry);
            if (hooks.stanceResolver().applyAsInt(entry) != expectedIdleStance
                    || agent.getStance() != expectedIdleStance) {
                hooks.idleOnGround().accept(entry, agent);
                hooks.movementBroadcaster().accept(entry);
            }
        }
    }

    private static PhysicsHooks defaultHooks() {
        return new PhysicsHooks(
                AgentMapEnvironmentService::isSwimMap,
                entry -> AgentMovementPhaseDispatchService.tickSwimming(entry, null),
                entry -> AgentMovementPhaseDispatchService.tickAirborne(entry, null),
                AgentMovementPoseService::resolveIdleGroundStance,
                AgentMovementPoseService::resolveStance,
                AgentMovementPoseService::idleOnGround,
                AgentMovementBroadcastService::broadcastMovement);
    }
}
