package server.agents.capabilities.supplies;


import client.Character;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned bridge for ammo-sharing timing/replies while reply delivery stays
 * behind the integration runtime boundary.
 */
public final class AgentAmmoRuntime {
    private AgentAmmoRuntime() {
    }

    public static void sayMapNow(Character bot, String message) {
        AgentReplyRuntime.sayMapNow(bot, message);
    }

    public static void afterDelay(AgentRuntimeEntry entry, long delayMs, Runnable action) {
        AgentSchedulerRuntime.afterDelay(entry, delayMs, action);
    }

    public static void afterRandomDelay(AgentRuntimeEntry entry, int minMs, int maxMs, Runnable action) {
        AgentSchedulerRuntime.afterRandomDelay(entry, minMs, maxMs, action);
    }

    public static long randomDelayMs(int minMs, int maxMs) {
        return AgentSchedulerRuntime.randomDelayMs(minMs, maxMs);
    }
}
