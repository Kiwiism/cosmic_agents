package server.agents.capabilities.dialogue;

public final class AgentChatSocialFlow {
    private AgentChatSocialFlow() {
    }

    public static boolean handle(String message, SocialCallbacks callbacks) {
        String fameTarget = AgentSocialDialogueClassifier.matchFameTarget(message);
        if (fameTarget != null) {
            callbacks.fame(fameTarget);
            return true;
        }
        return false;
    }

    public interface SocialCallbacks {
        void fame(String targetName);
    }
}
