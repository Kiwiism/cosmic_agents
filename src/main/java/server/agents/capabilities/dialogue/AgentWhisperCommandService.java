package server.agents.capabilities.dialogue;

import client.BotClient;
import client.Character;
import server.agents.integration.AgentBotChatOrchestratorContext;
import server.agents.integration.AgentBotReplyChannelStateRuntime;
import server.agents.runtime.AgentRuntimeRegistry;
import server.bots.BotEntry;

public final class AgentWhisperCommandService {
    private AgentWhisperCommandService() {
    }

    public static void handleWhisperToAgent(Character leader, Character target, String message) {
        if (leader == null || target == null || message == null) {
            return;
        }
        if (!(target.getClient() instanceof BotClient)) {
            return;
        }

        BotEntry entry = AgentRuntimeRegistry.findByCharacterId(
                AgentRuntimeRegistry.entriesByLeaderId(), leader.getId(), target.getId());
        if (entry == null) {
            return;
        }

        AgentBotReplyChannelStateRuntime.setWhisper(entry);
        AgentChatRuntime.handleChat(message, new AgentBotChatOrchestratorContext(entry));
    }
}
