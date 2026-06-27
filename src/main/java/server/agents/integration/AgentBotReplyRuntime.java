package server.agents.integration;

import server.agents.capabilities.dialogue.AgentChatReplyRuntime;
import server.agents.commands.AgentQueuedMessage;
import server.agents.commands.AgentReplyQueue;
import server.bots.BotEntry;
import server.bots.BotManager;

/**
 * Temporary Agent-owned adapter from legacy BotEntry message fields to the
 * Agent reply queue runtime.
 */
public final class AgentBotReplyRuntime {
    private AgentBotReplyRuntime() {
    }

    public static void queueSay(BotEntry entry, String message) {
        AgentChatReplyRuntime.queueSay(state(entry), message, dispatcher(entry));
    }

    public static void queueReply(BotEntry entry, String message) {
        AgentChatReplyRuntime.queueReply(state(entry), message, dispatcher(entry));
    }

    public static long queueSayWithEstimatedDelay(BotEntry entry, String message) {
        return AgentChatReplyRuntime.queueSayWithEstimatedDelay(state(entry), message, dispatcher(entry));
    }

    public static long queueReplyWithEstimatedDelay(BotEntry entry, String message) {
        return AgentChatReplyRuntime.queueReplyWithEstimatedDelay(state(entry), message, dispatcher(entry));
    }

    private static AgentReplyQueue.State state(BotEntry entry) {
        return new AgentReplyQueue.State() {
            @Override
            public java.util.Deque<AgentQueuedMessage> queue() {
                return entry.messageQueue();
            }

            @Override
            public boolean isSending() {
                return entry.isMessageSending();
            }

            @Override
            public void setSending(boolean sending) {
                entry.setMessageSending(sending);
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
                    BotManager.getInstance().botVisibleSay(entry, message.text());
                }
            }

            @Override
            public void scheduleNext(Runnable task, int delayMs) {
                BotManager.scheduleBotReplyAction(delayMs, task);
            }
        };
    }
}
