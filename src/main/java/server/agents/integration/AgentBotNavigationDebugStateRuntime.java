package server.agents.integration;

import client.Character;
import server.agents.capabilities.movement.AgentMovementTargetSnapshot;
import server.agents.monitoring.AgentPathLogger;
import server.bots.BotEntry;

import java.awt.Point;

/**
 * Temporary Agent-owned boundary for navigation debug/path-log state still
 * backed by BotEntry during reconstruction.
 */
public final class AgentBotNavigationDebugStateRuntime {
    private AgentBotNavigationDebugStateRuntime() {
    }

    public static boolean isPathLogging(BotEntry entry) {
        return entry != null && entry.navigationDebugState().pathLogger() != null;
    }

    public static void startPathLogging(BotEntry entry) {
        Character bot = entry.bot();
        entry.navigationDebugState().setPathLogger(new AgentPathLogger(bot.getName(), bot.getMapId()));
    }

    public static String dumpPathLog(BotEntry entry, AgentMovementTargetSnapshot targetSnapshot, String note) {
        AgentPathLogger logger = entry.navigationDebugState().pathLogger();
        entry.navigationDebugState().clearPathLogger();
        return logger.dumpToFile(entry, targetSnapshot, note);
    }

    public static void clearPathLogging(BotEntry entry) {
        if (entry != null) {
            entry.navigationDebugState().clearPathLogger();
        }
    }

    public static String lastDecision(BotEntry entry) {
        return entry.navigationDebugState().lastDecision();
    }

    public static void setLastDecision(BotEntry entry, String decision) {
        entry.navigationDebugState().setLastDecision(decision);
    }

    public static String lastEdgeBlockReason(BotEntry entry) {
        return entry.navigationDebugState().lastEdgeBlockReason();
    }

    public static void setLastEdgeBlockReason(BotEntry entry, String reason) {
        entry.navigationDebugState().setLastEdgeBlockReason(reason);
    }

    public static void clearLastEdgeBlockReason(BotEntry entry) {
        entry.navigationDebugState().setLastEdgeBlockReason(null);
    }

    public static String decisionWithBlockReason(BotEntry entry) {
        String reason = lastEdgeBlockReason(entry);
        return reason == null ? lastDecision(entry) : lastDecision(entry) + "[" + reason + "]";
    }

    public static boolean graphWarmupFallback(BotEntry entry) {
        return entry.navigationDebugState().graphWarmupFallback();
    }

    public static void setGraphWarmupFallback(BotEntry entry, boolean fallback) {
        entry.navigationDebugState().setGraphWarmupFallback(fallback);
    }

    public static void clearGraphWarmupFallback(BotEntry entry) {
        entry.navigationDebugState().setGraphWarmupFallback(false);
    }

    public static Point navTargetPosition(BotEntry entry) {
        return entry.navigationTargetState().position();
    }

    public static boolean hasNavTargetPosition(BotEntry entry) {
        return entry.navigationTargetState().hasPosition();
    }

    public static void setNavTargetPosition(BotEntry entry, Point position) {
        entry.navigationTargetState().setPosition(position);
    }

    public static void clearNavTargetPosition(BotEntry entry) {
        entry.setNavTargetPos(null);
    }

    public static int navTargetRegionId(BotEntry entry) {
        return entry.navigationTargetState().regionId();
    }

    public static void setNavTargetRegionId(BotEntry entry, int regionId) {
        entry.navigationTargetState().setRegionId(regionId);
    }

    public static boolean navPreciseTarget(BotEntry entry) {
        return entry.navigationTargetState().precise();
    }

    public static void setNavPreciseTarget(BotEntry entry, boolean precise) {
        entry.navigationTargetState().setPrecise(precise);
    }

    public static void clearNavTarget(BotEntry entry) {
        clearNavTargetPosition(entry);
        setNavTargetRegionId(entry, -1);
        setNavPreciseTarget(entry, false);
    }

    public static void setNavWaypoint(BotEntry entry, Point position, boolean precise) {
        setNavTargetPosition(entry, position);
        setNavPreciseTarget(entry, precise);
    }

    public static boolean portalUseOnCooldown(BotEntry entry, long nowMs) {
        return entry.portalCooldownState().onCooldown(nowMs);
    }

    public static long portalUseCooldownUntilMs(BotEntry entry) {
        return entry.portalCooldownState().useCooldownUntilMs();
    }

    public static void setPortalUseCooldownUntilMs(BotEntry entry, long cooldownUntilMs) {
        entry.portalCooldownState().setUseCooldownUntilMs(cooldownUntilMs);
    }

    public static boolean hasNavJumpLaunchEdge(BotEntry entry) {
        return entry.hasNavJumpLaunchEdge();
    }

    public static boolean hasActiveNavigationEdge(BotEntry entry) {
        return entry.hasActiveNavigationEdge();
    }

    public static Object activeNavigationEdge(BotEntry entry) {
        return entry.activeNavigationEdge();
    }

    public static void setActiveNavigationEdge(BotEntry entry, Object edge) {
        entry.setActiveNavigationEdge(edge);
    }

    public static void clearActiveNavigationEdge(BotEntry entry) {
        entry.clearActiveNavigationEdge();
    }

    public static int navJumpLaunchX(BotEntry entry) {
        return entry.navJumpLaunchX();
    }

    public static boolean matchesNavJumpLaunchEdge(BotEntry entry, Object edge) {
        return entry.matchesNavJumpLaunchEdge(edge);
    }

    public static void rememberNavJumpLaunch(BotEntry entry, Object edge, int launchX) {
        entry.setNavJumpLaunch(edge, launchX);
    }

    public static void clearNavJumpLaunch(BotEntry entry) {
        rememberNavJumpLaunch(entry, null, Integer.MIN_VALUE);
    }

    public static void recordPathLog(BotEntry entry,
                                     AgentMovementTargetSnapshot targetSnapshot,
                                     int botRegionId,
                                     boolean consumedTick,
                                     boolean aiTick) {
        AgentPathLogger logger = entry == null ? null : entry.navigationDebugState().pathLogger();
        if (logger != null) {
            logger.record(entry, targetSnapshot, botRegionId, consumedTick, aiTick);
        }
    }
}
