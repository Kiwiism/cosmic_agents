package server.agents.economy.scenario;

import server.agents.economy.activity.FarmSessionOutcome;
import server.agents.economy.activity.FarmSessionPlan;
import server.agents.economy.activity.RuleExactFarmResolver;
import server.agents.economy.clock.ScheduledEconomyEvent;
import server.agents.economy.persistence.EconomyLifecycleJournal;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Application state machine connecting logical time to replaceable real-world command adapters. */
public final class EconomyRunCoordinator {
    public static final String MARKET_CYCLE = "MARKET_CYCLE";
    public static final String START_ACTIVITY = "START_ACTIVITY";
    public static final String COMPLETE_ACTIVITY = "COMPLETE_ACTIVITY";
    public static final String RETURN_TO_FM = "RETURN_TO_FM";

    private final SimulationRunEngine engine;
    private final EconomyWorldPort world;
    private final RuleExactFarmResolver resolver;
    private final EconomyLifecycleJournal journal;
    private final Map<String, AgentState> agents = new LinkedHashMap<>();

    public EconomyRunCoordinator(SimulationRunEngine engine, EconomyWorldPort world,
                                 RuleExactFarmResolver resolver, EconomyLifecycleJournal journal) {
        this.engine = Objects.requireNonNull(engine);
        this.world = Objects.requireNonNull(world);
        this.resolver = Objects.requireNonNull(resolver);
        this.journal = Objects.requireNonNull(journal);
    }

    public void handle(ScheduledEconomyEvent event) {
        switch (event.kind()) {
            case SimulationRunEngine.ADMIT_AGENT -> admit(event);
            case MARKET_CYCLE -> market(event);
            case START_ACTIVITY -> startActivity(event);
            case COMPLETE_ACTIVITY -> completeActivity(event);
            case RETURN_TO_FM -> returnToMarket(event);
            case SimulationRunEngine.CHECKPOINT -> { }
            default -> throw new IllegalStateException("Unknown economy event kind: " + event.kind());
        }
    }

    private void admit(ScheduledEconomyEvent event) {
        EconomyAgentProfile profile = profile(event);
        if (agents.putIfAbsent(profile.agentId(), new AgentState(profile, Status.IN_FREE_MARKET, null)) != null)
            throw new IllegalStateException("agent admitted twice: " + profile.agentId());
        world.admit(profile, event.dueAt());
        recordPresence(profile, "ADMITTED", event.dueAt());
        journal.admitted(engine.runId(), profile, event.dueAt());
        journal.stateChanged(engine.runId(), profile.agentId(), Status.IN_FREE_MARKET, null, event.dueAt());
        engine.schedule(event.dueAt(), MARKET_CYCLE, profile.agentId(), Map.of());
    }

    private void market(ScheduledEconomyEvent event) {
        AgentState state = require(event.subjectId(), Status.IN_FREE_MARKET);
        EconomyWorldPort.MarketDirective directive = world.performMarketCycle(state.profile, event.dueAt());
        recordPresence(state.profile, "MARKET_CYCLE", event.dueAt());
        directive.startActivityAt().ifPresent(at -> engine.schedule(at, START_ACTIVITY,
                event.subjectId(), Map.of()));
        directive.revisitMarketAt().ifPresent(at -> engine.schedule(at, MARKET_CYCLE,
                event.subjectId(), Map.of()));
        if (directive.externalActionPending())
            engine.pauseAfterCurrentEvent("physical market capability pending for " + event.subjectId());
    }

    private void startActivity(ScheduledEconomyEvent event) {
        AgentState state = require(event.subjectId(), Status.IN_FREE_MARKET);
        FarmSessionPlan plan = world.planOffscreenActivity(state.profile, event.dueAt());
        if (!plan.agentId().equals(event.subjectId()) || !plan.startedAt().equals(event.dueAt()))
            throw new IllegalStateException("activity plan is not for the scheduled agent/time");
        if ("explicit-work".equals(plan.calibrationId()))
            throw new IllegalStateException("production runs require live calibration evidence");
        world.leaveFreeMarket(state.profile, plan, event.dueAt());
        recordPresence(state.profile, "OFFSCREEN_ACTIVITY_STARTED", event.dueAt());
        journal.activityStarted(engine.runId(), plan);
        journal.stateChanged(engine.runId(), event.subjectId(), Status.OFFSCREEN_ACTIVITY,
                plan.sessionId(), event.dueAt());
        agents.put(event.subjectId(), new AgentState(state.profile, Status.OFFSCREEN_ACTIVITY, plan));
        engine.schedule(plan.startedAt().plus(plan.duration()), COMPLETE_ACTIVITY,
                event.subjectId(), Map.of("sessionId", plan.sessionId()));
    }

