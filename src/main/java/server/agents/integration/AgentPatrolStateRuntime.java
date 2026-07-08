package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed patrol runtime state.
 */
public final class AgentPatrolStateRuntime {
    private AgentPatrolStateRuntime() {
    }

    public static boolean hasPatrolRegion(AgentRuntimeEntry entry) {
        return entry.patrolState().hasRegion();
    }

    public static int patrolRegionId(AgentRuntimeEntry entry) {
        return entry.patrolState().regionId();
    }

    public static int patrolMapId(AgentRuntimeEntry entry) {
        return entry.patrolState().mapId();
    }

    public static Point patrolWanderTarget(AgentRuntimeEntry entry) {
        return entry.patrolState().wanderTarget();
    }

    public static void startPatrol(AgentRuntimeEntry entry, int regionId, int mapId) {
        entry.patrolState().setRegion(regionId, mapId);
    }

    public static void setPatrolWanderTarget(AgentRuntimeEntry entry, Point target) {
        entry.patrolState().setWanderTarget(target);
    }

    public static void clearPatrolWanderTarget(AgentRuntimeEntry entry) {
        entry.patrolState().clearWanderTarget();
    }

    public static void clearPatrol(AgentRuntimeEntry entry) {
        entry.patrolState().clear();
    }

    public static boolean clearPatrolIfMapChanged(AgentRuntimeEntry entry, int mapId) {
        if (!hasPatrolRegion(entry) || patrolMapId(entry) == mapId) {
            return false;
        }
        clearPatrol(entry);
        return true;
    }
}
