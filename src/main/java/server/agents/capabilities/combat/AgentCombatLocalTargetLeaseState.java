package server.agents.capabilities.combat;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.Objects;

/** Per-Agent locality commitment created by a successful map-wide target promotion. */
public final class AgentCombatLocalTargetLeaseState {
    public static final AgentCapabilityStateKey<AgentCombatLocalTargetLeaseState> STATE_KEY =
            new AgentCapabilityStateKey<>("combat.local-target-lease",
                    AgentCombatLocalTargetLeaseState.class, AgentCombatLocalTargetLeaseState::new);

    private int mapId = Integer.MIN_VALUE;
    private String objectiveId = "";
    private int destinationRegionId = -1;
    private Phase phase = Phase.INACTIVE;
    private long expiresAtMs;
    private int killsRemaining;
    private int emptyScans;

    public synchronized void synchronizeScope(int currentMapId, String currentObjectiveId) {
        String normalizedObjective = Objects.requireNonNullElse(currentObjectiveId, "");
        if (mapId != currentMapId || !objectiveId.equals(normalizedObjective)) {
            clear();
            mapId = currentMapId;
            objectiveId = normalizedObjective;
        }
    }

    public synchronized void beginMapWideTravel(int currentMapId,
                                                String currentObjectiveId,
                                                int targetRegionId,
                                                long nowMs,
                                                long travelTimeoutMs) {
        synchronizeScope(currentMapId, currentObjectiveId);
        destinationRegionId = targetRegionId;
        phase = targetRegionId >= 0 ? Phase.TRAVELLING : Phase.INACTIVE;
        expiresAtMs = phase == Phase.TRAVELLING
                ? nowMs + Math.max(1L, travelTimeoutMs) : 0L;
        killsRemaining = 0;
        emptyScans = 0;
    }

    public synchronized void observeRegion(int currentMapId,
                                           String currentObjectiveId,
                                           int currentRegionId,
                                           long nowMs,
                                           long durationMs,
                                           int killQuota) {
        synchronizeScope(currentMapId, currentObjectiveId);
        if (phase == Phase.TRAVELLING && currentRegionId >= 0
                && currentRegionId == destinationRegionId) {
            phase = Phase.ACTIVE;
            expiresAtMs = nowMs + Math.max(1L, durationMs);
            killsRemaining = Math.max(1, killQuota);
            emptyScans = 0;
        }
        expire(nowMs);
    }

    /** Records one local-objective scan and returns whether map-wide promotion may run. */
    public synchronized boolean scan(boolean hasSuitableLocalObjective,
                                     long nowMs,
                                     int emptyScanThreshold) {
        expire(nowMs);
        if (phase == Phase.INACTIVE) {
            return true;
        }
        if (phase == Phase.TRAVELLING) {
            return false;
        }
        if (hasSuitableLocalObjective) {
            emptyScans = 0;
            return false;
        }
        emptyScans++;
        if (emptyScans >= Math.max(1, emptyScanThreshold)) {
            release();
            return true;
        }
        return false;
    }

    public synchronized void recordLocalKill(int currentMapId,
                                             String currentObjectiveId,
                                             long nowMs) {
        if (mapId != currentMapId
                || !objectiveId.equals(Objects.requireNonNullElse(currentObjectiveId, ""))) {
            return;
        }
        expire(nowMs);
        if (phase != Phase.ACTIVE) {
            return;
        }
        killsRemaining--;
        if (killsRemaining <= 0) {
            release();
        }
    }

    public synchronized Snapshot snapshot(long nowMs) {
        expire(nowMs);
        return new Snapshot(mapId, objectiveId, destinationRegionId, phase,
                expiresAtMs, killsRemaining, emptyScans);
    }

    public synchronized void cancelTravel() {
        if (phase == Phase.TRAVELLING) {
            release();
        }
    }

    public synchronized void clear() {
        mapId = Integer.MIN_VALUE;
        objectiveId = "";
        release();
    }

    private void expire(long nowMs) {
        if (phase != Phase.INACTIVE && nowMs >= expiresAtMs) {
            release();
        }
    }

    private void release() {
        destinationRegionId = -1;
        phase = Phase.INACTIVE;
        expiresAtMs = 0L;
        killsRemaining = 0;
        emptyScans = 0;
    }

    public enum Phase {
        INACTIVE,
        TRAVELLING,
        ACTIVE
    }

    public record Snapshot(int mapId,
                           String objectiveId,
                           int destinationRegionId,
                           Phase phase,
                           long expiresAtMs,
                           int killsRemaining,
                           int emptyScans) {
    }
}
