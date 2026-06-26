package server.agents.capabilities.dialogue;

public final class AgentChatToggleFlow {
    private AgentChatToggleFlow() {
    }

    public static boolean handle(String message, ToggleCallbacks callbacks) {
        if (AgentChatCommandClassifier.isSupportOffCommand(message)) {
            callbacks.setSupport(false);
            return true;
        }
        if (AgentChatCommandClassifier.isSupportOnCommand(message)) {
            callbacks.setSupport(true);
            return true;
        }
        if (AgentChatCommandClassifier.isHealsOffCommand(message)) {
            callbacks.setHeals(false);
            return true;
        }
        if (AgentChatCommandClassifier.isHealsOnCommand(message)) {
            callbacks.setHeals(true);
            return true;
        }
        if (AgentChatCommandClassifier.isBuffConsumablesOffCommand(message)) {
            callbacks.setBuffConsumables(false);
            return true;
        }
        if (AgentChatCommandClassifier.isBuffConsumablesOnCommand(message)) {
            callbacks.setBuffConsumables(true);
            return true;
        }
        if (AgentChatCommandClassifier.isBuffConsumablesCheapCommand(message)) {
            callbacks.setBuffConsumablesCheapMode(true);
            return true;
        }
        if (AgentChatCommandClassifier.isBuffConsumablesMaxCommand(message)) {
            callbacks.setBuffConsumablesCheapMode(false);
            return true;
        }
        if (AgentChatCommandClassifier.isProactiveOffersOffCommand(message)) {
            callbacks.setProactiveOffers(false);
            return true;
        }
        if (AgentChatCommandClassifier.isProactiveOffersOnCommand(message)) {
            callbacks.setProactiveOffers(true);
            return true;
        }
        return false;
    }

    public static String supportReply(boolean enabled) {
        return enabled ? AgentDialogueCatalog.supportOnReply() : AgentDialogueCatalog.supportOffReply();
    }

    public static String healsReply(boolean enabled) {
        return enabled ? AgentDialogueCatalog.healsOnReply() : AgentDialogueCatalog.healsOffReply();
    }

    public static String buffConsumablesReply(boolean enabled, boolean cheapMode) {
        return enabled
                ? AgentDialogueCatalog.buffConsumablesOnReply(AgentDialogueCatalog.buffConsumablesModeLabel(cheapMode))
                : AgentDialogueCatalog.buffConsumablesOffReply();
    }

    public static String buffConsumablesModeReply(boolean cheapMode) {
        return cheapMode
                ? AgentDialogueCatalog.buffConsumablesCheapReply()
                : AgentDialogueCatalog.buffConsumablesMaxReply();
    }

    public static String proactiveOffersReply(boolean enabled) {
        return enabled
                ? AgentDialogueCatalog.proactiveOffersOnReply()
                : AgentDialogueCatalog.proactiveOffersOffReply();
    }

    public interface ToggleCallbacks {
        void setSupport(boolean enabled);

        void setHeals(boolean enabled);

        void setBuffConsumables(boolean enabled);

        void setBuffConsumablesCheapMode(boolean cheapMode);

        void setProactiveOffers(boolean enabled);
    }
}
