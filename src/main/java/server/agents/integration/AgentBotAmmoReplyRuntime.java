package server.agents.integration;

import client.Character;

/**
 * Agent-owned ammo reply adapter. Ammo-sharing flows should depend on this
 * narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotAmmoReplyRuntime {
    private AgentBotAmmoReplyRuntime() {
    }

    public static void sayMapNow(Character bot, String message) {
        AgentBotReplyRuntime.sayMapNow(bot, message);
    }
}
