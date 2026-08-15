package server.agents.capabilities.combat;

import java.awt.Point;
import java.util.Objects;

/** Bounded commitment to eligible monsters around one live combat platform. */
public final class AgentCombatPlatformBatchState {
    private int mapId = Integer.MIN_VALUE;
    private String objectiveId = "";
    private int regionId = -1;
    private Point anchor;
    private int killsRemaining;
    private long expiresAtMs;

    public synchronized void synchronizeScope(int currentMapId, String currentObjectiveId) {
        String normalizedObjectiveId = Objects.requireNonNullElse(currentObjectiveId, "");
        if (mapId == currentMapId && objectiveId.equals(normalizedObjectiveId)) {
            return;
        }
        clear();
        mapId = currentMapId;
        objectiveId = normalizedObjectiveId;
    }

    public synchronized void begin(int currentMapId,
                                   String currentObjectiveId,
                                   int targetRegionId,
                                   Point targetAnchor,
                                   int killQuota,
                                   long nowMs,
                                   long durationMs) {
        synchronizeScope(currentMapId, currentObjectiveId);
        if (targetAnchor == null || killQuota < 2) {
            release();
            return;
        }
        regionId = targetRegionId;
        anchor = new Point(targetAnchor);
        killsRemaining = killQuota;
        expiresAtMs = nowMs + Math.max(1L, durationMs);
    }

    public synchronized boolean includes(int currentMapId,
                                         String currentObjectiveId,
                                         int candidateRegionId,
                                         Point candidatePosition,
                                         long nowMs,
                                         int radiusPx,
                                         int yTolerancePx) {
        synchronizeScope(currentMapId, currentObjectiveId);
        expire(nowMs);
        if (anchor == null || candidatePosition == null || killsRemaining <= 0) {
            return false;
        }
        if (anchor.distanceSq(candidatePosition) > (long) radiusPx * radiusPx) {
            return false;
        }
        return regionId >= 0 && candidateRegionId >= 0
                ? regionId == candidateRegionId
                : Math.abs(anchor.y - candidatePosition.y) <= yTolerancePx;
    }

    public synchronized boolean active(int currentMapId,
                                       String currentObjectiveId,
                                       long nowMs) {
        synchronizeScope(currentMapId, currentObjectiveId);
        expire(nowMs);
        return anchor != null && killsRemaining > 0;
    }

    public synchronized void killed(int currentMapId,
                                    String currentObjectiveId,
                                    long nowMs) {
        if (!active(currentMapId, currentObjectiveId, nowMs)) {
            return;
        }
        killsRemaining--;
        if (killsRemaining <= 0) {
            release();
        }
    }

    public synchronized Snapshot snapshot(long nowMs) {
        expire(nowMs);
        return new Snapshot(mapId, objectiveId, regionId,
                anchor == null ? null : new Point(anchor), killsRemaining, expiresAtMs);
    }

    public synchronized void release() {
        regionId = -1;
        anchor = null;
        killsRemaining = 0;
        expiresAtMs = 0L;
    }

    public synchronized void clear() {
        mapId = Integer.MIN_VALUE;
        objectiveId = "";
        release();
    }

    private void expire(long nowMs) {
        if (anchor != null && nowMs >= expiresAtMs) {
            release();
        }
    }

    public record Snapshot(int mapId,
                           String objectiveId,
                           int regionId,
                           Point anchor,
                           int killsRemaining,
                           long expiresAtMs) {
    }
}
