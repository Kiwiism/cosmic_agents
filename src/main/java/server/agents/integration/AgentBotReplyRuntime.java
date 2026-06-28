package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatReplyRuntime;
import server.agents.capabilities.dialogue.AgentChatTextSanitizer;
import server.agents.commands.AgentQueuedMessage;
import server.agents.commands.AgentReplyQueue;
import server.bots.BotEntry;
import server.bots.ReplyChannel;
import net.server.world.Party;
import tools.PacketCreator;

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

    public static void replyNow(BotEntry entry, String message) {
        switch (entry.getReplyChannel()) {
            case PARTY -> sayPartyNow(entry.getBot(), message);
            case WHISPER -> {
                Character owner = entry.getOwner();
                Character bot = entry.getBot();
                if (owner != null && owner.getClient() != null) {
                    owner.sendPacket(PacketCreator.getWhisperReceive(
                            bot.getName(),
                            bot.getClient().getChannel() - 1,
                            false,
                            AgentChatTextSanitizer.sanitize(message)));
                }
            }
            default -> sayMapNow(entry.getBot(), message);
        }
    }

    public static void visibleSayNow(BotEntry entry, String message) {
        sayNow(entry.getBot(), entry.getReplyChannel(), message);
    }

    public static void sayNow(Character bot, ReplyChannel channel, String message) {
        switch (channel) {
            case PARTY, WHISPER -> sayPartyNow(bot, message);
            default -> sayMapNow(bot, message);
        }
    }

    public static void sayMapNow(Character bot, String message) {
        bot.getMap().broadcastMessage(PacketCreator.getChatText(
                bot.getId(),
                AgentChatTextSanitizer.sanitize(message),
                false,
                0));
    }

    public static void sayPartyNow(Character bot, String message) {
        Party party = bot.getParty();
        if (party != null && bot.getClient() != null && bot.getClient().getWorldServer() != null) {
            bot.getClient().getWorldServer().partyChat(
                    party,
                    AgentChatTextSanitizer.sanitize(message),
                    bot.getName());
        } else {
            sayMapNow(bot, message);
        }
    }

    private static AgentReplyQueue.State state(BotEntry entry) {
        return new AgentReplyQueue.State() {
            @Override
            public Object lock() {
                return AgentBotMessageQueueStateRuntime.lock(entry);
            }

            @Override
            public int size() {
                return AgentBotMessageQueueStateRuntime.size(entry);
            }

            @Override
            public void enqueue(AgentQueuedMessage message) {
                AgentBotMessageQueueStateRuntime.enqueue(entry, message);
            }

            @Override
            public AgentQueuedMessage poll() {
                return AgentBotMessageQueueStateRuntime.poll(entry);
            }

            @Override
            public boolean isSending() {
                return AgentBotMessageQueueStateRuntime.isSending(entry);
            }

            @Override
            public void setSending(boolean sending) {
                AgentBotMessageQueueStateRuntime.setSending(entry, sending);
            }
        };
    }

    private static AgentReplyQueue.Dispatcher dispatcher(BotEntry entry) {
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
                AgentBotSchedulerRuntime.afterDelay(delayMs, task);
            }
        };
    }
}
