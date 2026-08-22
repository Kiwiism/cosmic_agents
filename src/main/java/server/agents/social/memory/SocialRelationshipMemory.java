package server.agents.social.memory;

/** Compact durable relationship summary; raw chat is not part of this record. */
public record SocialRelationshipMemory(
        SocialRelationshipKey key,
        double familiarity,
        double trust,
        double affinity,
        double annoyance,
        long interactionCount,
        String summary,
        long createdAtMs,
        long lastInteractionAtMs,
        long revision) {
    public SocialRelationshipMemory {
        if (key == null || invalid(familiarity) || invalid(trust) || invalid(affinity)
                || invalid(annoyance) || interactionCount < 0 || summary == null
                || summary.length() > 512 || createdAtMs < 0 || lastInteractionAtMs < 0
                || revision < 0) {
            throw new IllegalArgumentException("Valid bounded relationship memory is required");
        }
        summary = summary.trim();
    }

    public static SocialRelationshipMemory neutral(SocialRelationshipKey key, long nowMs) {
        return new SocialRelationshipMemory(key, 0.0, 0.5, 0.5, 0.0,
                0, "No prior relationship history.", nowMs, nowMs, 0);
    }

    public SocialRelationshipMemory recordConversation(long nowMs) {
        long count = interactionCount + 1;
        String nextSummary = count == 1
                ? "Met once in conversation."
                : "Spoken with " + count + " times; relationship remains casual.";
        return new SocialRelationshipMemory(
                key,
                clamp(familiarity + (count <= 5 ? 0.08 : 0.02)),
                trust,
                clamp(affinity + 0.01),
                clamp(annoyance * 0.95),
                count,
                nextSummary,
                createdAtMs,
                nowMs,
                revision + 1);
    }

    private static boolean invalid(double value) {
        return !Double.isFinite(value) || value < 0.0 || value > 1.0;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
