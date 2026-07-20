package server.agents.capabilities.presentation;

import server.agents.capabilities.movement.AgentGroundCollisionService;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.movement.fidget.AgentFidgetStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.awt.Point;

public final class AgentPresentationSafetyGate {
    public enum Result {
        SAFE,
        DEAD,
        PRECISE_NAVIGATION,
        NEAR_DESTINATION,
        AIRBORNE,
        CLIMBING,
        DOWN_JUMP,
        ACTIVE_NAVIGATION_EDGE,
        ACTIVE_FIDGET,
        UNSAFE_GROUND
    }

    private AgentPresentationSafetyGate() {
    }

    public static Result evaluate(AgentCapabilityContext context,
                                  AgentPresentationDecision decision,
                                  Point destination,
                                  int tolerancePx,
                                  boolean precise,
                                  PrimitiveCapabilityGateway gateway) {
        if (!gateway.alive(context.agent())) {
            return Result.DEAD;
        }
        if (precise) {
            return Result.PRECISE_NAVIGATION;
        }
        Point position = gateway.position(context.agent());
        int arrivalGuardPx = Math.max(48, tolerancePx + 32);
        if (position.distanceSq(destination) <= (long) arrivalGuardPx * arrivalGuardPx) {
            return Result.NEAR_DESTINATION;
        }
        if (!gateway.grounded(context.agent()) || AgentMovementStateRuntime.inAir(context.entry())) {
            return Result.AIRBORNE;
        }
        if (AgentMovementStateRuntime.climbing(context.entry())) {
            return Result.CLIMBING;
        }
        if (AgentMovementStateRuntime.downJumpPending(context.entry())) {
            return Result.DOWN_JUMP;
        }
        if (AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(context.entry())) {
            return Result.ACTIVE_NAVIGATION_EDGE;
        }
        if (AgentFidgetStateRuntime.active(context.entry())) {
            return Result.ACTIVE_FIDGET;
        }
        if (decision.intent() == AgentPresentationIntent.HOP
                || decision.intent() == AgentPresentationIntent.SHUFFLE
                || decision.intent() == AgentPresentationIntent.COMBAT_REPOSITION) {
            int step = AgentMovementKinematicsService.walkStep(
                    context.agent().getMap(), AgentMovementStateRuntime.movementProfile(context.entry()));
            boolean left = AgentGroundCollisionService.canWalkGroundStep(
                    context.agent().getMap(), position, -step);
            boolean right = AgentGroundCollisionService.canWalkGroundStep(
                    context.agent().getMap(), position, step);
            if (decision.intent() == AgentPresentationIntent.HOP ? !(left && right) : !(left || right)) {
                return Result.UNSAFE_GROUND;
            }
        }
        return Result.SAFE;
    }
}
