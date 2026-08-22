package server.agents.administration;

import java.util.List;

public record AgentCleanSlatePreview(
        String resetId,
        AgentCleanSlateTarget target,
        boolean eligible,
        List<String> blockers,
        List<String> resetScope,
        List<String> retainedScope,
        String confirmationToken,
        String confirmationPhrase,
        long expiresAtMs) {
    public AgentCleanSlatePreview {
        resetId = resetId == null ? "" : resetId.trim();
        confirmationToken = confirmationToken == null ? "" : confirmationToken.trim();
        confirmationPhrase = confirmationPhrase == null ? "" : confirmationPhrase.trim();
        blockers = List.copyOf(blockers == null ? List.of() : blockers);
        resetScope = List.copyOf(resetScope == null ? List.of() : resetScope);
        retainedScope = List.copyOf(retainedScope == null ? List.of() : retainedScope);
        if (resetId.isEmpty() || target == null || expiresAtMs < 0L) {
            throw new IllegalArgumentException("valid clean-slate preview is required");
        }
        if (eligible && (confirmationToken.isEmpty() || confirmationPhrase.isEmpty()
                || !blockers.isEmpty())) {
            throw new IllegalArgumentException("eligible reset preview requires confirmation data");
        }
    }
}
