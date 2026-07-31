package server.agents.progression;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Authored, reusable Victoria quest packs shared by every eligible career. */
final class AgentVictoriaSharedQuestPackCatalog {
    private static final String RESOURCE_PATH =
            "/agents/catalogs/victoria-shared-quest-packs.json";
    private static final Content CONTENT = load();

    private AgentVictoriaSharedQuestPackCatalog() {
    }

    static Pack require(String packId) {
        return CONTENT.packs().stream()
                .filter(pack -> pack.packId().equals(packId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown shared Victoria quest pack " + packId));
    }

    static Town town(int mapId) {
        return CONTENT.towns().stream()
                .filter(town -> town.mapId() == mapId)
                .findFirst()
                .orElse(null);
    }

    private static Content load() {
        try (InputStream input =
                     AgentVictoriaSharedQuestPackCatalog.class.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("missing shared quest-pack catalog " + RESOURCE_PATH);
            }
            Content content = new ObjectMapper().readValue(input, Content.class);
            if (content.schemaVersion() != 1 || content.packs().isEmpty()) {
                throw new IllegalStateException("unsupported or empty shared quest-pack catalog");
            }
            validate(content);
            return content;
        } catch (IOException failure) {
            throw new IllegalStateException("failed to load shared quest-pack catalog "
                    + RESOURCE_PATH, failure);
        }
    }

    record Content(int schemaVersion, String catalogId, List<Town> towns, List<Pack> packs) {
        Content {
            towns = towns == null ? List.of() : List.copyOf(towns);
            packs = packs == null ? List.of() : List.copyOf(packs);
        }
    }

    record Town(int mapId, int taxiNpcId, List<TaxiDestination> destinations) {
        Town {
            destinations = destinations == null ? List.of() : List.copyOf(destinations);
        }

        int selectionFor(int destinationMapId) {
            return destinations.stream()
                    .filter(destination -> destination.mapId() == destinationMapId)
                    .mapToInt(TaxiDestination::selection)
                    .findFirst()
                    .orElse(-1);
        }
    }

    record TaxiDestination(int mapId, int selection) {
    }

    record Pack(String packId, int homeTownMapId, List<Step> steps) {
        Pack {
            steps = steps == null ? List.of() : List.copyOf(steps);
        }
    }

    record Step(
            String type,
            String intention,
            int npcId,
            int questId,
            boolean complete,
            int requiredLevel,
            int mapId,
            int destinationMapId,
            int portalId,
            int exitPortalId,
            int instanceMapIdMin,
            int instanceMapIdMax,
            int itemId,
            int itemCount,
            List<Integer> preferredMobIds,
            List<Integer> incidentalMobIds,
            List<Condition> conditions,
            List<String> bundleIds) {
        Step {
            preferredMobIds = preferredMobIds == null ? List.of() : List.copyOf(preferredMobIds);
            incidentalMobIds = incidentalMobIds == null ? List.of() : List.copyOf(incidentalMobIds);
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
            bundleIds = bundleIds == null ? List.of() : List.copyOf(bundleIds);
        }
    }

    record Condition(String type, int questId, int targetId, int count) {
    }

    private static void validate(Content content) {
        if (content.catalogId() == null || content.catalogId().isBlank()) {
            throw new IllegalStateException("shared quest-pack catalog id is required");
        }
        Set<Integer> townIds = new HashSet<>();
        for (Town town : content.towns()) {
            if (town.mapId() <= 0 || town.taxiNpcId() <= 0 || !townIds.add(town.mapId())) {
                throw new IllegalStateException("invalid or duplicate shared quest-pack town");
            }
            Set<Integer> destinations = new HashSet<>();
            for (TaxiDestination destination : town.destinations()) {
                if (destination.mapId() <= 0 || destination.selection() < 0
                        || !destinations.add(destination.mapId())) {
                    throw new IllegalStateException(
                            "invalid or duplicate taxi destination for town " + town.mapId());
                }
            }
        }
        Set<String> packIds = new HashSet<>();
        for (Pack pack : content.packs()) {
            if (pack.packId() == null || pack.packId().isBlank()
                    || !packIds.add(pack.packId())
                    || !townIds.contains(pack.homeTownMapId())
                    || pack.steps().isEmpty()) {
                throw new IllegalStateException("invalid or duplicate shared quest pack");
            }
            for (Step step : pack.steps()) {
                validate(step, pack.packId(), content.towns());
            }
        }
    }

    private static void validate(Step step, String packId, List<Town> towns) {
        if (step.type() == null || step.type().isBlank()
                || step.intention() == null || step.intention().isBlank()) {
            throw new IllegalStateException("invalid step in shared quest pack " + packId);
        }
        if (step.bundleIds().stream().anyMatch(bundleId ->
                bundleId == null || bundleId.isBlank())) {
            throw new IllegalStateException(
                    "invalid bundle restriction in shared quest pack " + packId);
        }
        if ("TAXI".equals(step.type())) {
            Town source = towns.stream()
                    .filter(town -> town.mapId() == step.mapId())
                    .findFirst()
                    .orElse(null);
            boolean knownDestination = towns.stream()
                    .anyMatch(town -> town.mapId() == step.destinationMapId());
            if (source == null || !knownDestination
                    || (step.mapId() != step.destinationMapId()
                    && source.selectionFor(step.destinationMapId()) < 0)) {
                throw new IllegalStateException(
                        "invalid taxi route in shared quest pack " + packId
                                + ": " + step.mapId() + " -> " + step.destinationMapId());
            }
        }
        for (Condition condition : step.conditions()) {
            if ((!"QUEST_KILL".equals(condition.type()) && !"ITEM".equals(condition.type()))
                    || condition.targetId() <= 0 || condition.count() <= 0
                    || ("QUEST_KILL".equals(condition.type()) && condition.questId() <= 0)) {
                throw new IllegalStateException(
                        "invalid condition in shared quest pack " + packId);
            }
        }
        if ("MINI_DUNGEON_HUNT".equals(step.type())
                && (step.destinationMapId() <= 0 || step.portalId() < 0
                || step.exitPortalId() < 0 || step.instanceMapIdMin() <= 0
                || step.instanceMapIdMax() < step.instanceMapIdMin()
                || step.conditions().isEmpty())) {
            throw new IllegalStateException(
                    "invalid mini-dungeon hunt in shared quest pack " + packId);
        }
    }
}
