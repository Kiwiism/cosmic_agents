package server.agents.social.contracts;

import java.util.List;

/** Provider-neutral request containing only bounded immutable projections. */
public record DialogueRequest(
        String requestId,
        String intentKey,
        String speakerName,
        String speakerText,
        DialogueContextSnapshot context,
        List<ConversationTurn> recentTurns,
        List<String> fallbackReplies,
        boolean observed,
        int maxResponseChars,
        long timeoutMs) {
    public static final int MAX_RECENT_TURNS = 8;
    public static final int MAX_FALLBACK_REPLIES = 16;

    public DialogueRequest {
        if (blank(requestId) || blank(intentKey) || blank(speakerName) || blank(speakerText)
                || speakerText.length() > ConversationTurn.MAX_TEXT_CHARS || context == null
                || maxResponseChars < 1 || maxResponseChars > 512 || timeoutMs < 1) {
            throw new IllegalArgumentException("Valid bounded dialogue request is required");
        }
        requestId = requestId.trim();
        intentKey = intentKey.trim();
        speakerName = speakerName.trim();
        speakerText = speakerText.trim();
        recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
        fallbackReplies = fallbackReplies == null ? List.of() : List.copyOf(fallbackReplies);
        if (recentTurns.size() > MAX_RECENT_TURNS
                || fallbackReplies.isEmpty()
                || fallbackReplies.size() > MAX_FALLBACK_REPLIES
                || fallbackReplies.stream().anyMatch(reply -> blank(reply) || reply.length() > 512)) {
            throw new IllegalArgumentException("Dialogue history and fallbacks must be bounded");
        }
        fallbackReplies = fallbackReplies.stream().map(String::trim).toList();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
