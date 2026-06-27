package server.bots;

import server.agents.capabilities.dialogue.AgentChatEquipmentFlow;
import server.agents.integration.AgentBotEquipmentRuntime;

final class BotChatEquipmentRuntime {
    private BotChatEquipmentRuntime() {
    }

    static AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks(BotEntry entry) {
        return AgentBotEquipmentRuntime.equipmentCallbacks(entry);
    }
}
