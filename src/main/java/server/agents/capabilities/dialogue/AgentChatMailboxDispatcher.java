package server.agents.capabilities.dialogue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.commands.AgentReplyChannel;

import java.util.concurrent.CompletableFuture;

/** Compatibility adapter for moving chat mutation onto the Agent tick path. */
public final class AgentChatMailboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(AgentChatMailboxDispatcher.class);

    private AgentChatMailboxDispatcher() {
    }

    public static CompletableFuture<Boolean> handleChat(AgentRuntimeEntry entry, String message) {
        return handleChat(entry, message, null);
    }

    public static CompletableFuture<Boolean> handleChat(
            AgentRuntimeEntry entry,
            String message,
            AgentReplyChannel replyChannel) {
        var result = AgentMailboxRuntime.dispatch(entry, new AgentChatMailboxAction(message, replyChannel));
        if (!AgentMailboxRuntime.enabled()) {
            return result;
        }
        return result.handle((handled, failure) -> {
            boolean matched = failure == null && Boolean.TRUE.equals(handled);
            AgentChatRuntime.recordLastChatHandled(matched);
            if (failure != null) {
                log.warn("Agent chat mailbox action failed for session {}",
                        entry == null ? "unknown" : entry.sessionGeneration(),
                        failure);
            }
            return matched;
        });
    }
}
