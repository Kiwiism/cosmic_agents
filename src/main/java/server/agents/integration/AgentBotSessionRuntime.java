package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatAwayFlow;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.dialogue.AgentChatSessionRequestFlow;
import server.bots.BotEntry;

/**
 * Agent-owned session facade over temporary bot-side lifecycle side effects.
 */
public final class AgentBotSessionRuntime {
    private AgentBotSessionRuntime() {
    }

    public static AgentChatSessionRequestFlow.SessionRequestCallbacks sessionRequestCallbacks(BotEntry entry) {
        return new AgentChatSessionRequestFlow.SessionRequestCallbacks() {
            @Override
            public void requestRelog() {
                AgentBotSessionSchedulerRuntime.afterRandomDelay(900, 1100, () -> {
                    AgentBotPendingActionStateRuntime.setPendingAction(entry, AgentChatPendingAction.RELOG);
                    AgentBotMovementCommandRuntime.stop(entry);
                    AgentBotSessionReplyRuntime.replyNow(entry, AgentChatSessionRequestFlow.relogConfirmPrompt());
                });
            }

            @Override
            public void requestLogout() {
                AgentBotSessionSchedulerRuntime.afterRandomDelay(900, 1100, () -> {
                    AgentBotPendingActionStateRuntime.setPendingAction(entry, AgentChatPendingAction.LOGOUT);
                    AgentBotMovementCommandRuntime.stop(entry);
                    AgentBotSessionReplyRuntime.replyNow(entry, AgentChatSessionRequestFlow.logoutConfirmPrompt());
                });
            }

            @Override
            public void requestAway() {
                if (!AgentBotSessionControlRuntime.isPrimarySession(entry)) {
                    return;
                }
                AgentBotSessionSchedulerRuntime.afterRandomDelay(900, 1100, () -> promptOwnerAway(entry));
            }
        };
    }

    public static void scheduleRelogConfirm(BotEntry entry) {
        AgentBotSessionSchedulerRuntime.afterRandomDelay(900, 1100, () -> {
            Character owner = entry.owner();
            if (owner == null) {
                return;
            }
            AgentBotSessionReplyRuntime.replyNow(entry, AgentChatSessionRequestFlow.relogConfirmedReply());
            int charId = entry.bot().getId();
            int ownerCharId = owner.getId();
            int world = entry.bot().getClient().getWorld();
            int channel = entry.bot().getClient().getChannel();
            AgentBotSessionSchedulerRuntime.afterRandomDelay(1800, 2200, () -> {
                entry.bot().saveCharToDB(true);
                entry.bot().getClient().disconnect(false, false);
                AgentBotSessionSchedulerRuntime.afterRandomDelay(10000, 10100,
                        () -> AgentBotSessionLifecycleSideEffects.reloginBot(charId, ownerCharId, world, channel));
            });
        });
    }

    public static void scheduleLogoutConfirm(BotEntry entry) {
        AgentBotSessionSchedulerRuntime.afterRandomDelay(900, 1100, () -> {
            AgentBotSessionReplyRuntime.replyNow(entry, AgentChatSessionRequestFlow.logoutConfirmedReply());
            AgentBotSessionSchedulerRuntime.afterRandomDelay(1800, 2200, () -> {
                entry.bot().saveCharToDB(true);
                entry.bot().getClient().disconnect(false, false);
            });
        });
    }

    public static void handleOwnerAwayChoice(BotEntry entry, String message) {
        AgentChatAwayFlow.handleOwnerAwayChoice(
                message,
                AgentBotSessionControlRuntime.shouldOfferTownForAwayCommand(entry),
                awayChoiceCallbacks(entry));
    }

    private static void promptOwnerAway(BotEntry entry) {
        AgentChatAwayFlow.promptOwnerAway(
                AgentBotSessionControlRuntime.shouldOfferTownForAwayCommand(entry),
                awayPromptCallbacks(entry));
    }

    private static AgentChatAwayFlow.AwayPromptCallbacks awayPromptCallbacks(BotEntry entry) {
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
                AgentBotSessionReplyRuntime.replyNow(entry, AgentChatAwayFlow.townOrLogoutPrompt());
            }

            @Override
            public void replyStayOrLogout() {
                AgentBotSessionReplyRuntime.replyNow(entry, AgentChatAwayFlow.stayOrLogoutPrompt());
            }
        };
    }

    private static AgentChatAwayFlow.AwayChoiceCallbacks awayChoiceCallbacks(BotEntry entry) {
        return new AgentChatAwayFlow.AwayChoiceCallbacks() {
            @Override
            public void clearPendingAction() {
                AgentBotPendingActionStateRuntime.clearPendingAction(entry);
            }

            @Override
            public void logout() {
                AgentBotSessionSchedulerRuntime.afterRandomDelay(700, 900, () -> {
                    AgentBotSessionReplyRuntime.replyNow(entry, AgentChatAwayFlow.logoutConfirmReply());
                    logoutOwnerBots(entry);
                });
            }

            @Override
            public void townOrStay(boolean townOffered) {
                int ownerId = entry.owner() != null ? entry.owner().getId() : 0;
                if (ownerId != 0) {
                    AgentBotSessionControlRuntime.issueOwnerAwaySafeModeForLeader(ownerId, townOffered);
                }
                AgentBotSessionSchedulerRuntime.afterRandomDelay(700, 900, () ->
                        AgentBotSessionReplyRuntime.replyNow(entry, AgentChatAwayFlow.townOrStayConfirmReply(townOffered)));
            }

            @Override
            public void stay() {
                int ownerId = entry.owner() != null ? entry.owner().getId() : 0;
                if (ownerId != 0) {
                    AgentBotSessionControlRuntime.issueOwnerAwaySafeModeForLeader(ownerId, false);
                }
                AgentBotSessionSchedulerRuntime.afterRandomDelay(700, 900, () ->
                        AgentBotSessionReplyRuntime.replyNow(entry, AgentChatAwayFlow.stayConfirmReply()));
            }

            @Override
            public void cancel() {
                AgentBotSessionSchedulerRuntime.afterRandomDelay(700, 900, () ->
                        AgentBotSessionReplyRuntime.replyNow(entry, AgentChatAwayFlow.cancelReply()));
            }
        };
    }

    private static void logoutOwnerBots(BotEntry entry) {
        Character owner = entry.owner();
        if (owner == null) {
            return;
        }

        for (BotEntry owned : AgentBotSessionLifecycleSideEffects.getBotEntries(owner.getId())) {
            AgentBotMovementCommandRuntime.stop(owned);
            AgentBotSessionSchedulerRuntime.afterRandomDelay(1200, 1800, () -> {
                owned.bot().saveCharToDB(true);
                owned.bot().getClient().disconnect(false, false);
            });
        }
    }
}
