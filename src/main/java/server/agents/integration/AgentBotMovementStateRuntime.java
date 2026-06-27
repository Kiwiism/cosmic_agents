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
                entry.moveTarget(),
                entry.isMoveTargetPrecise(),
                entry.farmAnchor(),
                entry.farmAnchorMapId(),
                entry.patrolRegionId(),
                entry.patrolMapId(),
                entry.patrolWanderTarget(),
                position(entry.bot()),
                position(entry.owner()),
                mode(entry));
    }

    public static AgentMovementMode mode(BotEntry entry) {
        if (entry.isGrinding()) {
            return AgentMovementMode.GRINDING;
        }
        if (entry.patrolRegionId() >= 0) {
            return AgentMovementMode.PATROLLING;
        }
        if (entry.farmAnchor() != null) {
            return AgentMovementMode.FARMING;
        }
        if (entry.moveTarget() != null) {
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
