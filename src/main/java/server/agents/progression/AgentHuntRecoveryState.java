package server.agents.progression;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class AgentHuntRecoveryState {
    static final AgentCapabilityStateKey<AgentHuntRecoveryState> STATE_KEY =
            new AgentCapabilityStateKey<>("progression.hunt-recovery",
                    AgentHuntRecoveryState.class, AgentHuntRecoveryState::new);

    private final Map<String, Frame> frames = new HashMap<>();

    synchronized Frame frame(String objectiveKey, String objectiveId, int progress, long nowMs) {
        String normalizedObjectiveId = objectiveId == null ? "" : objectiveId;
        Frame frame = frames.get(objectiveKey);
        if (frame == null || !frame.objectiveId().equals(normalizedObjectiveId)) {
            frame = new Frame(normalizedObjectiveId, progress, nowMs);
            frames.put(objectiveKey, frame);
        }
        return frame;
    }

    synchronized void clear(String objectiveKey) {
        frames.remove(objectiveKey);
    }

    synchronized void observeRelevantDamage(String objectiveId, int mapId, long nowMs) {
        String normalizedObjectiveId = objectiveId == null ? "" : objectiveId;
        frames.values().stream()
                .filter(frame -> frame.objectiveId().equals(normalizedObjectiveId))
                .filter(frame -> frame.mapId() == mapId)
                .forEach(frame -> frame.observeRelevantDamage(nowMs));
    }

    synchronized void observeRelevantKill(String objectiveId, int mapId, long nowMs) {
        String normalizedObjectiveId = objectiveId == null ? "" : objectiveId;
        frames.values().stream()
                .filter(frame -> frame.objectiveId().equals(normalizedObjectiveId))
                .filter(frame -> frame.mapId() == mapId)
                .forEach(frame -> frame.observeRelevantKill(nowMs));
    }

    static final class Frame {
        private final String objectiveId;
        private int mapId = -1;
        private int lastProgress;
        private long lastProgressAtMs;
        private long mapEnteredAtMs;
        private long zeroTargetsSinceMs;
        private long firstRelevantDamageAtMs;
        private long lastRelevantDamageAtMs;
        private long navigationWarmupStartedAtMs;
        private int reentryAttempts;
        private boolean fallbackActive;
        private final Map<Integer, Long> failedMapUntilMs = new HashMap<>();

        private Frame(String objectiveId, int progress, long nowMs) {
            this.objectiveId = objectiveId;
            lastProgress = Math.max(0, progress);
            lastProgressAtMs = nowMs;
            mapEnteredAtMs = nowMs;
        }

        synchronized void enterMap(int nextMapId, long nowMs) {
            if (mapId == nextMapId) {
                return;
            }
            mapId = nextMapId;
            mapEnteredAtMs = nowMs;
            lastProgressAtMs = nowMs;
            zeroTargetsSinceMs = 0L;
            firstRelevantDamageAtMs = 0L;
            lastRelevantDamageAtMs = 0L;
            navigationWarmupStartedAtMs = 0L;
        }

        synchronized boolean observeProgress(int progress, long nowMs) {
            if (progress <= lastProgress) {
                return false;
            }
            lastProgress = progress;
            lastProgressAtMs = nowMs;
            zeroTargetsSinceMs = 0L;
            firstRelevantDamageAtMs = 0L;
            lastRelevantDamageAtMs = 0L;
            return true;
        }

        synchronized int mapId() {
            return mapId;
        }

        synchronized String objectiveId() {
            return objectiveId;
        }

        synchronized void observeRelevantDamage(long nowMs) {
            if (firstRelevantDamageAtMs == 0L) {
                firstRelevantDamageAtMs = nowMs;
            }
            lastRelevantDamageAtMs = Math.max(lastRelevantDamageAtMs, nowMs);
        }

        synchronized void observeRelevantKill(long nowMs) {
            firstRelevantDamageAtMs = 0L;
            lastRelevantDamageAtMs = Math.max(lastRelevantDamageAtMs, nowMs);
        }

        synchronized boolean recentRelevantDamage(long nowMs, long graceMs) {
            return lastRelevantDamageAtMs > 0L
                    && nowMs - lastRelevantDamageAtMs < graceMs;
        }

        synchronized long lastRelevantDamageAtMs() {
            return lastRelevantDamageAtMs > 0L ? lastRelevantDamageAtMs : -1L;
        }

        synchronized boolean hardKillGraceElapsed(long nowMs, long graceMs) {
            return firstRelevantDamageAtMs > 0L
                    && nowMs - firstRelevantDamageAtMs >= graceMs;
        }

        synchronized void observeTargets(int liveTargets, long nowMs) {
            if (liveTargets > 0) {
                zeroTargetsSinceMs = 0L;
            } else if (zeroTargetsSinceMs == 0L) {
                zeroTargetsSinceMs = nowMs;
            }
        }

        synchronized boolean suspendForNavigationWarmup(
                boolean warmupPending,
                long nowMs,
                long maximumPauseMs) {
            if (!warmupPending) {
                navigationWarmupStartedAtMs = 0L;
                return false;
            }
            if (navigationWarmupStartedAtMs == 0L) {
                navigationWarmupStartedAtMs = nowMs;
            }
            if (nowMs - navigationWarmupStartedAtMs >= Math.max(0L, maximumPauseMs)) {
                return false;
            }
            mapEnteredAtMs = nowMs;
            lastProgressAtMs = nowMs;
            zeroTargetsSinceMs = 0L;
            firstRelevantDamageAtMs = 0L;
            lastRelevantDamageAtMs = 0L;
            return true;
        }

        synchronized boolean arrivalGraceElapsed(long nowMs, long graceMs) {
            return nowMs - mapEnteredAtMs >= graceMs;
        }

        synchronized boolean zeroTargetGraceElapsed(long nowMs, long graceMs) {
            return zeroTargetsSinceMs > 0L && nowMs - zeroTargetsSinceMs >= graceMs;
        }

        synchronized boolean progressGraceElapsed(long nowMs, long graceMs) {
            return nowMs - lastProgressAtMs >= graceMs;
        }

        synchronized int reentryAttempts() {
            return reentryAttempts;
        }

        synchronized void recordReentry(long nowMs) {
            reentryAttempts++;
            zeroTargetsSinceMs = 0L;
            mapEnteredAtMs = nowMs;
        }

        synchronized void activateFallback() {
            fallbackActive = true;
        }

        synchronized boolean fallbackActive() {
            return fallbackActive;
        }

        synchronized void failMaps(Set<Integer> mapIds, long untilMs) {
            for (int failedMapId : mapIds) {
                failedMapUntilMs.put(failedMapId, untilMs);
            }
        }

        synchronized Set<Integer> failedMaps(long nowMs) {
            failedMapUntilMs.entrySet().removeIf(entry -> entry.getValue() <= nowMs);
            return failedMapUntilMs.entrySet().stream()
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toUnmodifiableSet());
        }
    }
}
