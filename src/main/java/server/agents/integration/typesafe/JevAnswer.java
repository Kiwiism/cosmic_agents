package server.agents.integration.typesafe;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One typed answer. Which fields are present depends on the question type: {@code choice} and
 * {@code probabilities} for a Choice, {@code noul} for a Noul, {@code score}, {@code legend} and
 * {@code probabilities} for a Score. {@code confidence} accompanies Choice and Score answers and
 * summarises how concentrated the distribution is; it is not a correctness guarantee.
 */
public record JevAnswer(String type,
                        String choice,
                        Map<String, Double> probabilities,
                        Double noul,
                        Double score,
                        Map<String, String> legend,
                        Double confidence) {
    public JevAnswer {
        type = type == null ? "" : type;
        probabilities = probabilities == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(probabilities));
        legend = legend == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(legend));
    }

    public boolean isChoice() {
        return "choice".equals(type);
    }

    public boolean isNoul() {
        return "noul".equals(type);
    }

    public boolean isScore() {
        return "score".equals(type);
    }

    /** Probability of {@code option} for a Choice or Score answer, {@code 0} when unknown. */
    public double probabilityOf(String option) {
        Double value = probabilities.get(option);
        return value == null ? 0.0d : value;
    }

    /** Noul probability, {@code 0} when this is not a Noul answer. */
    public double yesProbability() {
        return noul == null ? 0.0d : noul;
    }

    public double confidenceOrZero() {
        return confidence == null ? 0.0d : confidence;
    }

    public double scoreOrZero() {
        return score == null ? 0.0d : score;
    }

    /** Highest-probability option, useful when the API's {@code choice} field is absent. */
    public Optional<String> mostLikelyOption() {
        if (choice != null && !choice.isBlank()) {
            return Optional.of(choice);
        }
        return probabilities.entrySet().stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue))
                .map(Map.Entry::getKey);
    }
}
