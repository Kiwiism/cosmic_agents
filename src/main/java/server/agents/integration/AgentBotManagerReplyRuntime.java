package server.agents.integration;

import client.Character;
import server.agents.commands.AgentReplyChannel;
import server.bots.BotEntry;

/**
 * Agent-owned bridge for reply delivery from runtime services.
 */
public final class AgentBotManagerReplyRuntime {
    private AgentBotManagerReplyRuntime() {
    }

    public static void queueReply(BotEntry entry, String message) {
        AgentBotReplyRuntime.queueReply(entry, message);
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }

    public static void visibleSayNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.visibleSayNow(entry, message);
    }

    public static void sayMapNow(Character bot, String message) {
        AgentBotReplyRuntime.sayMapNow(bot, message);
    }

    public static void sayNow(Character bot, AgentReplyChannel channel, String message) {
        AgentBotReplyRuntime.sayNow(bot, channel, message);
    }

    public static void sayPartyNow(Character bot, String message) {
        AgentBotReplyRuntime.sayPartyNow(bot, message);
    }
}
