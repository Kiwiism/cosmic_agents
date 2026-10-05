package server.agents.integration.typesafe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One typed System One question. Instructions and criteria accept JSON structure (strings,
 * maps, lists) exactly as the TypeSafe API does; {@link #toJson()} produces the request shape.
 *
 * <ul>
 *   <li>{@link Choice}: pick one option from a closed set. Criteria map option id to its
 *       description (string, or a map such as {@code {what, not_for, examples}}).</li>
 *   <li>{@link Noul}: probability that a yes/no statement holds. Optional criteria describe the
 *       {@code true} and {@code false} sides of the boundary.</li>
 *   <li>{@link Score}: position on at least two ordered, described levels.</li>
 * </ul>
 */
public sealed interface JevQuestion permits JevQuestion.Choice, JevQuestion.Noul, JevQuestion.Score {
    String type();

    Object instructions();

    Map<String, Object> toJson();

    record Choice(Object instructions, Map<String, Object> criteria) implements JevQuestion {
        public Choice {
            requireInstructions(instructions);
            if (criteria == null || criteria.size() < 2) {
                throw new IllegalArgumentException("a Choice needs at least two options");
            }
            criteria = Map.copyOf(new LinkedHashMap<>(criteria));
        }

        @Override
        public String type() {
            return "choice";
        }

        @Override
        public Map<String, Object> toJson() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("type", type());
            json.put("instructions", instructions);
            json.put("criteria", new LinkedHashMap<>(criteria));
            return json;
        }
    }

    record Noul(Object instructions, Map<String, Object> criteria) implements JevQuestion {
        public Noul(Object instructions) {
            this(instructions, null);
        }

        public Noul {
            requireInstructions(instructions);
            criteria = criteria == null ? null : Map.copyOf(new LinkedHashMap<>(criteria));
        }

        @Override
        public String type() {
            return "noul";
        }

        @Override
        public Map<String, Object> toJson() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("type", type());
            json.put("instructions", instructions);
            if (criteria != null && !criteria.isEmpty()) {
                json.put("criteria", new LinkedHashMap<>(criteria));
            }
            return json;
        }
    }

    record Score(Object instructions, List<Object> criteria) implements JevQuestion {
        public Score {
            requireInstructions(instructions);
            if (criteria == null || criteria.size() < 2) {
                throw new IllegalArgumentException("a Score needs at least two ordered levels");
            }
            criteria = List.copyOf(criteria);
        }

        @Override
        public String type() {
            return "score";
        }

        @Override
        public Map<String, Object> toJson() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("type", type());
            json.put("instructions", instructions);
            json.put("criteria", criteria);
            return json;
        }
    }

    private static void requireInstructions(Object instructions) {
        if (instructions == null || (instructions instanceof String text && text.isBlank())) {
            throw new IllegalArgumentException("question instructions are required");
        }
    }
}
