package server.agents.social.contracts;

/** One bounded, immutable turn exposed to a dialogue provider. */
public record ConversationTurn(
        Role role,
        String speakerName,
        String text,
        long occurredAtMs) {
    public static final int MAX_TEXT_CHARS = 512;

    public enum Role {
        HUMAN,
        AGENT
    }

    public ConversationTurn {
        if (role == null || blank(speakerName) || blank(text)
                || text.length() > MAX_TEXT_CHARS || occurredAtMs < 0) {
            throw new IllegalArgumentException("Valid bounded conversation turn is required");
        }
        speakerName = speakerName.trim();
        text = text.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
