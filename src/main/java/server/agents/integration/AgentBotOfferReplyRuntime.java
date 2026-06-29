package server.agents.integration;

import client.Character;
import server.agents.commands.AgentReplyChannel;
import server.bots.BotEntry;

/**
 * Agent-owned offer reply adapter. Gear/loot offer flows should depend on this
 * narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotOfferReplyRuntime {
    private AgentBotOfferReplyRuntime() {
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }

    public static void queueReply(BotEntry entry, String message) {
        AgentBotReplyRuntime.queueReply(entry, message);
    }

    public static void queueSay(BotEntry entry, String message) {
        AgentBotReplyRuntime.queueSay(entry, message);
    }

    public static void sayMapNow(Character bot, String message) {
        AgentBotReplyRuntime.sayMapNow(bot, message);
    }

    public static void sayNow(Character bot, AgentReplyChannel channel, String message) {
        AgentBotReplyRuntime.sayNow(bot, channel, message);
    }

    public static long queueSayWithEstimatedDelay(BotEntry entry, String message) {
        return AgentBotReplyRuntime.queueSayWithEstimatedDelay(entry, message);
    }
}
