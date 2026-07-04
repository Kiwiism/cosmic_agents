package server.agents.capabilities.movement;

import client.Character;
import constants.game.CharacterStance;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotSwimStateRuntime;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;
import server.maps.Rope;

import java.awt.Point;

/**
 * Agent-owned pose and stance side-effect seam while physics internals migrate.
 */
public final class AgentMovementPoseService {
    private AgentMovementPoseService() {
    }

    public static void resetMotion(BotEntry entry, Point position) {
        BotPhysicsEngine.resetMotion(entry, position);
    }

    public static void teleportTo(BotEntry entry, Character agent, Point position) {
        BotPhysicsEngine.teleportTo(entry, agent, position);
    }

    public static void markDead(BotEntry entry, Character agent) {
        BotPhysicsEngine.markDead(entry, agent);
    }

    public static void idleOnGround(BotEntry entry, Character agent) {
        BotPhysicsEngine.idleOnGround(entry, agent);
    }

    public static void proneOnGround(BotEntry entry, Character agent) {
        BotPhysicsEngine.proneOnGround(entry, agent);
    }

    public static int resolveIdleGroundStance(BotEntry entry) {
        return AgentBotMovementStateRuntime.facingDirectionSign(entry) >= 0
                ? CharacterStance.STAND_RIGHT_STANCE
                : CharacterStance.STAND_LEFT_STANCE;
    }

    public static int resolveStance(BotEntry entry) {
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

    private static int resolveDeadStance(BotEntry entry) {
        return AgentBotMovementStateRuntime.facingDirectionSign(entry) >= 0
                ? CharacterStance.DEAD_RIGHT_STANCE
                : CharacterStance.DEAD_LEFT_STANCE;
    }

    public static boolean isStandingStance(int stance) {
        return CharacterStance.isStanding(stance);
    }

    public static boolean isStandingResolvedStance(BotEntry entry) {
        return isStandingStance(resolveStance(entry));
    }

    public static void syncCharacterState(BotEntry entry) {
        Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (agent == null) {
            return;
        }
        agent.setStance(resolveStance(entry));
    }
}
