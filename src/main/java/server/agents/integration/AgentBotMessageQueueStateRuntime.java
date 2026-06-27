package server.agents.integration;

import server.agents.commands.AgentQueuedMessage;
import server.bots.BotEntry;

import java.util.Deque;

/**
 * Agent-owned adapter for temporary BotEntry-backed chat queue state.
 */
public final class AgentBotMessageQueueStateRuntime {
    private AgentBotMessageQueueStateRuntime() {
    }

    public static Deque<AgentQueuedMessage> queue(BotEntry entry) {
        return entry.messageQueue();
    }

    public static boolean isSending(BotEntry entry) {
        return entry.isMessageSending();
    }

    public static void setSending(BotEntry entry, boolean sending) {
        entry.setMessageSending(sending);
    }

    public static boolean isIdle(BotEntry entry) {
        Deque<AgentQueuedMessage> queue = queue(entry);
        synchronized (queue) {
            return !isSending(entry) && queue.isEmpty();
        }
    }
}
