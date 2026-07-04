package server.agents.integration;

import server.bots.BotEntry;
import server.maps.MapItem;

/**
 * Agent-owned adapter for temporary BotEntry-backed grind loot targeting state.
 */
public final class AgentBotGrindLootStateRuntime {
    private AgentBotGrindLootStateRuntime() {
    }

    public static MapItem grindLootTarget(BotEntry entry) {
        return entry.grindLootState().target();
    }

    public static boolean hasGrindLootTarget(BotEntry entry) {
        return entry.grindLootState().hasTarget();
    }

    public static void setGrindLootTarget(BotEntry entry, MapItem loot) {
        entry.grindLootState().setTarget(loot);
    }

    public static void clearGrindLootTarget(BotEntry entry) {
        entry.grindLootState().clearTarget();
    }

    public static void suppressRetry(BotEntry entry, MapItem loot, long untilMs) {
        if (loot == null) {
            clearRetrySuppression(entry);
            return;
        }
        entry.grindLootState().suppressRetry(loot.getObjectId(), untilMs);
    }

    public static boolean isRetrySuppressed(BotEntry entry, MapItem loot, long nowMs) {
        if (entry == null || loot == null || entry.grindLootState().ignoredObjectId() <= 0) {
            return false;
        }
        if (nowMs >= entry.grindLootState().ignoredUntilMs()) {
            clearRetrySuppression(entry);
            return false;
        }
        return entry.grindLootState().ignoredObjectId() == loot.getObjectId();
    }

    public static void clearRetrySuppression(BotEntry entry) {
        entry.grindLootState().clearRetrySuppression();
    }
}
