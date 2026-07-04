package server.agents.capabilities.movement;

import client.Character;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;
import server.maps.Rope;

/**
 * Agent-owned seam for queued movement actions while physics internals migrate.
 */
public final class AgentQueuedMovementActionService {
    private AgentQueuedMovementActionService() {
    }

    public static void queueDownJump(BotEntry entry, Character agent) {
        AgentMovementPoseService.idleOnGround(entry, agent);
        AgentBotMovementStateRuntime.setDownJumpPending(entry, true);
        AgentBotMovementStateRuntime.setCrouching(entry, true);
        AgentMovementPoseService.syncCharacterState(entry);
    }

    public static void queueTopRopeEntry(BotEntry entry, Character agent, Rope rope, int y) {
        AgentMovementPoseService.idleOnGround(entry, agent);
        AgentBotClimbStateRuntime.queueRopeEntry(entry, rope, y);
        AgentMovementPoseService.syncCharacterState(entry);
    }

    public static void beginDownJump(BotEntry entry, Character agent) {
        if (!AgentGroundCollisionService.canStartDownJump(agent.getMap(), agent.getPosition())) {
            AgentBotMovementStateRuntime.setDownJumpPending(entry, false);
            AgentBotMovementStateRuntime.setDownJumpGracePeriodMs(entry, 0L);
            AgentBotMovementStateRuntime.setCrouching(entry, false);
            AgentMovementPoseService.syncCharacterState(entry);
            return;
        }
        AgentBotClimbStateRuntime.clearBlockedRopeGrab(entry);
        AgentAirborneLaunchService.launchAirborne(
                entry,
                agent.getPosition(),
                -AgentAirborneLaunchService.downJumpForcePerTick(),
                0,
                false);
        AgentBotMovementStateRuntime.setDownJumpGracePeriodMs(
                entry,
                AgentMovementPhysicsConfig.configuredDownJumpGraceMs());
    }

    public static void beginTopRopeEntry(BotEntry entry, Character agent) {
        Rope rope = AgentBotClimbStateRuntime.ropeEntryRope(entry);
        int ropeY = AgentBotClimbStateRuntime.ropeEntryY(entry);
        AgentBotClimbStateRuntime.clearRopeEntry(entry);
        if (rope == null || agent == null) {
            AgentMovementPoseService.syncCharacterState(entry);
            return;
        }
        if (agent.getPosition() == null
                || Math.abs(agent.getPosition().x - rope.x()) > AgentMovementPhysicsConfig.configuredRopeGrabX()) {
            AgentMovementPoseService.syncCharacterState(entry);
            return;
        }
        AgentRopeMovementService.attachToRope(entry, agent, rope, ropeY);
    }
}
