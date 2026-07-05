package server.agents.capabilities.movement;

import client.Character;
import constants.game.CharacterStance;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotSwimStateRuntime;
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
        AgentBotMovementStateRuntime.setInAir(entry, false);
        AgentBotClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentBotMovementStateRuntime.setCrouching(entry, false);
        AgentBotClimbStateRuntime.setClimbUpIntent(entry, false);
        AgentBotClimbStateRuntime.clearRopeEntry(entry);
        AgentBotMovementPhysicsStateRuntime.setVerticalVelocity(entry, 0f);
        AgentBotMovementPhysicsStateRuntime.setAirVelocityX(entry, 0);
        AgentBotMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentBotMovementStateRuntime.clearMoveDirection(entry);
        AgentBotMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentBotMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 0.0);
        AgentBotMovementStateRuntime.setMovementVelocity(entry, 0, 0);
        syncCharacterState(entry);
    }

    public static void proneOnGround(AgentRuntimeEntry entry, Character agent) {
        idleOnGround(entry, agent);
        AgentBotMovementStateRuntime.setCrouching(entry, true);
        AgentBotMovementStateRuntime.setDownJumpPending(entry, false);
        syncCharacterState(entry);
    }

    public static int resolveIdleGroundStance(AgentRuntimeEntry entry) {
        return AgentBotMovementStateRuntime.facingDirectionSign(entry) >= 0
                ? CharacterStance.STAND_RIGHT_STANCE
                : CharacterStance.STAND_LEFT_STANCE;
    }

    public static int resolveStance(AgentRuntimeEntry entry) {
        Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (agent != null && agent.getHp() <= 0) {
            return resolveDeadStance(entry);
        }
        if (AgentBotClimbStateRuntime.climbing(entry)) {
            Rope rope = AgentBotClimbStateRuntime.climbRope(entry);
            return rope != null && rope.isLadder()
                    ? CharacterStance.LADDER_STANCE
                    : CharacterStance.ROPE_STANCE;
        }
        if (AgentBotSwimStateRuntime.swimming(entry)) {
            return AgentBotMovementStateRuntime.facingDirectionSign(entry) >= 0
                    ? CharacterStance.SWIM_RIGHT_STANCE
                    : CharacterStance.SWIM_LEFT_STANCE;
        }
        if (AgentBotMovementStateRuntime.crouching(entry)) {
            return AgentBotMovementStateRuntime.facingDirectionSign(entry) >= 0
                    ? CharacterStance.PRONE_RIGHT_STANCE
                    : CharacterStance.PRONE_LEFT_STANCE;
        }
        if (AgentBotMovementStateRuntime.inAir(entry)) {
            return AgentBotMovementStateRuntime.facingDirectionSign(entry) >= 0
                    ? CharacterStance.JUMP_RIGHT_STANCE
                    : CharacterStance.JUMP_LEFT_STANCE;
        }
        if (AgentBotMovementStateRuntime.moveDirection(entry) > 0) {
            return CharacterStance.WALK_RIGHT_STANCE;
        }
        if (AgentBotMovementStateRuntime.moveDirection(entry) < 0) {
            return CharacterStance.WALK_LEFT_STANCE;
        }
        return resolveIdleGroundStance(entry);
    }

    private static int resolveDeadStance(AgentRuntimeEntry entry) {
        return AgentBotMovementStateRuntime.facingDirectionSign(entry) >= 0
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
        Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (agent == null) {
            return;
        }
        agent.setStance(resolveStance(entry));
    }

    private static void clearMovementState(AgentRuntimeEntry entry, Point position) {
        AgentBotMovementStateRuntime.setInAir(entry, false);
        AgentBotClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentBotMovementStateRuntime.setCrouching(entry, false);
        AgentBotMovementPhysicsStateRuntime.setVerticalVelocity(entry, 0f);
        AgentBotMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 0.0);
        AgentBotMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentBotMovementPhysicsStateRuntime.setGroundPhysicsCarryMs(entry, 0.0);
        AgentBotMovementPhysicsStateRuntime.setAirVelocityX(entry, 0);
        AgentBotMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentBotMovementStateRuntime.setWasMovingX(entry, false);
        AgentBotMovementStateRuntime.clearMoveDirection(entry);
        AgentBotClimbStateRuntime.setClimbUpIntent(entry, false);
        AgentBotClimbStateRuntime.clearBlockedRopeGrab(entry);
        AgentBotClimbStateRuntime.setRopeGrabCooldownMs(entry, 0);
        AgentBotMovementStateRuntime.setDownJumpPending(entry, false);
        AgentBotMovementStateRuntime.setDownJumpGracePeriodMs(entry, 0L);
        AgentBotClimbStateRuntime.clearRopeEntry(entry);
        AgentBotMovementStateRuntime.setMovementVelocity(entry, 0, 0);
    }
}
