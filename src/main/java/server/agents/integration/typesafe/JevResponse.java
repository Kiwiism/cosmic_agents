package server.agents.integration.typesafe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** A parsed System One response plus the measured round-trip latency. */
public record JevResponse(String model,
                          Map<String, JevAnswer> answers,
                          long inputTokens,
                          long outputTokens,
                          long latencyMs) {
    public JevResponse {
        model = model == null ? "" : model;
        answers = answers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(answers));
    }

    public Optional<JevAnswer> answer(String questionId) {
        return Optional.ofNullable(answers.get(questionId));
    }

    public double yesProbability(String questionId) {
        return answer(questionId).map(JevAnswer::yesProbability).orElse(0.0d);
    }
}
