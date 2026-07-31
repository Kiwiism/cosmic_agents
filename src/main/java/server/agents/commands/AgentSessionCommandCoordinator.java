package server.agents.commands;

import server.agents.capabilities.dialogue.AgentPendingActionStateRuntime;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.dialogue.AgentChatSessionRequestFlow;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.runtime.AgentLifecyclePhase;
import server.agents.runtime.AgentLifecycleStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentReloginRequest;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.AgentSessionLifecycleRuntime;

/**
 * Coordinates external session commands with runtime, dialogue, and integration
 * services while preserving the legacy delayed-action order.
 */
public final class AgentSessionCommandCoordinator {
    private AgentSessionCommandCoordinator() {
    }

    public static AgentChatSessionRequestFlow.SessionRequestCallbacks sessionRequestCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatSessionRequestFlow.SessionRequestCallbacks() {
            @Override
            public void requestRelog() {
                AgentSchedulerRuntime.afterRandomDelay(entry, 900, 1100, () -> {
                    AgentPendingActionStateRuntime.setPendingAction(entry, AgentChatPendingAction.RELOG);
                    AgentMovementCommandRuntime.stop(entry);
                    AgentReplyRuntime.replyNow(entry, AgentChatSessionRequestFlow.relogConfirmPrompt());
                });
            }

            @Override
            public void requestLogout() {
                AgentSchedulerRuntime.afterRandomDelay(entry, 900, 1100, () -> {
                    AgentPendingActionStateRuntime.setPendingAction(entry, AgentChatPendingAction.LOGOUT);
                    AgentMovementCommandRuntime.stop(entry);
                    AgentReplyRuntime.replyNow(entry, AgentChatSessionRequestFlow.logoutConfirmPrompt());
                });
            }

        };
    }

    public static void scheduleRelogConfirm(AgentRuntimeEntry entry) {
        AgentSchedulerRuntime.afterRandomDelay(entry, 900, 1100, () -> {
            AgentReplyRuntime.replyNow(entry, AgentChatSessionRequestFlow.relogConfirmedReply());
            Character bot = bot(entry);
            AgentReloginRequest request = reloginRequest(entry, bot);
            AgentSchedulerRuntime.afterRandomDelay(entry, 1800, 2200, () -> {
                AgentLifecycleStateRuntime.transition(
                        entry, AgentLifecyclePhase.RELOGIN_BACKOFF, "requested relog");
                Character relogBot = bot(entry);
                AgentCharacterGatewayRuntime.characters().save(relogBot, true);
                AgentCharacterGatewayRuntime.characters().disconnect(relogBot, false, false);
                AgentSchedulerRuntime.afterRandomDelay(10000, 10100,
                        () -> AgentSessionLifecycleRuntime.reloginAgent(request));
            });
        });
    }

    private static AgentReloginRequest reloginRequest(AgentRuntimeEntry entry, Character bot) {
        Character followTarget = AgentRelationshipRuntime.followTarget(entry);
        Character interactionTarget = AgentRelationshipRuntime.interactionTarget(entry);
        return new AgentReloginRequest(
                bot.getId(),
                Math.toIntExact(AgentRelationshipRuntime.cohortId(entry)),
                AgentRelationshipRuntime.formationId(entry),
                followTarget == null ? 0 : followTarget.getId(),
                interactionTarget == null ? 0 : interactionTarget.getId(),
                AgentClientGatewayRuntime.clients().world(bot),
                AgentClientGatewayRuntime.clients().channel(bot),
                bot.getMapId(),
                bot.getPosition());
    }

    public static void scheduleLogoutConfirm(AgentRuntimeEntry entry) {
        AgentSchedulerRuntime.afterRandomDelay(entry, 900, 1100, () -> {
            AgentReplyRuntime.replyNow(entry, AgentChatSessionRequestFlow.logoutConfirmedReply());
            AgentSchedulerRuntime.afterRandomDelay(entry, 1800, 2200, () -> {
                AgentLifecycleStateRuntime.transition(entry, AgentLifecyclePhase.STOPPING, "requested logout");
                Character logoutBot = bot(entry);
                AgentCharacterGatewayRuntime.characters().save(logoutBot, true);
                AgentCharacterGatewayRuntime.characters().disconnect(logoutBot, false, false);
            });
        });
    }

    private static Character bot(AgentRuntimeEntry entry) {
        return AgentRuntimeIdentityRuntime.bot(entry);
    }

}
