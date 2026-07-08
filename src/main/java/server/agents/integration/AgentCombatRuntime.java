package server.agents.integration;


import server.agents.runtime.AgentSchedulerRuntime;
import client.Character;

/**
 * Temporary Agent-owned bridge for combat-owned timing/replies while combat
 * execution still lives in the legacy bot runtime.
 */
public final class AgentCombatRuntime {
    private AgentCombatRuntime() {
    }

    public static void sayMapNow(Character bot, String message) {
        AgentReplyRuntime.sayMapNow(bot, message);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        AgentSchedulerRuntime.afterDelay(delayMs, action);
    }
}
