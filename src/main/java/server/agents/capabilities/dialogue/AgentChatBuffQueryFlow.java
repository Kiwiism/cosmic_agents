package server.agents.capabilities.dialogue;

public final class AgentChatBuffQueryFlow {
    private AgentChatBuffQueryFlow() {
    }

    public static boolean handle(String message, BuffQueryCallbacks callbacks) {
        if (AgentChatCommandClassifier.isBuffListQuery(message)) {
            callbacks.reportBuffList();
            return true;
        }
        if (AgentChatCommandClassifier.isBuffDebugQuery(message)) {
            callbacks.reportBuffDebug();
            return true;
        }
        if (AgentChatCommandClassifier.isSkillBuffDebugQuery(message)) {
            callbacks.reportSkillBuffDebug();
            return true;
        }
        return false;
    }

    public interface BuffQueryCallbacks {
        void reportBuffList();

        void reportBuffDebug();

        void reportSkillBuffDebug();
    }
}
