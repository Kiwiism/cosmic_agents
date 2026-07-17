package server.agents.commands;

import server.agents.capabilities.dialogue.AgentPendingActionStateRuntime;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatAwayFlow;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.dialogue.AgentChatSessionRequestFlow;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentLifecyclePhase;
import server.agents.runtime.AgentLifecycleStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentReloginRequest;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.AgentSessionControlRuntime;
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

            @Override
            public void requestAway() {
                if (!server.agents.runtime.AgentLegacyOwnerCompatibility.enabled()) {
                    return;
                }
                if (!AgentSessionControlRuntime.isPrimarySession(entry)) {
                    return;
                }
                AgentSchedulerRuntime.afterRandomDelay(entry, 900, 1100, () -> promptOwnerAway(entry));
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

    public static void handleOwnerAwayChoice(AgentRuntimeEntry entry, String message) {
        AgentChatAwayFlow.handleOwnerAwayChoice(
                message,
                AgentSessionControlRuntime.shouldOfferTownForAwayCommand(entry),
                awayChoiceCallbacks(entry));
    }

    private static void promptOwnerAway(AgentRuntimeEntry entry) {
        AgentChatAwayFlow.promptOwnerAway(
                AgentSessionControlRuntime.shouldOfferTownForAwayCommand(entry),
                awayPromptCallbacks(entry));
    }

    private static AgentChatAwayFlow.AwayPromptCallbacks awayPromptCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatAwayFlow.AwayPromptCallbacks() {
            @Override
            public void setPendingOwnerAway() {
                AgentPendingActionStateRuntime.setPendingAction(entry, AgentChatPendingAction.OWNER_AWAY);
            }

            @Override
            public void stopAgent() {
                AgentMovementCommandRuntime.stop(entry);
            }

            @Override
            public void replyTownOrLogout() {
                AgentReplyRuntime.replyNow(entry, AgentChatAwayFlow.townOrLogoutPrompt());
            }

            @Override
            public void replyStayOrLogout() {
                AgentReplyRuntime.replyNow(entry, AgentChatAwayFlow.stayOrLogoutPrompt());
            }
        };
    }

    private static AgentChatAwayFlow.AwayChoiceCallbacks awayChoiceCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatAwayFlow.AwayChoiceCallbacks() {
            @Override
            public void clearPendingAction() {
                AgentPendingActionStateRuntime.clearPendingAction(entry);
            }

            @Override
            public void logout() {
                AgentSchedulerRuntime.afterRandomDelay(entry, 700, 900, () -> {
                    AgentReplyRuntime.replyNow(entry, AgentChatAwayFlow.logoutConfirmReply());
                    logoutOwnerBots(entry);
                });
            }

            @Override
            public void townOrStay(boolean townOffered) {
                int ownerId = ownerId(entry);
                if (ownerId != 0) {
                    AgentSessionControlRuntime.issueOwnerAwaySafeModeForLeader(ownerId, townOffered);
                }
                AgentSchedulerRuntime.afterRandomDelay(entry, 700, 900, () ->
                        AgentReplyRuntime.replyNow(entry, AgentChatAwayFlow.townOrStayConfirmReply(townOffered)));
            }

            @Override
            public void stay() {
                int ownerId = ownerId(entry);
                if (ownerId != 0) {
                    AgentSessionControlRuntime.issueOwnerAwaySafeModeForLeader(ownerId, false);
                }
                AgentSchedulerRuntime.afterRandomDelay(entry, 700, 900, () ->
                        AgentReplyRuntime.replyNow(entry, AgentChatAwayFlow.stayConfirmReply()));
            }

            @Override
            public void cancel() {
                AgentSchedulerRuntime.afterRandomDelay(entry, 700, 900, () ->
                        AgentReplyRuntime.replyNow(entry, AgentChatAwayFlow.cancelReply()));
            }
        };
    }

    private static void logoutOwnerBots(AgentRuntimeEntry entry) {
        Character owner = owner(entry);
        if (owner == null) {
            return;
        }

        for (AgentRuntimeEntry owned : AgentSessionLifecycleRuntime.getBotEntries(owner.getId())) {
            AgentMailboxRuntime.dispatch(owned, ignored -> {
                AgentMovementCommandRuntime.stop(owned);
                AgentSchedulerRuntime.afterRandomDelay(owned, 1200, 1800, () -> {
                    Character ownedBot = bot(owned);
                    AgentCharacterGatewayRuntime.characters().save(ownedBot, true);
                    AgentCharacterGatewayRuntime.characters().disconnect(ownedBot, false, false);
                });
                return null;
            });
        }
    }

    private static Character bot(AgentRuntimeEntry entry) {
        return AgentRuntimeIdentityRuntime.bot(entry);
    }

    private static Character owner(AgentRuntimeEntry entry) {
        return AgentRelationshipRuntime.interactionTarget(entry);
    }

    private static int ownerId(AgentRuntimeEntry entry) {
        Character owner = owner(entry);
        return owner != null ? owner.getId() : 0;
    }
}
