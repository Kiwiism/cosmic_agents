package server.agents.administration;

import java.util.List;

public record AgentCleanSlateResult(
        String resetId,
        boolean success,
        String message,
        AgentCleanSlateTarget target,
        List<String> warnings,
        long executedAtMs) {
    public AgentCleanSlateResult {
        resetId = resetId == null ? "" : resetId.trim();
        message = message == null ? "" : message.trim();
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        if (resetId.isEmpty() || message.isEmpty() || executedAtMs < 0L) {
            throw new IllegalArgumentException("valid clean-slate result is required");
        }
    }
}
