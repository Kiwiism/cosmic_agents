package server.agents.capabilities.dialogue;

public final class AgentChatEquipmentFlow {
    private AgentChatEquipmentFlow() {
    }

    public static boolean handle(String message, EquipmentCallbacks callbacks) {
        String slotName = AgentEquipmentDialogueClassifier.matchUnequipSlotName(message);
        if (slotName != null) {
            return callbacks.unequipSlot(slotName);
        }
        if (AgentEquipmentDialogueClassifier.isUnequipAllCommand(message)) {
            callbacks.unequipAll();
            return true;
        }
        if (AgentEquipmentDialogueClassifier.isAutoEquipDebugCommand(message)) {
            callbacks.autoEquipDebug();
            return true;
        }
        if (AgentEquipmentDialogueClassifier.isAutoEquipCommand(message)) {
            callbacks.autoEquip();
            return true;
        }
        return false;
    }

    public static String gearOptimizedReply() {
        return AgentDialogueCatalog.gearOptimizedReply();
    }

    public interface EquipmentCallbacks {
        boolean unequipSlot(String slotName);

        void unequipAll();

        void autoEquipDebug();

        void autoEquip();
    }
}
