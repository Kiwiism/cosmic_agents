package server.agents.economy.scenario;

import server.agents.economy.catalog.CatalogBundleDescriptor;
import server.agents.economy.clock.LogicalClock;
import server.agents.economy.clock.LogicalEventQueue;
import server.agents.economy.clock.ScheduledEconomyEvent;
import server.agents.economy.clock.SimulationKernel;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Run aggregate supporting realtime or maximum-throughput progression over the same events. */
public final class SimulationRunEngine {
    public static final String ADMIT_AGENT = "ADMIT_AGENT";
    public static final String CHECKPOINT = "CHECKPOINT";
    private final UUID runId;
    private final LoadedEconomyConfig loadedConfig;
    private final CatalogBundleDescriptor catalog;
    private final LogicalClock clock;
    private final LogicalEventQueue queue;
    private final SimulationKernel kernel;
    private final NamedRandomStreams random;
    private final Consumer<ScheduledEconomyEvent> eventHandler;
    private Instant lastCheckpoint;

    public SimulationRunEngine(UUID runId, LoadedEconomyConfig loadedConfig,
                               CatalogBundleDescriptor catalog,
                               Consumer<ScheduledEconomyEvent> eventHandler) {
        this.runId = Objects.requireNonNull(runId);
        this.loadedConfig = Objects.requireNonNull(loadedConfig);
        this.catalog = Objects.requireNonNull(catalog);
        this.eventHandler = Objects.requireNonNull(eventHandler);
        EconomyEngineConfig config = loadedConfig.config();
        this.clock = new LogicalClock(Instant.parse(config.clock.logicalStart));
        this.queue = new LogicalEventQueue();
        this.kernel = new SimulationKernel(clock, queue, config.clock.maximumEventsPerBatch);
        this.random = new NamedRandomStreams(config.scenario.seed);
        schedulePopulation();
        scheduleCheckpoints();
    }

    public AdvanceSummary advanceDays(long days) {
        if (days < 0) throw new IllegalArgumentException("Cannot rewind a run");
        return advanceTo(clock.now().plus(Duration.ofDays(days)));
    }

    public static SimulationRunEngine restore(RunCheckpoint checkpoint,
                                              LoadedEconomyConfig loadedConfig,
                                              CatalogBundleDescriptor catalog,
                                              Consumer<ScheduledEconomyEvent> eventHandler) {
        Objects.requireNonNull(checkpoint);
        if (!checkpoint.configHash().equals(loadedConfig.sha256()))
            throw new IllegalStateException("Checkpoint configuration hash does not match");
        if (!checkpoint.catalogVersion().equals(catalog.version()))
            throw new IllegalStateException("Checkpoint catalog version does not match");
        SimulationRunEngine engine = new SimulationRunEngine(checkpoint.runId(), loadedConfig,
                catalog, eventHandler);
        engine.clock.advanceTo(checkpoint.logicalTime());
        if (checkpoint.queue().stream().anyMatch(event -> event.dueAt().isBefore(checkpoint.logicalTime())))
            throw new IllegalStateException("Checkpoint contains an event in the logical past");
        engine.queue.restore(checkpoint.queue());
        engine.random.restore(checkpoint.randomStates());
        engine.lastCheckpoint = checkpoint.logicalTime();
        return engine;
    }

    public AdvanceSummary advanceTo(Instant target) {
        int processed = 0;
        int batches = 0;
        boolean limited;
        do {
            SimulationKernel.AdvanceResult result = kernel.advanceUntil(target, event -> {
                if (CHECKPOINT.equals(event.kind())) lastCheckpoint = event.dueAt();
                eventHandler.accept(event);
            });
            processed = Math.addExact(processed, result.processedEvents());
            batches++;
            limited = result.batchLimitReached();
        } while (limited);
        return new AdvanceSummary(clock.now(), processed, batches, queue.size());
    }

    public RunCheckpoint checkpoint(Map<String, Object> domainState) {
        return new RunCheckpoint(runId, clock.now(), loadedConfig.sha256(), catalog.version(),
                queue.snapshot(), random.snapshot(), domainState == null ? Map.of() : Map.copyOf(domainState));
    }

    public Instant now() { return clock.now(); }
    public UUID runId() { return runId; }
    public Instant lastCheckpoint() { return lastCheckpoint; }
    public EconomyEngineConfig config() { return loadedConfig.config(); }
    public NamedRandomStreams randomStreams() { return random; }

    public ScheduledEconomyEvent schedule(Instant dueAt, String kind, String subjectId,
                                           Map<String, String> parameters) {
        if (dueAt.isBefore(clock.now())) throw new IllegalArgumentException("Cannot schedule in the logical past");
        return queue.schedule(dueAt, kind, subjectId, parameters);
    }

    private void schedulePopulation() {
        for (PopulationAdmissionPlanner.Admission admission : new PopulationAdmissionPlanner().plan(
                loadedConfig.config().population, clock.now(), random)) {
            Map<String, String> parameters = new LinkedHashMap<>();
            parameters.put("jobFamily", admission.jobFamily());
            parameters.put("dailyActivityFraction", Double.toString(admission.dailyActivityFraction()));
            EconomyAgentProfile profile = admission.profile();
            parameters.put("riskTolerance", Double.toString(profile.riskTolerance()));
            parameters.put("liquidityPreference", Double.toString(profile.liquidityPreference()));
            parameters.put("upgradeAggressiveness", Double.toString(profile.upgradeAggressiveness()));
            parameters.put("shoppingPatience", Double.toString(profile.shoppingPatience()));
            parameters.put("stallWillingness", Double.toString(profile.stallWillingness()));
            parameters.put("priceMemoryHours", Integer.toString(profile.priceMemoryHours()));
            parameters.put("negotiationAggressiveness", Double.toString(profile.negotiationAggressiveness()));
            parameters.put("chairInterest", Double.toString(profile.chairInterest()));
            queue.schedule(admission.admittedAt(), ADMIT_AGENT, admission.agentId(), parameters);
        }
    }

    private void scheduleCheckpoints() {
        EconomyEngineConfig config = loadedConfig.config();
        Duration cadence = Duration.ofHours(config.scenario.checkpointEveryLogicalHours);
        Instant end = clock.now().plus(Duration.ofDays(config.scenario.targetLogicalDays));
        for (Instant due = clock.now().plus(cadence); !due.isAfter(end); due = due.plus(cadence)) {
            queue.schedule(due, CHECKPOINT, runId.toString(), Map.of());
        }
    }

    public record AdvanceSummary(Instant reachedAt, int processedEvents, int batches, int queuedEvents) { }
    public record RunCheckpoint(UUID runId, Instant logicalTime, String configHash,
                                String catalogVersion, java.util.List<ScheduledEconomyEvent> queue,
                                Map<String, Long> randomStates, Map<String, Object> domainState) { }
}
