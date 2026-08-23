package server.agents.progression.questcatalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Loads generated quest truth and applies a small, separately reviewable guidance overlay. */
public final class AgentQuestCatalogRepository {
    private static final String FACTS_RESOURCE =
            "/agents/catalogs/adaptive/victoria-quest-facts.json";
    private static final String HUNT_RESOURCE =
            "/agents/catalogs/adaptive/victoria-quest-hunt-index.json";
    private static final String GUIDANCE_RESOURCE =
            "/agents/catalogs/victoria-quest-attempt-guidance.json";
    private static final AgentQuestCatalogRepository DEFAULT = loadDefault();

    private final AgentQuestCatalog catalog;
    private final Map<Integer, AgentQuestDefinition> byQuestId;

    public AgentQuestCatalogRepository(AgentQuestCatalog catalog) {
        if (catalog == null) throw new IllegalArgumentException("quest catalog is required");
        this.catalog = catalog;
        Map<Integer, AgentQuestDefinition> index = new LinkedHashMap<>();
        for (AgentQuestDefinition entry : catalog.entries()) {
            if (index.putIfAbsent(entry.questId(), entry) != null) {
                throw new IllegalArgumentException("duplicate universal quest " + entry.questId());
            }
        }
        byQuestId = Map.copyOf(index);
    }

    public static AgentQuestCatalogRepository defaultRepository() {
        return DEFAULT;
    }

    public AgentQuestCatalog catalog() {
        return catalog;
    }

    public Optional<AgentQuestDefinition> find(int questId) {
        return Optional.ofNullable(byQuestId.get(questId));
    }

    public AgentQuestEligibility evaluate(int questId, AgentQuestEligibilityContext context) {
        AgentQuestDefinition quest = find(questId).orElse(null);
        if (quest == null || context == null) {
            return new AgentQuestEligibility(
                    AgentQuestEligibility.Status.MANUAL_REVIEW_REQUIRED,
                    "quest definition and live eligibility context are required");
        }
        if (quest.selectionDisposition() == AgentQuestSelectionDisposition.REVIEW_BLOCKED) {
            return result(AgentQuestEligibility.Status.MANUAL_REVIEW_REQUIRED, "quest is review-blocked");
        }
        if (quest.selectionDisposition() == AgentQuestSelectionDisposition.CAPABILITY_GATED) {
            return result(AgentQuestEligibility.Status.CAPABILITY_GATED, "quest requires an unavailable capability");
        }
        int questState = context.questStates().getOrDefault(questId, 0);
        if (questState >= 2) {
            return result(AgentQuestEligibility.Status.ALREADY_COMPLETED,
                    "quest is already complete in authoritative state");
        }
        if (questState == 1) {
            return result(AgentQuestEligibility.Status.ALREADY_IN_PROGRESS,
                    "quest is already active and should be resumed instead of selected");
        }
        if ((quest.minimumLevel() != null && context.level() < quest.minimumLevel())
                || (quest.maximumLevel() != null && context.level() > quest.maximumLevel())) {
            return result(AgentQuestEligibility.Status.LEVEL_LOCKED, "server quest level bounds are not satisfied");
        }
        if (!quest.allowedJobIds().isEmpty() && !quest.allowedJobIds().contains(context.jobId())) {
            return result(AgentQuestEligibility.Status.JOB_LOCKED, "job is not eligible for this quest");
        }
        for (AgentQuestDefinition.Prerequisite prerequisite : quest.prerequisites()) {
            if (context.questStates().getOrDefault(prerequisite.questId(), 0)
                    < prerequisite.requiredState()) {
                return result(AgentQuestEligibility.Status.PREREQUISITE_LOCKED,
                        "quest " + prerequisite.questId() + " prerequisite is incomplete");
            }
        }
        for (AgentQuestDefinition.StartItemRequirement requirement : quest.startItemRequirements()) {
            if (context.itemCounts().getOrDefault(requirement.itemId(), 0)
                    < requirement.requiredCount()) {
                String item = requirement.itemName().isEmpty()
                        ? "item " + requirement.itemId() : requirement.itemName();
                String producers = requirement.producerQuestIds().isEmpty() ? ""
                        : "; obtainable from quest(s) " + requirement.producerQuestIds();
                return result(AgentQuestEligibility.Status.START_ITEM_LOCKED,
                        "requires " + requirement.requiredCount() + " " + item + producers);
            }
        }
        AgentQuestAttemptRequirements requirements = quest.attemptRequirements();
        if (context.estimatedHitChanceBasisPoints() < requirements.minimumHitChanceBasisPoints()) {
            return result(AgentQuestEligibility.Status.ACCURACY_INSUFFICIENT,
                    "estimated hit chance is below the authored threshold");
        }
        if (context.freeInventorySlots() < requirements.minimumFreeInventorySlots()) {
            return result(AgentQuestEligibility.Status.INVENTORY_INSUFFICIENT,
                    "free inventory slots are below the authored reserve");
        }
        if (context.hpPotionCount() < requirements.minimumHpPotionReserve()
                || context.mpPotionCount() < requirements.minimumMpPotionReserve()) {
            return result(AgentQuestEligibility.Status.SUPPLIES_INSUFFICIENT,
                    "potion reserves are below the authored threshold");
        }
        return result(AgentQuestEligibility.Status.ELIGIBLE, "live quest requirements are satisfied");
    }

