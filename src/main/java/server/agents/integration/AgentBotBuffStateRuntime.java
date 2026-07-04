package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed consumable buff state.
 */
public final class AgentBotBuffStateRuntime {
    private AgentBotBuffStateRuntime() {
    }

    public static boolean enabled(BotEntry entry) {
        return entry.buffState().consumablesEnabled();
    }

    public static void setEnabled(BotEntry entry, boolean enabled) {
        entry.buffState().setConsumablesEnabled(enabled);
    }

    public static void disable(BotEntry entry) {
        setEnabled(entry, false);
    }

    public static boolean cheapMode(BotEntry entry) {
        return entry.buffState().cheapMode();
    }

    public static void setCheapMode(BotEntry entry, boolean cheapMode) {
        entry.buffState().setCheapMode(cheapMode);
    }

    public static void resetScan(BotEntry entry) {
        entry.buffState().resetLastConsumableScan();
    }

    public static boolean scanDue(BotEntry entry, long nowMs, long intervalMs) {
        return entry.buffState().consumableScanDue(nowMs, intervalMs);
    }

    public static void markScanned(BotEntry entry, long nowMs) {
        entry.buffState().setLastConsumableScanMs(nowMs);
    }

    public static long lastActionAtMs(BotEntry entry) {
        return entry.buffState().lastConsumableActionAtMs();
    }

    public static String lastActionSummary(BotEntry entry) {
        return entry.buffState().lastConsumableActionSummary();
    }

    public static void noteDecision(BotEntry entry, long nowMs, String summary) {
        entry.buffState().rememberConsumableAction(nowMs, summary);
    }
}
