package server.agents.capabilities.dialogue;

public final class AgentChatBuildFlow {
    private AgentChatBuildFlow() {
    }

    public static boolean handleSpVariantSelection(String message, boolean awaitingVariant, SpVariantCallbacks callbacks) {
        if (!awaitingVariant) {
            return false;
        }
        if (AgentBuildDialogueClassifier.isOneHandedSpVariant(message)) {
            callbacks.oneHanded();
            return true;
        }
        if (AgentBuildDialogueClassifier.isTwoHandedSpVariant(message)) {
            callbacks.twoHanded();
            return true;
        }
        return false;
    }

    public static boolean handleApBuildSelection(String message, boolean awaitingSelection, ApBuildCallbacks callbacks) {
        if (AgentBuildDialogueClassifier.isApChangeBuildCommand(message)) {
            callbacks.requestBuildPrompt();
            return true;
        }
        if (awaitingSelection) {
            callbacks.selectBuild(message);
            return true;
        }
        return false;
    }

    public interface SpVariantCallbacks {
        void oneHanded();

        void twoHanded();
    }

    public interface ApBuildCallbacks {
        void requestBuildPrompt();

        void selectBuild(String message);
    }
}
