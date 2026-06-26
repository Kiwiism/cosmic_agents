package server.agents.capabilities.dialogue;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class AgentChatSessionRequestFlow {
    private AgentChatSessionRequestFlow() {
    }

    public static boolean handle(String message, SessionRequestCallbacks callbacks) {
        if (AgentChatCommandClassifier.isRelogRequest(message)) {
            callbacks.requestRelog();
            return true;
        }

        if (AgentChatCommandClassifier.isLogoutRequest(message)) {
            callbacks.requestLogout();
            return true;
        }

        if (AgentChatCommandClassifier.isAwayRequest(message)) {
            callbacks.requestAway();
            return true;
        }

        return false;
    }

    public static String relogConfirmPrompt() {
        return randomReply(AgentDialogueCatalog.relogConfirmPrompts());
    }

    public static String logoutConfirmPrompt() {
        return randomReply(AgentDialogueCatalog.logoutConfirmPrompts());
    }

    public static String relogConfirmedReply() {
        return randomReply(AgentDialogueCatalog.relogConfirmedReplies());
    }

    public static String logoutConfirmedReply() {
        return randomReply(AgentDialogueCatalog.logoutConfirmedReplies());
    }

    private static String randomReply(List<String> replies) {
        return replies.get(ThreadLocalRandom.current().nextInt(replies.size()));
    }

    public interface SessionRequestCallbacks {
        void requestRelog();

        void requestLogout();

        void requestAway();
    }
}
