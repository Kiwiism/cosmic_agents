package server.agents.progression;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.List;

/** Session-local cursor for one bounded Yeti or Pepe-scroll authored plan. */
public final class AgentMushroomKingdomPostStoryState
        implements AgentMushroomKingdomYetiLobbyState {
    public enum Activity { NONE, YETI_FARM, PEPE_SCROLL }
    public enum Phase { ACTIVE, COMPLETE, BLOCKED }

    public static final AgentCapabilityStateKey<AgentMushroomKingdomPostStoryState> STATE_KEY =
            new AgentCapabilityStateKey<>("progression.mushroom-kingdom-post-story",
                    AgentMushroomKingdomPostStoryState.class,
                    AgentMushroomKingdomPostStoryState::new);

    private Activity activity = Activity.NONE;
    private Phase phase = Phase.ACTIVE;
    private String reason = "";
    private long progressAtMs;
    private long nextActionAtMs;
    private int capabilityFailures;
    private int observedMapId;
    private int observedMetric = -1;
    private boolean yetiBossSeen;
    private long yetiBossDefeatedAtMs;
    private boolean yetiRunCounted;
    private boolean stopAfterYetiExit;
    private int scrollMobId;
    private int scrollMapId;
    private long yetiLobbyVisitStartedAtMs;
    private long yetiHumanInviteSentAtMs;
    private List<Integer> yetiHumanInviteeIds = List.of();
    private boolean yetiMatchmakingComplete;

    public synchronized void begin(Activity activity, long nowMs) {
        this.activity = activity;
        phase = Phase.ACTIVE;
        reason = activity == Activity.YETI_FARM
                ? "starting bounded Yeti farming" : "starting one King Pepe's Scroll quest";
        progressAtMs = nowMs;
        nextActionAtMs = 0L;
        capabilityFailures = 0;
        observedMapId = 0;
        observedMetric = -1;
        resetYetiRun();
        scrollMobId = 0;
        scrollMapId = 0;
        clearYetiLobbyVisit();
    }

    public synchronized void observe(int mapId, int metric, long nowMs) {
        if (observedMapId != mapId || observedMetric != metric) {
            progressAtMs = nowMs;
            capabilityFailures = 0;
        }
        observedMapId = mapId;
        observedMetric = metric;
    }

    public synchronized void active(String value) { phase = Phase.ACTIVE; reason = text(value); }
    public synchronized void complete(String value) { phase = Phase.COMPLETE; reason = text(value); }
    public synchronized void block(String value) { phase = Phase.BLOCKED; reason = text(value); }
    public synchronized Activity activity() { return activity; }
    public synchronized Phase phase() { return phase; }
    public synchronized String reason() { return reason; }
    public synchronized long progressAtMs() { return progressAtMs; }
    public synchronized long nextActionAtMs() { return nextActionAtMs; }
    public synchronized void nextActionAtMs(long value) { nextActionAtMs = Math.max(0L, value); }
    public synchronized int capabilityFailure() { return ++capabilityFailures; }
    public synchronized void capabilityProgress(long nowMs) {
        capabilityFailures = 0;
        progressAtMs = nowMs;
    }

    public synchronized void sawYetiBoss() { yetiBossSeen = true; }
    public synchronized boolean yetiBossSeen() { return yetiBossSeen; }
    public synchronized void beginYetiLootGrace(long nowMs) {
        if (yetiBossDefeatedAtMs == 0L) yetiBossDefeatedAtMs = nowMs;
    }
    public synchronized boolean yetiLootGraceExpired(long nowMs, long graceMs) {
        return yetiBossDefeatedAtMs > 0L && nowMs - yetiBossDefeatedAtMs >= graceMs;
    }
    public synchronized boolean yetiRunCounted() { return yetiRunCounted; }
    public synchronized void countYetiRun(boolean stopAfterExit) {
        yetiRunCounted = true;
        stopAfterYetiExit = stopAfterExit;
    }
    public synchronized boolean stopAfterYetiExit() { return stopAfterYetiExit; }
    public synchronized void resetYetiRun() {
        yetiBossSeen = false;
        yetiBossDefeatedAtMs = 0L;
        yetiRunCounted = false;
        stopAfterYetiExit = false;
    }
    public synchronized int scrollMap(int mobId) {
        return scrollMobId == mobId ? scrollMapId : 0;
    }
    public synchronized void selectScrollMap(int mobId, int mapId) {
        scrollMobId = mobId;
        scrollMapId = mapId;
    }
    public synchronized void clearScrollMap() { scrollMobId = 0; scrollMapId = 0; }

    @Override public synchronized void beginYetiLobbyVisit(long nowMs) {
        if (yetiLobbyVisitStartedAtMs == 0L) yetiLobbyVisitStartedAtMs = nowMs;
    }
    @Override public synchronized boolean yetiAgentScanExpired(long nowMs, long scanMs) {
        return yetiLobbyVisitStartedAtMs > 0L && nowMs - yetiLobbyVisitStartedAtMs >= scanMs;
    }
    @Override public synchronized void markYetiHumanInvites(List<Integer> ids, long nowMs) {
        yetiHumanInviteeIds = ids == null ? List.of() : List.copyOf(ids);
        yetiHumanInviteSentAtMs = yetiHumanInviteeIds.isEmpty() ? 0L : nowMs;
    }
    @Override public synchronized List<Integer> yetiHumanInviteeIds() {
        return yetiHumanInviteeIds;
    }
    @Override public synchronized boolean yetiHumanInviteResponseExpired(long nowMs, long responseMs) {
        return yetiHumanInviteSentAtMs > 0L && nowMs - yetiHumanInviteSentAtMs >= responseMs;
    }
    @Override public synchronized void clearYetiHumanInvites() {
        yetiHumanInviteSentAtMs = 0L;
        yetiHumanInviteeIds = List.of();
    }
    @Override public synchronized boolean yetiMatchmakingComplete() { return yetiMatchmakingComplete; }
    @Override public synchronized void completeYetiMatchmaking() { yetiMatchmakingComplete = true; }
    @Override public synchronized void restartYetiLobbyVisit(long nowMs) {
        clearYetiLobbyVisit();
        beginYetiLobbyVisit(nowMs);
    }
    @Override public synchronized void clearYetiLobbyVisit() {
        yetiLobbyVisitStartedAtMs = 0L;
        yetiMatchmakingComplete = false;
        clearYetiHumanInvites();
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
}