    public List<AgentQuestDefinition> selectable(AgentQuestEligibilityContext context) {
        if (context == null) return List.of();
        return catalog.entries().stream()
                .filter(entry -> evaluate(entry.questId(), context).eligible())
                .toList();
    }

    private static AgentQuestEligibility result(AgentQuestEligibility.Status status, String reason) {
        return new AgentQuestEligibility(status, reason);
    }

    private static AgentQuestCatalogRepository loadDefault() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream factsInput = resource(FACTS_RESOURCE);
             InputStream huntInput = resource(HUNT_RESOURCE);
             InputStream guidanceInput = resource(GUIDANCE_RESOURCE)) {
            JsonNode facts = mapper.readTree(factsInput);
            JsonNode hunt = mapper.readTree(huntInput);
            JsonNode guidance = mapper.readTree(guidanceInput);
            String factsRevision = requiredText(facts, "revision");
            if (!factsRevision.equals(requiredText(hunt, "revision"))) {
                throw new IllegalArgumentException("quest facts and hunt index revisions differ");
            }
            Map<String, List<AgentQuestDefinition.HuntMap>> huntMaps = huntMaps(hunt);
            Map<Integer, Guidance> overlays = guidance(guidance);
            Defaults defaults = defaults(guidance.path("defaults"));
            List<AgentQuestDefinition> entries = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            for (JsonNode node : facts.path("entries")) {
                int questId = node.path("questId").asInt();
                if (!seen.add(questId)) throw new IllegalArgumentException("duplicate quest fact " + questId);
                Guidance overlay = overlays.getOrDefault(questId, Guidance.EMPTY);
                entries.add(definition(node, huntMaps, defaults, overlay));
            }
            if (!seen.containsAll(overlays.keySet())) {
                throw new IllegalArgumentException("quest guidance references missing generated facts");
            }
            AgentQuestCatalog catalog = new AgentQuestCatalog(
                    guidance.path("schemaVersion").asInt(),
                    requiredText(guidance, "catalogId"), factsRevision,
                    requiredText(guidance, "revision"), entries);
            return new AgentQuestCatalogRepository(catalog);
        } catch (IOException failure) {
            throw new IllegalStateException("could not load universal Victoria quest catalog", failure);
        }
    }

    private static AgentQuestDefinition definition(
            JsonNode node,
            Map<String, List<AgentQuestDefinition.HuntMap>> huntMaps,
            Defaults defaults,
            Guidance guidance) {
        int questId = node.path("questId").asInt();
        Integer minLevel = nullableInt(node.get("minLevel"));
        int recommendedLevel = Math.max(minLevel == null ? 1 : minLevel,
                guidance.recommendedLevel() == null ? 1 : guidance.recommendedLevel());
        List<AgentQuestDefinition.Objective> objectives = new ArrayList<>();
        for (JsonNode objective : node.path("objectives")) {
            String objectiveId = requiredText(objective, "objectiveId");
            objectives.add(new AgentQuestDefinition.Objective(
                    objectiveId, requiredText(objective, "type"), objective.path("targetId").asInt(),
                    objective.path("targetName").asText(""), objective.path("requiredCount").asInt(),
                    integers(objective.path("sourceMobIds")),
                    huntMaps.getOrDefault(questId + ":" + objectiveId, List.of())));
        }
        for (JsonNode objective : node.path("nonHuntingAcquisitionObjectives")) {
            objectives.add(new AgentQuestDefinition.Objective(
                    requiredText(objective, "objectiveId"), requiredText(objective, "type"),
                    objective.path("targetId").asInt(), objective.path("targetName").asText(""),
                    objective.path("requiredCount").asInt(), List.of(), List.of()));
        }
        List<AgentQuestDefinition.Prerequisite> prerequisites = new ArrayList<>();
        for (JsonNode prerequisite : nodes(node.get("prerequisiteRequirements"))) {
            prerequisites.add(new AgentQuestDefinition.Prerequisite(
                    prerequisite.path("questId").asInt(), prerequisite.path("state").asInt()));
        }
        List<AgentQuestDefinition.StartItemRequirement> startItemRequirements = new ArrayList<>();
        for (JsonNode requirement : nodes(node.get("startItemRequirements"))) {
            startItemRequirements.add(new AgentQuestDefinition.StartItemRequirement(
                    requirement.path("itemId").asInt(), requirement.path("itemName").asText(""),
                    requirement.path("requiredCount").asInt(),
                    requirement.path("consumedOnStart").asBoolean(false),
                    integers(requirement.path("producerQuestIds"))));
        }
        AgentQuestAttemptRequirements requirements = new AgentQuestAttemptRequirements(
                guidance.minimumHitChanceBasisPoints(defaults),
                guidance.minimumFreeInventorySlots(defaults),
                guidance.minimumHpPotionReserve(defaults),
                guidance.minimumMpPotionReserve(defaults));
        AgentQuestDefinition.Endpoint start = new AgentQuestDefinition.Endpoint(
                node.path("startNpcId").asInt(), integers(node.path("startMapIds")));
        AgentQuestDefinition.Endpoint completion = new AgentQuestDefinition.Endpoint(
                node.path("completeNpcId").asInt(), integers(node.path("completeMapIds")));
        AgentQuestSelectionDisposition disposition =
                AgentQuestSelectionDisposition.fromCatalog(node.path("selectionDisposition").asText());
        if (!start.complete() || !completion.complete()) {
            disposition = AgentQuestSelectionDisposition.REVIEW_BLOCKED;
        }
        return new AgentQuestDefinition(
                questId, requiredText(node, "questName"), minLevel, nullableInt(node.get("maxLevel")),
                recommendedLevel, Set.copyOf(integers(node.path("jobs"))), prerequisites,
                startItemRequirements,
                node.path("autonomousStartAllowed").asBoolean(false)
                        && start.complete() && completion.complete(),
                disposition, start, completion,
                objectives, requirements, guidance.rationale(), strings(node.path("warnings")));
    }

    private static Map<String, List<AgentQuestDefinition.HuntMap>> huntMaps(JsonNode root) {
        Map<String, List<AgentQuestDefinition.HuntMap>> maps = new HashMap<>();
        for (JsonNode quest : root.path("entries")) {
            int questId = quest.path("questId").asInt();
            for (JsonNode objective : quest.path("objectives")) {
                List<AgentQuestDefinition.HuntMap> candidates = new ArrayList<>();
                for (JsonNode candidate : objective.path("candidates")) {
                    candidates.add(new AgentQuestDefinition.HuntMap(
                            candidate.path("rank").asInt(), candidate.path("mapId").asInt(),
                            candidate.path("mapName").asText(""), integers(candidate.path("targetMobIds")),
                            candidate.path("maxMobLevel").asInt(), candidate.path("recommendedAgents").asInt(),
                            candidate.path("maximumAgents").asInt()));
                }
                maps.put(questId + ":" + requiredText(objective, "objectiveId"), List.copyOf(candidates));
            }
        }
        return Map.copyOf(maps);
    }

    private static Map<Integer, Guidance> guidance(JsonNode root) {
        Map<Integer, Guidance> result = new HashMap<>();
        for (JsonNode node : root.path("quests")) {
            int questId = node.path("questId").asInt();
            Guidance guidance = new Guidance(
                    nullableInt(node.get("recommendedLevel")),
                    nullableInt(node.get("minimumHitChanceBasisPoints")),
                    nullableInt(node.get("minimumFreeInventorySlots")),
                    nullableInt(node.get("minimumHpPotionReserve")),
                    nullableInt(node.get("minimumMpPotionReserve")),
                    node.path("rationale").asText(""));
            if (questId <= 0 || result.putIfAbsent(questId, guidance) != null) {
                throw new IllegalArgumentException("duplicate or invalid quest guidance " + questId);
            }
        }
        return Map.copyOf(result);
    }

    private static Defaults defaults(JsonNode node) {
        return new Defaults(node.path("minimumHitChanceBasisPoints").asInt(),
                node.path("minimumFreeInventorySlots").asInt(),
                node.path("minimumHpPotionReserve").asInt(),
                node.path("minimumMpPotionReserve").asInt());
    }

    private static InputStream resource(String path) {
        InputStream input = AgentQuestCatalogRepository.class.getResourceAsStream(path);
        if (input == null) throw new IllegalStateException("missing quest catalog resource: " + path);
        return input;
    }

    private static Integer nullableInt(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? null : node.asInt();
    }

    private static List<Integer> integers(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<Integer> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asInt()));
        return List.copyOf(values);
    }

    private static List<String> strings(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText("")));
        return List.copyOf(values);
    }

    private static List<JsonNode> nodes(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return List.of();
        if (!node.isArray()) return List.of(node);
        List<JsonNode> values = new ArrayList<>();
        node.forEach(values::add);
        return List.copyOf(values);
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("missing quest catalog field " + field);
        return value;
    }

    private record Defaults(int minimumHitChanceBasisPoints, int minimumFreeInventorySlots,
                            int minimumHpPotionReserve, int minimumMpPotionReserve) {
    }

    private record Guidance(Integer recommendedLevel, Integer minimumHitChanceBasisPoints,
                            Integer minimumFreeInventorySlots, Integer minimumHpPotionReserve,
                            Integer minimumMpPotionReserve, String rationale) {
        private static final Guidance EMPTY = new Guidance(null, null, null, null, null, "");

        private Guidance {
            rationale = rationale == null ? "" : rationale.trim();
        }

        int minimumHitChanceBasisPoints(Defaults defaults) {
            return minimumHitChanceBasisPoints == null
                    ? defaults.minimumHitChanceBasisPoints() : minimumHitChanceBasisPoints;
        }

        int minimumFreeInventorySlots(Defaults defaults) {
            return minimumFreeInventorySlots == null
                    ? defaults.minimumFreeInventorySlots() : minimumFreeInventorySlots;
        }

        int minimumHpPotionReserve(Defaults defaults) {
            return minimumHpPotionReserve == null
                    ? defaults.minimumHpPotionReserve() : minimumHpPotionReserve;
        }

        int minimumMpPotionReserve(Defaults defaults) {
            return minimumMpPotionReserve == null
                    ? defaults.minimumMpPotionReserve() : minimumMpPotionReserve;
        }
    }
}
