package server.agents.capabilities.dialogue;

import client.Character;
import server.agents.commands.AgentReplyChannel;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.runtime.AgentRuntimeHandle;

public final class AgentWhisperCommandService {
    private AgentWhisperCommandService() {
    }

    public record Hooks<E extends AgentRuntimeHandle>(EntryResolver<E> entryResolver,
                                                      WhisperChatHandler<E> whisperChatHandler) {
    }

    @FunctionalInterface
    public interface EntryResolver<E extends AgentRuntimeHandle> {
        E resolve(Character leader, Character target);
    }

    @FunctionalInterface
    public interface WhisperChatHandler<E extends AgentRuntimeHandle> {
        void handle(E entry, String message, AgentReplyChannel channel);
    }

    public static <E extends AgentRuntimeHandle> void handleWhisperToAgent(
            Character leader,
            Character target,
            String message,
            Hooks<E> hooks) {
        if (leader == null || target == null || message == null) {
            return;
        }
        if (!AgentCharacterGatewayRuntime.characters().isAgentCharacter(target)) {
            return;
        }

        E entry = hooks.entryResolver().resolve(leader, target);
        if (entry == null) {
            return;
        }

        hooks.whisperChatHandler().handle(entry, message, AgentReplyChannel.WHISPER);
    }
}
