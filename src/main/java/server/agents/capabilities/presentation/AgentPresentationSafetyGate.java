package server.agents.capabilities.presentation;

import client.Character;
import server.agents.capabilities.movement.AgentGroundCollisionService;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.movement.fidget.AgentFidgetStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

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

    public static Result evaluate(AgentRuntimeEntry entry,
                                  Character agent,
                                  AgentPresentationDecision decision,
                                  Point destination,
                                  PrimitiveCapabilityGateway gateway) {
        if (!gateway.alive(agent)) {
            return Result.DEAD;
        }
        if (AgentNavigationDebugStateRuntime.navPreciseTarget(entry)) {
            return Result.PRECISE_NAVIGATION;
        }
        Point position = gateway.position(agent);
        if (position == null) {
            return Result.UNSAFE_GROUND;
        }
        int arrivalGuardPx = 48;
        if (destination != null && movementIntent(decision.intent())
                && position.distanceSq(destination) <= (long) arrivalGuardPx * arrivalGuardPx) {
            return Result.NEAR_DESTINATION;
        }
        if (!gateway.grounded(agent) || AgentMovementStateRuntime.inAir(entry)) {
            return Result.AIRBORNE;
        }
        if (AgentMovementStateRuntime.climbing(entry)) {
            return Result.CLIMBING;
        }
        if (AgentMovementStateRuntime.downJumpPending(entry)) {
            return Result.DOWN_JUMP;
        }
        if (AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)) {
            return Result.ACTIVE_NAVIGATION_EDGE;
        }
        if (AgentFidgetStateRuntime.active(entry)) {
            return Result.ACTIVE_FIDGET;
        }
        if (decision.intent() == AgentPresentationIntent.HOP
                || decision.intent() == AgentPresentationIntent.SHUFFLE
                || decision.intent() == AgentPresentationIntent.COMBAT_REPOSITION) {
            int step = AgentMovementKinematicsService.walkStep(
                    agent.getMap(), AgentMovementStateRuntime.movementProfile(entry));
            boolean left = AgentGroundCollisionService.canWalkGroundStep(
                    agent.getMap(), position, -step);
            boolean right = AgentGroundCollisionService.canWalkGroundStep(
                    agent.getMap(), position, step);
            if (decision.intent() == AgentPresentationIntent.HOP ? !(left && right) : !(left || right)) {
                return Result.UNSAFE_GROUND;
            }
        }
        return Result.SAFE;
    }

    private static boolean movementIntent(AgentPresentationIntent intent) {
        return intent == AgentPresentationIntent.HOP
                || intent == AgentPresentationIntent.SHUFFLE
                || intent == AgentPresentationIntent.COMBAT_REPOSITION;
    }
}
