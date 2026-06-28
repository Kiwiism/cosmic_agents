package server.agents.integration;

import client.Character;
import server.agents.capabilities.movement.AgentMovementMode;
import server.agents.capabilities.movement.AgentMovementSnapshot;
import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned read-only movement state facade over temporary BotEntry state.
 */
public final class AgentBotMovementStateRuntime {
    private AgentBotMovementStateRuntime() {
    }

    public static AgentMovementSnapshot snapshot(BotEntry entry) {
        return new AgentMovementSnapshot(
                entry.isFollowing(),
                entry.isGrinding(),
                entry.followTargetId(),
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
        if (entry.isGrinding()) {
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
        if (entry.isFollowing()) {
            return AgentMovementMode.FOLLOWING;
        }
        return AgentMovementMode.STOPPED;
    }

    private static Point position(Character character) {
        return character == null || character.getPosition() == null ? null : new Point(character.getPosition());
    }
}
