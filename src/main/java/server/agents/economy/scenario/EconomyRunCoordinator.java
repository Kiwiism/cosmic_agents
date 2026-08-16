package server.agents.economy.scenario;

import server.agents.economy.activity.FarmSessionOutcome;
import server.agents.economy.activity.FarmSessionPlan;
import server.agents.economy.activity.RuleExactFarmResolver;
import server.agents.economy.clock.ScheduledEconomyEvent;
import server.agents.economy.persistence.EconomyLifecycleJournal;
import server.agents.economy.session.EconomySessionPort;
import server.agents.economy.session.CommerceParticipant;
import server.agents.simulation.activity.ExternalAgentActivityPort;
import server.agents.simulation.activity.RuleExactExternalActivityAdapter;

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
    public static final String ENTER_ECONOMY = "ENTER_ECONOMY";

    private final SimulationRunEngine engine;
    private final EconomySessionPort sessions;
    private final ExternalAgentActivityPort activities;
    private final EconomyLifecycleJournal journal;
    private final Duration maximumSessionDuration;
    private final Duration maximumIdleDuration;
    private final Map<String, AgentState> agents = new LinkedHashMap<>();

    public EconomyRunCoordinator(SimulationRunEngine engine, EconomySessionPort sessions,
                                 ExternalAgentActivityPort activities,
                                 EconomyLifecycleJournal journal) {
        this.engine = Objects.requireNonNull(engine);
        this.sessions = Objects.requireNonNull(sessions);
        this.activities = Objects.requireNonNull(activities);
        this.journal = Objects.requireNonNull(journal);
        this.maximumSessionDuration = Duration.parse(engine.config().session.defaultMaximumDuration);
        this.maximumIdleDuration = Duration.parse(engine.config().session.maximumIdleDuration);
    }

    /** Compatibility constructor for scenario fixtures; production composition passes separate ports. */
    @Deprecated
    public EconomyRunCoordinator(SimulationRunEngine engine, EconomyWorldPort world,
                                 RuleExactFarmResolver resolver, EconomyLifecycleJournal journal) {
        this(engine, world, new RuleExactExternalActivityAdapter(world::planOffscreenActivity,
                resolver, new RuleExactExternalActivityAdapter.Lifecycle() {
            @Override public void begin(CommerceParticipant profile, FarmSessionPlan plan, Instant at) {
                world.leaveFreeMarket(profile, plan, at);
            }
            @Override public FarmSessionOutcome settle(CommerceParticipant profile,
                                                       FarmSessionOutcome outcome, Instant at,
                                                       java.util.function.LongSupplier random) {
                return world.settleOffscreenActivity(profile, outcome, at, random);
            }
            @Override public void returnToEconomyEntrance(CommerceParticipant profile, Instant at) {
                world.returnThroughFreeMarketEntrance(profile, at);
            }
        }), journal);
    }

    public void handle(ScheduledEconomyEvent event) {
        switch (event.kind()) {
            case SimulationRunEngine.ADMIT_AGENT -> admit(event);
            case MARKET_CYCLE -> market(event);
            case START_ACTIVITY -> startActivity(event);
            case COMPLETE_ACTIVITY -> completeActivity(event);
            case RETURN_TO_FM -> returnToMarket(event);
            case ENTER_ECONOMY -> enterEconomy(event);
            case SimulationRunEngine.CHECKPOINT -> { }
            default -> throw new IllegalStateException("Unknown economy event kind: " + event.kind());
        }
    }

    private void admit(ScheduledEconomyEvent event) {
        CommerceParticipant profile = profile(event);
        if (agents.containsKey(profile.agentId()))
            throw new IllegalStateException("agent admitted twice: " + profile.agentId());
        EconomySessionPort.EntryResult entry = requestEntry(profile, event.dueAt());
        if (entry.status() == EconomySessionPort.EntryResult.Status.DEFERRED) {
            engine.schedule(entry.retryAt(), SimulationRunEngine.ADMIT_AGENT,
                    event.subjectId(), event.parameters());
            return;
        }
        if (entry.status() == EconomySessionPort.EntryResult.Status.REJECTED)
            throw new IllegalStateException("economy admission rejected: " + entry.reason());
        agents.put(profile.agentId(), new AgentState(profile, Status.IN_FREE_MARKET,
                null, null, entry.sessionId()));
        recordPresence(profile, "ADMITTED", event.dueAt());
        journal.admitted(engine.runId(), profile, event.dueAt());
        journal.stateChanged(engine.runId(), profile.agentId(), Status.IN_FREE_MARKET, null, event.dueAt());
        engine.schedule(event.dueAt(), MARKET_CYCLE, profile.agentId(), Map.of());
    }

    private void market(ScheduledEconomyEvent event) {
        AgentState state = require(event.subjectId(), Status.IN_FREE_MARKET);
        EconomySessionPort.Directive directive = sessions.performMarketCycle(
                state.sessionId, state.profile, event.dueAt());
        recordPresence(state.profile, "MARKET_CYCLE", event.dueAt());
        if (directive.releaseRequested()) {
            EconomySessionPort.ReleaseResult release = sessions.release(state.sessionId,
                    state.profile, event.dueAt(), directive.reason());
            journal.sessionEvent(engine.runId(), state.profile.agentId(), null, state.sessionId,
                    "RELEASE_" + release.status().name(), event.dueAt(), release.reason(),
                    release.retryAt(), null);
            if (release.status() == EconomySessionPort.ReleaseResult.Status.DEFERRED) {
                engine.schedule(release.retryAt(), MARKET_CYCLE, event.subjectId(), Map.of());
            } else if (release.status() == EconomySessionPort.ReleaseResult.Status.REJECTED) {
                throw new IllegalStateException("economy release rejected: " + release.reason());
            } else {
                agents.put(event.subjectId(), new AgentState(state.profile, Status.IN_FREE_MARKET,
                        null, null, null));
                engine.schedule(directive.outsideAvailableAt().orElse(event.dueAt()), START_ACTIVITY,
                        event.subjectId(), Map.of());
            }
        }
        directive.revisitAt().ifPresent(at -> engine.schedule(at, MARKET_CYCLE,
                event.subjectId(), Map.of()));
        if (directive.externalActionPending())
            engine.pauseAfterCurrentEvent("physical market capability pending for " + event.subjectId());
    }

    private void startActivity(ScheduledEconomyEvent event) {
        AgentState state = require(event.subjectId(), Status.IN_FREE_MARKET);
        FarmSessionPlan plan = activities.plan(state.profile, event.dueAt());
        if (!plan.agentId().equals(event.subjectId()) || !plan.startedAt().equals(event.dueAt()))
            throw new IllegalStateException("activity plan is not for the scheduled agent/time");
        if ("explicit-work".equals(plan.calibrationId()))
            throw new IllegalStateException("production runs require live calibration evidence");
        FarmSessionOutcome outcome = activities.resolve(plan, engine.randomStreams());
        activities.begin(state.profile, plan, event.dueAt());
        recordPresence(state.profile, "OFFSCREEN_ACTIVITY_STARTED", event.dueAt());
        journal.activityStarted(engine.runId(), plan);
        journal.stateChanged(engine.runId(), event.subjectId(), Status.OFFSCREEN_ACTIVITY,
                plan.sessionId(), event.dueAt());
        agents.put(event.subjectId(), new AgentState(state.profile, Status.OFFSCREEN_ACTIVITY,
                plan, outcome, null));
        engine.schedule(outcome.completedAt(), COMPLETE_ACTIVITY,
                event.subjectId(), Map.of("sessionId", plan.sessionId()));
    }

    private void completeActivity(ScheduledEconomyEvent event) {
        AgentState state = require(event.subjectId(), Status.OFFSCREEN_ACTIVITY);
        if (!state.pendingActivity.sessionId().equals(event.parameters().get("sessionId")))
            throw new IllegalStateException("activity completion does not match pending session");
        FarmSessionOutcome outcome = state.pendingOutcome == null
                ? activities.resolve(state.pendingActivity, engine.randomStreams()) : state.pendingOutcome;
        if (!outcome.completedAt().equals(event.dueAt()))
            throw new IllegalStateException("activity completion event does not match resolved downtime");
        outcome = activities.settle(state.profile, outcome, event.dueAt(),
                engine.randomStreams().stream("agent." + event.subjectId() + ".progression")::nextLong);
        if (!outcome.sessionId().equals(state.pendingActivity.sessionId())
                || !outcome.completedAt().equals(event.dueAt()))
            throw new IllegalStateException("settlement result does not match pending activity");
        journal.activityCompleted(engine.runId(), outcome);
        journal.stateChanged(engine.runId(), event.subjectId(), Status.RETURNING_TO_FM,
                null, event.dueAt());
        agents.put(event.subjectId(), new AgentState(state.profile, Status.RETURNING_TO_FM,
                null, null, null));
        engine.schedule(event.dueAt(), RETURN_TO_FM, event.subjectId(), Map.of());
    }

    private void returnToMarket(ScheduledEconomyEvent event) {
        AgentState state = require(event.subjectId(), Status.RETURNING_TO_FM);
        activities.returnToEconomyEntrance(state.profile, event.dueAt());
        recordPresence(state.profile, "RETURNED_TO_FREE_MARKET", event.dueAt());
        engine.schedule(event.dueAt(), ENTER_ECONOMY, event.subjectId(), Map.of());
    }

    private void enterEconomy(ScheduledEconomyEvent event) {
        AgentState state = require(event.subjectId(), Status.RETURNING_TO_FM);
        EconomySessionPort.EntryResult entry = requestEntry(state.profile, event.dueAt());
        if (entry.status() == EconomySessionPort.EntryResult.Status.DEFERRED) {
            engine.schedule(entry.retryAt(), ENTER_ECONOMY, event.subjectId(), Map.of());
            return;
        }
        if (entry.status() == EconomySessionPort.EntryResult.Status.REJECTED)
            throw new IllegalStateException("economy re-entry rejected: " + entry.reason());
        journal.stateChanged(engine.runId(), event.subjectId(), Status.IN_FREE_MARKET, null, event.dueAt());
        agents.put(event.subjectId(), new AgentState(state.profile, Status.IN_FREE_MARKET,
                null, null, entry.sessionId()));
        engine.schedule(event.dueAt(), MARKET_CYCLE, event.subjectId(), Map.of());
    }

    private EconomySessionPort.EntryResult requestEntry(CommerceParticipant profile, Instant at) {
        EconomySessionPort.EntryRequest request = EconomySessionPort.EntryRequest.scheduled(
                engine.runId(), profile.agentId(), at, maximumSessionDuration, maximumIdleDuration);
        EconomySessionPort.EntryResult result = sessions.requestEntry(profile, request, at);
        journal.sessionEvent(engine.runId(), profile.agentId(), request.requestId(), result.sessionId(),
                "ENTRY_" + result.status().name(), at, result.reason(), result.retryAt(), result.expiresAt());
        return result;
    }

    private AgentState require(String agentId, Status expected) {
        AgentState state = agents.get(agentId);
        if (state == null || state.status != expected)
            throw new IllegalStateException(agentId + " must be " + expected);
        return state;
    }

    private void recordPresence(CommerceParticipant profile, String reason, Instant at) {
        sessions.sessionPresence(profile).ifPresent(value ->
                journal.presence(engine.runId(), profile.agentId(), value, reason, at));
    }

    private static CommerceParticipant profile(ScheduledEconomyEvent event) {
        Map<String, String> p = event.parameters();
        return new CommerceParticipant(event.subjectId(), p.get("jobFamily"),
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
                state.pendingActivity == null ? null : state.pendingActivity.sessionId(), state.sessionId)));
        return Map.copyOf(result);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> agentState = new LinkedHashMap<>();
        agents.forEach((id, state) -> agentState.put(id, Map.of(
                "profile", profileMap(state.profile),
                "status", state.status.name(),
                "sessionId", state.sessionId == null ? "" : state.sessionId.toString(),
                "pendingActivity", state.pendingActivity == null ? Map.of() : planMap(state.pendingActivity),
                "pendingOutcome", state.pendingOutcome == null ? Map.of() : outcomeMap(state.pendingOutcome))));
        return Map.of("schemaVersion", 3, "agents", Map.copyOf(agentState),
                "sessions", sessions.snapshotState(), "externalActivity", activities.snapshotState());
    }

    @SuppressWarnings("unchecked")
    public void restore(Map<String, Object> snapshot) {
        if (!agents.isEmpty()) throw new IllegalStateException("coordinator state is already initialized");
        Map<String, Object> agentState;
        Map<String, Object> sessionState;
        Map<String, Object> activityState;
        int coordinatorSchema;
        if (snapshot.containsKey("schemaVersion")) {
            int schemaVersion = integer(snapshot, "schemaVersion");
            if (schemaVersion != 2 && schemaVersion != 3)
                throw new IllegalStateException("unsupported coordinator checkpoint schema");
            agentState = (Map<String, Object>) snapshot.get("agents");
            sessionState = schemaVersion == 3
                    ? (Map<String, Object>) snapshot.get("sessions")
                    : (Map<String, Object>) snapshot.get("world");
            activityState = schemaVersion == 3
                    ? (Map<String, Object>) snapshot.get("externalActivity") : Map.of();
            coordinatorSchema = schemaVersion;
        } else {
            agentState = snapshot; // compatibility with checkpoints written before world state existed
            sessionState = Map.of(); activityState = Map.of();
            coordinatorSchema = 1;
        }
        agentState.forEach((id, value) -> {
            Map<String, Object> row = (Map<String, Object>) value;
            CommerceParticipant profile = profileFrom((Map<String, Object>) row.get("profile"));
            Status status = Status.valueOf(row.get("status").toString());
            Map<String, Object> pending = (Map<String, Object>) row.get("pendingActivity");
            FarmSessionPlan plan = pending == null || pending.isEmpty() ? null : planFrom(pending);
            Map<String, Object> pendingResult = (Map<String, Object>) row.get("pendingOutcome");
            FarmSessionOutcome outcome = pendingResult == null || pendingResult.isEmpty()
                    ? null : outcomeFrom(pendingResult);
            if ((status == Status.OFFSCREEN_ACTIVITY) != (plan != null))
                throw new IllegalStateException("checkpoint activity state is inconsistent for " + id);
            if (status != Status.OFFSCREEN_ACTIVITY && outcome != null)
                throw new IllegalStateException("checkpoint outcome state is inconsistent for " + id);
            String sessionId = Objects.toString(row.get("sessionId"), "");
            if (sessionId.isBlank() && coordinatorSchema == 2 && status == Status.IN_FREE_MARKET)
                sessionId = java.util.UUID.nameUUIDFromBytes((engine.runId() + ":" + id
                        + ":restored-session").getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            agents.put(id, new AgentState(profile, status, plan, outcome,
                    sessionId.isBlank() ? null : java.util.UUID.fromString(sessionId)));
        });
        Map<String, CommerceParticipant> profiles = new LinkedHashMap<>();
        agents.forEach((id, value) -> profiles.put(id, value.profile));
        sessions.restoreState(sessionState == null ? Map.of() : sessionState, Map.copyOf(profiles));
        activities.restoreState(activityState == null ? Map.of() : activityState, Map.copyOf(profiles));
    }

    private static Map<String, Object> profileMap(CommerceParticipant p) {
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

    private static CommerceParticipant profileFrom(Map<String, Object> p) {
        return new CommerceParticipant(text(p, "agentId"), text(p, "jobFamily"), decimal(p, "dailyActivityFraction"),
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
        value.put("deathProbabilityPerHour", p.deathProbabilityPerHour());
        value.put("respawnDowntime", p.respawnDowntime().toString());
        value.put("monsters", p.monsters().stream().map(work -> Map.of("monsterId", work.monsterId(),
                "kills", work.kills(), "experiencePerKill", work.experiencePerKill())).toList());
        value.put("activeQuestIds", new ArrayList<>(p.activeQuestIds()));
        value.put("consumedItems", p.consumedItems().stream().map(item -> Map.of("itemId", item.itemId(),
                "quantity", item.quantity(), "lotId", item.lotId())).toList());
        return value;
    }

    private static Map<String, Object> outcomeMap(FarmSessionOutcome o) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("sessionId", o.sessionId()); value.put("calibrationId", o.calibrationId());
        value.put("agentId", o.agentId()); value.put("mapId", o.mapId());
        value.put("completedAt", o.completedAt().toString()); value.put("experience", o.experience());
        value.put("mesos", o.mesos());
        value.put("itemDrops", o.itemDrops().stream().map(drop -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("lotId", drop.lotId()); row.put("monsterId", drop.monsterId());
            row.put("killOrdinal", drop.killOrdinal()); row.put("itemId", drop.itemId());
            row.put("quantity", drop.quantity()); row.put("questId", drop.questId());
            row.put("baseChance", drop.baseChance()); row.put("effectiveChance", drop.effectiveChance());
            row.put("equipmentStats", drop.equipmentStats());
            return row;
        }).toList());
        value.put("uncollectedDrops", o.uncollectedDrops().stream().map(uncollected -> Map.of(
                "reason", uncollected.reason(), "drop", dropMap(uncollected.drop()))).toList());
        value.put("consumedItems", o.consumedItems().stream().map(item -> Map.of(
                "itemId", item.itemId(), "quantity", item.quantity(), "lotId", item.lotId())).toList());
        Map<String, Object> kills = new LinkedHashMap<>();
        o.killCounts().forEach((id, quantity) -> kills.put(Integer.toString(id), quantity));
        value.put("killCounts", kills);
        value.put("death", Map.of("died", o.death().died(),
                "occurredAt", o.death().occurredAt() == null ? "" : o.death().occurredAt().toString(),
                "downtimeMillis", o.death().downtimeMillis(),
                "calibratedProbabilityPerHour", o.death().calibratedProbabilityPerHour()));
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
                integer(p, "dropRateMultiplier"), p.containsKey("deathProbabilityPerHour")
                ? decimal(p, "deathProbabilityPerHour") : 0d,
                p.containsKey("respawnDowntime") ? Duration.parse(text(p, "respawnDowntime")) : Duration.ZERO,
                monsters, quests, consumed);
    }

    @SuppressWarnings("unchecked")
    private static FarmSessionOutcome outcomeFrom(Map<String, Object> p) {
        List<FarmSessionOutcome.ItemDrop> drops = ((List<Map<String, Object>>) p.get("itemDrops")).stream()
                .map(EconomyRunCoordinator::dropFrom).toList();
        List<FarmSessionOutcome.UncollectedDrop> uncollected =
                ((List<Map<String, Object>>) p.getOrDefault("uncollectedDrops", List.of())).stream()
                        .map(row -> new FarmSessionOutcome.UncollectedDrop(
                                dropFrom((Map<String, Object>) row.get("drop")), text(row, "reason"))).toList();
        List<FarmSessionPlan.ItemConsumption> consumed = ((List<Map<String, Object>>) p.get("consumedItems"))
                .stream().map(row -> new FarmSessionPlan.ItemConsumption(integer(row, "itemId"),
                        integer(row, "quantity"), text(row, "lotId"))).toList();
        Map<Integer, Integer> kills = new LinkedHashMap<>();
        ((Map<?, ?>) p.get("killCounts")).forEach((key, value) ->
                kills.put(Integer.parseInt(key.toString()), ((Number) value).intValue()));
        Map<String, Object> death = (Map<String, Object>) p.getOrDefault("death", Map.of());
        boolean died = Boolean.TRUE.equals(death.get("died"));
        String occurredAt = Objects.toString(death.get("occurredAt"), "");
        FarmSessionOutcome.DeathOutcome deathOutcome = new FarmSessionOutcome.DeathOutcome(died,
                occurredAt.isBlank() ? null : Instant.parse(occurredAt),
                ((Number) death.getOrDefault("downtimeMillis", 0)).longValue(),
                ((Number) death.getOrDefault("calibratedProbabilityPerHour", 0d)).doubleValue());
        return new FarmSessionOutcome(text(p, "sessionId"), text(p, "calibrationId"), text(p, "agentId"),
                integer(p, "mapId"), Instant.parse(text(p, "completedAt")),
                ((Number) p.get("experience")).longValue(), ((Number) p.get("mesos")).longValue(),
                drops, uncollected, consumed, kills, deathOutcome);
    }

    private static Map<String, Object> dropMap(FarmSessionOutcome.ItemDrop drop) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("lotId", drop.lotId()); row.put("monsterId", drop.monsterId());
        row.put("killOrdinal", drop.killOrdinal()); row.put("itemId", drop.itemId());
        row.put("quantity", drop.quantity()); row.put("questId", drop.questId());
        row.put("baseChance", drop.baseChance()); row.put("effectiveChance", drop.effectiveChance());
        row.put("equipmentStats", drop.equipmentStats());
        return row;
    }

    private static FarmSessionOutcome.ItemDrop dropFrom(Map<String, Object> row) {
        return new FarmSessionOutcome.ItemDrop(text(row, "lotId"), integer(row, "monsterId"),
                integer(row, "killOrdinal"), integer(row, "itemId"), integer(row, "quantity"),
                integer(row, "questId"), integer(row, "baseChance"), integer(row, "effectiveChance"),
                ((Map<String, Number>) row.getOrDefault("equipmentStats", Map.of())).entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                                entry -> entry.getValue().intValue())));
    }

    private static String text(Map<String, Object> values, String key) { return values.get(key).toString(); }
    private static int integer(Map<String, Object> values, String key) { return ((Number) values.get(key)).intValue(); }
    private static double decimal(Map<String, Object> values, String key) { return ((Number) values.get(key)).doubleValue(); }

    public enum Status { IN_FREE_MARKET, OFFSCREEN_ACTIVITY, RETURNING_TO_FM }
    public record AgentView(CommerceParticipant profile, Status status, String pendingActivityId,
                            java.util.UUID economySessionId) { }
    private record AgentState(CommerceParticipant profile, Status status,
                              FarmSessionPlan pendingActivity, FarmSessionOutcome pendingOutcome,
                              java.util.UUID sessionId) { }
}
