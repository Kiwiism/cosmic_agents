package server.agents.runtime.activity.control.chat;

/** One untrusted model ranking before it is joined back to catalog evidence. */
public record AgentDirectorRankedSelection(String actionId, String rationale) {
    public AgentDirectorRankedSelection {
        actionId = text(actionId);
        rationale = text(rationale);
        if (actionId.isEmpty() || rationale.isEmpty()) {
            throw new IllegalArgumentException("complete ranked selection is required");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
