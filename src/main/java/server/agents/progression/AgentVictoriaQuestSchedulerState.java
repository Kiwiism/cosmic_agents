package server.agents.progression;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.awt.Point;
import java.util.HashSet;
import java.util.Set;

final class AgentVictoriaQuestSchedulerState {
    private static final int NAVIGATION_PROGRESS_DISTANCE_PX = config.AgentTuning.intValue(
            "server.agents.progression.AgentVictoriaQuestSchedulerState.NAVIGATION_PROGRESS_DISTANCE_PX");

    static final AgentCapabilityStateKey<AgentVictoriaQuestSchedulerState> STATE_KEY =
            new AgentCapabilityStateKey<>("progression.victoria-quest-scheduler",
                    AgentVictoriaQuestSchedulerState.class, AgentVictoriaQuestSchedulerState::new);

    enum Stage {
        IDLE,
        TRAVEL_TO_START,
        START,
        HUNT,
        TRAVEL_TO_COMPLETE,
        COMPLETE
    }

    private final Set<Integer> failedQuestIds = new HashSet<>();
    private int questId;
    private int startMapId;
    private int completeMapId;
    private int objectiveIndex;
    private int huntMapId;
    private int shopAttemptedObjectiveIndex = -1;
    private Stage stage = Stage.IDLE;
    private long nextActionAtMs;
    private int deferUntilLevel;
    private long attemptStartedAtMs;
    private long lastObjectiveProgressAtMs = -1L;
    private long lastNavigationProgressAtMs = -1L;
    private int lastObservedMapId;
    private int lastObservedRegionId = -1;
    private Point lastNavigationProgressPosition;
    private int lastObjectiveCount;
    private int initialResourceUnits;
    private int resourceBudget;
    private int navigationFailureCount;
    private int retryCount;
    private long nextAssessmentAtMs;
    private int suspendedQuestId;
    private int requestedQuestId;
    private String terminalReason = "";

    synchronized boolean active() {
        return questId > 0;
    }

    synchronized int questId() { return questId; }
    synchronized int startMapId() { return startMapId; }
    synchronized int completeMapId() { return completeMapId; }
    synchronized int objectiveIndex() { return objectiveIndex; }
    synchronized int huntMapId() { return huntMapId; }
    synchronized Stage stage() { return stage; }
    synchronized long nextActionAtMs() { return nextActionAtMs; }
    synchronized int deferUntilLevel() { return deferUntilLevel; }
    synchronized long attemptStartedAtMs() { return attemptStartedAtMs; }
    synchronized long lastObjectiveProgressAtMs() { return lastObjectiveProgressAtMs; }
    synchronized long lastNavigationProgressAtMs() { return lastNavigationProgressAtMs; }
    synchronized int initialResourceUnits() { return initialResourceUnits; }
    synchronized int resourceBudget() { return resourceBudget; }
    synchronized int navigationFailureCount() { return navigationFailureCount; }
    synchronized int retryCount() { return retryCount; }
    synchronized long nextAssessmentAtMs() { return nextAssessmentAtMs; }
    synchronized int requestedQuestId() { return requestedQuestId; }
    synchronized String terminalReason() { return terminalReason; }

    synchronized void requestQuest(int nextQuestId) {
        int normalizedQuestId = Math.max(0, nextQuestId);
        if (normalizedQuestId > 0) {
            terminalReason = "";
        }
        // Cancelling a containing training activity deliberately preserves this scheduler state
        // so ordinary mixed progression can resume it. An explicit Director request for a
        // different quest is stronger: park the old live quest for later and reset only the
        // scheduler cursor, otherwise the newly admitted individual-quest plan silently keeps
        // executing the previous quest until it completes.
        if (normalizedQuestId > 0 && questId > 0 && questId != normalizedQuestId) {
            clear(0);
        } else if (normalizedQuestId > 0 && questId == normalizedQuestId) {
            // A fresh explicit Director visit is a new bounded attempt even when it resumes the
            // same authoritative quest. Activity parking/abandonment deliberately preserves the
            // scheduler cursor, but its elapsed-time, retry, and navigation-failure evidence must
            // not leak into the new visit and immediately trip the struggle watchdog.
            resetAttemptEvidence();
        }
        requestedQuestId = normalizedQuestId;
        if (requestedQuestId > 0) {
            failedQuestIds.remove(requestedQuestId);
            if (suspendedQuestId == requestedQuestId) {
                suspendedQuestId = 0;
            }
            deferUntilLevel = 0;
        }
    }

    synchronized boolean suspendedAtLevel(int candidateQuestId, int level) {
        return suspendedQuestId == candidateQuestId && deferUntilLevel == level;
    }

    synchronized boolean suspended(int candidateQuestId) {
        return suspendedQuestId == candidateQuestId;
    }

    synchronized void begin(int questId, int startMapId, int completeMapId, boolean alreadyStarted) {
        this.questId = questId;
        this.startMapId = startMapId;
        this.completeMapId = completeMapId;
        objectiveIndex = 0;
        huntMapId = 0;
        shopAttemptedObjectiveIndex = -1;
        stage = alreadyStarted ? Stage.HUNT : Stage.TRAVEL_TO_START;
        nextActionAtMs = 0L;
        suspendedQuestId = 0;
    }

