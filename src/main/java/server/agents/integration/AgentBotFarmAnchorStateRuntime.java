package server.agents.integration;

import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed sentry/farm anchor state.
 */
public final class AgentBotFarmAnchorStateRuntime {
    private AgentBotFarmAnchorStateRuntime() {
    }

    public static Point farmAnchor(BotEntry entry) {
        return entry.farmAnchor();
    }

    public static int farmAnchorMapId(BotEntry entry) {
        return entry.farmAnchorMapId();
    }

    public static boolean hasFarmAnchor(BotEntry entry) {
        return entry.hasFarmAnchor();
    }

    public static boolean isFarmAnchorInMap(BotEntry entry, int mapId) {
        return hasFarmAnchor(entry) && farmAnchorMapId(entry) == mapId;
    }

    public static Point farmAnchorInMap(BotEntry entry, int mapId) {
        return isFarmAnchorInMap(entry, mapId) ? farmAnchor(entry) : null;
    }

    public static void setFarmAnchor(BotEntry entry, Point anchor, int mapId) {
        entry.setFarmAnchor(anchor, mapId);
    }

    public static void clearFarmAnchor(BotEntry entry) {
        entry.clearFarmAnchor();
    }

    public static boolean clearFarmAnchorIfMapChanged(BotEntry entry, int mapId) {
        if (!hasFarmAnchor(entry) || farmAnchorMapId(entry) == mapId) {
            return false;
        }
        clearFarmAnchor(entry);
        return true;
    }
}
