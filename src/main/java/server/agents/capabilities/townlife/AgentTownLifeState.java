package server.agents.capabilities.townlife;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.awt.Point;

public final class AgentTownLifeState {
    private static final int MIN_INITIAL_RESPONSE_DELAY_MS = config.AgentTuning.intValue("server.agents.capabilities.townlife.AgentTownLifeState.MIN_INITIAL_RESPONSE_DELAY_MS");
    private static final int MAX_INITIAL_RESPONSE_DELAY_MS = config.AgentTuning.intValue("server.agents.capabilities.townlife.AgentTownLifeState.MAX_INITIAL_RESPONSE_DELAY_MS");
    public static final AgentCapabilityStateKey<AgentTownLifeState> STATE_KEY =
            new AgentCapabilityStateKey<>(
                    "town-life", AgentTownLifeState.class, AgentTownLifeState::new);

    public enum Stage {
        DISABLED,
        TRAVEL_TO_TOWN,
        COMPLETE_ARRIVAL,
        SETTLING,
        CHOOSE_ACTIVITY,
        MOVE_TO_ACTIVITY,
        DWELL,
        VISIT_SHOP,
        RETURN_FROM_SHOP
    }

    public enum VisitPhase {
        ARRIVING,
        ERRAND,
        FREE_TIME,
        DEPARTING
    }

    public enum Activity {
        NONE,
        REST,
        SOCIAL,
        NPC_PAUSE,
        ROAM,
        SHOP_VISIT,
        WEAPON_FLOURISH
    }

    public enum Role {
        STATIONED,
        WANDERER
    }

    public enum District {
        UPPER,
        MIDDLE,
        LOWER,
        ANY
    }

    public enum PlatformKind {
        MAIN,
        MINI,
        ANY
    }

    private boolean enabled;
    private int townMapId;
    private Stage stage = Stage.DISABLED;
    private Activity activity = Activity.NONE;
    private Point target;
    private int targetCharacterId;
    private int destinationMapId;
    private long nextActionAtMs;
    private int sequence;
    private boolean expressionShown;
    private boolean flourishShown;
    private Role role = Role.WANDERER;
    private District homeDistrict = District.ANY;
    private PlatformKind platformPreference = PlatformKind.ANY;
    private boolean initialPlacementComplete;
    private long roleUntilMs;
    private String destinationKey;
    private String venueId = "";
    private String decisionSource = "default-policy";
    private String decisionCorrelationId = "";
    private VisitPhase visitPhase = VisitPhase.ARRIVING;
    private AgentTownLifeFidelity fidelity = AgentTownLifeFidelity.PRESENTATION;
    private AgentTownLifeVisitRequest.Purpose visitPurpose =
            AgentTownLifeVisitRequest.Purpose.LEISURE;
    private String visitReason = "";
    private long freeTimeBudgetMs;
    private long freeTimeUntilMs;
    private final AgentTownLifeMemory memory = new AgentTownLifeMemory();
    private final AgentTownLifeProgressWatchdog progressWatchdog = new AgentTownLifeProgressWatchdog();

    public synchronized void start(long nowMs, int initialSequence, int selectedTownMapId) {
        start(nowMs, initialSequence, AgentTownLifeVisitRequest.leisure(selectedTownMapId));
    }

    public synchronized void start(long nowMs,
                                   int initialSequence,
                                   AgentTownLifeVisitRequest request) {
        int selectedTownMapId = request.townMapId();
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(selectedTownMapId);
        enabled = true;
        townMapId = selectedTownMapId;
        stage = Stage.TRAVEL_TO_TOWN;
        activity = Activity.NONE;
        target = null;
        targetCharacterId = 0;
        destinationMapId = 0;
        nextActionAtMs = nowMs + initialResponseDelayMs(initialSequence);
        sequence = Math.max(0, initialSequence);
        expressionShown = false;
        flourishShown = false;
        role = Role.WANDERER;
        homeDistrict = AgentTownLifeDistributionPolicy.homeDistrict(
                initialSequence, profile.distribution());
        platformPreference = AgentTownLifeDistributionPolicy.platformPreference(
                initialSequence, profile.distribution());
        initialPlacementComplete = false;
        roleUntilMs = 0L;
        destinationKey = null;
        venueId = "";
        decisionSource = "default-policy";
        decisionCorrelationId = "";
        visitPhase = VisitPhase.ARRIVING;
        fidelity = AgentTownLifeFidelity.PRESENTATION;
        visitPurpose = request.purpose();
        visitReason = request.reason();
        freeTimeBudgetMs = request.freeTimeBudgetMs();
        freeTimeUntilMs = 0L;
        memory.clearVisit();
        progressWatchdog.clear();
    }

