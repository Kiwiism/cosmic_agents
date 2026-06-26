package server.agents.capabilities.dialogue;

public final class AgentChatSupplyRequestFlow {
    private AgentChatSupplyRequestFlow() {
    }

    public static boolean handle(String message, SupplyRequestCallbacks callbacks) {
        if (AgentChatCommandClassifier.isNeedHpPotCommand(message)) {
            callbacks.requestPotion(true);
            return true;
        }
        if (AgentChatCommandClassifier.isNeedMpPotCommand(message)) {
            callbacks.requestPotion(false);
            return true;
        }
        if (AgentChatCommandClassifier.isNeedPotCommand(message)) {
            callbacks.requestAnyPotion();
            return true;
        }
        if (AgentChatCommandClassifier.isNeedAmmoCommand(message)) {
            callbacks.requestAmmo();
            return true;
        }
        return false;
    }

    public interface SupplyRequestCallbacks {
        void requestPotion(boolean hpPotion);

        void requestAnyPotion();

        void requestAmmo();
    }
}