    private void completeActivity(ScheduledEconomyEvent event) {
        AgentState state = require(event.subjectId(), Status.OFFSCREEN_ACTIVITY);
        if (!state.pendingActivity.sessionId().equals(event.parameters().get("sessionId")))
            throw new IllegalStateException("activity completion does not match pending session");
        FarmSessionOutcome outcome = resolver.resolve(state.pendingActivity, engine.randomStreams());
        world.settleOffscreenActivity(state.profile, outcome, event.dueAt(),
                engine.randomStreams().stream("agent." + event.subjectId() + ".progression")::nextLong);
        journal.activityCompleted(engine.runId(), outcome);
        journal.stateChanged(engine.runId(), event.subjectId(), Status.RETURNING_TO_FM,
                null, event.dueAt());
        agents.put(event.subjectId(), new AgentState(state.profile, Status.RETURNING_TO_FM, null));
        engine.schedule(event.dueAt(), RETURN_TO_FM, event.subjectId(), Map.of());
    }

    private void returnToMarket(ScheduledEconomyEvent event) {
        AgentState state = require(event.subjectId(), Status.RETURNING_TO_FM);
        world.returnThroughFreeMarketEntrance(state.profile, event.dueAt());
        recordPresence(state.profile, "RETURNED_TO_FREE_MARKET", event.dueAt());
        journal.stateChanged(engine.runId(), event.subjectId(), Status.IN_FREE_MARKET, null, event.dueAt());
        agents.put(event.subjectId(), new AgentState(state.profile, Status.IN_FREE_MARKET, null));
        engine.schedule(event.dueAt(), MARKET_CYCLE, event.subjectId(), Map.of());
    }

    private AgentState require(String agentId, Status expected) {
        AgentState state = agents.get(agentId);
        if (state == null || state.status != expected)
            throw new IllegalStateException(agentId + " must be " + expected);
        return state;
    }

    private void recordPresence(EconomyAgentProfile profile, String reason, Instant at) {
        world.currentPresence(profile).ifPresent(value ->
                journal.presence(engine.runId(), profile.agentId(), value, reason, at));
    }

    private static EconomyAgentProfile profile(ScheduledEconomyEvent event) {
        Map<String, String> p = event.parameters();
        return new EconomyAgentProfile(event.subjectId(), p.get("jobFamily"),
                number(p, "dailyActivityFraction"), number(p, "riskTolerance"),
                number(p, "liquidityPreference"), number(p, "upgradeAggressiveness"),
                number(p, "shoppingPatience"), number(p, "stallWillingness"),
                Integer.parseInt(p.get("priceMemoryHours")), number(p, "negotiationAggressiveness"),
                number(p, "chairInterest"));
    }

    private static double number(Map<String, String> parameters, String key) {
        return Double.parseDouble(parameters.get(key));
    }

    public Map<String, AgentView> agentViews() {
        Map<String, AgentView> result = new LinkedHashMap<>();
        agents.forEach((id, state) -> result.put(id, new AgentView(state.profile, state.status,
                state.pendingActivity == null ? null : state.pendingActivity.sessionId())));
        return Map.copyOf(result);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> agentState = new LinkedHashMap<>();
        agents.forEach((id, state) -> agentState.put(id, Map.of(
                "profile", profileMap(state.profile),
                "status", state.status.name(),
                "pendingActivity", state.pendingActivity == null ? Map.of() : planMap(state.pendingActivity))));
        return Map.of("schemaVersion", 2, "agents", Map.copyOf(agentState),
                "world", world.snapshotState());
    }

    @SuppressWarnings("unchecked")
    public void restore(Map<String, Object> snapshot) {
        if (!agents.isEmpty()) throw new IllegalStateException("coordinator state is already initialized");
        Map<String, Object> agentState;
        Map<String, Object> worldState;
        if (snapshot.containsKey("schemaVersion")) {
            if (integer(snapshot, "schemaVersion") != 2)
                throw new IllegalStateException("unsupported coordinator checkpoint schema");
            agentState = (Map<String, Object>) snapshot.get("agents");
            worldState = (Map<String, Object>) snapshot.get("world");
        } else {
            agentState = snapshot; // compatibility with checkpoints written before world state existed
            worldState = Map.of();
        }
        agentState.forEach((id, value) -> {
            Map<String, Object> row = (Map<String, Object>) value;
            EconomyAgentProfile profile = profileFrom((Map<String, Object>) row.get("profile"));
            Status status = Status.valueOf(row.get("status").toString());
            Map<String, Object> pending = (Map<String, Object>) row.get("pendingActivity");
            FarmSessionPlan plan = pending == null || pending.isEmpty() ? null : planFrom(pending);
            if ((status == Status.OFFSCREEN_ACTIVITY) != (plan != null))
                throw new IllegalStateException("checkpoint activity state is inconsistent for " + id);
            agents.put(id, new AgentState(profile, status, plan));
        });
        world.restoreState(worldState == null ? Map.of() : worldState);
    }

