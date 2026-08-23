package server.agents.runtime.activity.control.binding;

import server.agents.capabilities.townlife.AgentTownLifeEntryRequest;
import server.agents.capabilities.townlife.AgentTownLifeVisitRequest;
import server.agents.economy.session.CommerceParticipant;
import server.agents.field.AgentFieldIntent;
import server.agents.field.AgentFieldObservationState;
import server.agents.plans.AgentPlanEntryRequest;
import server.agents.runtime.activity.world.AgentWorldActivityRequestType;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.commerce.AgentCommerceVisitRequest;
import server.agents.runtime.field.AgentFieldEntryRequest;
import server.agents.runtime.field.AgentFieldVisitRequest;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Converts the durable string payload into one child system's public entry contract. */
public final class AgentWorldDirectiveRequestCompiler {
    public AgentWorldTypedActivityRequest compile(AgentWorldDirective directive) {
        if (directive == null || directive.targetActivityKind() == null
                || directive.requestType() == null) {
            throw new IllegalArgumentException("an activity directive is required");
        }
        AgentWorldTypedActivityRequest compiled = switch (directive.requestType()) {
            case AUTHORED_PLAN -> questing(directive);
            case INDIVIDUAL_QUEST -> individualQuest(directive);
            case FIELD_VISIT -> hunting(directive);
            case TOWN_LIFE_VISIT -> townLife(directive);
            case COMMERCE_VISIT -> commerce(directive);
            case PARTY_QUEST_VISIT -> partyQuest(directive);
            case OBSERVE_ONLY -> throw new IllegalArgumentException(
                    "observe-only requests cannot be admitted as an activity");
        };
        if (compiled.kind() != directive.targetActivityKind()) {
            throw new IllegalArgumentException("request type does not match target activity kind");
        }
        return compiled;
    }

