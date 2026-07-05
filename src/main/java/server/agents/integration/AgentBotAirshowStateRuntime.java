package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed airshow state.
 */
public final class AgentBotAirshowStateRuntime {
    private AgentBotAirshowStateRuntime() {
    }

    public static boolean active(AgentRuntimeEntry entry) {
        return entry.airshowState().active();
    }

    public static void start(AgentRuntimeEntry entry) {
        entry.airshowState().setActive(true);
        entry.airshowState().setLastTrailAtMs(0L);
    }

    public static void stop(AgentRuntimeEntry entry) {
        entry.airshowState().setActive(false);
        entry.airshowState().setLastTrailAtMs(0L);
    }

    public static boolean trailDue(AgentRuntimeEntry entry, long nowMs, long intervalMs) {
        return nowMs - entry.airshowState().lastTrailAtMs() >= intervalMs;
    }

    public static void markTrail(AgentRuntimeEntry entry, long nowMs) {
        entry.airshowState().setLastTrailAtMs(nowMs);
    }

    public static void applyFrame(AgentRuntimeEntry entry,
                                  Point position,
                                  int velocityX,
                                  int velocityY,
                                  int facingDirection,
                                  boolean inAir,
                                  boolean climbing) {
        entry.setScriptedMovementFrame(position, velocityX, velocityY, facingDirection, inAir, climbing);
    }
}
