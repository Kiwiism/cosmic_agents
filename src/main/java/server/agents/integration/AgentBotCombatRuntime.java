package server.agents.integration;

import client.Character;

/**
 * Temporary Agent-owned bridge for combat-owned timing/replies while combat
 * execution still lives in the legacy bot runtime.
 */
public final class AgentBotCombatRuntime {
    private AgentBotCombatRuntime() {
    }

    public static void sayMapNow(Character bot, String message) {
        AgentBotCombatReplyRuntime.sayMapNow(bot, message);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentBotCombatSchedulerRuntime.afterDelay(delayMs, action);
    }
}
