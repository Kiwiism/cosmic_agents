package server.agents.progression.questcatalog;

/** Pure catalog eligibility result. Starting and accepting a quest remain external operations. */
public record AgentQuestEligibility(Status status, String reason) {
    public enum Status {
        ELIGIBLE,
        ALREADY_IN_PROGRESS,
        ALREADY_COMPLETED,
        LEVEL_LOCKED,
        JOB_LOCKED,
        PREREQUISITE_LOCKED,
        ACCURACY_INSUFFICIENT,
        INVENTORY_INSUFFICIENT,
        SUPPLIES_INSUFFICIENT,
        CAPABILITY_GATED,
        MANUAL_REVIEW_REQUIRED
    }

    public AgentQuestEligibility {
        reason = reason == null ? "" : reason.trim();
        if (status == null) throw new IllegalArgumentException("quest eligibility status is required");
    }

    public boolean eligible() {
        return status == Status.ELIGIBLE;
    }
}
