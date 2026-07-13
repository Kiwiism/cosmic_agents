package server.agents.plans.amherst;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AmherstPlanCardLoader {
    private final ObjectMapper mapper;
    private final AmherstPlanValidator validator;

    public AmherstPlanCardLoader() {
        this(new ObjectMapper(), new AmherstPlanValidator());
    }

    public AmherstPlanCardLoader(ObjectMapper mapper, AmherstPlanValidator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    public AmherstPlanCard load(Path path) throws IOException, AmherstPlanValidationException {
        JsonNode root;
        try {
            root = mapper.readTree(path.toFile());
        } catch (IOException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new AmherstPlanValidationException(List.of(new AmherstPlanValidationIssue(
                    AmherstPlanValidationCode.MALFORMED_JSON, "$", failure.getMessage())));
        }
        if (root == null || !root.isObject()) {
            throw new AmherstPlanValidationException(List.of(new AmherstPlanValidationIssue(
                    AmherstPlanValidationCode.MALFORMED_JSON, "$", "plan card root must be an object")));
        }
        AmherstPlanCard card;
        try {
            card = parse(root);
        } catch (RuntimeException failure) {
            throw new AmherstPlanValidationException(List.of(new AmherstPlanValidationIssue(
                    AmherstPlanValidationCode.MALFORMED_JSON, "$", failure.getMessage())));
        }
        List<AmherstPlanValidationIssue> issues = validator.validate(card);
        if (!issues.isEmpty()) {
            throw new AmherstPlanValidationException(issues);
        }
        return card;
    }

    AmherstPlanCard parse(JsonNode root) {
        JsonNode focus = root.path("focusPolicy");
        JsonNode entry = root.path("entryCriteria");
        JsonNode exit = root.path("exitCriteria");
        JsonNode questPolicy = root.path("questPolicy");
        Set<Integer> forbiddenMaps = new HashSet<>();
        Set<Integer> forbiddenNpcs = new HashSet<>();
        for (JsonNode action : exit.path("forbiddenActions")) {
            if ("map-travel".equals(text(action, "type")) && action.has("mapId")) {
                forbiddenMaps.add(action.path("mapId").asInt());
            }
            if ("npc-travel".equals(text(action, "type")) && action.has("npcId")) {
                forbiddenNpcs.add(action.path("npcId").asInt());
            }
        }

        List<AmherstPlanObjective> objectives = new ArrayList<>();
        int routeIndex = 0;
        for (JsonNode route : root.path("route")) {
            int mapId = route.path("mapId").asInt();
            int objectiveIndex = 0;
            for (JsonNode objective : route.path("objectives")) {
                String kindName = text(objective, "kind");
                String generatedId = String.format("r%03d-o%03d-%s",
                        routeIndex, objectiveIndex, kindName == null ? "unknown" : kindName);
                objectives.add(new AmherstPlanObjective(
                        textOrDefault(objective, "objectiveId", generatedId),
                        AmherstPlanObjectiveKind.fromJsonName(kindName),
                        routeIndex,
                        objectiveIndex,
                        mapId,
                        nullableInt(objective, objective.has("forQuestId") ? "forQuestId" : "questId"),
                        ints(objective.path("questIds")),
                        nullableInt(objective, "npcId"),
                        ints(objective.path("npcIds")),
                        nullableInt(objective, "itemId"),
                        ints(objective.path("itemIds")),
                        ints(objective.path("mobIds")),
                        ints(objective.path("counts")),
                        text(objective, "mode"),
                        text(objective, "reason")));
                objectiveIndex++;
            }
            routeIndex++;
        }

        return new AmherstPlanCard(
                root.path("schemaVersion").asInt(),
                text(root, "planId"),
                text(root, "title"),
                text(root, "category"),
                text(root, "priority"),
                text(root, "status"),
                text(root, "objectiveMode"),
                new AmherstPlanCard.FocusPolicy(
                        text(focus, "focusLevel"),
                        focus.path("allowSidetracks").asBoolean(),
                        strings(focus.path("allowedSidetrackTypes")),
                        text(focus, "returnToPlan")),
                new AmherstPlanCard.EntryCriteria(
                        entry.path("requiredStartMapId").asInt(),
                        text(entry, "requiredRegion"),
                        text(entry, "requiredCharacterState")),
                new AmherstPlanCard.ExitCriteria(
                        text(exit, "completeWhen"),
                        exit.path("finalMapId").asInt(),
                        Set.copyOf(ints(exit.path("startOnlyQuestIds"))),
                        Set.copyOf(ints(exit.path("blockedCompletedQuestIds"))),
                        forbiddenMaps,
                        forbiddenNpcs),
                Set.copyOf(ints(questPolicy.path("requiredQuestIds"))),
                Set.copyOf(ints(questPolicy.path("excludedQuestIds"))),
                objectives);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static String textOrDefault(JsonNode node, String field, String defaultValue) {
        String value = text(node, field);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isIntegralNumber() ? value.asInt() : null;
    }

    private static List<Integer> ints(JsonNode node) {
        List<Integer> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(value -> {
                if (value.isIntegralNumber()) {
                    values.add(value.asInt());
                }
            });
        }
        return List.copyOf(values);
    }

    private static Set<String> strings(JsonNode node) {
        Set<String> values = new HashSet<>();
        if (node.isArray()) {
            node.forEach(value -> {
                if (value.isTextual()) {
                    values.add(value.asText());
                }
            });
        }
        return Set.copyOf(values);
    }
}
