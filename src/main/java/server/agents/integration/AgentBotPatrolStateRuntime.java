package server.agents.integration;

import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed patrol runtime state.
 */
public final class AgentBotPatrolStateRuntime {
    private AgentBotPatrolStateRuntime() {
    }

    public static boolean hasPatrolRegion(BotEntry entry) {
        return entry.hasPatrolRegion();
    }

    public static int patrolRegionId(BotEntry entry) {
        return entry.patrolRegionId();
    }

    public static int patrolMapId(BotEntry entry) {
        return entry.patrolMapId();
    }

    public static Point patrolWanderTarget(BotEntry entry) {
        return entry.patrolWanderTarget();
    }

    public static void startPatrol(BotEntry entry, int regionId, int mapId) {
        entry.setPatrolRegion(regionId, mapId);
    }

    public static void setPatrolWanderTarget(BotEntry entry, Point target) {
        entry.setPatrolWanderTarget(target);
    }

    public static void clearPatrolWanderTarget(BotEntry entry) {
        entry.clearPatrolWanderTarget();
    }

    public static void clearPatrol(BotEntry entry) {
        entry.clearPatrol();
    }

    public static boolean clearPatrolIfMapChanged(BotEntry entry, int mapId) {
        if (!hasPatrolRegion(entry) || patrolMapId(entry) == mapId) {
            return false;
        }
        clearPatrol(entry);
        return true;
    }
}
