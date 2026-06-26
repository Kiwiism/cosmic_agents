package server.agents.capabilities.dialogue;

public final class AgentChatAwayFlow {
    private AgentChatAwayFlow() {
    }

    public static void handleOwnerAwayChoice(String message, boolean townOffered, AwayChoiceCallbacks callbacks) {
        String choice = AgentChatCommandClassifier.normalizeCommandText(message);
        callbacks.clearPendingAction();

        if (AgentChatCommandClassifier.isAwayLogoutConfirm(choice)) {
            callbacks.logout();
            return;
        }

        if (AgentChatCommandClassifier.isAwayTownConfirm(choice)) {
            callbacks.townOrStay(townOffered);
            return;
        }

        if (AgentChatCommandClassifier.isAwayStayConfirm(choice) && !townOffered) {
            callbacks.stay();
            return;
        }

        callbacks.cancel();
    }

    public static void promptOwnerAway(boolean townOffered, AwayPromptCallbacks callbacks) {
        callbacks.setPendingOwnerAway();
        callbacks.stopAgent();
        if (townOffered) {
            callbacks.replyTownOrLogout();
        } else {
            callbacks.replyStayOrLogout();
        }
    }

    public static String townOrLogoutPrompt() {
        return AgentDialogueCatalog.awayTownOrLogoutPrompt();
    }

    public static String stayOrLogoutPrompt() {
        return AgentDialogueCatalog.awayStayOrLogoutPrompt();
    }

    public static String logoutConfirmReply() {
        return AgentDialogueCatalog.awayLogoutConfirmReply();
    }

    public static String townOrStayConfirmReply(boolean townOffered) {
        return townOffered
                ? AgentDialogueCatalog.awayTownConfirmReply()
                : AgentDialogueCatalog.awayStayConfirmReply();
    }

    public static String stayConfirmReply() {
        return AgentDialogueCatalog.awayStayConfirmReply();
    }

    public static String cancelReply() {
        return AgentDialogueCatalog.awayCancelReply();
    }

    public interface AwayChoiceCallbacks {
        void clearPendingAction();

        void logout();

        void townOrStay(boolean townOffered);

        void stay();

        void cancel();
    }

    public interface AwayPromptCallbacks {
        void setPendingOwnerAway();

        void stopAgent();

        void replyTownOrLogout();

        void replyStayOrLogout();
    }
}
