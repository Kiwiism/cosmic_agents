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

    public static String lastDecision(BotEntry entry) {
        return entry.lastNavDecision();
    }

    public static void setLastDecision(BotEntry entry, String decision) {
        entry.setLastNavDecision(decision);
    }

    public static String lastEdgeBlockReason(BotEntry entry) {
        return entry.lastEdgeBlockReason();
    }

    public static void setLastEdgeBlockReason(BotEntry entry, String reason) {
        entry.setLastEdgeBlockReason(reason);
    }

    public static void clearLastEdgeBlockReason(BotEntry entry) {
        entry.setLastEdgeBlockReason(null);
    }

    public static String decisionWithBlockReason(BotEntry entry) {
        String reason = lastEdgeBlockReason(entry);
        return reason == null ? lastDecision(entry) : lastDecision(entry) + "[" + reason + "]";
    }

    public static boolean graphWarmupFallback(BotEntry entry) {
        return entry.graphWarmupFallback();
    }

    public static void setGraphWarmupFallback(BotEntry entry, boolean fallback) {
        entry.setGraphWarmupFallback(fallback);
    }

    public static void clearGraphWarmupFallback(BotEntry entry) {
        entry.setGraphWarmupFallback(false);
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
