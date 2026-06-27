package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed consumable buff state.
 */
public final class AgentBotBuffStateRuntime {
    private AgentBotBuffStateRuntime() {
    }

    public static boolean scanDue(BotEntry entry, long nowMs, long intervalMs) {
        return nowMs - entry.lastBuffScanMs() >= intervalMs;
    }

    public static void markScanned(BotEntry entry, long nowMs) {
        entry.setLastBuffScanMs(nowMs);
    }

    public static long lastActionAtMs(BotEntry entry) {
        return entry.lastBuffActionAtMs();
    }

    public static String lastActionSummary(BotEntry entry) {
        return entry.lastBuffActionSummary();
    }

    public static void noteDecision(BotEntry entry, long nowMs, String summary) {
        entry.setLastBuffAction(nowMs, summary);
    }
}
