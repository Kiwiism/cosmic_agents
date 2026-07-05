package server.agents.integration;

import client.Character;
import server.agents.capabilities.movement.AgentMovementMode;
import server.agents.capabilities.movement.AgentMovementInputState;
import server.agents.capabilities.movement.AgentMovementSnapshot;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.movement.AgentDownJumpState;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentMovementProfileState;

import java.awt.Point;

/**
 * Agent-owned read-only movement state facade over temporary AgentRuntimeEntry state.
 */
public final class AgentBotMovementStateRuntime {
    private AgentBotMovementStateRuntime() {
    }

    public static AgentMovementSnapshot snapshot(AgentRuntimeEntry entry) {
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
                position(AgentBotRuntimeIdentityRuntime.bot(entry)),
                position(AgentBotRuntimeIdentityRuntime.owner(entry)),
                mode(entry));
    }

    public static AgentMovementMode mode(AgentRuntimeEntry entry) {
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

    public static AgentMovementProfile movementProfile(AgentRuntimeEntry entry) {
        return movementProfileState(entry).profile();
    }

    public static AgentMovementProfile movementProfileOrCharacter(AgentRuntimeEntry entry, Character bot) {
        if (entry == null) {
            return AgentMovementProfile.fromCharacter(null);
        }
        AgentMovementProfile profile = movementProfileState(entry).profile();
        return profile == null ? AgentMovementProfile.fromCharacter(bot) : profile;
    }

    public static void setMovementProfile(AgentRuntimeEntry entry, AgentMovementProfile movementProfile) {
        movementProfileState(entry).setProfile(movementProfile);
    }

    public static void refreshMovementProfile(AgentRuntimeEntry entry, Character bot) {
        movementProfileState(entry).refreshFrom(bot);
    }

    public static int moveDirection(AgentRuntimeEntry entry) {
        return movementInputState(entry).moveDirection();
    }

    public static boolean hasMoveDirection(AgentRuntimeEntry entry) {
        return movementInputState(entry).moveDirection() != 0;
    }

    public static void setMoveDirection(AgentRuntimeEntry entry, int moveDirection) {
        movementInputState(entry).setMoveDirection(moveDirection);
    }

    public static void clearMoveDirection(AgentRuntimeEntry entry) {
        movementInputState(entry).clearMoveDirection();
    }

    public static int facingDirection(AgentRuntimeEntry entry) {
        return movementInputState(entry).facingDirection();
    }

    public static int facingDirectionSign(AgentRuntimeEntry entry) {
        return movementInputState(entry).facingDirectionSign();
    }

    public static void setFacingDirection(AgentRuntimeEntry entry, int facingDirection) {
        movementInputState(entry).setFacingDirection(facingDirection);
    }

    public static boolean inAir(AgentRuntimeEntry entry) {
        return entry.movementPhysicsState().inAir();
    }

    public static void setInAir(AgentRuntimeEntry entry, boolean inAir) {
        entry.movementPhysicsState().setInAir(inAir);
    }

    public static boolean grounded(AgentRuntimeEntry entry) {
        return !entry.movementPhysicsState().inAir();
    }

    public static boolean climbing(AgentRuntimeEntry entry) {
        return entry.climbState().climbing();
    }

    public static boolean notClimbing(AgentRuntimeEntry entry) {
        return !entry.climbState().climbing();
    }

    public static boolean downJumpPending(AgentRuntimeEntry entry) {
        return downJumpState(entry).pending();
    }

    public static void setDownJumpPending(AgentRuntimeEntry entry, boolean downJumpPending) {
        downJumpState(entry).setPending(downJumpPending);
    }

    public static boolean crouching(AgentRuntimeEntry entry) {
        return movementInputState(entry).crouching();
    }

    public static void setCrouching(AgentRuntimeEntry entry, boolean crouching) {
        movementInputState(entry).setCrouching(crouching);
    }

    public static boolean hasDownJumpPending(AgentRuntimeEntry entry) {
        return downJumpState(entry).pending();
    }

    public static boolean hasDownJumpGracePeriod(AgentRuntimeEntry entry) {
        return downJumpState(entry).hasGracePeriod();
    }

    public static long downJumpGracePeriodMs(AgentRuntimeEntry entry) {
        return downJumpState(entry).gracePeriodMs();
    }

    public static void setDownJumpGracePeriodMs(AgentRuntimeEntry entry, long downJumpGracePeriodMs) {
        downJumpState(entry).setGracePeriodMs(downJumpGracePeriodMs);
    }

    public static boolean wasMovingX(AgentRuntimeEntry entry) {
        return movementInputState(entry).wasMovingX();
    }

    public static void setWasMovingX(AgentRuntimeEntry entry, boolean wasMovingX) {
        movementInputState(entry).setWasMovingX(wasMovingX);
    }

    public static int movementVelocityX(AgentRuntimeEntry entry) {
        return movementInputState(entry).velocityX();
    }

    public static int movementVelocityY(AgentRuntimeEntry entry) {
        return movementInputState(entry).velocityY();
    }

    public static boolean hasMovementVelocity(AgentRuntimeEntry entry) {
        return movementInputState(entry).hasVelocity();
    }

    public static void setMovementVelocity(AgentRuntimeEntry entry, int velocityX, int velocityY) {
        movementInputState(entry).setVelocity(velocityX, velocityY);
    }

    private static AgentDownJumpState downJumpState(AgentRuntimeEntry entry) {
        return entry.downJumpState();
    }

    private static AgentMovementInputState movementInputState(AgentRuntimeEntry entry) {
        return entry.movementInputState();
    }

    private static AgentMovementProfileState movementProfileState(AgentRuntimeEntry entry) {
        return entry.movementProfileState();
    }

    private static Point position(Character character) {
        return character == null || character.getPosition() == null ? null : new Point(character.getPosition());
    }
}
