package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed grind retarget search cadence.
 */
public final class AgentBotGrindSearchStateRuntime {
    private AgentBotGrindSearchStateRuntime() {
    }

    public static long nextSearchAtMs(BotEntry entry) {
        return entry.nextGrindTargetSearchAtMs();
    }

    public static boolean searchBlocked(BotEntry entry, long nowMs) {
        return nowMs < nextSearchAtMs(entry);
    }

    public static void scheduleNextSearch(BotEntry entry, long nextSearchAtMs) {
        entry.setNextGrindTargetSearchAtMs(nextSearchAtMs);
    }

    public static void clear(BotEntry entry) {
        entry.clearNextGrindTargetSearchAtMs();
    }
}
