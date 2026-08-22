package server.agents.social.contracts;

import java.util.List;

/** Immutable presentation style supplied to deterministic and model providers. */
public record DialogueStyleSnapshot(
        String styleId,
        int version,
        String toneInstruction,
        int formality,
        int verbosity,
        int slangFrequency,
        int lowercaseFrequency,
        int emoticonFrequency,
        List<String> slangExamples) {
    public DialogueStyleSnapshot {
        if (blank(styleId) || version <= 0 || blank(toneInstruction)
                || invalid(formality) || invalid(verbosity) || invalid(slangFrequency)
                || invalid(lowercaseFrequency) || invalid(emoticonFrequency)) {
            throw new IllegalArgumentException("Valid bounded dialogue style is required");
        }
        styleId = styleId.trim();
        toneInstruction = toneInstruction.trim();
        slangExamples = slangExamples == null ? List.of() : List.copyOf(slangExamples);
        if (slangExamples.size() > 12
                || slangExamples.stream().anyMatch(value -> blank(value) || value.length() > 48)) {
            throw new IllegalArgumentException("Dialogue slang examples must be bounded");
        }
    }

    private static boolean invalid(int value) {
        return value < 0 || value > 100;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
