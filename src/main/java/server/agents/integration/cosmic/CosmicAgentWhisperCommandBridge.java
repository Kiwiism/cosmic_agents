package server.agents.integration.cosmic;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatMailboxDispatcher;
import server.agents.capabilities.dialogue.AgentWhisperCommandService;
import server.agents.commands.AgentReplyChannel;
import server.agents.commands.AgentReplyChannelStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

public final class CosmicAgentWhisperCommandBridge {
    private CosmicAgentWhisperCommandBridge() {
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
                AgentChatMailboxDispatcher::handleChat);
    }
}
