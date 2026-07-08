package server.agents.capabilities.movement;

import client.Character;
import constants.game.CharacterStance;
import server.agents.integration.AgentMovementPhysicsStateRuntime;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Rope;

import java.awt.Point;

/**
 * Agent-owned pose and stance side-effect seam while physics internals migrate.
 */
public final class AgentMovementPoseService {
    private AgentMovementPoseService() {
    }

    public static void resetMotion(AgentRuntimeEntry entry, Point position) {
        clearMovementState(entry, position);
        syncCharacterState(entry);
    }

    public static void teleportTo(AgentRuntimeEntry entry, Character agent, Point position) {
        agent.setPosition(position);
        clearMovementState(entry, position);
        syncCharacterState(entry);
    }

    public static void markDead(AgentRuntimeEntry entry, Character agent) {
        clearMovementState(entry, agent.getPosition());
        syncCharacterState(entry);
    }

    public static void idleOnGround(AgentRuntimeEntry entry, Character agent) {
        Point position = agent.getPosition();
        AgentMovementStateRuntime.setInAir(entry, false);
        AgentClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentMovementStateRuntime.setCrouching(entry, false);
        AgentClimbStateRuntime.setClimbUpIntent(entry, false);
        AgentClimbStateRuntime.clearRopeEntry(entry);
        AgentMovementPhysicsStateRuntime.setVerticalVelocity(entry, 0f);
        AgentMovementPhysicsStateRuntime.setAirVelocityX(entry, 0);
        AgentMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentMovementStateRuntime.clearMoveDirection(entry);
        AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 0.0);
        AgentMovementStateRuntime.setMovementVelocity(entry, 0, 0);
        syncCharacterState(entry);
    }

    public static void proneOnGround(AgentRuntimeEntry entry, Character agent) {
        idleOnGround(entry, agent);
        AgentMovementStateRuntime.setCrouching(entry, true);
        AgentMovementStateRuntime.setDownJumpPending(entry, false);
        syncCharacterState(entry);
    }

    public static int resolveIdleGroundStance(AgentRuntimeEntry entry) {
        return AgentMovementStateRuntime.facingDirectionSign(entry) >= 0
                ? CharacterStance.STAND_RIGHT_STANCE
                : CharacterStance.STAND_LEFT_STANCE;
    }

    public static int resolveStance(AgentRuntimeEntry entry) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent != null && agent.getHp() <= 0) {
            return resolveDeadStance(entry);
        }
        if (AgentClimbStateRuntime.climbing(entry)) {
            Rope rope = AgentClimbStateRuntime.climbRope(entry);
            return rope != null && rope.isLadder()
                    ? CharacterStance.LADDER_STANCE
                    : CharacterStance.ROPE_STANCE;
        }
        if (AgentSwimStateRuntime.swimming(entry)) {
            return AgentMovementStateRuntime.facingDirectionSign(entry) >= 0
                    ? CharacterStance.SWIM_RIGHT_STANCE
                    : CharacterStance.SWIM_LEFT_STANCE;
        }
        if (AgentMovementStateRuntime.crouching(entry)) {
            return AgentMovementStateRuntime.facingDirectionSign(entry) >= 0
                    ? CharacterStance.PRONE_RIGHT_STANCE
                    : CharacterStance.PRONE_LEFT_STANCE;
        }
        if (AgentMovementStateRuntime.inAir(entry)) {
            return AgentMovementStateRuntime.facingDirectionSign(entry) >= 0
                    ? CharacterStance.JUMP_RIGHT_STANCE
                    : CharacterStance.JUMP_LEFT_STANCE;
        }
        if (AgentMovementStateRuntime.moveDirection(entry) > 0) {
            return CharacterStance.WALK_RIGHT_STANCE;
        }
        if (AgentMovementStateRuntime.moveDirection(entry) < 0) {
            return CharacterStance.WALK_LEFT_STANCE;
        }
        return resolveIdleGroundStance(entry);
    }

    private static int resolveDeadStance(AgentRuntimeEntry entry) {
        return AgentMovementStateRuntime.facingDirectionSign(entry) >= 0
                ? CharacterStance.DEAD_RIGHT_STANCE
                : CharacterStance.DEAD_LEFT_STANCE;
    }

    public static boolean isStandingStance(int stance) {
        return CharacterStance.isStanding(stance);
    }

    public static boolean isStandingResolvedStance(AgentRuntimeEntry entry) {
        return isStandingStance(resolveStance(entry));
    }

    public static void syncCharacterState(AgentRuntimeEntry entry) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) {
            return;
        }
        agent.setStance(resolveStance(entry));
    }

    private static void clearMovementState(AgentRuntimeEntry entry, Point position) {
        AgentMovementStateRuntime.setInAir(entry, false);
        AgentClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentMovementStateRuntime.setCrouching(entry, false);
        AgentMovementPhysicsStateRuntime.setVerticalVelocity(entry, 0f);
        AgentMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 0.0);
        AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentMovementPhysicsStateRuntime.setGroundPhysicsCarryMs(entry, 0.0);
        AgentMovementPhysicsStateRuntime.setAirVelocityX(entry, 0);
        AgentMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentMovementStateRuntime.setWasMovingX(entry, false);
        AgentMovementStateRuntime.clearMoveDirection(entry);
        AgentClimbStateRuntime.setClimbUpIntent(entry, false);
        AgentClimbStateRuntime.clearBlockedRopeGrab(entry);
        AgentClimbStateRuntime.setRopeGrabCooldownMs(entry, 0);
        AgentMovementStateRuntime.setDownJumpPending(entry, false);
        AgentMovementStateRuntime.setDownJumpGracePeriodMs(entry, 0L);
        AgentClimbStateRuntime.clearRopeEntry(entry);
        AgentMovementStateRuntime.setMovementVelocity(entry, 0, 0);
    }
}
