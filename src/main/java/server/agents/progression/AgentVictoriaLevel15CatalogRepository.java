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

/** Loads and cross-validates the level-15 content catalog against durable career bundles. */
public final class AgentVictoriaLevel15CatalogRepository {
    private static final String DEFAULT_RESOURCE = "/agents/catalogs/victoria-level15-mvp-catalog.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final AgentVictoriaLevel15CatalogRepository DEFAULT = loadResource(DEFAULT_RESOURCE);

    private final AgentVictoriaLevel15Catalog catalog;
    private final Map<String, AgentVictoriaLevel15Catalog.Career> byBundleId;
    private final Map<Integer, AgentVictoriaLevel15Catalog.Career> byFirstJobId;
    private final Map<String, AgentVictoriaLevel15Catalog.StartVariant> startVariantsById;
    private final Map<String, AgentVictoriaLevel15Catalog.QuestPack> questPacksById;
    private final Map<Integer, AgentVictoriaLevel15Catalog.InteractionQuest> interactionQuestsById;

    AgentVictoriaLevel15CatalogRepository(AgentVictoriaLevel15Catalog catalog,
                                           AgentCareerBuildBundleRepository bundles) {
        this.catalog = catalog;
        Map<String, AgentVictoriaLevel15Catalog.Career> bundleIndex = new LinkedHashMap<>();
        Map<Integer, AgentVictoriaLevel15Catalog.Career> jobIndex = new LinkedHashMap<>();
        Map<String, AgentVictoriaLevel15Catalog.StartVariant> variantIndex = new LinkedHashMap<>();
        Map<String, AgentVictoriaLevel15Catalog.QuestPack> packIndex = new LinkedHashMap<>();
        Map<Integer, AgentVictoriaLevel15Catalog.InteractionQuest> interactionQuestIndex = new LinkedHashMap<>();
        for (AgentVictoriaLevel15Catalog.StartVariant variant : catalog.startVariants()) {
            if (variantIndex.putIfAbsent(variant.variantId(), variant) != null) {
                throw new IllegalArgumentException("duplicate Victoria start variant: " + variant.variantId());
            }
        }
        Set<String> corridorIds = new HashSet<>();
        for (AgentVictoriaLevel15Catalog.RouteCorridor corridor : catalog.routeCorridors()) {
            if (!corridorIds.add(corridor.corridorId())) {
                throw new IllegalArgumentException("duplicate Victoria route corridor: " + corridor.corridorId());
            }
        }
        for (AgentVictoriaLevel15Catalog.InteractionQuest quest : catalog.interactionQuests()) {
            if (interactionQuestIndex.putIfAbsent(quest.questId(), quest) != null) {
                throw new IllegalArgumentException("duplicate Victoria interaction quest: " + quest.questId());
            }
        }
        AgentVictoriaQuestRuntimeCatalogRepository questRepository =
                AgentVictoriaQuestRuntimeCatalogRepository.defaultRepository();
        for (AgentVictoriaLevel15Catalog.QuestPack pack : catalog.questPacks()) {
            if (packIndex.putIfAbsent(pack.packId(), pack) != null) {
                throw new IllegalArgumentException("duplicate Victoria quest pack: " + pack.packId());
            }
            for (int questId : pack.questIds()) {
                if (questRepository.find(questId).isEmpty()
                        && !interactionQuestIndex.containsKey(questId)) {
                    throw new IllegalArgumentException("Victoria quest pack " + pack.packId()
                            + " references an unavailable quest " + questId);
                }
            }
        }
        for (AgentVictoriaLevel15Catalog.Career career : catalog.careers()) {
            if (jobIndex.putIfAbsent(career.firstJobId(), career) != null) {
                throw new IllegalArgumentException("duplicate Victoria first job: " + career.firstJobId());
            }
            for (String bundleId : career.supportedBundleIds()) {
                AgentCareerBuildBundle bundle = bundles.find(bundleId).orElseThrow(
                        () -> new IllegalArgumentException("unknown career bundle in Victoria catalog: " + bundleId));
                validateCareer(bundle, career);
                if (bundleIndex.putIfAbsent(bundleId, career) != null) {
                    throw new IllegalArgumentException("duplicate Victoria career bundle: " + bundleId);
                }
            }
            validateCatchUpPlan(career, packIndex);
        }
        for (AgentCareerBuildBundle bundle : bundles.all()) {
            if (!bundleIndex.containsKey(bundle.bundleId())) {
                throw new IllegalArgumentException("career bundle is missing from Victoria catalog: "
                        + bundle.bundleId());
            }
        }
        byBundleId = Map.copyOf(bundleIndex);
        byFirstJobId = Map.copyOf(jobIndex);
        startVariantsById = Map.copyOf(variantIndex);
        questPacksById = Map.copyOf(packIndex);
        interactionQuestsById = Map.copyOf(interactionQuestIndex);
    }

