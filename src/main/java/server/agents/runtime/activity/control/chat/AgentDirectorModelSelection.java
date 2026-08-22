package server.agents.runtime.activity.control.chat;

/** Untrusted model suggestion before Agent OS action validation. */
public record AgentDirectorModelSelection(
        String actionId,
        String rationale,
        int expectedEnergyDelta,
        String provider,
        long latencyMs) {
    public AgentDirectorModelSelection {
        actionId = text(actionId);
        rationale = text(rationale);
        provider = text(provider);
        expectedEnergyDelta = Math.max(-100, Math.min(100, expectedEnergyDelta));
        if (actionId.isEmpty() || rationale.isEmpty() || latencyMs < 0L) {
            throw new IllegalArgumentException("complete Director model selection is required");
        }
    }
    private static String text(String value) { return value == null ? "" : value.trim(); }
}
