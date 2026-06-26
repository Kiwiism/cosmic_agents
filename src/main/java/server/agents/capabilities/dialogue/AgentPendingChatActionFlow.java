package server.agents.capabilities.dialogue;

public final class AgentPendingChatActionFlow {
    private AgentPendingChatActionFlow() {
    }

    public static boolean handle(PendingActionState state, String message, PendingActionCallbacks callbacks) {
        String action = state.pendingAction();
        if (action == null) {
            return false;
        }

        if (AgentChatPendingAction.isOwnerAway(action)) {
            callbacks.handleOwnerAwayChoice(message);
            return true;
        }

        if (AgentChatPendingAction.isItemChoice(action)) {
            handleItemChoice(state, message, callbacks);
            return true;
        }

        if (AgentChatPendingAction.isSkillTreeChoice(action)) {
            callbacks.handleSkillTreeChoice(message);
            return true;
        }

        if (AgentChatCommandClassifier.isLogoutConfirm(message)) {
            state.clearPendingAction();
            if (AgentChatPendingAction.isRelog(action)) {
                callbacks.confirmRelog();
            } else {
                callbacks.confirmLogout();
            }
            return true;
        }

        state.clearPendingAction();
        callbacks.cancelPendingAction(AgentChatPendingAction.isDropAction(action));
        return true;
    }

    private static void handleItemChoice(PendingActionState state, String message, PendingActionCallbacks callbacks) {
        String category = state.pendingDropCategory();
        String choice = AgentChatCommandClassifier.normalizeCommandText(message);
        state.clearPendingAction();
        state.clearPendingDropCategory();

        if (AgentTradeDialogueClassifier.isDropChoiceTradeCommand(choice)) {
            callbacks.executeItemChoice(category, true);
            return;
        }

        if (AgentTradeDialogueClassifier.isDropChoiceDropCommand(choice)) {
            callbacks.executeItemChoice(category, false);
            return;
        }

        callbacks.cancelItemChoice();
    }

    public interface PendingActionState {
        String pendingAction();

        String pendingDropCategory();

        void clearPendingAction();

        void clearPendingDropCategory();
    }

    public interface PendingActionCallbacks {
        void handleOwnerAwayChoice(String message);

        void executeItemChoice(String category, boolean trade);

        void cancelItemChoice();

        void handleSkillTreeChoice(String message);

        void confirmRelog();

        void confirmLogout();

        void cancelPendingAction(boolean dropAction);
    }
}