    synchronized void stage(Stage stage) {
        this.stage = stage;
        nextActionAtMs = 0L;
    }

    synchronized void objectiveIndex(int objectiveIndex) {
        this.objectiveIndex = objectiveIndex;
        huntMapId = 0;
        shopAttemptedObjectiveIndex = -1;
    }

    synchronized boolean shopAttemptedForCurrentObjective() {
        return shopAttemptedObjectiveIndex == objectiveIndex;
    }

    synchronized void markShopAttemptedForCurrentObjective() {
        shopAttemptedObjectiveIndex = objectiveIndex;
    }

    synchronized void huntMapId(int huntMapId) {
        this.huntMapId = huntMapId;
    }

    synchronized void nextActionAtMs(long nextActionAtMs) {
        this.nextActionAtMs = nextActionAtMs;
    }

    synchronized void beginAttempt(
            long nowMs, int mapId, int regionId, Point position,
            int objectiveCount, int resourceUnits, int budget) {
        attemptStartedAtMs = nowMs;
        lastObjectiveProgressAtMs = nowMs;
        lastNavigationProgressAtMs = nowMs;
        lastObservedMapId = mapId;
        lastObservedRegionId = regionId;
        lastNavigationProgressPosition = copy(position);
        lastObjectiveCount = Math.max(0, objectiveCount);
        initialResourceUnits = Math.max(0, resourceUnits);
        resourceBudget = Math.max(0, budget);
        navigationFailureCount = 0;
        retryCount = 0;
        nextAssessmentAtMs = nowMs;
    }

    synchronized void observeAttempt(
            int mapId, int regionId, Point position, int objectiveCount, long nowMs) {
        boolean mapChanged = mapId != lastObservedMapId;
        boolean regionChanged = !mapChanged && regionId >= 0
                && lastObservedRegionId >= 0 && regionId != lastObservedRegionId;
        boolean movedMeaningfully = !mapChanged && position != null
                && (lastNavigationProgressPosition == null
                || lastNavigationProgressPosition.distanceSq(position)
                >= (long) NAVIGATION_PROGRESS_DISTANCE_PX * NAVIGATION_PROGRESS_DISTANCE_PX);
        if (mapChanged || regionChanged || movedMeaningfully) {
            lastObservedMapId = mapId;
            lastNavigationProgressAtMs = nowMs;
            lastNavigationProgressPosition = copy(position);
        }
        if (regionId >= 0) {
            lastObservedRegionId = regionId;
        }
        if (objectiveCount > lastObjectiveCount) {
            lastObjectiveCount = objectiveCount;
            lastObjectiveProgressAtMs = nowMs;
        }
    }

    synchronized void recordNavigationFailure() {
        navigationFailureCount++;
    }

    synchronized void recordRetry() {
        retryCount++;
    }

    synchronized void assessedAt(long nextAtMs) {
        nextAssessmentAtMs = nextAtMs;
    }

    synchronized boolean failed(int questId) {
        return failedQuestIds.contains(questId);
    }

    synchronized void failAndDefer(int level) {
        if (questId > 0) {
            failedQuestIds.add(questId);
        }
        clear(level);
    }

    synchronized void failRequestedAndDefer(int level) {
        failRequestedAndDefer(level,
                "requested quest did not satisfy its live start requirements");
    }

    synchronized void failRequestedAndDefer(int level, String reason) {
        if (requestedQuestId > 0) {
            failedQuestIds.add(requestedQuestId);
        }
        clear(level);
        terminalReason = reason == null ? "" : reason.trim();
    }

    synchronized void completeAndDefer(int level) {
        clear(level);
    }

    synchronized void suspendAndDefer(int level) {
        suspendAndDefer(level, "bounded quest-attempt policy requested suspension");
    }

    synchronized void suspendAndDefer(int level, String reason) {
        suspendedQuestId = questId;
        clear(level);
        terminalReason = reason == null ? "" : reason.trim();
    }

    synchronized void defer(int level) {
        deferUntilLevel = level;
    }

    private void clear(int level) {
        questId = 0;
        startMapId = 0;
        completeMapId = 0;
        objectiveIndex = 0;
        huntMapId = 0;
        shopAttemptedObjectiveIndex = -1;
        stage = Stage.IDLE;
        nextActionAtMs = 0L;
        deferUntilLevel = level;
        resetAttemptEvidence();
    }

    private void resetAttemptEvidence() {
        attemptStartedAtMs = 0L;
        lastObjectiveProgressAtMs = -1L;
        lastNavigationProgressAtMs = -1L;
        lastObservedMapId = 0;
        lastObservedRegionId = -1;
        lastNavigationProgressPosition = null;
        lastObjectiveCount = 0;
        initialResourceUnits = 0;
        resourceBudget = 0;
        navigationFailureCount = 0;
        retryCount = 0;
        nextAssessmentAtMs = 0L;
    }

    private static Point copy(Point point) {
        return point == null ? null : new Point(point);
    }
}
