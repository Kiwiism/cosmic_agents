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
