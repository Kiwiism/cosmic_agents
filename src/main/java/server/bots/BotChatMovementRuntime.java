package server.bots;

import server.agents.capabilities.dialogue.AgentChatMovementFlow;
import server.agents.integration.AgentBotMovementRuntime;

final class BotChatMovementRuntime {
    private BotChatMovementRuntime() {
    }

    static AgentChatMovementFlow.MovementCallbacks movementCallbacks(BotEntry entry) {
        return AgentBotMovementRuntime.movementCallbacks(entry);
    }
}
