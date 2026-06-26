package server.agents.capabilities.dialogue;

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

    public interface SessionRequestCallbacks {
        void requestRelog();

        void requestLogout();

        void requestAway();
    }
}
