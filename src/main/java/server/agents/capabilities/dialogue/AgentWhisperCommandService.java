package server.agents.capabilities.dialogue;

import client.BotClient;
import client.Character;
import server.agents.runtime.AgentRuntimeHandle;

public final class AgentWhisperCommandService {
    private AgentWhisperCommandService() {
    }

    public record Hooks<E extends AgentRuntimeHandle>(EntryResolver<E> entryResolver,
                                                      ReplyChannelMarker<E> replyChannelMarker,
                                                      WhisperChatHandler<E> whisperChatHandler) {
    }

    @FunctionalInterface
    public interface EntryResolver<E extends AgentRuntimeHandle> {
        E resolve(Character leader, Character target);
    }

    @FunctionalInterface
    public interface ReplyChannelMarker<E extends AgentRuntimeHandle> {
        void markWhisper(E entry);
    }

    @FunctionalInterface
    public interface WhisperChatHandler<E extends AgentRuntimeHandle> {
        void handle(E entry, String message);
    }

    public static <E extends AgentRuntimeHandle> void handleWhisperToAgent(
            Character leader,
            Character target,
            String message,
            Hooks<E> hooks) {
        if (leader == null || target == null || message == null) {
            return;
        }
        if (!(target.getClient() instanceof BotClient)) {
            return;
        }

        E entry = hooks.entryResolver().resolve(leader, target);
        if (entry == null) {
            return;
        }

        hooks.replyChannelMarker().markWhisper(entry);
        hooks.whisperChatHandler().handle(entry, message);
    }
}
