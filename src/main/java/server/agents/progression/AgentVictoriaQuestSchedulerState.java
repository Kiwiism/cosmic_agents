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

    synchronized void begin(int questId, int startMapId, int completeMapId, boolean alreadyStarted) {
        this.questId = questId;
        this.startMapId = startMapId;
        this.completeMapId = completeMapId;
        objectiveIndex = 0;
        huntMapId = 0;
        stage = alreadyStarted ? Stage.HUNT : Stage.TRAVEL_TO_START;
        nextActionAtMs = 0L;
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

    synchronized boolean failed(int questId) {
        return failedQuestIds.contains(questId);
    }

    synchronized void failAndDefer(int level) {
        if (questId > 0) {
            failedQuestIds.add(questId);
        }
        clear(level);
    }

    synchronized void completeAndDefer(int level) {
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
    }
}
