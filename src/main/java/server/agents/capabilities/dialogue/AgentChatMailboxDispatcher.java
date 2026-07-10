package server.agents.capabilities.dialogue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.TimeUnit;

/** Compatibility adapter for moving chat mutation onto the Agent tick path. */
public final class AgentChatMailboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(AgentChatMailboxDispatcher.class);

    private AgentChatMailboxDispatcher() {
    }

    public static void handleChat(AgentRuntimeEntry entry, String message) {
        if (!AgentMailboxRuntime.enabled()) {
            AgentChatRuntime.handleChat(message, new server.agents.runtime.AgentChatOrchestratorContext(entry));
            return;
        }

        var result = AgentMailboxRuntime.submit(entry, new AgentChatMailboxAction(message));
        try {
            AgentChatRuntime.recordLastChatHandled(result.get(2, TimeUnit.SECONDS));
        } catch (Exception failure) {
            result.cancel(false);
            AgentChatRuntime.recordLastChatHandled(false);
            log.warn("Agent chat mailbox action did not complete for session {}", entry.sessionGeneration(), failure);
        }
    }
}
