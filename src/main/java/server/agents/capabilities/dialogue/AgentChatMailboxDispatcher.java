package server.agents.capabilities.dialogue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.commands.AgentReplyChannel;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.runtime.interaction.AgentInteractionLeaseRuntime;
import server.agents.runtime.interaction.AgentInteractionLeaseState;

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
        if (entry == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (AgentTownLifeRuntime.active(entry)) {
            var participant = AgentRelationshipRuntime.interactionTarget(entry);
            AgentInteractionLeaseRuntime.beginChat(
                    entry,
                    server.agents.integration.AgentRuntimeIdentityRuntime.bot(entry),
                    participant == null ? 0 : participant.getId(),
                    System.currentTimeMillis());
        }
        var result = AgentMailboxRuntime.dispatch(entry, new AgentChatMailboxAction(message, replyChannel));
        if (!AgentMailboxRuntime.enabled()) {
            AgentInteractionLeaseRuntime.complete(entry, AgentInteractionLeaseState.Type.CHAT);
            return result;
        }
        return result.handle((handled, failure) -> {
            boolean matched = failure == null && Boolean.TRUE.equals(handled);
            AgentChatRuntime.recordLastChatHandled(matched);
            AgentInteractionLeaseRuntime.complete(entry, AgentInteractionLeaseState.Type.CHAT);
            if (failure != null) {
                log.warn("Agent chat mailbox action failed for session {}",
                        entry == null ? "unknown" : entry.sessionGeneration(),
                        failure);
            }
            return matched;
        });
    }
}
