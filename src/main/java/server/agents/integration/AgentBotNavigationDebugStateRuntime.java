package server.agents.integration;

import client.Character;
import server.agents.capabilities.movement.AgentMovementTargetSnapshot;
import server.bots.BotEntry;
import server.bots.BotPathLogger;

/**
 * Temporary Agent-owned boundary for navigation debug/path-log state still
 * backed by BotEntry during reconstruction.
 */
public final class AgentBotNavigationDebugStateRuntime {
    private AgentBotNavigationDebugStateRuntime() {
    }

    public static boolean isPathLogging(BotEntry entry) {
        return entry != null && entry.pathLogger() != null;
    }

    public static void startPathLogging(BotEntry entry) {
        Character bot = entry.bot();
        entry.setPathLogger(new BotPathLogger(bot.getName(), bot.getMapId()));
    }

    public static String dumpPathLog(BotEntry entry, AgentMovementTargetSnapshot targetSnapshot, String note) {
        BotPathLogger logger = entry.pathLogger();
        entry.clearPathLogger();
        return logger.dumpToFile(entry, targetSnapshot, note);
    }

    public static void clearPathLogging(BotEntry entry) {
        if (entry != null) {
            entry.clearPathLogger();
        }
    }

    public static void recordPathLog(BotEntry entry,
                                     AgentMovementTargetSnapshot targetSnapshot,
                                     int botRegionId,
                                     boolean consumedTick,
                                     boolean aiTick) {
        BotPathLogger logger = entry == null ? null : entry.pathLogger();
        if (logger != null) {
            logger.record(entry, targetSnapshot, botRegionId, consumedTick, aiTick);
        }
    }
}
