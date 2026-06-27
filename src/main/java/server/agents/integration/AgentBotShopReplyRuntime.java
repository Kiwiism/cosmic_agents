package server.agents.integration;

import client.Character;
import server.bots.BotEntry;

/**
 * Agent-owned shop reply adapter. Shop automation flows should depend on this
 * narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotShopReplyRuntime {
    private AgentBotShopReplyRuntime() {
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }

    public static void sayMapNow(Character bot, String message) {
        AgentBotReplyRuntime.sayMapNow(bot, message);
    }
}
