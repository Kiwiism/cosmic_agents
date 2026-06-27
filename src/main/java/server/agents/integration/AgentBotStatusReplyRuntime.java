package server.agents.integration;

import client.Character;
import server.bots.BotEntry;

/**
 * Agent-owned status reply adapter. AFK and welcome-back status flows should
 * depend on this narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotStatusReplyRuntime {
    private AgentBotStatusReplyRuntime() {
    }

    public static void sayPartyNow(Character bot, String text) {
        AgentBotReplyRuntime.sayPartyNow(bot, text);
    }

    public static void replyNow(BotEntry entry, String text) {
        AgentBotReplyRuntime.replyNow(entry, text);
    }
}
