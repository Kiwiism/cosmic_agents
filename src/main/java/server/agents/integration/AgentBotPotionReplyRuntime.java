package server.agents.integration;

import client.Character;

/**
 * Agent-owned potion reply adapter. Potion-sharing flows should depend on this
 * narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotPotionReplyRuntime {
    private AgentBotPotionReplyRuntime() {
    }

    public static void sayMapNow(Character bot, String message) {
        AgentBotReplyRuntime.sayMapNow(bot, message);
    }
}
