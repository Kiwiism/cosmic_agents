package server.agents.integration;

import server.agents.commands.AgentQueuedMessage;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

/**
 * Agent-owned adapter for temporary BotEntry-backed chat queue state.
 */
public final class AgentBotMessageQueueStateRuntime {
    private AgentBotMessageQueueStateRuntime() {
    }

    public static Object lock(AgentRuntimeEntry entry) {
        return entry.messageQueueState().lock();
    }

    public static int size(AgentRuntimeEntry entry) {
        return entry.messageQueueState().size();
    }

    public static void enqueue(AgentRuntimeEntry entry, AgentQueuedMessage message) {
        entry.messageQueueState().enqueue(message);
    }

    public static AgentQueuedMessage poll(AgentRuntimeEntry entry) {
        return entry.messageQueueState().poll();
    }

    public static AgentQueuedMessage peek(AgentRuntimeEntry entry) {
        return entry.messageQueueState().peek();
    }

    public static List<AgentQueuedMessage> snapshot(AgentRuntimeEntry entry) {
        return entry.messageQueueState().snapshot();
    }

    public static boolean isSending(AgentRuntimeEntry entry) {
        return entry.messageQueueState().isSending();
    }

    public static void setSending(AgentRuntimeEntry entry, boolean sending) {
        entry.messageQueueState().setSending(sending);
    }

    public static boolean isIdle(AgentRuntimeEntry entry) {
        return entry.messageQueueState().isIdle();
    }
}
