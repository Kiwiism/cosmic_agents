package server.agents.progression.questcatalog;

/** Catalog-level automation readiness, independent of one Agent's current eligibility. */
public enum AgentQuestSelectionDisposition {
    ELIGIBLE,
    CAPABILITY_GATED,
    REVIEW_BLOCKED;

    static AgentQuestSelectionDisposition fromCatalog(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "eligible-now" -> ELIGIBLE;
            case "capability-gated" -> CAPABILITY_GATED;
            default -> REVIEW_BLOCKED;
        };
    }
}
