package server.agents.capabilities.combat;

import server.agents.capabilities.movement.AgentMapEnvironmentService;
import client.Character;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;

public final class AgentGrindNoTargetFallbackService {
    private AgentGrindNoTargetFallbackService() {
    }

    public record Result(boolean consumedTick, Point targetPos) {
    }

    public record Hooks(AirMovementTick swimTick,
                        AirMovementTick airborneTick,
                        PatrolWanderTargetResolver patrolWanderTargetResolver,
                        NoGrindTargetResolver noGrindTargetResolver,
                        MovementStep movementStep) {
    }

    @FunctionalInterface
    public interface AirMovementTick {
        void tick(AgentRuntimeEntry entry, Point targetPos);
    }

    @FunctionalInterface
    public interface PatrolWanderTargetResolver {
        Point resolve(AgentRuntimeEntry entry, Point agentPosition, MapleMap map);
    }

    @FunctionalInterface
    public interface NoGrindTargetResolver {
        Point resolve(AgentRuntimeEntry entry, Point agentPosition, MapleMap map);
    }

    @FunctionalInterface
    public interface MovementStep {
        void step(AgentRuntimeEntry entry, Point targetPos, boolean runAiTick);
    }

    public static Result handleNoTarget(AgentRuntimeEntry entry,
                                        Character agent,
                                        Point agentPosition,
                                        Point currentTargetPos,
                                        boolean runAiTick,
                                        Hooks hooks) {
        AgentGrindTargetStateRuntime.clear(entry);
        if (AgentMapEnvironmentService.isSwimMap(entry) && AgentMovementStateRuntime.inAir(entry)) {
            hooks.swimTick().tick(entry, currentTargetPos);
            return new Result(true, currentTargetPos);
        } else if (AgentMovementStateRuntime.inAir(entry)) {
            hooks.airborneTick().tick(entry, currentTargetPos);
            return new Result(true, currentTargetPos);
        }
        if (AgentCombatBehaviorRuntime.waitingForResponse(entry)) {
            return new Result(true, agentPosition);
        }

        if (AgentCombatIdleBehaviorRuntime.tick(entry, agent, agentPosition, System.currentTimeMillis())) {
            return new Result(true, agentPosition);
        }

        // Preserve the legacy pre-resolve wander-direction side effect before
        // the shared no-target resolver recomputes the concrete target.
        Point fallbackTargetPos = new Point(
                agentPosition.x + AgentGrindWanderStateRuntime.ensureWanderDirection(entry) * 200,
                agentPosition.y);
        MapleMap map = agent.getMap();
        fallbackTargetPos = AgentMovementStateRuntime.hasPatrolRegion(entry)
                ? hooks.patrolWanderTargetResolver().resolve(entry, agentPosition, map)
                : hooks.noGrindTargetResolver().resolve(entry, agentPosition, map);
        hooks.movementStep().step(entry, fallbackTargetPos, runAiTick);
        return new Result(true, fallbackTargetPos);
    }
}
