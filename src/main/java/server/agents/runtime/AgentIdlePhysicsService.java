package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotShopStateRuntime;

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

    public static boolean tickIdleEntry(AgentRuntimeEntry entry, Character agent, PhysicsHooks hooks) {
        if (AgentBotModeStateRuntime.following(entry)
                || AgentBotModeStateRuntime.grinding(entry)
                || AgentBotMoveTargetStateRuntime.hasMoveTarget(entry)
                || AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry)
                || AgentBotShopStateRuntime.shopVisitPending(entry)) {
            return false;
        }
        tickPhysicsOnly(entry, agent, hooks);
        return true;
    }

    public static void tickPhysicsOnly(AgentRuntimeEntry entry, Character agent, PhysicsHooks hooks) {
        if (hooks.swimMap().test(entry)
                && AgentBotMovementStateRuntime.inAir(entry)
                && !AgentBotMovementStateRuntime.climbing(entry)) {
            hooks.swimmingTick().accept(entry);
        } else if (AgentBotMovementStateRuntime.inAir(entry)) {
            hooks.airborneTick().accept(entry);
        } else if (!AgentBotMovementStateRuntime.climbing(entry)) {
            int expectedIdleStance = hooks.idleGroundStanceResolver().applyAsInt(entry);
            if (hooks.stanceResolver().applyAsInt(entry) != expectedIdleStance
                    || agent.getStance() != expectedIdleStance) {
                hooks.idleOnGround().accept(entry, agent);
                hooks.movementBroadcaster().accept(entry);
            }
        }
    }
}
