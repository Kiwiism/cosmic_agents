package server.agents.economy.integration.cosmic;

import client.Character;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.TimerManager;
import server.agents.economy.clock.RealtimeEconomyClock;
import server.agents.economy.persistence.EconomyPostgresDataSource;
import server.agents.economy.persistence.EconomyDatabaseVerifier;
import server.agents.economy.persistence.JdbcActivityCalibrationRepository;
import server.agents.economy.persistence.JdbcEconomyParticipantBindingStore;
import server.agents.economy.scenario.EconomyConfigLoader;
import server.agents.economy.scenario.LoadedEconomyConfig;
import server.agents.economy.scenario.ManagedEconomyRun;
import server.agents.economy.scenario.NamedRandomStreams;
import server.agents.economy.scenario.PopulationAdmissionPlanner;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentExclusiveControlRuntime;
import server.agents.runtime.activity.AgentForegroundActivityDefaults;
import tools.DatabaseConnection;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

/** Process-level operator lifecycle for one economy run. Market actions remain autonomous. */
public final class EconomySimulationRuntime {
    private static final Logger log = LoggerFactory.getLogger(EconomySimulationRuntime.class);
    private static final long AUTO_ADVANCE_POLL_MS = 500L;
    private static final long REALTIME_POLL_MS = 1_000L;
    private static final int AUTO_ADVANCE_BATCH_ACTIONS = 64;
    private static ManagedEconomyRun run;
    private static HikariDataSource economyDatabase;
    private static Map<String, Character> directory = Map.of();
    private static String controlOwner;
    private static Instant requestedLogicalTarget;
    private static Instant physicalBatchLogicalTime;
    private static ScheduledFuture<?> autoAdvanceTask;
    private static RealtimeEconomyClock realtimeClock;
    private static String clockMode;

    private EconomySimulationRuntime() { }

    public static synchronized Status start() {
        return start(UUID.randomUUID(), EconomyConfigLoader.DEFAULT_PATH);
    }

    public static synchronized Preflight preflight() {
        return preflight(EconomyConfigLoader.DEFAULT_PATH);
    }

