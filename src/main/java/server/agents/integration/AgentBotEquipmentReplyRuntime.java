package server.agents.integration;

import client.Character;
import server.bots.BotEntry;

/**
 * Agent-owned equipment reply adapter. Equipment chat flows should depend on
 * this narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotEquipmentReplyRuntime {
    private AgentBotEquipmentReplyRuntime() {
    }

    public static void sayMapNow(Character bot, String message) {
        AgentBotReplyRuntime.sayMapNow(bot, message);
    }

    public static void replyNow(BotEntry entry, String message) {
        AgentBotReplyRuntime.replyNow(entry, message);
    }
}
