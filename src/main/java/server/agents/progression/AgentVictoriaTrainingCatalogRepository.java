package server.agents.progression;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/** Loads and cross-validates the curated level 15-30 Victoria training catalog. */
public final class AgentVictoriaTrainingCatalogRepository {
    private static final String DEFAULT_RESOURCE =
            "/agents/catalogs/victoria-level15-30-training-catalog.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final AgentVictoriaTrainingCatalogRepository DEFAULT = loadResource(DEFAULT_RESOURCE);

    private final AgentVictoriaTrainingCatalog catalog;
    private final Map<Integer, AgentVictoriaTrainingCatalog.TrainingMap> mapsById;
    private final Map<Integer, AgentVictoriaTrainingCatalog.LevelPlan> plansByLevel;

    AgentVictoriaTrainingCatalogRepository(AgentVictoriaTrainingCatalog catalog) {
        this.catalog = catalog;

        Map<Integer, AgentVictoriaTrainingCatalog.TrainingMap> mapIndex = new LinkedHashMap<>();
        for (AgentVictoriaTrainingCatalog.TrainingMap map : catalog.trainingMaps()) {
            if (mapIndex.putIfAbsent(map.mapId(), map) != null) {
                throw new IllegalArgumentException("duplicate Victoria training map " + map.mapId());
            }
            Set<Integer> mobIds = new HashSet<>();
            for (AgentVictoriaTrainingCatalog.SpawnGroup spawn : map.spawns()) {
                if (!mobIds.add(spawn.mobId())) {
                    throw new IllegalArgumentException("duplicate mob " + spawn.mobId()
                            + " in Victoria training map " + map.mapId());
                }
            }
        }

        Set<String> evidenceIds = new HashSet<>();
        for (AgentVictoriaTrainingCatalog.EvidenceSource source : catalog.evidenceSources()) {
            if (!evidenceIds.add(source.sourceId())) {
                throw new IllegalArgumentException("duplicate Victoria training evidence " + source.sourceId());
            }
        }
        for (AgentVictoriaTrainingCatalog.TrainingMap map : catalog.trainingMaps()) {
            if (!evidenceIds.containsAll(map.evidenceSourceIds())) {
                throw new IllegalArgumentException("training map " + map.mapId()
                        + " references unknown community evidence");
            }
        }

        Map<Integer, AgentVictoriaTrainingCatalog.LevelPlan> levelIndex = new LinkedHashMap<>();
        for (AgentVictoriaTrainingCatalog.LevelPlan plan : catalog.levelPlans()) {
            if (levelIndex.putIfAbsent(plan.level(), plan) != null) {
                throw new IllegalArgumentException("duplicate Victoria training level " + plan.level());
            }
            Set<Integer> choices = new HashSet<>();
            Set<Integer> ranks = new HashSet<>();
            for (AgentVictoriaTrainingCatalog.TrainingChoice choice : plan.choices()) {
                AgentVictoriaTrainingCatalog.TrainingMap map = Optional.ofNullable(mapIndex.get(choice.mapId()))
                        .orElseThrow(() -> new IllegalArgumentException(
                                "level " + plan.level() + " references unknown map " + choice.mapId()));
                if (!choices.add(choice.mapId())) {
                    throw new IllegalArgumentException("level " + plan.level()
                            + " repeats training map " + choice.mapId());
                }
                if (!ranks.add(choice.rank())) {
                    throw new IllegalArgumentException("level " + plan.level()
                            + " repeats training rank " + choice.rank());
                }
                if (plan.level() < map.recommendedMinLevel()
                        || plan.level() > map.recommendedMaxLevel()) {
                    throw new IllegalArgumentException("level " + plan.level() + " is outside map "
                            + map.mapId() + " recommendation band");
                }
            }
            List<Integer> expectedRanks = IntStream.rangeClosed(1, plan.choices().size()).boxed().toList();
            if (!ranks.stream().sorted().toList().equals(expectedRanks)) {
                throw new IllegalArgumentException("level " + plan.level()
                        + " training ranks must be contiguous from 1");
            }
        }
        List<Integer> expectedLevels = IntStream.rangeClosed(15, 30).boxed().toList();
        if (!levelIndex.keySet().stream().sorted().toList().equals(expectedLevels)) {
            throw new IllegalArgumentException("Victoria training catalog must cover every level 15-30");
        }

        mapsById = Map.copyOf(mapIndex);
        plansByLevel = Map.copyOf(levelIndex);
    }

    public static AgentVictoriaTrainingCatalogRepository defaultRepository() {
        return DEFAULT;
    }

    public AgentVictoriaTrainingCatalog catalog() {
        return catalog;
    }

    public Optional<AgentVictoriaTrainingCatalog.TrainingMap> findMap(int mapId) {
        return Optional.ofNullable(mapsById.get(mapId));
    }

    public List<AgentVictoriaTrainingCatalog.TrainingChoice> choicesForLevel(int level) {
        AgentVictoriaTrainingCatalog.LevelPlan plan = plansByLevel.get(level);
        return plan == null ? List.of() : plan.choices();
    }

    private static AgentVictoriaTrainingCatalogRepository loadResource(String resourcePath) {
        try (InputStream input = AgentVictoriaTrainingCatalogRepository.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("missing Victoria training catalog: " + resourcePath);
            }
            return new AgentVictoriaTrainingCatalogRepository(
                    MAPPER.readValue(input, AgentVictoriaTrainingCatalog.class));
        } catch (IOException failure) {
            throw new IllegalStateException("could not load Victoria training catalog: " + resourcePath, failure);
        }
    }
}
