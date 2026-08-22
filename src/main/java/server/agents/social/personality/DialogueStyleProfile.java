package server.agents.social.personality;

import server.agents.social.contracts.DialogueStyleSnapshot;

import java.util.List;

/** Versioned communication style associated with an Agent personality identity. */
public record DialogueStyleProfile(
        String personalityProfileId,
        String styleId,
        int version,
        String toneInstruction,
        int formality,
        int verbosity,
        int slangFrequency,
        int lowercaseFrequency,
        int emoticonFrequency,
        List<String> slangExamples) {
    public DialogueStyleProfile {
        if (personalityProfileId == null || personalityProfileId.isBlank()) {
            throw new IllegalArgumentException("personality profile id is required");
        }
        personalityProfileId = personalityProfileId.trim();
        new DialogueStyleSnapshot(styleId, version, toneInstruction, formality, verbosity,
                slangFrequency, lowercaseFrequency, emoticonFrequency, slangExamples);
        slangExamples = slangExamples == null ? List.of() : List.copyOf(slangExamples);
    }

    public DialogueStyleSnapshot snapshot() {
        return new DialogueStyleSnapshot(styleId, version, toneInstruction, formality, verbosity,
                slangFrequency, lowercaseFrequency, emoticonFrequency, slangExamples);
    }
}
