package server.agents.integration;

import client.Character;

/**
 * Agent-owned combat reply adapter. Combat warning/status flows should depend
 * on this narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotCombatReplyRuntime {
    private AgentBotCombatReplyRuntime() {
    }

    public static void sayMapNow(Character bot, String message) {
        AgentBotReplyRuntime.sayMapNow(bot, message);
    }
}
