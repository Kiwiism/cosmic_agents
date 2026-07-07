package server.agents.capabilities.movement;

import client.Character;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;

public final class AgentGroundActionExecutor {
    private AgentGroundActionExecutor() {
    }

    public static void applyGroundAction(AgentRuntimeEntry entry, Foothold currentFoothold, AgentGroundAction action) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        AgentBotMovementStateRuntime.setMoveDirection(entry, switch (action.type()) {
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

        AgentGroundMotion motion = AgentGroundPhysicsService.applyGroundMotion(asBotEntry(entry), bot, currentFoothold);
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
        if (AgentBotMovementStateRuntime.movementVelocityX(entry) == 0 && action.type() == AgentGroundAction.Type.IDLE) {
            AgentMovementPoseService.idleOnGround(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
        }
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    private static server.bots.BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (server.bots.BotEntry) entry;
    }
}
