package server.agents.runtime;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatRuntime;
import server.agents.capabilities.dialogue.AgentWhisperCommandService;
import server.agents.commands.AgentReplyChannel;
import server.agents.integration.AgentBotChatOrchestratorContext;
import server.agents.integration.AgentBotReplyChannelStateRuntime;
import server.bots.BotEntry;

public final class AgentWhisperCommandRuntime {
    private AgentWhisperCommandRuntime() {
    }

    public static void handleWhisperToAgent(Character leader, Character target, String message) {
        AgentWhisperCommandService.handleWhisperToAgent(
                leader,
                target,
                message,
                hooks());
    }

    private static AgentWhisperCommandService.Hooks<BotEntry> hooks() {
        return new AgentWhisperCommandService.Hooks<>(
                (leader, target) -> AgentRuntimeRegistry.findByCharacterId(
                        AgentRuntimeRegistry.entriesByLeaderId(),
                        leader.getId(),
                        target.getId()),
                entry -> AgentBotReplyChannelStateRuntime.setReplyChannel(entry, AgentReplyChannel.WHISPER),
                (entry, message) -> AgentChatRuntime.handleChat(message, new AgentBotChatOrchestratorContext(entry)));
    }
}
