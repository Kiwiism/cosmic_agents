package server.agents.economy.experiment;

import com.esotericsoftware.yamlbeans.YamlReader;
import server.agents.economy.catalog.CatalogBundleLoader;
import server.agents.economy.scenario.EconomyConfigException;
import server.agents.economy.scenario.EconomyConfigLoader;
import server.agents.economy.scenario.LoadedEconomyConfig;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Validates and durably registers explicitly paired scenario runs. */
public final class EconomyExperimentPlanner {
    private final DataSource dataSource;

    public EconomyExperimentPlanner(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    public Plan plan(Path manifestPath) {
        Objects.requireNonNull(manifestPath);
        String yaml;
        try { yaml = Files.readString(manifestPath, StandardCharsets.UTF_8); }
        catch (IOException failure) {
            throw new EconomyConfigException("Could not read economy experiment manifest " + manifestPath, failure);
        }
        EconomyExperimentManifest manifest = parse(yaml);
        Path base = manifestPath.toAbsolutePath().normalize().getParent();
        List<PlannedPair> pairs = new ArrayList<>();
        Set<String> pairIds = new HashSet<>();
        Set<Long> seeds = new HashSet<>();
        for (int index = 0; index < manifest.pairs.size(); index++) {
            EconomyExperimentManifest.Pair pair = manifest.pairs.get(index);
            require(pair != null, "experiment pair is required");
            require(text(pair.pairId), "pairId is required");
            require(pairIds.add(pair.pairId), "duplicate pairId: " + pair.pairId);
            require(seeds.add(pair.seed), "paired experiment seeds must be unique: " + pair.seed);
            Path baselinePath = resolve(base, pair.baselineConfig);
            Path candidatePath = resolve(base, pair.candidateConfig);
            LoadedEconomyConfig baseline = new EconomyConfigLoader().load(baselinePath);
            LoadedEconomyConfig candidate = new EconomyConfigLoader().load(candidatePath);
            require(baseline.config().scenario.seed == pair.seed,
                    "baseline seed does not match pair " + pair.pairId);
            require(candidate.config().scenario.seed == pair.seed,
                    "candidate seed does not match pair " + pair.pairId);
            require(baseline.config().clock.logicalStart.equals(candidate.config().clock.logicalStart),
                    "paired runs must share logicalStart: " + pair.pairId);
            require(baseline.config().scenario.targetLogicalDays == candidate.config().scenario.targetLogicalDays,
                    "paired runs must share targetLogicalDays: " + pair.pairId);
            String baselineCatalog = new CatalogBundleLoader().load(baseline.config().catalog).version();
            String candidateCatalog = new CatalogBundleLoader().load(candidate.config().catalog).version();
            require(baselineCatalog.equals(candidateCatalog),
                    "paired runs must use the same catalog: " + pair.pairId);
            pairs.add(new PlannedPair(pair.pairId, index, pair.seed,
                    deterministicRunId(manifest.experimentId, pair.pairId, "baseline"),
                    deterministicRunId(manifest.experimentId, pair.pairId, "candidate"),
                    baselinePath, candidatePath, baseline.sha256(), candidate.sha256(), baselineCatalog));
        }
        Plan plan = new Plan(manifest.experimentId, manifest.description, manifest.design,
                yaml, List.copyOf(pairs));
        persist(plan);
        return plan;
    }

    public NextRun next(String experimentId) {
        String sql = "SELECT p.pair_id, p.seed, p.baseline_run_id, p.candidate_run_id, "
                + "p.baseline_config_path, p.candidate_config_path, b.status baseline_status, "
                + "c.status candidate_status FROM economy_experiment_pair p "
                + "LEFT JOIN simulation_run b ON b.run_id = p.baseline_run_id "
                + "LEFT JOIN simulation_run c ON c.run_id = p.candidate_run_id "
                + "WHERE p.experiment_id = ? ORDER BY p.pair_order";
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, experimentId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    String baselineStatus = rows.getString("baseline_status");
                    if (!"COMPLETED".equals(baselineStatus))
                        return new NextRun(rows.getString("pair_id"), rows.getLong("seed"), "BASELINE",
                                rows.getObject("baseline_run_id", UUID.class),
                                Path.of(rows.getString("baseline_config_path")), baselineStatus);
                    String candidateStatus = rows.getString("candidate_status");
                    if (!"COMPLETED".equals(candidateStatus))
                        return new NextRun(rows.getString("pair_id"), rows.getLong("seed"), "CANDIDATE",
                                rows.getObject("candidate_run_id", UUID.class),
                                Path.of(rows.getString("candidate_config_path")), candidateStatus);
                }
                return null;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not read economy experiment progress", failure);
        }
    }

    private void persist(Plan plan) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO economy_experiment (experiment_id, design, description, manifest_yaml) "
                                + "VALUES (?, ?, ?, ?)")) {
                    statement.setString(1, plan.experimentId()); statement.setString(2, plan.design());
                    statement.setString(3, plan.description()); statement.setString(4, plan.manifestYaml());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO economy_experiment_pair (experiment_id, pair_id, pair_order, seed, "
                                + "baseline_run_id, candidate_run_id, baseline_config_path, "
                                + "candidate_config_path, baseline_config_hash, candidate_config_hash, "
                                + "catalog_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (PlannedPair pair : plan.pairs()) {
                        statement.setString(1, plan.experimentId()); statement.setString(2, pair.pairId());
                        statement.setInt(3, pair.pairOrder()); statement.setLong(4, pair.seed());
                        statement.setObject(5, pair.baselineRunId()); statement.setObject(6, pair.candidateRunId());
                        statement.setString(7, pair.baselineConfig().toString());
                        statement.setString(8, pair.candidateConfig().toString());
                        statement.setString(9, pair.baselineConfigHash());
                        statement.setString(10, pair.candidateConfigHash());
                        statement.setString(11, pair.catalogVersion()); statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback(); throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not persist economy experiment plan", failure);
        }
    }

    private static EconomyExperimentManifest parse(String yaml) {
        try {
            YamlReader reader = new YamlReader(new StringReader(yaml));
            EconomyExperimentManifest manifest = reader.read(EconomyExperimentManifest.class);
            reader.close();
            require(manifest != null && manifest.schemaVersion == 1,
                    "experiment schemaVersion must be 1");
            require(text(manifest.experimentId), "experimentId is required");
            require(text(manifest.description), "experiment description is required");
            require("PAIRED_SAME_SEED".equals(manifest.design),
                    "experiment design must be PAIRED_SAME_SEED");
            require(manifest.pairs != null && !manifest.pairs.isEmpty(),
                    "experiment pairs cannot be empty");
            return manifest;
        } catch (IOException failure) {
            throw new EconomyConfigException("Could not parse economy experiment manifest", failure);
        }
    }

    private static Path resolve(Path base, String configured) {
        require(text(configured), "paired config path is required");
        Path value = Path.of(configured);
        return (value.isAbsolute() ? value : base.resolve(value)).toAbsolutePath().normalize();
    }

    private static UUID deterministicRunId(String experiment, String pair, String side) {
        return UUID.nameUUIDFromBytes((experiment + ':' + pair + ':' + side)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static boolean text(String value) { return value != null && !value.isBlank(); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new EconomyConfigException(message);
    }

    public record Plan(String experimentId, String description, String design,
                       String manifestYaml, List<PlannedPair> pairs) { }
    public record PlannedPair(String pairId, int pairOrder, long seed,
                              UUID baselineRunId, UUID candidateRunId,
                              Path baselineConfig, Path candidateConfig,
                              String baselineConfigHash, String candidateConfigHash,
                              String catalogVersion) { }
    public record NextRun(String pairId, long seed, String side, UUID runId,
                          Path configPath, String currentStatus) { }
}
