package server.agents.integration;


import server.agents.runtime.AgentSchedulerRuntime;
import client.Character;
import server.agents.capabilities.dialogue.AgentChatReplyRuntime;
import server.agents.capabilities.dialogue.AgentChatTextSanitizer;
import server.agents.commands.AgentQueuedMessage;
import server.agents.commands.AgentReplyQueue;
import server.agents.commands.AgentReplyChannel;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.commands.AgentMessageQueueStateRuntime;
import server.agents.commands.AgentReplyChannelStateRuntime;

/**
 * Boundary adapter from Agent reply queues to Cosmic chat, whisper, party, and
 * packet delivery.
 */
public final class AgentReplyRuntime {
    private AgentReplyRuntime() {
    }

    public static void queueSay(AgentRuntimeEntry entry, String message) {
        AgentChatReplyRuntime.queueSay(state(entry), message, dispatcher(entry));
    }

    public static void queueReply(AgentRuntimeEntry entry, String message) {
        AgentChatReplyRuntime.queueReply(state(entry), message, dispatcher(entry));
    }

    public static long queueSayWithEstimatedDelay(AgentRuntimeEntry entry, String message) {
        return AgentChatReplyRuntime.queueSayWithEstimatedDelay(state(entry), message, dispatcher(entry));
    }

    public static long queueReplyWithEstimatedDelay(AgentRuntimeEntry entry, String message) {
        return AgentChatReplyRuntime.queueReplyWithEstimatedDelay(state(entry), message, dispatcher(entry));
    }

    public static void replyNow(AgentRuntimeEntry entry, String message) {
        switch (AgentReplyChannelStateRuntime.replyChannel(entry)) {
            case PARTY -> sayPartyNow(AgentRuntimeIdentityRuntime.bot(entry), message);
            case WHISPER -> {
                Character owner = AgentRuntimeIdentityRuntime.owner(entry);
                Character bot = AgentRuntimeIdentityRuntime.bot(entry);
                if (owner != null && AgentClientGatewayRuntime.clients().hasClient(owner)) {
                    AgentPacketGatewayRuntime.packets().sendWhisperReceive(
                            owner,
                            bot.getName(),
                            AgentClientGatewayRuntime.clients().channel(bot) - 1,
                            false,
                            AgentChatTextSanitizer.sanitize(message));
                }
            }
            default -> sayMapNow(AgentRuntimeIdentityRuntime.bot(entry), message);
        }
    }

    public static void visibleSayNow(AgentRuntimeEntry entry, String message) {
        sayNow(AgentRuntimeIdentityRuntime.bot(entry), AgentReplyChannelStateRuntime.replyChannel(entry), message);
    }

    public static void sayNow(Character bot, AgentReplyChannel channel, String message) {
        switch (channel) {
            case PARTY, WHISPER -> sayPartyNow(bot, message);
            default -> sayMapNow(bot, message);
        }
    }

    public static void sayMapNow(Character bot, String message) {
        AgentPacketGatewayRuntime.packets().broadcastChatText(
                bot,
                AgentChatTextSanitizer.sanitize(message),
                false,
                0);
    }

    public static void sayPartyNow(Character bot, String message) {
        if (!AgentPartyGatewayRuntime.party().sendPartyChat(bot, AgentChatTextSanitizer.sanitize(message))) {
            sayMapNow(bot, message);
        }
    }

    private static AgentReplyQueue.State state(AgentRuntimeEntry entry) {
        return new AgentReplyQueue.State() {
            @Override
            public Object lock() {
                return AgentMessageQueueStateRuntime.lock(entry);
            }

            @Override
            public int size() {
                return AgentMessageQueueStateRuntime.size(entry);
            }

            @Override
            public void enqueue(AgentQueuedMessage message) {
                AgentMessageQueueStateRuntime.enqueue(entry, message);
            }

            @Override
            public AgentQueuedMessage poll() {
                return AgentMessageQueueStateRuntime.poll(entry);
            }

            @Override
            public boolean isSending() {
                return AgentMessageQueueStateRuntime.isSending(entry);
            }

            @Override
            public void setSending(boolean sending) {
                AgentMessageQueueStateRuntime.setSending(entry, sending);
            }
        };
    }

    private static AgentReplyQueue.Dispatcher dispatcher(AgentRuntimeEntry entry) {
        return new AgentReplyQueue.Dispatcher() {
            @Override
            public void dispatch(AgentQueuedMessage message) {
                if (message.ownerDirected()) {
                    replyNow(entry, message.text());
                } else {
                    visibleSayNow(entry, message.text());
                }
            }

            @Override
            public void scheduleNext(Runnable task, int delayMs) {
                AgentSchedulerRuntime.afterDelay(delayMs, task);
            }
        };
    }
}
