package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed consumable buff state.
 */
public final class AgentBuffStateRuntime {
    private AgentBuffStateRuntime() {
    }

    public static boolean enabled(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentBuffState.STATE_KEY).consumablesEnabled();
    }

    public static void setEnabled(AgentRuntimeEntry entry, boolean enabled) {
        entry.capabilityStates().require(AgentBuffState.STATE_KEY).setConsumablesEnabled(enabled);
    }

    public static void disable(AgentRuntimeEntry entry) {
        setEnabled(entry, false);
    }

    public static boolean cheapMode(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentBuffState.STATE_KEY).cheapMode();
    }

    public static void setCheapMode(AgentRuntimeEntry entry, boolean cheapMode) {
        entry.capabilityStates().require(AgentBuffState.STATE_KEY).setCheapMode(cheapMode);
    }

    public static void resetScan(AgentRuntimeEntry entry) {
        entry.capabilityStates().require(AgentBuffState.STATE_KEY).resetLastConsumableScan();
    }

    public static boolean scanDue(AgentRuntimeEntry entry, long nowMs, long intervalMs) {
        return entry.capabilityStates().require(AgentBuffState.STATE_KEY).consumableScanDue(nowMs, intervalMs);
    }

    public static void markScanned(AgentRuntimeEntry entry, long nowMs) {
        entry.capabilityStates().require(AgentBuffState.STATE_KEY).setLastConsumableScanMs(nowMs);
    }

    public static long lastActionAtMs(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentBuffState.STATE_KEY).lastConsumableActionAtMs();
    }

    public static String lastActionSummary(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentBuffState.STATE_KEY).lastConsumableActionSummary();
    }

    public static void noteDecision(AgentRuntimeEntry entry, long nowMs, String summary) {
        entry.capabilityStates().require(AgentBuffState.STATE_KEY).rememberConsumableAction(nowMs, summary);
    }
}
