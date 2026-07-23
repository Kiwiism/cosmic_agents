package server.agents.capabilities.townlife;

import java.awt.Point;

final class AgentTownLifeProgressWatchdog {
    enum Result {
        PROGRESSING,
        STALLED,
        TIMED_OUT
    }

    private static final int MEANINGFUL_PROGRESS_PX = config.AgentTuning.intValue("server.agents.capabilities.townlife.AgentTownLifeProgressWatchdog.MEANINGFUL_PROGRESS_PX");
    private static final long STALL_TIMEOUT_MS = config.AgentTuning.longValue("server.agents.capabilities.townlife.AgentTownLifeProgressWatchdog.STALL_TIMEOUT_MS");
    private static final long TOTAL_TIMEOUT_MS = config.AgentTuning.longValue("server.agents.capabilities.townlife.AgentTownLifeProgressWatchdog.TOTAL_TIMEOUT_MS");

    private Point target;
    private Point lastPosition;
    private double bestDistanceSq;
    private long startedAtMs;
    private long lastProgressAtMs;

    synchronized void begin(Point nextTarget, long nowMs) {
        target = nextTarget == null ? null : new Point(nextTarget);
        lastPosition = null;
        bestDistanceSq = Double.POSITIVE_INFINITY;
        startedAtMs = nowMs;
        lastProgressAtMs = nowMs;
    }

    synchronized Result observe(Point position, long nowMs) {
        if (target == null || position == null) {
            return Result.PROGRESSING;
        }
        double distanceSq = position.distanceSq(target);
        boolean moved = lastPosition == null
                || lastPosition.distanceSq(position) >= MEANINGFUL_PROGRESS_PX * MEANINGFUL_PROGRESS_PX;
        boolean approached = bestDistanceSq == Double.POSITIVE_INFINITY
                || Math.sqrt(bestDistanceSq) - Math.sqrt(distanceSq) >= MEANINGFUL_PROGRESS_PX;
        if (moved || approached) {
            lastPosition = new Point(position);
            bestDistanceSq = Math.min(bestDistanceSq, distanceSq);
            lastProgressAtMs = nowMs;
        }
        if (nowMs - startedAtMs >= TOTAL_TIMEOUT_MS) {
            return Result.TIMED_OUT;
        }
        return nowMs - lastProgressAtMs >= STALL_TIMEOUT_MS ? Result.STALLED : Result.PROGRESSING;
    }

    synchronized void clear() {
        target = null;
        lastPosition = null;
        bestDistanceSq = Double.POSITIVE_INFINITY;
        startedAtMs = 0L;
        lastProgressAtMs = 0L;
    }
}
