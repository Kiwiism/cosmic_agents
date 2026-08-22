package server.agents.social.validation;

import server.agents.social.contracts.DialogueRequest;
import server.agents.social.contracts.DialogueResult;

import java.util.Optional;

/** Normalizes model text and rejects output outside the advertised display contract. */
public final class DialogueOutputValidator {
    public Optional<DialogueResult> validate(DialogueRequest request, DialogueResult result) {
        if (request == null || result == null || result.source() != DialogueResult.Source.MODEL) {
            return Optional.empty();
        }
        String text = result.displayText()
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1).trim();
        }
        String prefix = request.context().agentName() + ":";
        if (text.regionMatches(true, 0, prefix, 0, prefix.length())) {
            text = text.substring(prefix.length()).trim();
        }
        if (text.isBlank() || text.length() > request.maxResponseChars()) {
            return Optional.empty();
        }
        return Optional.of(new DialogueResult(
                text,
                result.source(),
                result.providerId(),
                result.fallbackReason(),
                result.latencyMs()));
    }
}
