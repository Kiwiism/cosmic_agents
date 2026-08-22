package server.agents.social.provider;

import server.agents.social.contracts.DialogueRequest;
import server.agents.social.contracts.DialogueResult;

/** Always-available catalog provider used when no model is installed or accepted. */
public final class DeterministicDialogueProvider {
    public DialogueResult generate(DialogueRequest request, String fallbackReason) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        int hash = 31 * request.requestId().hashCode() + request.context().agentId();
        int index = Math.floorMod(hash, request.fallbackReplies().size());
        String text = bound(request.fallbackReplies().get(index), request.maxResponseChars());
        return DialogueResult.deterministic(text, "catalog:v1", fallbackReason);
    }

    private static String bound(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        String bounded = text.substring(0, maxChars).trim();
        int lastSpace = bounded.lastIndexOf(' ');
        return lastSpace > maxChars / 2 ? bounded.substring(0, lastSpace) : bounded;
    }
}
