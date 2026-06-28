package server.agents.integration;

import server.agents.commands.AgentQueuedMessage;
import server.bots.BotEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent-owned adapter for temporary BotEntry-backed chat queue state.
 */
public final class AgentBotMessageQueueStateRuntime {
    private AgentBotMessageQueueStateRuntime() {
    }

    public static Object lock(BotEntry entry) {
        return entry.messageQueue();
    }

    public static int size(BotEntry entry) {
        return entry.messageQueue().size();
    }

    public static void enqueue(BotEntry entry, AgentQueuedMessage message) {
        entry.messageQueue().add(message);
    }

    public static AgentQueuedMessage poll(BotEntry entry) {
        return entry.messageQueue().poll();
    }

    public static AgentQueuedMessage peek(BotEntry entry) {
        return entry.messageQueue().peek();
    }

    public static List<AgentQueuedMessage> snapshot(BotEntry entry) {
        synchronized (lock(entry)) {
            return List.copyOf(new ArrayList<>(entry.messageQueue()));
        }
    }

    public static boolean isSending(BotEntry entry) {
        return entry.isMessageSending();
    }

    public static void setSending(BotEntry entry, boolean sending) {
        entry.setMessageSending(sending);
    }

    public static boolean isIdle(BotEntry entry) {
        synchronized (lock(entry)) {
            return !isSending(entry) && size(entry) == 0;
        }
    }
}
