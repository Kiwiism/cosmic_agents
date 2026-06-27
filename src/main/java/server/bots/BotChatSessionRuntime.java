package server.bots;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatAwayFlow;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.dialogue.AgentChatSessionRequestFlow;

public final class BotChatSessionRuntime {
    private BotChatSessionRuntime() {
    }

    static AgentChatSessionRequestFlow.SessionRequestCallbacks sessionRequestCallbacks(BotEntry entry) {
        return new AgentChatSessionRequestFlow.SessionRequestCallbacks() {
            @Override
            public void requestRelog() {
                BotManager.after(BotManager.randMs(900, 1100), () -> {
                    entry.pendingAction = AgentChatPendingAction.RELOG;
                    BotManager.getInstance().issueStop(entry);
                    BotManager.getInstance().botReply(entry, AgentChatSessionRequestFlow.relogConfirmPrompt());
                });
            }

            @Override
            public void requestLogout() {
                BotManager.after(BotManager.randMs(900, 1100), () -> {
                    entry.pendingAction = AgentChatPendingAction.LOGOUT;
                    BotManager.getInstance().issueStop(entry);
                    BotManager.getInstance().botReply(entry, AgentChatSessionRequestFlow.logoutConfirmPrompt());
                });
            }

            @Override
            public void requestAway() {
                if (!BotManager.getInstance().isFirstBotEntry(entry)) {
                    return;
                }
                BotManager.after(BotManager.randMs(900, 1100), () -> promptOwnerAway(entry));
            }
        };
    }

    public static void scheduleRelogConfirm(BotEntry entry) {
        BotManager.after(BotManager.randMs(900, 1100), () -> {
            Character owner = entry.owner;
            if (owner == null) {
                return;
            }
            BotManager.getInstance().botReply(entry, AgentChatSessionRequestFlow.relogConfirmedReply());
            int charId = entry.bot.getId();
            int ownerCharId = owner.getId();
            int world = entry.bot.getClient().getWorld();
            int channel = entry.bot.getClient().getChannel();
            BotManager.after(BotManager.randMs(1800, 2200), () -> {
                entry.bot.saveCharToDB(true);
                entry.bot.getClient().disconnect(false, false);
                BotManager.after(BotManager.randMs(10000, 10100),
                        () -> BotManager.getInstance().reloginBot(charId, ownerCharId, world, channel));
            });
        });
    }

    public static void scheduleLogoutConfirm(BotEntry entry) {
        BotManager.after(BotManager.randMs(900, 1100), () -> {
            BotManager.getInstance().botReply(entry, AgentChatSessionRequestFlow.logoutConfirmedReply());
            BotManager.after(BotManager.randMs(1800, 2200), () -> {
                entry.bot.saveCharToDB(true);
                entry.bot.getClient().disconnect(false, false);
            });
        });
    }

    public static void handleOwnerAwayChoice(BotEntry entry, String message) {
        AgentChatAwayFlow.handleOwnerAwayChoice(
                message,
                BotManager.getInstance().shouldOfferTownForAwayCommand(entry),
                awayChoiceCallbacks(entry));
    }

    private static void promptOwnerAway(BotEntry entry) {
        AgentChatAwayFlow.promptOwnerAway(
                BotManager.getInstance().shouldOfferTownForAwayCommand(entry),
                awayPromptCallbacks(entry));
    }

    private static AgentChatAwayFlow.AwayPromptCallbacks awayPromptCallbacks(BotEntry entry) {
        return new AgentChatAwayFlow.AwayPromptCallbacks() {
            @Override
            public void setPendingOwnerAway() {
                entry.pendingAction = AgentChatPendingAction.OWNER_AWAY;
            }

            @Override
            public void stopAgent() {
                BotManager.getInstance().issueStop(entry);
            }

            @Override
            public void replyTownOrLogout() {
                BotManager.getInstance().botReply(entry, AgentChatAwayFlow.townOrLogoutPrompt());
            }

            @Override
            public void replyStayOrLogout() {
                BotManager.getInstance().botReply(entry, AgentChatAwayFlow.stayOrLogoutPrompt());
            }
        };
    }

    private static AgentChatAwayFlow.AwayChoiceCallbacks awayChoiceCallbacks(BotEntry entry) {
        return new AgentChatAwayFlow.AwayChoiceCallbacks() {
            @Override
            public void clearPendingAction() {
                entry.pendingAction = null;
            }

            @Override
            public void logout() {
                BotManager.after(BotManager.randMs(700, 900), () -> {
                    BotManager.getInstance().botReply(entry, AgentChatAwayFlow.logoutConfirmReply());
                    logoutOwnerBots(entry);
                });
            }

            @Override
            public void townOrStay(boolean townOffered) {
                int ownerId = entry.owner != null ? entry.owner.getId() : 0;
                if (ownerId != 0) {
                    BotManager.getInstance().issueOwnerAwaySafeModeForOwner(ownerId, townOffered);
                }
                BotManager.after(BotManager.randMs(700, 900), () ->
                        BotManager.getInstance().botReply(entry, AgentChatAwayFlow.townOrStayConfirmReply(townOffered)));
            }

            @Override
            public void stay() {
                int ownerId = entry.owner != null ? entry.owner.getId() : 0;
                if (ownerId != 0) {
                    BotManager.getInstance().issueOwnerAwaySafeModeForOwner(ownerId, false);
                }
                BotManager.after(BotManager.randMs(700, 900), () ->
                        BotManager.getInstance().botReply(entry, AgentChatAwayFlow.stayConfirmReply()));
            }

            @Override
            public void cancel() {
                BotManager.after(BotManager.randMs(700, 900), () ->
                        BotManager.getInstance().botReply(entry, AgentChatAwayFlow.cancelReply()));
            }
        };
    }

    private static void logoutOwnerBots(BotEntry entry) {
        Character owner = entry.owner;
        if (owner == null) {
            return;
        }

        for (BotEntry owned : BotManager.getInstance().getBotEntries(owner.getId())) {
            BotManager.getInstance().issueStop(owned);
            BotManager.after(BotManager.randMs(1200, 1800), () -> {
                owned.bot.saveCharToDB(true);
                owned.bot.getClient().disconnect(false, false);
            });
        }
    }
}
