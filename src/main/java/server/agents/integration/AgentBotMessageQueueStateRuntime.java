package server.agents.integration;

import server.agents.commands.AgentQueuedMessage;
import server.bots.BotEntry;

import java.util.List;

/**
 * Agent-owned adapter for temporary BotEntry-backed chat queue state.
 */
public final class AgentBotMessageQueueStateRuntime {
    private AgentBotMessageQueueStateRuntime() {
    }

    public static Object lock(BotEntry entry) {
        return entry.messageQueueState().lock();
    }

    public static int size(BotEntry entry) {
        return entry.messageQueueState().size();
    }

    public static void enqueue(BotEntry entry, AgentQueuedMessage message) {
        entry.messageQueueState().enqueue(message);
    }

    public static AgentQueuedMessage poll(BotEntry entry) {
        return entry.messageQueueState().poll();
    }

    public static AgentQueuedMessage peek(BotEntry entry) {
        return entry.messageQueueState().peek();
    }

    public static List<AgentQueuedMessage> snapshot(BotEntry entry) {
        return entry.messageQueueState().snapshot();
    }

    public static boolean isSending(BotEntry entry) {
        return entry.messageQueueState().isSending();
    }

    public static void setSending(BotEntry entry, boolean sending) {
        entry.messageQueueState().setSending(sending);
    }

    public static boolean isIdle(BotEntry entry) {
        return entry.messageQueueState().isIdle();
    }
}
