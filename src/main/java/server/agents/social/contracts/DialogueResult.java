package server.agents.social.contracts;

/** Display-only provider result. It deliberately carries no executable action. */
public record DialogueResult(
        String displayText,
        Source source,
        String providerId,
        String fallbackReason,
        long latencyMs) {
    public enum Source {
        DETERMINISTIC,
        MODEL
    }

    public DialogueResult {
        if (displayText == null || displayText.isBlank() || source == null
                || providerId == null || providerId.isBlank() || latencyMs < 0) {
            throw new IllegalArgumentException("Valid dialogue result is required");
        }
        displayText = displayText.trim();
        providerId = providerId.trim();
        fallbackReason = fallbackReason == null ? "" : fallbackReason.trim();
    }

    public static DialogueResult model(String text, String providerId, long latencyMs) {
        return new DialogueResult(text, Source.MODEL, providerId, "", latencyMs);
    }

    public static DialogueResult deterministic(String text, String providerId, String fallbackReason) {
        return new DialogueResult(text, Source.DETERMINISTIC, providerId, fallbackReason, 0L);
    }
}