    static long initialResponseDelayMs(int characterId) {
        int mixed = characterId;
        mixed ^= mixed >>> 16;
        mixed *= 0x7feb352d;
        mixed ^= mixed >>> 15;
        mixed *= 0x846ca68b;
        mixed ^= mixed >>> 16;
        int range = MAX_INITIAL_RESPONSE_DELAY_MS - MIN_INITIAL_RESPONSE_DELAY_MS + 1;
        return MIN_INITIAL_RESPONSE_DELAY_MS + Math.floorMod(mixed, range);
    }

    public synchronized void stop() {
        enabled = false;
        stage = Stage.DISABLED;
        activity = Activity.NONE;
        target = null;
        targetCharacterId = 0;
        destinationMapId = 0;
        nextActionAtMs = 0L;
        expressionShown = false;
        flourishShown = false;
        roleUntilMs = 0L;
        destinationKey = null;
        venueId = "";
        decisionSource = "default-policy";
        decisionCorrelationId = "";
        visitPhase = VisitPhase.DEPARTING;
        freeTimeUntilMs = 0L;
        memory.clearVisit();
        progressWatchdog.clear();
    }

    public synchronized boolean enabled() {
        return enabled;
    }

    public synchronized int townMapId() {
        return townMapId;
    }

    public synchronized Stage stage() {
        return stage;
    }

    public synchronized Activity activity() {
        return activity;
    }

    public synchronized Point target() {
        return target == null ? null : new Point(target);
    }

    public synchronized int targetCharacterId() {
        return targetCharacterId;
    }

    public synchronized int destinationMapId() {
        return destinationMapId;
    }

    public synchronized long nextActionAtMs() {
        return nextActionAtMs;
    }

    public synchronized int sequence() {
        return sequence;
    }

    public synchronized boolean expressionShown() {
        return expressionShown;
    }

    public synchronized boolean flourishShown() {
        return flourishShown;
    }

    public synchronized Role role() {
        return role;
    }

    public synchronized long roleUntilMs() {
        return roleUntilMs;
    }

    public synchronized District preferredDistrict() {
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(townMapId);
        return AgentTownLifeDistributionPolicy.allowsCrossDistrictVisit(
                sequence, profile.distribution())
                ? District.ANY : homeDistrict;
    }

    public synchronized District homeDistrict() {
        return homeDistrict;
    }

    public synchronized PlatformKind platformPreference() {
        return platformPreference;
    }

    public synchronized boolean initialPlacementComplete() {
        return initialPlacementComplete;
    }

    public synchronized void markInitialPlacementComplete() {
        initialPlacementComplete = true;
    }

    public synchronized String destinationKey() {
        return destinationKey;
    }

    public synchronized String venueId() {
        return venueId;
    }

    public synchronized String decisionSource() {
        return decisionSource;
    }

    public synchronized String decisionCorrelationId() {
        return decisionCorrelationId;
    }

    public synchronized VisitPhase visitPhase() {
        return visitPhase;
    }

    public synchronized AgentTownLifeFidelity fidelity() {
        return fidelity;
    }

    public synchronized AgentTownLifeVisitRequest.Purpose visitPurpose() {
        return visitPurpose;
    }

    public synchronized String visitReason() {
        return visitReason;
    }

    public synchronized boolean freeTimeExpired(long nowMs) {
        return visitPhase == VisitPhase.FREE_TIME
                && freeTimeUntilMs > 0L && nowMs >= freeTimeUntilMs;
    }

