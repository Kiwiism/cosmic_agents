package server.agents.capabilities.combat;


import client.Character;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;

/**
 * Agent-owned bridge for combat-owned timing/replies while reply delivery stays
 * behind the integration runtime boundary.
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
