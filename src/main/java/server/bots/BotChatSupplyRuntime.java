package server.bots;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatSupplyRequestFlow;
import server.agents.integration.AgentBotSupplyRuntime;

public final class BotChatSupplyRuntime {
    private BotChatSupplyRuntime() {
    }

    static AgentChatSupplyRequestFlow.SupplyRequestCallbacks supplyRequestCallbacks(BotEntry entry) {
        return AgentBotSupplyRuntime.supplyRequestCallbacks(entry);
    }

    public static void handleRequestUpgradeCommand(BotEntry entry, Character bot) {
        AgentBotSupplyRuntime.handleRequestUpgradeCommand(entry, bot);
    }
}
