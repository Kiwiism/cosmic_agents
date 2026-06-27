package server.bots;

import server.agents.capabilities.dialogue.AgentChatReplyRuntime;
import server.agents.commands.AgentQueuedMessage;
import server.agents.commands.AgentReplyQueue;

/**
 * Temporary bot-side adapter from legacy BotEntry message fields to the
 * Agent-owned reply runtime.
 */
public final class BotChatReplyRuntime {
    private BotChatReplyRuntime() {
    }

    public static void queueSay(BotEntry entry, String message) {
        AgentChatReplyRuntime.queueSay(state(entry), message, dispatcher(entry));
    }

    static void queueReply(BotEntry entry, String message) {
        AgentChatReplyRuntime.queueReply(state(entry), message, dispatcher(entry));
    }

    static long queueSayWithEstimatedDelay(BotEntry entry, String message) {
        return AgentChatReplyRuntime.queueSayWithEstimatedDelay(state(entry), message, dispatcher(entry));
    }

    static long queueReplyWithEstimatedDelay(BotEntry entry, String message) {
        return AgentChatReplyRuntime.queueReplyWithEstimatedDelay(state(entry), message, dispatcher(entry));
    }

    private static AgentReplyQueue.State state(BotEntry entry) {
        return new AgentReplyQueue.State() {
            @Override
            public java.util.Deque<AgentQueuedMessage> queue() {
                return entry.msgQueue;
            }

            @Override
            public boolean isSending() {
                return entry.msgSending;
            }

            @Override
            public void setSending(boolean sending) {
                entry.msgSending = sending;
            }
        };
    }

    private static AgentReplyQueue.Dispatcher dispatcher(BotEntry entry) {
        return new AgentReplyQueue.Dispatcher() {
            @Override
            public void dispatch(AgentQueuedMessage message) {
                if (message.ownerDirected()) {
                    BotManager.getInstance().botReply(entry, message.text());
                } else {
                    BotManager.getInstance().botSay(entry, message.text());
                }
            }

            @Override
            public void scheduleNext(Runnable task, int delayMs) {
                BotManager.after(delayMs, task);
            }
        };
    }
}