    public static synchronized Preflight preflight(Path configPath) {
        LoadedEconomyConfig loaded = new EconomyConfigLoader().load(configPath);
        List<Character> agents = AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .map(AgentRuntimeEntry::bot).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(Character::getId)).toList();
        return inspect(loaded, agents);
    }

    private static Preflight inspect(LoadedEconomyConfig loaded, List<Character> agents) {
        var config = loaded.config();
        List<String> blockers = new ArrayList<>();
        if (run != null) blockers.add("AN_ECONOMY_RUN_IS_ALREADY_ACTIVE");
        if (agents.size() < config.population.maximumAgents)
            blockers.add("LIVE_ROSTER_SHORTFALL:" + agents.size() + '/' + config.population.maximumAgents);

        var admissions = new PopulationAdmissionPlanner().plan(config.population,
                java.time.Instant.parse(config.clock.logicalStart),
                new NamedRandomStreams(config.scenario.seed));
        Map<String, Character> mapped = Map.of();
        try {
            mapped = new EconomyAgentRosterBinder().bind(admissions, agents,
                    config.bootstrap.shopPermitItemId);
        } catch (RuntimeException failure) {
            blockers.add("ROSTER_BINDING:" + message(failure));
        }

        java.time.Instant start = java.time.Instant.parse(config.clock.logicalStart);
        int initialReady = 0;
        for (var admission : admissions) {
            if (!admission.admittedAt().equals(start)) continue;
            Character character = mapped.get(admission.agentId());
            if (character != null && character.getClient() != null
                    && character.getClient().getChannel() == config.world.channelId
                    && character.getMapId() >= config.world.freeMarketEntranceMapId
                    && character.getMapId() <= config.world.lastFreeMarketRoomMapId) initialReady++;
        }
        if (initialReady < config.population.initialAgents)
            blockers.add("INITIAL_FM_PRESENCE:" + initialReady + '/' + config.population.initialAgents);

        int missingCalibrations = 0;
        Map<String, Integer> missingCalibrationCohorts = new java.util.TreeMap<>();
        boolean databaseReady = false;
        try (HikariDataSource database = EconomyPostgresDataSource.fromEnvironment()) {
            new EconomyDatabaseVerifier(database).verify(config.persistence.database);
            databaseReady = true;
            var repository = new JdbcActivityCalibrationRepository(database);
            var maps = new server.agents.economy.activity.VictoriaActivityMapCatalog(
                    config.activity.mapCatalogResource);
            for (var admission : admissions) {
                Character character = mapped.get(admission.agentId());
                if (character == null) {
                    missingCalibrations++;
                    missingCalibrationCohorts.merge(admission.jobFamily() + "@UNBOUND", 1, Integer::sum);
                    continue;
                }
                boolean present = maps.candidates(character.getLevel()).stream().anyMatch(map ->
                        repository.find(config.activity.agentBuild, map.mapId(), character.getLevel(),
                                admission.jobFamily(), config.activity.minimumCalibrationSamples).isPresent());
                if (!present) {
                    missingCalibrations++;
                    missingCalibrationCohorts.merge(admission.jobFamily() + "@L" + character.getLevel(),
                            1, Integer::sum);
                }
            }
        } catch (RuntimeException failure) {
            blockers.add("EVIDENCE_DATABASE:" + message(failure));
        }
        if (databaseReady && missingCalibrations > 0)
            blockers.add("MISSING_ACTIVITY_CALIBRATIONS:" + missingCalibrations
                    + ":cohorts=" + missingCalibrationCohorts);

        long sellers = admissions.stream().filter(value -> value.profile().stallWillingness() >= .5d).count();
        long permits = mapped.values().stream().filter(character -> character
                .getInventory(client.inventory.InventoryType.CASH)
                .countById(config.bootstrap.shopPermitItemId) > 0).count();
        return new Preflight(blockers.isEmpty(), agents.size(), config.population.maximumAgents,
                mapped.size(), initialReady, config.population.initialAgents,
                (int) sellers, (int) permits, missingCalibrations, databaseReady, List.copyOf(blockers));
    }

    public static synchronized Status start(UUID runId, Path configPath) {
        if (run != null) throw new IllegalStateException("an economy run is already active");
        LoadedEconomyConfig config = new EconomyConfigLoader().load(configPath);
        List<Character> agents = AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .map(AgentRuntimeEntry::bot).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(Character::getId)).toList();
        var admissions = new PopulationAdmissionPlanner().plan(config.config().population,
                java.time.Instant.parse(config.config().clock.logicalStart),
                new NamedRandomStreams(config.config().scenario.seed));
        Map<String, Character> mapped = new EconomyAgentRosterBinder().bind(admissions, agents,
                config.config().bootstrap.shopPermitItemId);
        String owner = controlOwner(runId);
        HikariDataSource database = null;
        try {
            // Acquire control before the database-backed readiness checks. Without this
            // boundary, an ordinary self-directed tick can move a verified participant
            // out of the FM between preflight and admission.
            claim(owner, mapped);
            Preflight readiness = inspect(config, agents);
            if (!readiness.ready()) throw new IllegalStateException(
                    "economy preflight blocked startup: " + String.join(" | ", readiness.blockers()));
            database = EconomyPostgresDataSource.fromEnvironment();
            ManagedEconomyRun started = EconomyRuntimeFactory.start(runId, configPath,
                    DatabaseConnection.dataSource(), database, mapped::get);
            economyDatabase = database; directory = Map.copyOf(mapped); run = started;
            controlOwner = owner;
            activateClock(config.config().clock.mode);
            return status();
        } catch (RuntimeException failure) {
            server.agents.integration.AgentEconomicActionGuardRuntime.clear();
            clearFailedStart(runId);
            AgentExclusiveControlRuntime.release(owner);
            if (database != null) database.close();
            throw failure;
        }
    }

    public static synchronized Status resume(UUID runId) {
        return resume(runId, EconomyConfigLoader.DEFAULT_PATH);
    }

    public static synchronized Status resume(UUID runId, Path configPath) {
        if (run != null) throw new IllegalStateException("an economy run is already active");
        LoadedEconomyConfig config = new EconomyConfigLoader().load(configPath);
        List<Character> agents = activeAgents();
        HikariDataSource database = EconomyPostgresDataSource.fromEnvironment();
        try {
            new EconomyDatabaseVerifier(database).verify(config.config().persistence.database);
            Map<String, Integer> persisted = new JdbcEconomyParticipantBindingStore(database).load(runId);
            if (persisted.size() != config.config().population.maximumAgents)
                throw new IllegalStateException("durable roster reservation is incomplete: "
                        + persisted.size() + '/' + config.config().population.maximumAgents);
            Map<Integer, Character> byId = agents.stream().collect(java.util.stream.Collectors.toMap(
                    Character::getId, java.util.function.Function.identity(), (left, right) -> left));
            Map<String, Character> mapped = new java.util.LinkedHashMap<>();
            persisted.forEach((logicalId, characterId) -> {
                Character character = byId.get(characterId);
                if (character == null || character.getClient() == null
                        || character.getClient().getChannel() != config.config().world.channelId)
                    throw new IllegalStateException("reserved character is not live on channel "
                            + config.config().world.channelId + ": " + logicalId + " -> " + characterId);
                mapped.put(logicalId, character);
            });
            String owner = controlOwner(runId);
            claim(owner, mapped);
            ManagedEconomyRun resumed = EconomyRuntimeFactory.resume(runId, configPath,
                    DatabaseConnection.dataSource(), database, mapped::get);
            economyDatabase = database; directory = Map.copyOf(mapped); run = resumed;
            controlOwner = owner;
            activateClock(config.config().clock.mode);
            return status();
        } catch (RuntimeException failure) {
            server.agents.integration.AgentEconomicActionGuardRuntime.clear();
            clearFailedStart(runId);
            AgentExclusiveControlRuntime.release(controlOwner(runId));
            database.close(); throw failure;
        }
    }

    public static synchronized ManagedEconomyRun.AdvanceResult advanceDays(long days) {
        requireRun();
        if (realtimeClock != null)
            throw new IllegalStateException("REALTIME runs advance automatically at 1x wall-clock speed");
        if (days < 0) throw new IllegalArgumentException("economy runs cannot move backward");
        Instant requested = run.application().now().plus(Duration.ofDays(days));
        if (requested.isAfter(run.application().targetAt())) requested = run.application().targetAt();
        if (requestedLogicalTarget == null || requested.isAfter(requestedLogicalTarget)) {
            requestedLogicalTarget = requested;
        }
        ManagedEconomyRun.AdvanceResult result = run.advanceTo(requestedLogicalTarget);
        afterAdvance(result, requestedLogicalTarget, false);
        return result;
    }

    public static synchronized server.agents.economy.persistence.EconomyEvidencePipeline.Result audit() {
        requireRun(); return run.audit();
    }

    public static synchronized server.agents.economy.persistence.EconomyEvidencePipeline.Result complete() {
        requireRun();
        var result = run.complete();
        cancelAutoAdvance();
        server.agents.integration.AgentEconomicActionGuardRuntime.clear();
        releaseControl();
        return result;
    }

    public static synchronized server.agents.economy.persistence.EconomyEvidencePipeline.Result fail(String reason) {
        requireRun();
        var result = run.fail(reason);
        cancelAutoAdvance();
        server.agents.integration.AgentEconomicActionGuardRuntime.clear();
        releaseControl();
        return result;
    }

    public static synchronized Status status() {
        if (run == null) return new Status(false, null, null, null, null, null, 0, 0);
        return new Status(true, run.application().runId(), run.application().now(),
                run.application().targetAt(), run.status(), clockMode,
                run.application().agents().size(), directory.size());
    }

    public static synchronized void stop() {
        cancelAutoAdvance();
        server.agents.integration.AgentEconomicActionGuardRuntime.clear();
        if (run != null && !java.util.Set.of("COMPLETED", "FAILED", "STOPPED").contains(run.status()))
            run.checkpoint("STOPPED");
        run = null; directory = Map.of();
        releaseControl();
        clockMode = null;
        if (economyDatabase != null) economyDatabase.close();
        economyDatabase = null;
    }

    /**
     * Advances the selected clock without operator commands at every physical boundary.
     * All events at one logical instant may assign independent real capabilities concurrently;
     * logical time does not proceed into the next batch until those capabilities have finished.
     */
    private static synchronized void autoAdvanceTick() {
        autoAdvanceTask = null;
        if (run == null) return;
        try {
            Instant current = run.application().now();
            boolean realtime = realtimeClock != null;
            Instant target = realtime ? realtimeClock.targetAt(System.nanoTime()) : requestedLogicalTarget;
            if (target == null) return;
            int activeCapabilities = activeEconomyCapabilities();
            if (physicalBatchLogicalTime == null) physicalBatchLogicalTime = current;
            if (activeCapabilities > 0 && !current.equals(physicalBatchLogicalTime)) {
                scheduleAutoAdvance();
                return;
            }
            if (activeCapabilities == 0 && !current.equals(physicalBatchLogicalTime)) {
                physicalBatchLogicalTime = current;
            }

            for (int action = 0; action < AUTO_ADVANCE_BATCH_ACTIONS; action++) {
                ManagedEconomyRun.AdvanceResult result = run.advanceTo(target);
                afterAdvance(result, target, realtime);
                if (!result.advance().waitingExternalAction() || run == null
                        || (!realtime && requestedLogicalTarget == null)) return;
                if (!result.advance().reachedAt().equals(physicalBatchLogicalTime)) break;
                if (activeEconomyCapabilities() >= directory.size()) break;
            }
            scheduleAutoAdvance();
        } catch (RuntimeException failure) {
            log.error("Automatic economy advancement failed", failure);
            try {
                if (run != null && !java.util.Set.of("COMPLETED", "FAILED", "STOPPED").contains(run.status())) {
                    run.fail("automatic advancement failed: " + message(failure));
                }
            } catch (RuntimeException evidenceFailure) {
                log.error("Could not persist automatic economy advancement failure", evidenceFailure);
            }
            cancelAutoAdvance();
            server.agents.integration.AgentEconomicActionGuardRuntime.clear();
            releaseControl();
        }
    }

    private static void afterAdvance(ManagedEconomyRun.AdvanceResult result,
                                     Instant target, boolean realtime) {
        if (result.advance().waitingExternalAction()) {
            if (physicalBatchLogicalTime == null) {
                physicalBatchLogicalTime = result.advance().reachedAt();
            }
            scheduleAutoAdvance();
            return;
        }
        if ("COMPLETED".equals(result.status())) {
            cancelAutoAdvance();
            server.agents.integration.AgentEconomicActionGuardRuntime.clear();
            releaseControl();
            return;
        }
        if (!run.application().now().isBefore(target)) {
            physicalBatchLogicalTime = null;
            if (realtime) scheduleAutoAdvance();
            else {
                requestedLogicalTarget = null;
                cancelScheduledTask();
            }
        }
    }

    private static int activeEconomyCapabilities() {
        int active = 0;
        for (Character character : directory.values()) {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterInstance(character);
            if (entry != null && entry.capabilityRuntimeState().hasActiveCapability()) active++;
        }
        return active;
    }

    private static void scheduleAutoAdvance() {
        if (autoAdvanceTask == null && run != null
                && (requestedLogicalTarget != null || realtimeClock != null)) {
            autoAdvanceTask = TimerManager.getInstance().schedule(
                    EconomySimulationRuntime::autoAdvanceTick,
                    realtimeClock == null ? AUTO_ADVANCE_POLL_MS : REALTIME_POLL_MS);
        }
    }

    private static void cancelAutoAdvance() {
        cancelScheduledTask();
        requestedLogicalTarget = null;
        physicalBatchLogicalTime = null;
        realtimeClock = null;
    }

    private static void cancelScheduledTask() {
        if (autoAdvanceTask != null) autoAdvanceTask.cancel(false);
        autoAdvanceTask = null;
    }

    private static void activateClock(String mode) {
        clockMode = mode;
        if ("REALTIME".equals(mode)) {
            realtimeClock = new RealtimeEconomyClock(
                    run.application().now(), run.application().targetAt(), System.nanoTime());
            scheduleAutoAdvance();
        } else {
            realtimeClock = null;
        }
    }

    private static void clearFailedStart(UUID runId) {
        if (run == null || !run.application().runId().equals(runId)) return;
        cancelAutoAdvance();
        run = null;
        directory = Map.of();
        economyDatabase = null;
        controlOwner = null;
        clockMode = null;
    }

    private static void requireRun() {
        if (run == null) throw new IllegalStateException("no economy run is active");
    }

    private static List<Character> activeAgents() {
        return AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .map(AgentRuntimeEntry::bot).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(Character::getId)).toList();
    }

    private static String message(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName()
                : failure.getMessage().replaceAll("\\s+", " ");
    }

    private static void claim(String owner, Map<String, Character> mapped) {
        try {
            for (Character character : mapped.values()) {
                AgentExclusiveControlRuntime.claim(character.getId(), owner);
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterInstance(character);
                if (entry == null) {
                    throw new IllegalStateException("reserved economy character has no runtime entry: "
                            + character.getId());
                }
                AgentForegroundActivityDefaults.coordinator().prepareExclusive(
                        "exclusive-control", entry, character, "economy run claimed character",
                        System.currentTimeMillis());
            }
        } catch (RuntimeException failure) {
            AgentExclusiveControlRuntime.release(owner);
            throw failure;
        }
    }

    private static String controlOwner(UUID runId) {
        return "economy:" + runId;
    }

    private static void releaseControl() {
        AgentExclusiveControlRuntime.release(controlOwner);
        controlOwner = null;
    }

    public record Status(boolean active, UUID runId, java.time.Instant logicalTime,
                         java.time.Instant targetLogicalTime, String state, String clockMode,
                         int admittedAgents, int reservedCharacters) { }
    public record Preflight(boolean ready, int liveCharacters, int requiredCharacters,
                            int mappedCharacters, int initialFmReady, int initialAgents,
                            int configuredSellers, int realPermits, int missingCalibrations,
                            boolean databaseReady, List<String> blockers) {
        public Preflight { blockers = List.copyOf(blockers); }
    }
}
