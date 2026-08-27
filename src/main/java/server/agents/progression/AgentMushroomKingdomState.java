package server.agents.progression;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.awt.Point;
import java.util.List;

/** Resumable live state for one Mushroom Kingdom visit. */
public final class AgentMushroomKingdomState implements AgentMushroomKingdomYetiLobbyState {
    public enum Phase { ACTIVE, COMPLETE, BLOCKED }

    public static final AgentCapabilityStateKey<AgentMushroomKingdomState> STATE_KEY =
            new AgentCapabilityStateKey<>("progression.mushroom-kingdom",
                    AgentMushroomKingdomState.class, AgentMushroomKingdomState::new);

    private Phase phase = Phase.ACTIVE;
    private String reason = "";
    private int currentQuestId;
    private int observedMetric = -1;
    private int observedMapId;
    private Point observedPosition;
    private long progressAtMs;
    private long nextActionAtMs;
    private int capabilityFailures;
    private int huntMapQuestId;
    private int selectedHuntMapId;
    private long yetiLobbyVisitStartedAtMs;
    private long yetiHumanInviteSentAtMs;
    private List<Integer> yetiHumanInviteeIds = List.of();
    private boolean yetiMatchmakingComplete;
    private long yetiBossDefeatedAtMs;

    public synchronized void begin(long nowMs) {
        phase = Phase.ACTIVE;
        reason = "starting Mushroom Kingdom questline";
        currentQuestId = 0;
        observedMetric = -1;
        observedMapId = 0;
        observedPosition = null;
        progressAtMs = nowMs;
        nextActionAtMs = 0L;
        capabilityFailures = 0;
        huntMapQuestId = 0;
        selectedHuntMapId = 0;
        clearYetiLobbyVisit();
        yetiBossDefeatedAtMs = 0L;
    }

    public synchronized void observe(int questId, int metric, int mapId, Point position, long nowMs) {
        Point safe = position == null ? null : new Point(position);
        boolean moved = observedPosition == null || safe == null
                || observedPosition.distanceSq(safe) >= 24L * 24L;
        boolean questChanged = currentQuestId != questId;
        if (questChanged || metric != observedMetric || observedMapId != mapId || moved) {
            progressAtMs = nowMs;
            capabilityFailures = 0;
        }
        if (questChanged) {
            huntMapQuestId = 0;
            selectedHuntMapId = 0;
            clearYetiLobbyVisit();
            yetiBossDefeatedAtMs = 0L;
        }
        currentQuestId = questId;
        observedMetric = metric;
        observedMapId = mapId;
        observedPosition = safe;
    }

    public synchronized void active(String reason) {
        phase = Phase.ACTIVE;
        this.reason = reason == null ? "" : reason;
    }

    public synchronized void complete(String reason) {
        phase = Phase.COMPLETE;
        this.reason = reason == null ? "" : reason;
    }

    public synchronized void block(String reason) {
        phase = Phase.BLOCKED;
        this.reason = reason == null ? "" : reason;
    }

    public synchronized Phase phase() { return phase; }
    public synchronized String reason() { return reason; }
    public synchronized int currentQuestId() { return currentQuestId; }
    public synchronized long progressAtMs() { return progressAtMs; }
    public synchronized long nextActionAtMs() { return nextActionAtMs; }
    public synchronized void nextActionAtMs(long value) { nextActionAtMs = value; }
    public synchronized int capabilityFailure() { return ++capabilityFailures; }
    public synchronized void capabilityProgress() { capabilityFailures = 0; }

    public synchronized int selectedHuntMap(int questId) {
        return huntMapQuestId == questId ? selectedHuntMapId : 0;
    }

    public synchronized int selectedHuntMap() { return selectedHuntMapId; }

    public synchronized void selectHuntMap(int questId, int mapId) {
        huntMapQuestId = questId;
        selectedHuntMapId = mapId;
    }

    public synchronized void clearHuntMap() {
        huntMapQuestId = 0;
        selectedHuntMapId = 0;
    }

    public synchronized void beginYetiLobbyVisit(long nowMs) {
        if (yetiLobbyVisitStartedAtMs == 0L) yetiLobbyVisitStartedAtMs = nowMs;
    }

    public synchronized boolean yetiAgentScanExpired(long nowMs, long scanMs) {
        return yetiLobbyVisitStartedAtMs > 0L
                && nowMs - yetiLobbyVisitStartedAtMs >= scanMs;
    }

    public synchronized void markYetiHumanInvites(List<Integer> inviteeIds, long nowMs) {
        yetiHumanInviteeIds = inviteeIds == null ? List.of() : List.copyOf(inviteeIds);
        yetiHumanInviteSentAtMs = yetiHumanInviteeIds.isEmpty() ? 0L : nowMs;
    }

    public synchronized List<Integer> yetiHumanInviteeIds() {
        return yetiHumanInviteeIds;
    }

    public synchronized boolean yetiHumanInviteResponseExpired(long nowMs, long responseMs) {
        return yetiHumanInviteSentAtMs > 0L
                && nowMs - yetiHumanInviteSentAtMs >= responseMs;
    }

    public synchronized void clearYetiHumanInvites() {
        yetiHumanInviteSentAtMs = 0L;
        yetiHumanInviteeIds = List.of();
    }

    public synchronized boolean yetiMatchmakingComplete() {
        return yetiMatchmakingComplete;
    }

    public synchronized void completeYetiMatchmaking() {
        yetiMatchmakingComplete = true;
    }

    public synchronized void restartYetiLobbyVisit(long nowMs) {
        clearYetiLobbyVisit();
        beginYetiLobbyVisit(nowMs);
    }

    public synchronized void clearYetiLobbyVisit() {
        yetiLobbyVisitStartedAtMs = 0L;
        yetiMatchmakingComplete = false;
        clearYetiHumanInvites();
    }

    public synchronized void beginYetiLootGrace(long nowMs) {
        if (yetiBossDefeatedAtMs == 0L) yetiBossDefeatedAtMs = nowMs;
    }

    public synchronized boolean yetiLootGraceExpired(long nowMs, long graceMs) {
        return yetiBossDefeatedAtMs > 0L && nowMs - yetiBossDefeatedAtMs >= graceMs;
    }

    public synchronized void clearYetiLootGrace() {
        yetiBossDefeatedAtMs = 0L;
    }
}
