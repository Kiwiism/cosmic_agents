package server.agents.capabilities.dialogue;

public final class AgentChatRespecFlow {
    private AgentChatRespecFlow() {
    }

    public static boolean handle(String message, RespecCallbacks callbacks) {
        if (AgentChatCommandClassifier.isApRespecCommand(message)) {
            callbacks.respecAp();
            return true;
        }
        if (AgentChatCommandClassifier.isRespecCommand(message)) {
            callbacks.respecSp();
            return true;
        }
        return false;
    }

    public interface RespecCallbacks {
        void respecAp();

        void respecSp();
    }
}
