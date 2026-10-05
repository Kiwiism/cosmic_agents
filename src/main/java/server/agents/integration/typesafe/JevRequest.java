package server.agents.integration.typesafe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A System One request: one {@code state} (string, map, or list) and a named set of questions
 * that are all evaluated against that state in a single round trip. {@code kind} names the
 * judgment for usage accounting (for example {@code chat-intent}); it is never sent to the API.
 */
public record JevRequest(Object state, Map<String, JevQuestion> questions, String kind) {
    public static final String DEFAULT_KIND = "other";

    public JevRequest(Object state, Map<String, JevQuestion> questions) {
        this(state, questions, DEFAULT_KIND);
    }

    public JevRequest {
        kind = kind == null || kind.isBlank() ? DEFAULT_KIND : kind.trim();
        if (state == null) {
            throw new IllegalArgumentException("request state is required");
        }
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("at least one question is required");
        }
        questions = Map.copyOf(new LinkedHashMap<>(questions));
    }

    /** JSON body for {@code POST /v1/systemone}. */
    public Map<String, Object> toJson(String model) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("model", model);
        json.put("state", state);
        Map<String, Object> questionJson = new LinkedHashMap<>();
        for (Map.Entry<String, JevQuestion> entry : questions.entrySet()) {
            questionJson.put(entry.getKey(), entry.getValue().toJson());
        }
        json.put("questions", questionJson);
        return json;
    }

    public static Builder builder(Object state) {
        return new Builder(state);
    }

    public static final class Builder {
        private final Object state;
        private final LinkedHashMap<String, JevQuestion> questions = new LinkedHashMap<>();
        private String kind = DEFAULT_KIND;

        private Builder(Object state) {
            this.state = state;
        }

        public Builder choice(String id, Object instructions, Map<String, Object> criteria) {
            return question(id, new JevQuestion.Choice(instructions, criteria));
        }

        public Builder noul(String id, Object instructions) {
            return question(id, new JevQuestion.Noul(instructions));
        }

        public Builder noul(String id, Object instructions, Map<String, Object> criteria) {
            return question(id, new JevQuestion.Noul(instructions, criteria));
        }

        public Builder score(String id, Object instructions, List<Object> levels) {
            return question(id, new JevQuestion.Score(instructions, levels));
        }

        public Builder question(String id, JevQuestion question) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("question id is required");
            }
            if (questions.putIfAbsent(id, question) != null) {
                throw new IllegalArgumentException("duplicate question id: " + id);
            }
            return this;
        }

        /** Usage-accounting label; see {@link JevUsageMeter}. */
        public Builder kind(String kind) {
            this.kind = kind;
            return this;
        }

        public int size() {
            return questions.size();
        }

        public JevRequest build() {
            return new JevRequest(state, questions, kind);
        }
    }
}
