package server.agents.integration;

import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed mob-touch sweep state.
 */
public final class AgentBotMobTouchStateRuntime {
    private AgentBotMobTouchStateRuntime() {
    }

    public static Point previousCheckPositionOnMap(BotEntry entry, int mapId) {
        if (entry == null) {
            return null;
        }
        return entry.mobTouchState().previousCheckPositionOnMap(mapId);
    }

    public static void rememberCheck(BotEntry entry, Point position, int mapId) {
        entry.mobTouchState().rememberCheck(position, mapId);
    }
}
