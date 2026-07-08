package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatAwayFlow;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.dialogue.AgentChatSessionRequestFlow;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned session facade over temporary bot-side lifecycle side effects.
 */
public final class AgentBotSessionRuntime {
    private AgentBotSessionRuntime() {
    }

    public static AgentChatSessionRequestFlow.SessionRequestCallbacks sessionRequestCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatSessionRequestFlow.SessionRequestCallbacks() {
            @Override
            public void requestRelog() {
                AgentSchedulerRuntime.afterRandomDelay(900, 1100, () -> {
                    AgentBotPendingActionStateRuntime.setPendingAction(entry, AgentChatPendingAction.RELOG);
                    AgentBotMovementCommandRuntime.stop(entry);
                    AgentReplyRuntime.replyNow(entry, AgentChatSessionRequestFlow.relogConfirmPrompt());
                });
            }

            @Override
            public void requestLogout() {
                AgentSchedulerRuntime.afterRandomDelay(900, 1100, () -> {
                    AgentBotPendingActionStateRuntime.setPendingAction(entry, AgentChatPendingAction.LOGOUT);
                    AgentBotMovementCommandRuntime.stop(entry);
                    AgentReplyRuntime.replyNow(entry, AgentChatSessionRequestFlow.logoutConfirmPrompt());
                });
            }

            @Override
            public void requestAway() {
                if (!AgentBotSessionControlRuntime.isPrimarySession(entry)) {
                    return;
                }
                AgentSchedulerRuntime.afterRandomDelay(900, 1100, () -> promptOwnerAway(entry));
            }
        };
    }

    public static void scheduleRelogConfirm(AgentRuntimeEntry entry) {
        AgentSchedulerRuntime.afterRandomDelay(900, 1100, () -> {
            Character owner = owner(entry);
            if (owner == null) {
                return;
            }
            AgentReplyRuntime.replyNow(entry, AgentChatSessionRequestFlow.relogConfirmedReply());
            Character bot = bot(entry);
            int charId = bot.getId();
            int ownerCharId = owner.getId();
            int world = bot.getClient().getWorld();
            int channel = bot.getClient().getChannel();
            AgentSchedulerRuntime.afterRandomDelay(1800, 2200, () -> {
                Character relogBot = bot(entry);
                relogBot.saveCharToDB(true);
                relogBot.getClient().disconnect(false, false);
                AgentSchedulerRuntime.afterRandomDelay(10000, 10100,
                        () -> AgentBotSessionLifecycleSideEffects.reloginBot(charId, ownerCharId, world, channel));
            });
        });
    }

    public static void scheduleLogoutConfirm(AgentRuntimeEntry entry) {
        AgentSchedulerRuntime.afterRandomDelay(900, 1100, () -> {
            AgentReplyRuntime.replyNow(entry, AgentChatSessionRequestFlow.logoutConfirmedReply());
            AgentSchedulerRuntime.afterRandomDelay(1800, 2200, () -> {
                Character logoutBot = bot(entry);
                logoutBot.saveCharToDB(true);
                logoutBot.getClient().disconnect(false, false);
            });
        });
    }

    public static void handleOwnerAwayChoice(AgentRuntimeEntry entry, String message) {
        AgentChatAwayFlow.handleOwnerAwayChoice(
                message,
                AgentBotSessionControlRuntime.shouldOfferTownForAwayCommand(entry),
                awayChoiceCallbacks(entry));
    }

    private static void promptOwnerAway(AgentRuntimeEntry entry) {
        AgentChatAwayFlow.promptOwnerAway(
                AgentBotSessionControlRuntime.shouldOfferTownForAwayCommand(entry),
                awayPromptCallbacks(entry));
    }

    private static AgentChatAwayFlow.AwayPromptCallbacks awayPromptCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatAwayFlow.AwayPromptCallbacks() {
            @Override
            public void setPendingOwnerAway() {
                AgentBotPendingActionStateRuntime.setPendingAction(entry, AgentChatPendingAction.OWNER_AWAY);
            }

            @Override
            public void stopAgent() {
                AgentBotMovementCommandRuntime.stop(entry);
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
                AgentBotPendingActionStateRuntime.clearPendingAction(entry);
            }

            @Override
            public void logout() {
                AgentSchedulerRuntime.afterRandomDelay(700, 900, () -> {
                    AgentReplyRuntime.replyNow(entry, AgentChatAwayFlow.logoutConfirmReply());
                    logoutOwnerBots(entry);
                });
            }

            @Override
            public void townOrStay(boolean townOffered) {
                int ownerId = ownerId(entry);
                if (ownerId != 0) {
                    AgentBotSessionControlRuntime.issueOwnerAwaySafeModeForLeader(ownerId, townOffered);
                }
                AgentSchedulerRuntime.afterRandomDelay(700, 900, () ->
                        AgentReplyRuntime.replyNow(entry, AgentChatAwayFlow.townOrStayConfirmReply(townOffered)));
            }

            @Override
            public void stay() {
                int ownerId = ownerId(entry);
                if (ownerId != 0) {
                    AgentBotSessionControlRuntime.issueOwnerAwaySafeModeForLeader(ownerId, false);
                }
                AgentSchedulerRuntime.afterRandomDelay(700, 900, () ->
                        AgentReplyRuntime.replyNow(entry, AgentChatAwayFlow.stayConfirmReply()));
            }

            @Override
            public void cancel() {
                AgentSchedulerRuntime.afterRandomDelay(700, 900, () ->
                        AgentReplyRuntime.replyNow(entry, AgentChatAwayFlow.cancelReply()));
            }
        };
    }

    private static void logoutOwnerBots(AgentRuntimeEntry entry) {
        Character owner = owner(entry);
        if (owner == null) {
            return;
        }

        for (AgentRuntimeEntry owned : AgentBotSessionLifecycleSideEffects.getBotEntries(owner.getId())) {
            AgentBotMovementCommandRuntime.stop(owned);
            AgentSchedulerRuntime.afterRandomDelay(1200, 1800, () -> {
                Character ownedBot = bot(owned);
                ownedBot.saveCharToDB(true);
                ownedBot.getClient().disconnect(false, false);
            });
        }
    }

    private static Character bot(AgentRuntimeEntry entry) {
        return AgentRuntimeIdentityRuntime.bot(entry);
    }

    private static Character owner(AgentRuntimeEntry entry) {
        return AgentRuntimeIdentityRuntime.owner(entry);
    }

    private static int ownerId(AgentRuntimeEntry entry) {
        Character owner = owner(entry);
        return owner != null ? owner.getId() : 0;
    }
}
