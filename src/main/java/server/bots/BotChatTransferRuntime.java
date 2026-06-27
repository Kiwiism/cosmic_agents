package server.bots;

import server.agents.capabilities.dialogue.AgentChatTransferFlow;
import server.agents.integration.AgentBotTransferRuntime;

final class BotChatTransferRuntime {
    private BotChatTransferRuntime() {
    }

    static AgentChatTransferFlow.ItemQueryCallbacks itemQueryCallbacks(BotEntry entry) {
        return AgentBotTransferRuntime.itemQueryCallbacks(entry);
    }

    static void handleTransferCommand(BotEntry entry,
                                      AgentChatTransferFlow.TransferCommand transferCommand,
                                      String message) {
        AgentBotTransferRuntime.handleTransferCommand(entry, transferCommand, message);
    }
}
