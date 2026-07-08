package server.agents.runtime;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatRuntime;
import server.agents.capabilities.dialogue.AgentWhisperCommandService;
import server.agents.commands.AgentReplyChannel;
import server.agents.runtime.AgentReplyChannelStateRuntime;

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

    private static AgentWhisperCommandService.Hooks<AgentRuntimeEntry> hooks() {
        return new AgentWhisperCommandService.Hooks<>(
                (leader, target) -> AgentRuntimeRegistry.findByCharacterId(
                        AgentRuntimeRegistry.entriesByLeaderId(),
                        leader.getId(),
                        target.getId()),
                entry -> AgentReplyChannelStateRuntime.setReplyChannel(entry, AgentReplyChannel.WHISPER),
                (entry, message) -> AgentChatRuntime.handleChat(message, new AgentChatOrchestratorContext(entry)));
    }
}
