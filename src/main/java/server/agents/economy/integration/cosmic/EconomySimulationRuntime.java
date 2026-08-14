package server.agents.economy.integration.cosmic;

import client.Character;
import com.zaxxer.hikari.HikariDataSource;
import server.agents.economy.persistence.EconomyPostgresDataSource;
import server.agents.economy.persistence.EconomyDatabaseVerifier;
import server.agents.economy.persistence.JdbcActivityCalibrationRepository;
import server.agents.economy.scenario.EconomyConfigLoader;
import server.agents.economy.scenario.LoadedEconomyConfig;
import server.agents.economy.scenario.ManagedEconomyRun;
import server.agents.economy.scenario.NamedRandomStreams;
import server.agents.economy.scenario.PopulationAdmissionPlanner;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import tools.DatabaseConnection;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;

/** Process-level operator lifecycle for one economy run. Market actions remain autonomous. */
public final class EconomySimulationRuntime {
    private static ManagedEconomyRun run;
    private static HikariDataSource economyDatabase;
    private static Map<String, Character> directory = Map.of();

    private EconomySimulationRuntime() { }

    public static synchronized Status start() {
        return start(UUID.randomUUID(), EconomyConfigLoader.DEFAULT_PATH);
    }

    public static synchronized Preflight preflight() {
        LoadedEconomyConfig loaded = new EconomyConfigLoader().load();
        var config = loaded.config();
        List<Character> agents = AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .map(AgentRuntimeEntry::bot).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(Character::getId)).toList();
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
        boolean databaseReady = false;
        try (HikariDataSource database = EconomyPostgresDataSource.fromEnvironment()) {
            new EconomyDatabaseVerifier(database).verify(config.persistence.database);
            databaseReady = true;
            var repository = new JdbcActivityCalibrationRepository(database);
            var maps = new server.agents.economy.activity.VictoriaActivityMapCatalog(
                    config.activity.mapCatalogResource);
            for (var admission : admissions) {
                Character character = mapped.get(admission.agentId());
                if (character == null) { missingCalibrations++; continue; }
                boolean present = maps.candidates(character.getLevel()).stream().anyMatch(map ->
                        repository.find(config.activity.agentBuild, map.mapId(), character.getLevel(),
                                admission.jobFamily(), config.activity.minimumCalibrationSamples).isPresent());
                if (!present) missingCalibrations++;
            }
        } catch (RuntimeException failure) {
            blockers.add("EVIDENCE_DATABASE:" + message(failure));
        }
        if (databaseReady && missingCalibrations > 0)
            blockers.add("MISSING_ACTIVITY_CALIBRATIONS:" + missingCalibrations);

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
        if (agents.size() < config.config().population.maximumAgents)
            throw new IllegalStateException("economy scenario requires "
                    + config.config().population.maximumAgents + " already-live autonomous characters; found "
                    + agents.size());
        var admissions = new PopulationAdmissionPlanner().plan(config.config().population,
                java.time.Instant.parse(config.config().clock.logicalStart),
                new NamedRandomStreams(config.config().scenario.seed));
        Map<String, Character> mapped = new EconomyAgentRosterBinder().bind(admissions, agents,
                config.config().bootstrap.shopPermitItemId);
        HikariDataSource database = EconomyPostgresDataSource.fromEnvironment();
        try {
            ManagedEconomyRun started = EconomyRuntimeFactory.start(runId, configPath,
                    DatabaseConnection.dataSource(), database, mapped::get);
            economyDatabase = database; directory = Map.copyOf(mapped); run = started;
            return status();
        } catch (RuntimeException failure) {
            database.close(); throw failure;
        }
    }

    public static synchronized ManagedEconomyRun.AdvanceResult advanceDays(long days) {
        requireRun(); return run.advanceDays(days);
    }

    public static synchronized Status status() {
        if (run == null) return new Status(false, null, null, 0, 0);
        return new Status(true, run.application().runId(), run.application().now(),
                run.application().agents().size(), directory.size());
    }

    public static synchronized void stop() {
        if (run != null) run.checkpoint("STOPPED");
        run = null; directory = Map.of();
        if (economyDatabase != null) economyDatabase.close();
        economyDatabase = null;
    }

    private static void requireRun() {
        if (run == null) throw new IllegalStateException("no economy run is active");
    }

    private static String message(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName()
                : failure.getMessage().replaceAll("\\s+", " ");
    }

    public record Status(boolean active, UUID runId, java.time.Instant logicalTime,
                         int admittedAgents, int reservedCharacters) { }
    public record Preflight(boolean ready, int liveCharacters, int requiredCharacters,
                            int mappedCharacters, int initialFmReady, int initialAgents,
                            int configuredSellers, int realPermits, int missingCalibrations,
                            boolean databaseReady, List<String> blockers) {
        public Preflight { blockers = List.copyOf(blockers); }
    }
}
