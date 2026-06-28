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
        return entry.grindLootTarget();
    }

    public static boolean hasGrindLootTarget(BotEntry entry) {
        return entry.hasGrindLootTarget();
    }

    public static void setGrindLootTarget(BotEntry entry, MapItem loot) {
        entry.setGrindLootTarget(loot);
    }

    public static void clearGrindLootTarget(BotEntry entry) {
        entry.clearGrindLootTarget();
    }

    public static void suppressRetry(BotEntry entry, MapItem loot, long untilMs) {
        if (loot == null) {
            clearRetrySuppression(entry);
            return;
        }
        entry.suppressGrindLootRetry(loot.getObjectId(), untilMs);
    }

    public static boolean isRetrySuppressed(BotEntry entry, MapItem loot, long nowMs) {
        if (entry == null || loot == null || entry.ignoredGrindLootObjectId() <= 0) {
            return false;
        }
        if (nowMs >= entry.ignoredGrindLootUntilMs()) {
            clearRetrySuppression(entry);
            return false;
        }
        return entry.ignoredGrindLootObjectId() == loot.getObjectId();
    }

    public static void clearRetrySuppression(BotEntry entry) {
        entry.clearGrindLootRetrySuppression();
    }
}
