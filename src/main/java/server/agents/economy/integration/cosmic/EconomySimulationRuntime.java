package server.agents.economy.integration.cosmic;

import client.Character;
import com.zaxxer.hikari.HikariDataSource;
import server.agents.economy.persistence.EconomyPostgresDataSource;
import server.agents.economy.scenario.EconomyConfigLoader;
import server.agents.economy.scenario.LoadedEconomyConfig;
import server.agents.economy.scenario.ManagedEconomyRun;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import tools.DatabaseConnection;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Process-level operator lifecycle for one economy run. Market actions remain autonomous. */
public final class EconomySimulationRuntime {
    private static ManagedEconomyRun run;
    private static HikariDataSource economyDatabase;
    private static Map<String, Character> directory = Map.of();

    private EconomySimulationRuntime() { }

    public static synchronized Status start() {
        return start(UUID.randomUUID(), EconomyConfigLoader.DEFAULT_PATH);
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
        Map<String, Character> mapped = new LinkedHashMap<>();
        for (int index = 0; index < config.config().population.maximumAgents; index++)
            mapped.put("agent-" + (index + 1), agents.get(index));
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

    public record Status(boolean active, UUID runId, java.time.Instant logicalTime,
                         int admittedAgents, int reservedCharacters) { }
}
