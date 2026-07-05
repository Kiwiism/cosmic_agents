package server.agents.integration;

import client.Character;
import server.agents.capabilities.movement.AgentMovementTargetSnapshot;
import server.agents.monitoring.AgentPathLogger;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Temporary Agent-owned boundary for navigation debug/path-log state still
 * backed by AgentRuntimeEntry during reconstruction.
 */
public final class AgentBotNavigationDebugStateRuntime {
    private AgentBotNavigationDebugStateRuntime() {
    }

    public static boolean isPathLogging(AgentRuntimeEntry entry) {
        return entry != null && entry.navigationDebugState().pathLogger() != null;
    }

    public static void startPathLogging(AgentRuntimeEntry entry) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        entry.navigationDebugState().setPathLogger(new AgentPathLogger(bot.getName(), bot.getMapId()));
    }

    public static String dumpPathLog(AgentRuntimeEntry entry, AgentMovementTargetSnapshot targetSnapshot, String note) {
        AgentPathLogger logger = entry.navigationDebugState().pathLogger();
        entry.navigationDebugState().clearPathLogger();
        return logger.dumpToFile(entry, targetSnapshot, note);
    }

    public static void clearPathLogging(AgentRuntimeEntry entry) {
        if (entry != null) {
            entry.navigationDebugState().clearPathLogger();
        }
    }

    public static String lastDecision(AgentRuntimeEntry entry) {
        return entry.navigationDebugState().lastDecision();
    }

    public static void setLastDecision(AgentRuntimeEntry entry, String decision) {
        entry.navigationDebugState().setLastDecision(decision);
    }

    public static String lastEdgeBlockReason(AgentRuntimeEntry entry) {
        return entry.navigationDebugState().lastEdgeBlockReason();
    }

    public static void setLastEdgeBlockReason(AgentRuntimeEntry entry, String reason) {
        entry.navigationDebugState().setLastEdgeBlockReason(reason);
    }

    public static void clearLastEdgeBlockReason(AgentRuntimeEntry entry) {
        entry.navigationDebugState().setLastEdgeBlockReason(null);
    }

    public static String decisionWithBlockReason(AgentRuntimeEntry entry) {
        String reason = lastEdgeBlockReason(entry);
        return reason == null ? lastDecision(entry) : lastDecision(entry) + "[" + reason + "]";
    }

    public static boolean graphWarmupFallback(AgentRuntimeEntry entry) {
        return entry.navigationDebugState().graphWarmupFallback();
    }

    public static void setGraphWarmupFallback(AgentRuntimeEntry entry, boolean fallback) {
        entry.navigationDebugState().setGraphWarmupFallback(fallback);
    }

    public static void clearGraphWarmupFallback(AgentRuntimeEntry entry) {
        entry.navigationDebugState().setGraphWarmupFallback(false);
    }

    public static Point navTargetPosition(AgentRuntimeEntry entry) {
        return entry.navigationTargetState().position();
    }

    public static boolean hasNavTargetPosition(AgentRuntimeEntry entry) {
        return entry.navigationTargetState().hasPosition();
    }

    public static void setNavTargetPosition(AgentRuntimeEntry entry, Point position) {
        entry.navigationTargetState().setPosition(position);
    }

    public static void clearNavTargetPosition(AgentRuntimeEntry entry) {
        entry.navigationTargetState().setPosition(null);
    }

    public static int navTargetRegionId(AgentRuntimeEntry entry) {
        return entry.navigationTargetState().regionId();
    }

    public static void setNavTargetRegionId(AgentRuntimeEntry entry, int regionId) {
        entry.navigationTargetState().setRegionId(regionId);
    }

    public static boolean navPreciseTarget(AgentRuntimeEntry entry) {
        return entry.navigationTargetState().precise();
    }

    public static void setNavPreciseTarget(AgentRuntimeEntry entry, boolean precise) {
        entry.navigationTargetState().setPrecise(precise);
    }

    public static void clearNavTarget(AgentRuntimeEntry entry) {
        clearNavTargetPosition(entry);
        setNavTargetRegionId(entry, -1);
        setNavPreciseTarget(entry, false);
    }

    public static void setNavWaypoint(AgentRuntimeEntry entry, Point position, boolean precise) {
        setNavTargetPosition(entry, position);
        setNavPreciseTarget(entry, precise);
    }

    public static boolean portalUseOnCooldown(AgentRuntimeEntry entry, long nowMs) {
        return entry.portalCooldownState().onCooldown(nowMs);
    }

    public static long portalUseCooldownUntilMs(AgentRuntimeEntry entry) {
        return entry.portalCooldownState().useCooldownUntilMs();
    }

    public static void setPortalUseCooldownUntilMs(AgentRuntimeEntry entry, long cooldownUntilMs) {
        entry.portalCooldownState().setUseCooldownUntilMs(cooldownUntilMs);
    }

    public static boolean hasNavJumpLaunchEdge(AgentRuntimeEntry entry) {
        return entry.navigationEdgeState().hasJumpLaunchEdge();
    }

    public static boolean hasActiveNavigationEdge(AgentRuntimeEntry entry) {
        return entry.navigationEdgeState().hasActiveEdge();
    }

    public static Object activeNavigationEdge(AgentRuntimeEntry entry) {
        return entry.navigationEdgeState().activeEdge();
    }

    public static void setActiveNavigationEdge(AgentRuntimeEntry entry, Object edge) {
        entry.navigationEdgeState().setActiveEdge(edge);
    }

    public static void clearActiveNavigationEdge(AgentRuntimeEntry entry) {
        entry.navigationEdgeState().clearActiveEdge();
    }

    public static int navJumpLaunchX(AgentRuntimeEntry entry) {
        return entry.navigationEdgeState().jumpLaunchX();
    }

    public static boolean matchesNavJumpLaunchEdge(AgentRuntimeEntry entry, Object edge) {
        return entry.navigationEdgeState().matchesJumpLaunchEdge(edge);
    }

    public static void rememberNavJumpLaunch(AgentRuntimeEntry entry, Object edge, int launchX) {
        entry.navigationEdgeState().setJumpLaunch(edge, launchX);
    }

    public static void clearNavJumpLaunch(AgentRuntimeEntry entry) {
        rememberNavJumpLaunch(entry, null, Integer.MIN_VALUE);
    }

    public static void recordPathLog(AgentRuntimeEntry entry,
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