    private static Map<String, Object> profileMap(EconomyAgentProfile p) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("agentId", p.agentId()); value.put("jobFamily", p.jobFamily());
        value.put("dailyActivityFraction", p.dailyActivityFraction());
        value.put("riskTolerance", p.riskTolerance()); value.put("liquidityPreference", p.liquidityPreference());
        value.put("upgradeAggressiveness", p.upgradeAggressiveness());
        value.put("shoppingPatience", p.shoppingPatience()); value.put("stallWillingness", p.stallWillingness());
        value.put("priceMemoryHours", p.priceMemoryHours());
        value.put("negotiationAggressiveness", p.negotiationAggressiveness());
        value.put("chairInterest", p.chairInterest());
        return value;
    }

    private static EconomyAgentProfile profileFrom(Map<String, Object> p) {
        return new EconomyAgentProfile(text(p, "agentId"), text(p, "jobFamily"), decimal(p, "dailyActivityFraction"),
                decimal(p, "riskTolerance"), decimal(p, "liquidityPreference"),
                decimal(p, "upgradeAggressiveness"), decimal(p, "shoppingPatience"),
                decimal(p, "stallWillingness"), integer(p, "priceMemoryHours"),
                decimal(p, "negotiationAggressiveness"), decimal(p, "chairInterest"));
    }

    private static Map<String, Object> planMap(FarmSessionPlan p) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("sessionId", p.sessionId()); value.put("calibrationId", p.calibrationId());
        value.put("agentId", p.agentId()); value.put("mapId", p.mapId());
        value.put("startedAt", p.startedAt().toString()); value.put("duration", p.duration().toString());
        value.put("dropRateMultiplier", p.dropRateMultiplier());
        value.put("monsters", p.monsters().stream().map(work -> Map.of("monsterId", work.monsterId(),
                "kills", work.kills(), "experiencePerKill", work.experiencePerKill())).toList());
        value.put("activeQuestIds", new ArrayList<>(p.activeQuestIds()));
        value.put("consumedItems", p.consumedItems().stream().map(item -> Map.of("itemId", item.itemId(),
                "quantity", item.quantity(), "lotId", item.lotId())).toList());
        return value;
    }

    @SuppressWarnings("unchecked")
    private static FarmSessionPlan planFrom(Map<String, Object> p) {
        List<FarmSessionPlan.MonsterWork> monsters = ((List<Map<String, Object>>) p.get("monsters")).stream()
                .map(row -> new FarmSessionPlan.MonsterWork(integer(row, "monsterId"), integer(row, "kills"),
                        integer(row, "experiencePerKill"))).toList();
        Set<Integer> quests = new HashSet<>(((List<Number>) p.get("activeQuestIds")).stream()
                .map(Number::intValue).toList());
        List<FarmSessionPlan.ItemConsumption> consumed = ((List<Map<String, Object>>) p.get("consumedItems"))
                .stream().map(row -> new FarmSessionPlan.ItemConsumption(integer(row, "itemId"),
                        integer(row, "quantity"), text(row, "lotId"))).toList();
        return new FarmSessionPlan(text(p, "sessionId"), text(p, "calibrationId"), text(p, "agentId"),
                integer(p, "mapId"), Instant.parse(text(p, "startedAt")), Duration.parse(text(p, "duration")),
                integer(p, "dropRateMultiplier"), monsters, quests, consumed);
    }

    private static String text(Map<String, Object> values, String key) { return values.get(key).toString(); }
    private static int integer(Map<String, Object> values, String key) { return ((Number) values.get(key)).intValue(); }
    private static double decimal(Map<String, Object> values, String key) { return ((Number) values.get(key)).doubleValue(); }

    public enum Status { IN_FREE_MARKET, OFFSCREEN_ACTIVITY, RETURNING_TO_FM }
    public record AgentView(EconomyAgentProfile profile, Status status, String pendingSessionId) { }
    private record AgentState(EconomyAgentProfile profile, Status status,
                              FarmSessionPlan pendingActivity) { }
}
