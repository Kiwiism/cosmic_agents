package server.agents.capabilities.movement;

import client.Character;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;

public final class AgentGroundActionExecutor {
    private AgentGroundActionExecutor() {
    }

    public static void applyGroundAction(AgentRuntimeEntry entry, Foothold currentFoothold, AgentGroundAction action) {
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        AgentMovementStateRuntime.setMoveDirection(entry, switch (action.type()) {
            case WALK, JUMP -> Integer.compare(action.stepX(), 0);
            default -> 0;
        });

        if (action.type() == AgentGroundAction.Type.CROUCH) {
            AgentQueuedMovementActionService.queueDownJump(entry, bot);
            AgentMovementBroadcastService.broadcastMovement(entry);
            return;
        }
        if (action.type() == AgentGroundAction.Type.JUMP) {
            AgentJumpActionService.initiateFixedArcJump(entry, bot, action.stepX());
            return;
        }

        AgentGroundMotion motion = AgentGroundPhysicsService.applyGroundMotion(entry, bot, currentFoothold);
        if (motion.lostGround()) {
            AgentMovementBroadcastService.broadcastMovement(entry);
            return;
        }

        if (motion.stepX() == 0) {
            applyIdleOrInPlaceMotion(entry, action);
            return;
        }

        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    private static void applyIdleOrInPlaceMotion(AgentRuntimeEntry entry, AgentGroundAction action) {
        // Preserve ground momentum while still trying to walk/jump toward a nav target.
        // Otherwise subpixel uphill/transition movement gets zeroed every tick and the agent
        // can stall forever short of a valid launch window.
        if (AgentMovementStateRuntime.movementVelocityX(entry) == 0 && action.type() == AgentGroundAction.Type.IDLE) {
            AgentMovementPoseService.idleOnGround(entry, AgentRuntimeIdentityRuntime.bot(entry));
        }
        AgentMovementBroadcastService.broadcastMovement(entry);
    }
}
