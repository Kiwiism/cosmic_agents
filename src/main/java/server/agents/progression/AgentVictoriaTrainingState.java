package server.agents.progression;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.HashMap;
import java.util.Map;

public final class AgentVictoriaTrainingState {
    private static final long NEXT_SELECTION_DELAY_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentVictoriaTrainingState.NEXT_SELECTION_DELAY_MS");
    public static final AgentCapabilityStateKey<AgentVictoriaTrainingState> STATE_KEY =
            new AgentCapabilityStateKey<>("progression.victoria-training",
                    AgentVictoriaTrainingState.class, AgentVictoriaTrainingState::new);

    private final Map<Integer, Long> unavailableMapsUntilMs = new HashMap<>();
    private boolean active;
    private boolean questsEnabled;
    private int targetLevel;
    private int selectedMapId;
    private int selectedAtLevel;
    private String selectionReason = "";
    private long nextSelectionAtMs;
    private int lastEvidenceLevel;

    public synchronized void start(int targetLevel, long nowMs) {
        start(targetLevel, AgentVictoriaProgressionPolicy.defaultPolicy().questingEnabledByDefault(), nowMs);
    }

    public synchronized void start(int targetLevel, boolean questsEnabled, long nowMs) {
        active = true;
        this.targetLevel = targetLevel;
        this.questsEnabled = questsEnabled;
        selectedMapId = 0;
        selectedAtLevel = 0;
        selectionReason = "";
        nextSelectionAtMs = nowMs;
        unavailableMapsUntilMs.clear();
    }

    public synchronized void stop() {
        active = false;
        selectedMapId = 0;
        selectionReason = "";
    }

    public synchronized boolean active() {
        return active;
    }

    public synchronized int targetLevel() {
        return targetLevel;
    }

    public synchronized boolean questsEnabled() {
        return questsEnabled;
    }

    public synchronized int selectedMapId() {
        return selectedMapId;
    }

    public synchronized int selectedAtLevel() {
        return selectedAtLevel;
    }

    public synchronized String selectionReason() {
        return selectionReason;
    }

    public synchronized long nextSelectionAtMs() {
        return nextSelectionAtMs;
    }

    public synchronized void selected(int mapId, int level, String reason, long nowMs) {
        selectedMapId = mapId;
        selectedAtLevel = level;
        selectionReason = reason == null ? "" : reason;
        nextSelectionAtMs = nowMs + NEXT_SELECTION_DELAY_MS;
    }

    public synchronized void retrySelectionAt(long timeMs) {
        selectedMapId = 0;
        nextSelectionAtMs = timeMs;
    }

    public synchronized void markUnavailable(int mapId, long untilMs) {
        unavailableMapsUntilMs.put(mapId, untilMs);
        selectedMapId = 0;
        nextSelectionAtMs = 0L;
    }

    public synchronized boolean available(int mapId, long nowMs) {
        unavailableMapsUntilMs.entrySet().removeIf(entry -> entry.getValue() <= nowMs);
        return !unavailableMapsUntilMs.containsKey(mapId);
    }

    public synchronized boolean markEvidenceLevelIfChanged(int level) {
        if (lastEvidenceLevel == level) {
            return false;
        }
        lastEvidenceLevel = level;
        return true;
    }
}