    AgentTownLifeMemory memory() {
        return memory;
    }

    AgentTownLifeProgressWatchdog progressWatchdog() {
        return progressWatchdog;
    }

    public synchronized void assignRole(Role nextRole, long untilMs) {
        role = nextRole == null ? Role.WANDERER : nextRole;
        roleUntilMs = untilMs;
    }

    public synchronized void transition(Stage nextStage, long dueAtMs) {
        stage = nextStage;
        nextActionAtMs = dueAtMs;
        if (nextStage == Stage.COMPLETE_ARRIVAL) {
            visitPhase = VisitPhase.ERRAND;
        } else if (nextStage == Stage.SETTLING || nextStage == Stage.CHOOSE_ACTIVITY) {
            if (visitPhase != VisitPhase.FREE_TIME && freeTimeBudgetMs > 0L) {
                freeTimeUntilMs = dueAtMs + freeTimeBudgetMs;
            }
            visitPhase = VisitPhase.FREE_TIME;
        }
    }

    public synchronized boolean updateFidelity(AgentTownLifeFidelity nextFidelity) {
        AgentTownLifeFidelity normalized = nextFidelity == null
                ? AgentTownLifeFidelity.PRESENTATION : nextFidelity;
        if (fidelity == normalized) {
            return false;
        }
        fidelity = normalized;
        return true;
    }

    public synchronized void select(Activity nextActivity,
                                    Point nextTarget,
                                    int nextTargetCharacterId,
                                    int nextDestinationMapId,
                                    long dueAtMs) {
        select(nextActivity, nextTarget, nextTargetCharacterId, nextDestinationMapId, null, dueAtMs);
    }

    public synchronized void select(Activity nextActivity,
                                    Point nextTarget,
                                    int nextTargetCharacterId,
                                    int nextDestinationMapId,
                                    String nextDestinationKey,
                                    long dueAtMs) {
        select(nextActivity, nextTarget, nextTargetCharacterId, nextDestinationMapId,
                nextDestinationKey, "", "default-policy", "", dueAtMs);
    }

    public synchronized void select(Activity nextActivity,
                                    Point nextTarget,
                                    int nextTargetCharacterId,
                                    int nextDestinationMapId,
                                    String nextDestinationKey,
                                    String nextVenueId,
                                    String nextDecisionSource,
                                    String nextDecisionCorrelationId,
                                    long dueAtMs) {
        activity = nextActivity;
        target = nextTarget == null ? null : new Point(nextTarget);
        targetCharacterId = nextTargetCharacterId;
        destinationMapId = nextDestinationMapId;
        stage = nextActivity == Activity.SHOP_VISIT ? Stage.VISIT_SHOP : Stage.MOVE_TO_ACTIVITY;
        nextActionAtMs = dueAtMs;
        expressionShown = false;
        flourishShown = false;
        destinationKey = nextDestinationKey;
        venueId = nextVenueId == null ? "" : nextVenueId;
        decisionSource = nextDecisionSource == null || nextDecisionSource.isBlank()
                ? "default-policy" : nextDecisionSource;
        decisionCorrelationId = nextDecisionCorrelationId == null ? "" : nextDecisionCorrelationId;
        progressWatchdog.begin(target, dueAtMs);
        sequence++;
    }

    synchronized void retarget(Point nextTarget,
                               int nextTargetCharacterId,
                               String nextDestinationKey,
                               String nextVenueId) {
        target = nextTarget == null ? null : new Point(nextTarget);
        targetCharacterId = nextTargetCharacterId;
        destinationKey = nextDestinationKey;
        venueId = nextVenueId == null ? "" : nextVenueId;
        stage = Stage.MOVE_TO_ACTIVITY;
        progressWatchdog.begin(target, nextActionAtMs);
    }

    public synchronized void beginDwell(long untilMs) {
        stage = Stage.DWELL;
        nextActionAtMs = untilMs;
        expressionShown = false;
        flourishShown = false;
    }

    public synchronized void markExpressionShown() {
        expressionShown = true;
    }

    public synchronized void markFlourishShown() {
        flourishShown = true;
    }
}