    private AgentWorldTypedActivityRequest questing(AgentWorldDirective directive) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        directive.parameters().forEach((key, value) -> {
            if (key.startsWith("input.")) inputs.put(key.substring("input.".length()), value);
        });
        String planId = directive.parameters().getOrDefault("planId", directive.requestId());
        return new AgentWorldTypedActivityRequest.Questing(new AgentPlanEntryRequest(
                directive.directiveId(), caller(directive), planId, inputs, null));
    }

    private AgentWorldTypedActivityRequest individualQuest(AgentWorldDirective directive) {
        Map<String, String> values = directive.parameters();
        int questId = positiveInt(values, "questId");
        int targetLevel = positiveInt(values, "targetLevel");
        if (targetLevel < 16 || targetLevel > 30) {
            throw new IllegalArgumentException("targetLevel must be between 16 and 30");
        }
        return new AgentWorldTypedActivityRequest.Questing(new AgentPlanEntryRequest(
                directive.directiveId(), caller(directive), "victoria-individual-quest",
                Map.of("targetLevel", targetLevel, "questsEnabled", true,
                        "questId", questId), null));
    }

    private AgentWorldTypedActivityRequest hunting(AgentWorldDirective directive) {
        Map<String, String> values = directive.parameters();
        Set<Integer> mobIds = integers(values.get("mobIds"));
        Map<Integer, Integer> kills = kills(values.get("requiredKills"));
        AgentFieldIntent.Type type = enumValue(
                values.getOrDefault("intent", "FREE_GRIND"), AgentFieldIntent.Type.class, "intent");
        AgentFieldIntent intent = new AgentFieldIntent(type, directive.requestId(), mobIds, kills,
                type == AgentFieldIntent.Type.QUEST_VISITOR);
        AgentFieldVisitRequest visit = new AgentFieldVisitRequest(
                positiveInt(values, "mapId"), intent,
                booleanValue(values, "acceptingQuestVisitors"),
                positiveInt(values, "maximumParticipants"),
                booleanValue(values, "restAllowed"),
                enumValue(values.getOrDefault("narration", "SUMMARY"),
                        AgentFieldObservationState.NarrationLevel.class, "narration"));
        return new AgentWorldTypedActivityRequest.Hunting(new AgentFieldEntryRequest(
                directive.directiveId(), caller(directive), visit));
    }

    private AgentWorldTypedActivityRequest townLife(AgentWorldDirective directive) {
        Map<String, String> values = directive.parameters();
        AgentTownLifeVisitRequest visit = new AgentTownLifeVisitRequest(
                positiveInt(values, "mapId"),
                enumValue(required(values, "purpose"), AgentTownLifeVisitRequest.Purpose.class,
                        "purpose"),
                directive.reason(), nonNegativeLong(values, "freeTimeBudgetMs"));
        return new AgentWorldTypedActivityRequest.TownLife(AgentTownLifeEntryRequest.external(
                directive.directiveId(), caller(directive), visit));
    }

    private AgentWorldTypedActivityRequest commerce(AgentWorldDirective directive) {
        Map<String, String> values = directive.parameters();
        CommerceParticipant participant = new CommerceParticipant(
                Integer.toString(directive.agentId()), required(values, "jobFamily"),
                fraction(values, "dailyActivityFraction"), fraction(values, "riskTolerance"),
                fraction(values, "liquidityPreference"), fraction(values, "upgradeAggressiveness"),
                fraction(values, "shoppingPatience"), fraction(values, "stallWillingness"),
                positiveInt(values, "priceMemoryHours"),
                fraction(values, "negotiationAggressiveness"), fraction(values, "chairInterest"));
        Map<String, String> attributes = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key.startsWith("attribute.")) {
                attributes.put(key.substring("attribute.".length()), value);
            }
        });
        AgentCommerceVisitRequest visit = new AgentCommerceVisitRequest(
                directive.directiveId(), caller(directive), participant,
                enumValue(required(values, "purpose"), AgentCommerceVisitRequest.Purpose.class,
                        "purpose"),
                positiveLong(values, "maximumDurationMs"),
                nonNegativeLong(values, "maximumIdleMs"), attributes);
        return new AgentWorldTypedActivityRequest.Commerce(visit);
    }

    private AgentWorldTypedActivityRequest partyQuest(AgentWorldDirective directive) {
        Map<String, String> values = directive.parameters();
        String scenarioId = required(values, "scenarioId");
        int partySize = positiveInt(values, "partySize");
        int maximumRuns = positiveInt(values, "maximumRuns");
        if (!"kpq".equalsIgnoreCase(scenarioId)) {
            throw new IllegalArgumentException("only the KPQ lobby is currently available");
        }
        if (partySize < 3 || partySize > 4) {
            throw new IllegalArgumentException("KPQ partySize must be three or four");
        }
        if (maximumRuns != 1) {
            throw new IllegalArgumentException("KPQ maximumRuns must be one");
        }
        return new AgentWorldTypedActivityRequest.PartyQuest(
                new AgentWorldTypedActivityRequest.AgentPartyQuestVisitRequest(
                        directive.directiveId(), caller(directive), scenarioId,
                        partySize, maximumRuns));
    }

    private String caller(AgentWorldDirective directive) {
        return "world-director:" + directive.source().name().toLowerCase();
    }

    private static Set<Integer> integers(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        Set<Integer> result = new LinkedHashSet<>();
        for (String token : raw.split(",")) result.add(positiveInt(token, "mob id"));
        return Set.copyOf(result);
    }

    private static Map<Integer, Integer> kills(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (String token : raw.split(",")) {
            String[] pair = token.trim().split(":", -1);
            if (pair.length != 2) throw new IllegalArgumentException("invalid requiredKills entry");
            result.put(positiveInt(pair[0], "mob id"), positiveInt(pair[1], "kill count"));
        }
        return Map.copyOf(result);
    }

    private static boolean booleanValue(Map<String, String> values, String key) {
        String raw = required(values, key);
        if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return Boolean.parseBoolean(raw);
    }

    private static int positiveInt(Map<String, String> values, String key) {
        return positiveInt(required(values, key), key);
    }

    private static int positiveInt(String raw, String label) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value > 0) return value;
        } catch (RuntimeException ignored) {
        }
        throw new IllegalArgumentException(label + " must be a positive integer");
    }

    private static long positiveLong(Map<String, String> values, String key) {
        long value = nonNegativeLong(values, key);
        if (value <= 0L) throw new IllegalArgumentException(key + " must be positive");
        return value;
    }

    private static long nonNegativeLong(Map<String, String> values, String key) {
        try {
            long value = Long.parseLong(required(values, key));
            if (value >= 0L) return value;
        } catch (RuntimeException ignored) {
        }
        throw new IllegalArgumentException(key + " must be a non-negative integer");
    }

    private static double fraction(Map<String, String> values, String key) {
        try {
            double value = Double.parseDouble(required(values, key));
            if (Double.isFinite(value) && value >= 0.0d && value <= 1.0d) return value;
        } catch (RuntimeException ignored) {
        }
        throw new IllegalArgumentException(key + " must be between zero and one");
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value.trim();
    }

    private static <E extends Enum<E>> E enumValue(String raw, Class<E> type, String label) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid " + label + ": " + raw);
        }
    }
}
