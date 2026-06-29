package server.agents.integration;

import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed airshow state.
 */
public final class AgentBotAirshowStateRuntime {
    private AgentBotAirshowStateRuntime() {
    }

    public static boolean active(BotEntry entry) {
        return entry.airshowActive();
    }

    public static void start(BotEntry entry) {
        entry.setAirshowActive(true);
        entry.setAirshowLastTrailAtMs(0L);
    }

    public static void stop(BotEntry entry) {
        entry.setAirshowActive(false);
        entry.setAirshowLastTrailAtMs(0L);
    }

    public static boolean trailDue(BotEntry entry, long nowMs, long intervalMs) {
        return nowMs - entry.airshowLastTrailAtMs() >= intervalMs;
    }

    public static void markTrail(BotEntry entry, long nowMs) {
        entry.setAirshowLastTrailAtMs(nowMs);
    }

    public static void applyFrame(BotEntry entry,
                                  Point position,
                                  int velocityX,
                                  int velocityY,
                                  int facingDirection,
                                  boolean inAir,
                                  boolean climbing) {
        entry.setScriptedMovementFrame(position, velocityX, velocityY, facingDirection, inAir, climbing);
    }
}
