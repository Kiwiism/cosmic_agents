package server.agents.integration;

import client.Character;
import server.agents.capabilities.movement.AgentMovementMode;
import server.agents.capabilities.movement.AgentMovementSnapshot;
import server.bots.BotEntry;
import server.bots.BotMovementProfile;

import java.awt.Point;

/**
 * Agent-owned read-only movement state facade over temporary BotEntry state.
 */
public final class AgentBotMovementStateRuntime {
    private AgentBotMovementStateRuntime() {
    }

    public static AgentMovementSnapshot snapshot(BotEntry entry) {
        return new AgentMovementSnapshot(
                AgentBotModeStateRuntime.following(entry),
                AgentBotModeStateRuntime.grinding(entry),
                AgentBotModeStateRuntime.followTargetId(entry),
                AgentBotMoveTargetStateRuntime.moveTarget(entry),
                AgentBotMoveTargetStateRuntime.isPrecise(entry),
                AgentBotFarmAnchorStateRuntime.farmAnchor(entry),
                AgentBotFarmAnchorStateRuntime.farmAnchorMapId(entry),
                AgentBotPatrolStateRuntime.patrolRegionId(entry),
                AgentBotPatrolStateRuntime.patrolMapId(entry),
                AgentBotPatrolStateRuntime.patrolWanderTarget(entry),
                position(entry.bot()),
                position(entry.owner()),
                mode(entry));
    }

    public static AgentMovementMode mode(BotEntry entry) {
        if (AgentBotModeStateRuntime.grinding(entry)) {
            return AgentMovementMode.GRINDING;
        }
        if (AgentBotPatrolStateRuntime.hasPatrolRegion(entry)) {
            return AgentMovementMode.PATROLLING;
        }
        if (AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry)) {
            return AgentMovementMode.FARMING;
        }
        if (AgentBotMoveTargetStateRuntime.hasMoveTarget(entry)) {
            return AgentMovementMode.MOVING;
        }
        if (AgentBotModeStateRuntime.following(entry)) {
            return AgentMovementMode.FOLLOWING;
        }
        return AgentMovementMode.STOPPED;
    }

    public static BotMovementProfile movementProfile(BotEntry entry) {
        return entry.movementProfile();
    }

    public static void setMovementProfile(BotEntry entry, BotMovementProfile movementProfile) {
        entry.setMovementProfile(movementProfile);
    }

    public static void refreshMovementProfile(BotEntry entry, Character bot) {
        entry.setMovementProfile(BotMovementProfile.fromCharacter(bot));
    }

    public static int moveDirection(BotEntry entry) {
        return entry.moveDirection();
    }

    public static boolean hasMoveDirection(BotEntry entry) {
        return entry.moveDirection() != 0;
    }

    public static void setMoveDirection(BotEntry entry, int moveDirection) {
        entry.setMoveDirection(moveDirection);
    }

    public static void clearMoveDirection(BotEntry entry) {
        entry.clearMoveDirection();
    }

    private static Point position(Character character) {
        return character == null || character.getPosition() == null ? null : new Point(character.getPosition());
    }
}
