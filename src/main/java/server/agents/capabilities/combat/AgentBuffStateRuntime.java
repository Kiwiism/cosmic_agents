package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed consumable buff state.
 */
public final class AgentBuffStateRuntime {
    private AgentBuffStateRuntime() {
    }

    public static boolean enabled(AgentRuntimeEntry entry) {
        return entry.buffState().consumablesEnabled();
    }

    public static void setEnabled(AgentRuntimeEntry entry, boolean enabled) {
        entry.buffState().setConsumablesEnabled(enabled);
    }

    public static void disable(AgentRuntimeEntry entry) {
        setEnabled(entry, false);
    }

    public static boolean cheapMode(AgentRuntimeEntry entry) {
        return entry.buffState().cheapMode();
    }

    public static void setCheapMode(AgentRuntimeEntry entry, boolean cheapMode) {
        entry.buffState().setCheapMode(cheapMode);
    }

    public static void resetScan(AgentRuntimeEntry entry) {
        entry.buffState().resetLastConsumableScan();
    }

    public static boolean scanDue(AgentRuntimeEntry entry, long nowMs, long intervalMs) {
        return entry.buffState().consumableScanDue(nowMs, intervalMs);
    }

    public static void markScanned(AgentRuntimeEntry entry, long nowMs) {
        entry.buffState().setLastConsumableScanMs(nowMs);
    }

    public static long lastActionAtMs(AgentRuntimeEntry entry) {
        return entry.buffState().lastConsumableActionAtMs();
    }

    public static String lastActionSummary(AgentRuntimeEntry entry) {
        return entry.buffState().lastConsumableActionSummary();
    }

    public static void noteDecision(AgentRuntimeEntry entry, long nowMs, String summary) {
        entry.buffState().rememberConsumableAction(nowMs, summary);
    }
}
