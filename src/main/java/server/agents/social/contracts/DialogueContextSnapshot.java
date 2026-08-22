package server.agents.social.contracts;

import java.util.Map;

/** Safe immutable Agent context; no Cosmic or mutable runtime objects may cross this boundary. */
public record DialogueContextSnapshot(
        int agentId,
        long revision,
        String agentName,
        String profileKey,
        DialogueStyleSnapshot style,
        String activitySummary,
        String relationshipSummary,
        int energyPercent,
        Map<String, String> publicFacts) {
    public static final int MAX_PUBLIC_FACTS = 48;
    public static final int MAX_FACT_CHARS = 256;

    public DialogueContextSnapshot {
        if (agentId <= 0 || revision < 0 || blank(agentName) || blank(profileKey) || style == null
                || blank(activitySummary) || blank(relationshipSummary)
                || energyPercent < 0 || energyPercent > 100) {
            throw new IllegalArgumentException("Valid dialogue context is required");
        }
        agentName = agentName.trim();
        profileKey = profileKey.trim();
        activitySummary = activitySummary.trim();
        relationshipSummary = relationshipSummary.trim();
        publicFacts = publicFacts == null ? Map.of() : Map.copyOf(publicFacts);
        if (publicFacts.size() > MAX_PUBLIC_FACTS
                || publicFacts.entrySet().stream().anyMatch(entry -> invalidFact(entry.getKey(), entry.getValue()))) {
            throw new IllegalArgumentException("Public dialogue facts must be bounded and non-blank");
        }
    }

    private static boolean invalidFact(String key, String value) {
        return blank(key) || blank(value) || key.length() > MAX_FACT_CHARS || value.length() > MAX_FACT_CHARS;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
