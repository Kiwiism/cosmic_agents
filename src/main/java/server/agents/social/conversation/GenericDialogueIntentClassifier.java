package server.agents.social.conversation;

import java.util.Locale;

/** Small deterministic classifier for the generic-chat rollout. */
public final class GenericDialogueIntentClassifier {
    public String classify(String message) {
        String text = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        if (text.matches("^(hi|hey|hello|yo|sup)([!.?, ].*)?$")) {
            return "casual.greeting";
        }
        if (text.contains("how are you") || text.contains("how r u") || text.contains("hows it going")) {
            return "casual.status";
        }
        if (text.matches(".*\\b(thanks|thank you|ty|thx)\\b.*")) {
            return "casual.thanks";
        }
        if (text.matches(".*\\b(bye|goodbye|cya|see ya|later)\\b.*")) {
            return "casual.goodbye";
        }
        if (text.endsWith("?")) {
            return "casual.question";
        }
        return "casual.general";
    }
}
