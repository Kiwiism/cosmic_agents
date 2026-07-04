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
        return entry.patrolState().hasRegion();
    }

    public static int patrolRegionId(BotEntry entry) {
        return entry.patrolState().regionId();
    }

    public static int patrolMapId(BotEntry entry) {
        return entry.patrolState().mapId();
    }

    public static Point patrolWanderTarget(BotEntry entry) {
        return entry.patrolState().wanderTarget();
    }

    public static void startPatrol(BotEntry entry, int regionId, int mapId) {
        entry.patrolState().setRegion(regionId, mapId);
    }

    public static void setPatrolWanderTarget(BotEntry entry, Point target) {
        entry.patrolState().setWanderTarget(target);
    }

    public static void clearPatrolWanderTarget(BotEntry entry) {
        entry.patrolState().clearWanderTarget();
    }

    public static void clearPatrol(BotEntry entry) {
        entry.patrolState().clear();
    }

    public static boolean clearPatrolIfMapChanged(BotEntry entry, int mapId) {
        if (!hasPatrolRegion(entry) || patrolMapId(entry) == mapId) {
            return false;
        }
        clearPatrol(entry);
        return true;
    }
}
