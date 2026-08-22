package server.agents.progression;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.HashSet;
import java.util.Set;

final class AgentVictoriaQuestSchedulerState {
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
    private Stage stage = Stage.IDLE;
    private long nextActionAtMs;
    private int deferUntilLevel;
    private long attemptStartedAtMs;
    private long lastObjectiveProgressAtMs = -1L;
    private long lastNavigationProgressAtMs = -1L;
    private int lastObservedMapId;
    private int lastObjectiveCount;
    private int initialResourceUnits;
    private int resourceBudget;
    private int navigationFailureCount;
    private int retryCount;
    private long nextAssessmentAtMs;
    private int suspendedQuestId;
    private int requestedQuestId;

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

    synchronized void requestQuest(int nextQuestId) {
        requestedQuestId = Math.max(0, nextQuestId);
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
    }

    synchronized void huntMapId(int huntMapId) {
        this.huntMapId = huntMapId;
    }

    synchronized void nextActionAtMs(long nextActionAtMs) {
        this.nextActionAtMs = nextActionAtMs;
    }

    synchronized void beginAttempt(
            long nowMs, int mapId, int objectiveCount, int resourceUnits, int budget) {
        attemptStartedAtMs = nowMs;
        lastObjectiveProgressAtMs = nowMs;
        lastNavigationProgressAtMs = nowMs;
        lastObservedMapId = mapId;
        lastObjectiveCount = Math.max(0, objectiveCount);
        initialResourceUnits = Math.max(0, resourceUnits);
        resourceBudget = Math.max(0, budget);
        navigationFailureCount = 0;
        retryCount = 0;
        nextAssessmentAtMs = nowMs;
    }

    synchronized void observeAttempt(int mapId, int objectiveCount, long nowMs) {
        if (mapId != lastObservedMapId) {
            lastObservedMapId = mapId;
            lastNavigationProgressAtMs = nowMs;
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
        if (requestedQuestId > 0) {
            failedQuestIds.add(requestedQuestId);
        }
        clear(level);
    }

    synchronized void completeAndDefer(int level) {
        clear(level);
    }

    synchronized void suspendAndDefer(int level) {
        suspendedQuestId = questId;
        clear(level);
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
        stage = Stage.IDLE;
        nextActionAtMs = 0L;
        deferUntilLevel = level;
        attemptStartedAtMs = 0L;
        lastObjectiveProgressAtMs = -1L;
        lastNavigationProgressAtMs = -1L;
        lastObservedMapId = 0;
        lastObjectiveCount = 0;
        initialResourceUnits = 0;
        resourceBudget = 0;
        navigationFailureCount = 0;
        retryCount = 0;
        nextAssessmentAtMs = 0L;
    }
}
