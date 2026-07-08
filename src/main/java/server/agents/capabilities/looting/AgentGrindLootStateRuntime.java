package server.agents.capabilities.looting;

import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapItem;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed grind loot targeting state.
 */
public final class AgentGrindLootStateRuntime {
    private AgentGrindLootStateRuntime() {
    }

    public static MapItem grindLootTarget(AgentRuntimeEntry entry) {
        return entry.grindLootState().target();
    }

    public static boolean hasGrindLootTarget(AgentRuntimeEntry entry) {
        return entry.grindLootState().hasTarget();
    }

    public static void setGrindLootTarget(AgentRuntimeEntry entry, MapItem loot) {
        entry.grindLootState().setTarget(loot);
    }

    public static void clearGrindLootTarget(AgentRuntimeEntry entry) {
        entry.grindLootState().clearTarget();
    }

    public static void suppressRetry(AgentRuntimeEntry entry, MapItem loot, long untilMs) {
        if (loot == null) {
            clearRetrySuppression(entry);
            return;
        }
        entry.grindLootState().suppressRetry(loot.getObjectId(), untilMs);
    }

    public static boolean isRetrySuppressed(AgentRuntimeEntry entry, MapItem loot, long nowMs) {
        if (entry == null || loot == null || entry.grindLootState().ignoredObjectId() <= 0) {
            return false;
        }
        if (nowMs >= entry.grindLootState().ignoredUntilMs()) {
            clearRetrySuppression(entry);
            return false;
        }
        return entry.grindLootState().ignoredObjectId() == loot.getObjectId();
    }

    public static void clearRetrySuppression(AgentRuntimeEntry entry) {
        entry.grindLootState().clearRetrySuppression();
    }
}