    public static AgentVictoriaLevel15CatalogRepository defaultRepository() {
        return DEFAULT;
    }

    public AgentVictoriaLevel15Catalog catalog() {
        return catalog;
    }

    public AgentVictoriaLevel15Catalog.Career careerFor(AgentCareerBuildBundle bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("a career bundle is required");
        }
        return Optional.ofNullable(byBundleId.get(bundle.bundleId())).orElseThrow(
                () -> new IllegalArgumentException("unsupported Victoria career bundle " + bundle.bundleId()));
    }

    public AgentVictoriaLevel15Catalog.Career careerForFirstJob(int firstJobId) {
        return Optional.ofNullable(byFirstJobId.get(firstJobId)).orElseThrow(
                () -> new IllegalArgumentException("unsupported Victoria first job " + firstJobId));
    }

    public AgentVictoriaLevel15Catalog.StartVariant startVariant(String variantId) {
        return Optional.ofNullable(startVariantsById.get(variantId)).orElseThrow(
                () -> new IllegalArgumentException("unknown Victoria start variant " + variantId));
    }

    public AgentVictoriaLevel15Catalog.QuestPack questPack(String packId) {
        return Optional.ofNullable(questPacksById.get(packId)).orElseThrow(
                () -> new IllegalArgumentException("unknown Victoria quest pack " + packId));
    }

    public Optional<AgentVictoriaLevel15Catalog.InteractionQuest> interactionQuest(int questId) {
        return Optional.ofNullable(interactionQuestsById.get(questId));
    }

    private static void validateCareer(AgentCareerBuildBundle bundle,
                                       AgentVictoriaLevel15Catalog.Career career) {
        List<Integer> questIds = career.trainingSteps().stream()
                .map(AgentVictoriaLevel15Catalog.TrainingStep::questId)
                .toList();
        if (bundle.firstJobId() != career.firstJobId()
                || bundle.instructorNpcId() != career.instructorNpcId()
                || bundle.instructorMapId() != career.instructorMapId()
                || !bundle.instructorTrainingQuestIds().equals(questIds)) {
            throw new IllegalArgumentException("Victoria catalog conflicts with career bundle " + bundle.bundleId());
        }
    }

    private static void validateCatchUpPlan(
            AgentVictoriaLevel15Catalog.Career career,
            Map<String, AgentVictoriaLevel15Catalog.QuestPack> packs) {
        AgentVictoriaLevel15Catalog.CatchUpPlan plan = career.catchUpPlan();
        if (!packs.containsKey(plan.homePackId())) {
            throw new IllegalArgumentException("unknown home quest pack for job " + career.firstJobId()
                    + ": " + plan.homePackId());
        }
        if (plan.afterHomeStrategy() == AgentVictoriaLevel15Catalog.AfterHomeStrategy.ROTATION_PACK
                && !packs.containsKey(plan.rotationPackId())) {
            throw new IllegalArgumentException("unknown rotation quest pack for job " + career.firstJobId()
                    + ": " + plan.rotationPackId());
        }
    }

    private static AgentVictoriaLevel15CatalogRepository loadResource(String resourcePath) {
        try (InputStream input = AgentVictoriaLevel15CatalogRepository.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("missing Victoria level-15 catalog: " + resourcePath);
            }
            AgentVictoriaLevel15Catalog catalog = MAPPER.readValue(input, AgentVictoriaLevel15Catalog.class);
            return new AgentVictoriaLevel15CatalogRepository(
                    catalog, AgentCareerBuildBundleRepository.defaultRepository());
        } catch (IOException failure) {
            throw new IllegalStateException("could not load Victoria level-15 catalog: " + resourcePath, failure);
        }
    }
}
