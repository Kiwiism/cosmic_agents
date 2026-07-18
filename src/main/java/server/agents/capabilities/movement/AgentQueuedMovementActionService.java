package server.agents.capabilities.movement;

import client.Character;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Rope;

/**
 * Agent-owned seam for queued movement actions while physics internals migrate.
 */
public final class AgentQueuedMovementActionService {
    private AgentQueuedMovementActionService() {
    }

    public static void queueDownJump(AgentRuntimeEntry entry, Character agent) {
        AgentMovementPoseService.idleOnGround(entry, agent);
        AgentMovementStateRuntime.setDownJumpPending(entry, true);
        AgentMovementStateRuntime.setCrouching(entry, true);
        AgentMovementPoseService.syncCharacterState(entry);
    }

    public static void queueTopRopeEntry(AgentRuntimeEntry entry,
                                         Character agent,
                                         Rope rope,
                                         int y,
                                         int climbDirection) {
        AgentMovementPoseService.idleOnGround(entry, agent);
        AgentClimbStateRuntime.queueRopeEntry(entry, rope, y, climbDirection);
        AgentMovementPoseService.syncCharacterState(entry);
    }

    public static void beginDownJump(AgentRuntimeEntry entry, Character agent) {
        if (!AgentGroundCollisionService.canStartDownJump(agent.getMap(), agent.getPosition())) {
            AgentMovementStateRuntime.setDownJumpPending(entry, false);
            AgentMovementStateRuntime.setDownJumpGracePeriodMs(entry, 0L);
            AgentMovementStateRuntime.setCrouching(entry, false);
            AgentMovementPoseService.syncCharacterState(entry);
            return;
        }
        AgentClimbStateRuntime.clearBlockedRopeGrab(entry);
        AgentAirborneLaunchService.launchAirborne(
                entry,
                agent.getPosition(),
                -AgentAirborneLaunchService.downJumpForcePerTick(),
                0,
                false);
        AgentMovementStateRuntime.setDownJumpGracePeriodMs(
                entry,
                AgentMovementPhysicsConfig.configuredDownJumpGraceMs());
    }

    public static void beginTopRopeEntry(AgentRuntimeEntry entry, Character agent) {
        Rope rope = AgentClimbStateRuntime.ropeEntryRope(entry);
        int ropeY = AgentClimbStateRuntime.ropeEntryY(entry);
        int climbDirection = AgentClimbStateRuntime.ropeEntryDirection(entry);
        AgentClimbStateRuntime.clearRopeEntry(entry);
        if (rope == null || agent == null) {
            AgentMovementPoseService.syncCharacterState(entry);
            return;
        }
        if (agent.getPosition() == null
                || Math.abs(agent.getPosition().x - rope.x()) > AgentMovementPhysicsConfig.configuredRopeGrabX()) {
            AgentMovementPoseService.syncCharacterState(entry);
            return;
        }
        AgentRopeMovementService.attachToRope(entry, agent, rope, ropeY, climbDirection);
    }
}
